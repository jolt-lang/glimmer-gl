(ns glimmer-gl.shader
  "Shaders as data, ported from thi.ng/geom's shader-spec model
  (thi.ng.geom.gl.shaders). A shader is a plain map declaring its *interface* —

    {:version  \"330 core\"
     :uniforms {:u_mvp :mat4, :u_time [:float 0.0]}   ; type, or [type default]
     :attribs  {:a_pos [:vec3 0], :a_normal [:vec3 1]} ; [type location]
     :varying  {:v_normal :vec3}
     :prelude  \"#define PI 3.14159\\n\"               ; optional
     :vs <glsl>      ; a string, or a seq of snippets joined with blank lines
     :fs <glsl>}

  The `uniform`/`in`/`out` declarations are *generated* from the maps, so the
  bodies only contain helper functions and `main()`. Because the spec is just
  data, you compose and manipulate shaders with ordinary map ops — `assoc` a
  uniform, `merge` two specs, swap a body, or build `:fs` from a vector of
  reusable GLSL snippets (a palette fn, a noise fn, the main fn). Nothing touches
  OpenGL until `program` renders the spec to GLSL strings and compiles it.

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

(defn- body
  "A GLSL body is a string, or a seq of snippets joined by blank lines — the
  latter is how reusable functions compose into one stage."
  [x] (if (sequential? x) (str/join "\n\n" x) (or x "")))

(defn- snippets [x] (cond (nil? x) [] (sequential? x) (vec x) :else [x]))

(defn merge-specs
  "Combine shader spec fragments into one. Lets you build a shader from reusable
  *modules* — each a map carrying its own uniforms plus the GLSL function(s) that
  use them — and assemble them as data:

    (merge-specs base plasma-module stripes-module main)

  Merge rules: :uniforms / :attribs / :varying maps merge (later wins on a key
  conflict); :vs and :fs snippet lists concatenate in argument order, so a
  module's helper functions land before the main() that calls them; :prelude
  concatenates; :version takes the last one set."
  [& specs]
  (reduce (fn [a b]
            {:version  (or (:version b) (:version a))
             :prelude  (str (:prelude a) (:prelude b))
             :uniforms (merge (:uniforms a) (:uniforms b))
             :attribs  (merge (:attribs a) (:attribs b))
             :varying  (merge (:varying a) (:varying b))
             :vs (into (snippets (:vs a)) (snippets (:vs b)))
             :fs (into (snippets (:fs a)) (snippets (:fs b)))})
          {} specs))

(defn sources
  "Render a shader spec to {:vs-src :fs-src} GLSL strings: `#version`, optional
  `:prelude`, generated uniform/varying/attribute declarations, then the bodies.
  Pure — call it without a GL context (handy for tests and inspection)."
  [{:keys [vs fs uniforms attribs varying prelude version]
    :or {version "330 core"}}]
  (let [head (str "#version " version "\n" (or prelude "")
                  (glsl-vars "uniform" uniforms))]
    {:vs-src (str head (glsl-vars "out" varying) (glsl-attribs attribs) (body vs))
     :fs-src (str head (glsl-vars "in" varying) (body fs))}))

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
  (let [{:keys [vs-src fs-src]} (sources spec)]
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
