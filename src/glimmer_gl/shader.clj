(ns glimmer-gl.shader
  "Shaders as data, ported from thi.ng/geom's shader-spec model
  (thi.ng.geom.gl.shaders). A shader is a plain map declaring its *interface* —

    {:version  \"330 core\"
     :uniforms {:u_mvp :mat4, :u_time [:float 0.0]}   ; type, or [type default]
     :attribs  {:a_pos [:vec3 0], :a_normal [:vec3 1]} ; [type location]
     :varying  {:v_normal :vec3}                       ; out in vs, in in fs
     :fs-out   {:frag_color :vec4}                     ; fragment outputs
     :prelude  \"#define PI 3.14159\\n\"               ; optional
     :vs-main  [[:set :gl_Position [:* :u_mvp [:vec4 :a_pos 1.0]]] …]
     :fs-main  [[:let :n :vec3 [:normalize :v_normal]] …]}

   The `uniform`/`in`/`out` declarations are *generated* from the maps, and the
   bodies are data — vectors of statements built from expression nodes that
   `compile-expr` / `compile-stmt` turn into GLSL (see those fns for the node
   forms). Because the spec is just data, you compose shaders with ordinary map
   ops and `merge-specs`: a reusable lighting module is a map of its own
   uniforms plus the statements that compute its term, merged before the
   statements that consume it. Nothing touches OpenGL until `program` compiles
   the GLSL.

  Targets the GL3 core profile (GtkGLArea is 3.2+): attributes use
  `layout(location=N) in …`, varyings use `out`/`in`."
  (:require [clojure.string :as str]
            [glimmer-gl.gl :as gl]
            [glimmer-gl.matrix :as mat]
            [jolt.ffi :as ffi]))

;; --- source generation (pure — no GL context needed) ------------------------
(defn- type-name
  "The GLSL type name for a spec entry value, which is either a bare type
  keyword (:vec3) or a [type extra] pair ([:vec3 0])."
  [t] (name (if (sequential? t) (first t) t)))

(defn glsl-vars
  "Declare each entry of `coll` (name -> type|[type ..]) with `qualifier`
  (\"uniform\", \"in\", \"out\"). Returns the concatenated declaration lines."
  [qualifier coll]
  (->> coll
       (map (fn [[id t]] (str qualifier " " (type-name t) " " (name id) ";\n")))
       (apply str)))

(defn glsl-attribs
  "Declare vertex attributes GL3-style. A [type location] pair emits an explicit
  `layout(location=N) in …`; a bare type emits a plain `in …`."
  [coll]
  (->> coll
       (map (fn [[id t]]
              (if (sequential? t)
                (str "layout(location=" (nth t 1) ") in " (name (first t)) " " (name id) ";\n")
                (str "in " (name t) " " (name id) ";\n"))))
       (apply str)))

;; === shader IR: bodies as composable data expressions ========================
;; A body is a vector of statements; each statement's expressions are data nodes
;; a pure compiler turns into GLSL. No GLSL strings in specs.

(def ^:private infix-op
  "GLSL symbol for each infix operator node. Any op not in this map is emitted
  as a function/constructor call."
  {:+ "+" :- "-" :* "*" :/ "/"
   :> ">" :< "<" :>= ">=" :<= "<=" :== "==" :!= "!="
   :and "&&" :or "||"})

(defn- fmt-num
  "Emit a GLSL float literal: coerce to double and ensure a fractional part
  (GLSL floats need the dot — a bare `1` would be an int)."
  [n]
  (let [s (str (double n))]
    (if (or (str/includes? s ".") (str/includes? s "e") (str/includes? s "E"))
      s
      (str s ".0"))))

(declare compile-expr)

(defn- compile-call [op args]
  (str (name op) "(" (str/join ", " (map compile-expr args)) ")"))

(defn compile-expr
  "Compile a shader IR expression node to a GLSL string. Pure — no GL context.

  Node forms:
    :name                     named ref (uniform / varying / attrib / local)
    0.5                       float literal (whole numbers gain `.0`)
    [:. x :xyz]               swizzle: x.xyz
    [:neg x]                  unary minus: (-(x))
    [:+ a b …] [:- :* :/ …]   arithmetic infix; infix operands are parenthesized
    [:>= :<= :> :< :== :!=]   comparison infix
    [:and …] [:or …]          logical infix
    [:fn arg …] [:vec3 …] …   function/constructor call: fn(arg, …)"
  [node]
  (cond
    (keyword? node) (name node)
    (number? node)  (fmt-num node)
    (vector? node)  (let [op (nth node 0)]
                      (cond
                        (= op :.)   (let [base (nth node 1)
                                          c    (compile-expr base)]
                                      ;; parenthesize a compound base so the
                                      ;; swizzle binds to the whole expression,
                                      ;; not its last factor
                                      (str (if (or (keyword? base) (number? base))
                                             c (str "(" c ")"))
                                           "." (name (nth node 2))))
                        (= op :neg) (str "(-(" (compile-expr (nth node 1)) "))")
                        (infix-op op) (let [sep  (str " " (infix-op op) " ")
                                            part (fn [x]
                                                   (let [c (compile-expr x)]
                                                     ;; wrap only operands that are
                                                     ;; themselves infix — preserves
                                                     ;; precedence without drowning
                                                     ;; the output in parens
                                                     (if (and (vector? x) (infix-op (nth x 0)))
                                                       (str "(" c ")")
                                                       c)))]
                                        (str/join sep (map part (rest node))))
                        :else (compile-call op (rest node))))
    :else (throw (ex-info "shader: cannot compile expression node" {:node node}))))

(defn- indent-lines
  "Prefix every line of `s` with `n` spaces."
  [s n]
  (let [pad (str/join (repeat n " "))]
    (->> (str/split s #"\n")
         (map #(str pad %))
         (str/join "\n"))))

(declare compile-stmt)

(defn- compile-stmts [stmts]
  (str/join "\n" (map compile-stmt stmts)))

(defn compile-stmt
  "Compile one shader IR statement to GLSL. Pure.

  Statement forms:
    [:let name type expr]      declare+bind a local:  <type> <name> = <expr>;
    [:set name expr]           assign existing target: <name> = <expr>;
    [:if cond [then…]]        if (<cond>) { <then…> }
    [:if cond [then…] [else…]] … with an else block"
  [node]
  (let [tag (nth node 0)]
    (cond
      (= tag :let) (let [sym (nth node 1) ty (nth node 2) x (nth node 3)]
                    (str (name ty) " " (name sym) " = " (compile-expr x) ";"))
      (= tag :set) (let [sym (nth node 1) x (nth node 2)]
                    (str (name sym) " = " (compile-expr x) ";"))
      (= tag :if)  (let [cond (nth node 1) then (nth node 2)
                         body (str "if (" (compile-expr cond) ") {\n"
                                   (indent-lines (compile-stmts then) 2) "\n}")]
                    (if-let [els (nth node 3 nil)]
                      (str body " else {\n" (indent-lines (compile-stmts els) 2) "\n}")
                      body))
      :else (throw (ex-info "shader: cannot compile statement node" {:node node})))))

(defn compile-main
  "Wrap a vector of statements in `void main() { … }`, each indented two spaces.
  An empty body yields `void main() {}`."
  [stmts]
  (if (empty? stmts)
    "void main() {}"
    (str "void main() {\n"
         (indent-lines (compile-stmts stmts) 2)
         "\n}")))

(defn merge-specs
  "Combine shader spec fragments into one — assemble a shader from reusable
  *modules*, each a map of its own uniforms/varyings plus the data statements
  that use them:

    (merge-specs base shadow-module lighting-module fog-module)

  Merge rules: :uniforms / :attribs / :varying / :fs-out maps merge (later wins
  on a key conflict); :vs-main / :fs-main statement vectors concatenate in
  argument order, so a module's declarations land before the statements that use
  them; :prelude concatenates; :precision takes the last one set; :version takes
  the last one set."
  [& specs]
  (reduce (fn [a b]
            {:version   (or (:version b) (:version a))
             :precision (or (:precision b) (:precision a))
             :prelude   (str (:prelude a) (:prelude b))
             :uniforms  (merge (:uniforms a) (:uniforms b))
             :attribs   (merge (:attribs a) (:attribs b))
             :varying   (merge (:varying a) (:varying b))
             :fs-out    (merge (:fs-out a) (:fs-out b))
             :vs-main   (into (vec (:vs-main a)) (:vs-main b))
             :fs-main   (into (vec (:fs-main a)) (:fs-main b))})
           {} specs))

(def ^:private gles-sampler-types
  "Sampler types that have no default precision in a GLSL ES fragment shader."
  #{:sampler2D :sampler-2d :sampler2DShadow :sampler-2d-shadow
    :samplerCube :sampler-cube})

(defn detect-profile
  "Classify a GL_VERSION string (from glGetString GL_VERSION) as :gles when the
  context is OpenGL ES, otherwise :core. nil → :core (the safe desktop default,
  so paths without a current context are unchanged). Pure."
  [version-str]
  (if (and version-str (str/includes? version-str "ES"))
    :gles
    :core))

(defn adapt-spec
  "Retarget `spec` for a GLSL `profile` (:core or :gles). :core is identity.
  :gles switches the #version to ES 3.00 and sets the :precision key with the
  qualifiers the ES fragment stage needs — highp float/int always, plus highp for
  each sampler type the spec declares (ES fragment shaders have no default sampler
  precision). Pure; call without a GL context."
  [spec profile]
  (if (= profile :gles)
    (let [type-of   (fn [v] (if (sequential? v) (first v) v))
          samplers  (set (filter gles-sampler-types
                                 (map type-of (vals (:uniforms spec)))))
          precision (str "precision highp float;\n"
                         "precision highp int;\n"
                         (str/join (map #(str "precision highp " (name %) ";\n")
                                        samplers)))]
      (assoc spec
             :version "300 es"
             :precision precision))
    spec))

(defn sources
  "Render a shader spec to {:vs-src :fs-src} GLSL strings: `#version`, optional
  `:precision` (GLES), generated uniform declarations (before `:prelude` so helper
  functions can reference them), `:prelude`, then varying/attribute/output decls,
  then the compiled data bodies (:vs-main / :fs-main statements → void main()).
  Pure — call it without a GL context (handy for tests and inspection)."
  [{:keys [vs-main fs-main fs-out uniforms attribs varying prelude precision version]
    :or {version "330 core"}}]
  ;; Emit uniforms before prelude so helper functions that reference them
  ;; (e.g. a palette() that uses u_scale declared below) don't produce
  ;; "undeclared identifier" errors on strict GLSL compilers (Apple's).
  (let [head (str "#version " version "\n"
                  (or precision "")
                  (glsl-vars "uniform" uniforms)
                  (or prelude ""))]
    {:vs-src (str head (glsl-vars "out" varying) (glsl-attribs attribs)
                  (compile-main (or vs-main [])))
     :fs-src (str head (glsl-vars "in" varying) (glsl-vars "out" fs-out)
                  (compile-main (or fs-main [])))}))

;; --- compilation (needs a current GL context) -------------------------------
(defn- located-uniforms [prog uniforms]
  (into {} (map (fn [[id t]]
                  (let [[type default] (if (sequential? t) t [t])]
                    [id {:loc     (gl/gl-get-uniform-location prog (name id))
                         :type    type
                         :default default}]))
                uniforms)))

(defn- located-attribs [prog attribs]
  (into {} (map (fn [[id t]]
                  (let [type (if (sequential? t) (first t) t)]
                    [id {:loc  (gl/gl-get-attrib-location prog (name id))
                         :type type}]))
                attribs)))

(defn program
  "Compile and link `spec` into a GL program (needs a current GL context).
  Returns the spec enriched with :program (the GL id), and :uniforms / :attribs
  remapped to {name {:loc … :type … :default …}} so callers set them by name.
  Throws if compilation/linking fails (gl/make-program prints the info log)."
  [spec]
  ;; Auto-detect desktop vs ES from the current GL context: some Linux GTK4
  ;; setups expose an ES-only driver that rejects #version 330 core.
  (let [adapted (adapt-spec spec (detect-profile (gl/gl-get-string* gl/GL-VERSION)))
        {:keys [vs-src fs-src]} (sources adapted)]
    (if-let [prog (gl/make-program vs-src fs-src)]
      (assoc spec
             :program  prog
             :uniforms (located-uniforms prog (:uniforms spec))
             :attribs  (located-attribs  prog (:attribs spec)))
      (throw (ex-info "shader: program failed to compile/link" {})))))

(defn uniform-loc [compiled id] (get-in compiled [:uniforms id :loc]))
(defn attrib-loc  [compiled id] (get-in compiled [:attribs id :loc]))

(defn set-uniform!
  "Upload one uniform by name on a compiled shader, dispatching on its declared
  type. :mat4 accepts a glimmer-gl.matrix Matrix44 or a seq of 16 numbers; vector
  types accept a seq of components; :float/:int a number. Unknown names are
  ignored (the uniform may have been optimized out by the GLSL compiler, giving
  loc -1)."
  [compiled id v]
  (when-let [u (get-in compiled [:uniforms id])]
    (let [loc (:loc u)]
      (when (>= loc 0)
        (case (:type u)
          :float (gl/gl-uniform-1f loc (double v))
          :int   (gl/gl-uniform-1i loc (int v))
          ;; samplers are set with uniform1i whose value is the texture unit
          (:sampler2D :sampler-2d :sampler2DShadow :sampler-2d-shadow)
          (gl/gl-uniform-1i loc (int v))
          :vec2  (let [[a b] v]     (gl/gl-uniform-2f loc (double a) (double b)))
          :vec3  (let [[a b c] v]   (gl/gl-uniform-3f loc (double a) (double b) (double c)))
          :vec4  (let [[a b c d] v] (gl/gl-uniform-4f loc (double a) (double b) (double c) (double d)))
          :mat4  (let [ptr (gl/write-floats (if (sequential? v) v (mat/->vec v)))]
                   (gl/gl-uniform-matrix4fv loc 1 gl/GL-FALSE ptr)
                   (ffi/free ptr))
          nil)))))

(defn set-uniforms!
  "Upload a map of {name value} on a compiled shader (calls set-uniform! for each)."
  [compiled m]
  (doseq [[id v] m] (set-uniform! compiled id v))
  compiled)
