(ns glimmer-gl.shader-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [glimmer-gl.shader :as sh]))

(def ^:private spec
  {:version  "330 core"
   :uniforms {:u_mvp :mat4 :u_time [:float 0.0]}
   :attribs  {:a_pos [:vec3 0] :a_normal [:vec3 1]}
   :varying  {:v_normal :vec3}
   :fs-out   {:frag :vec4}
   :vs-main  [[:set :gl_Position [:* :u_mvp [:vec4 :a_pos 1.0]]]
              [:set :v_normal :a_normal]]
   :fs-main  [[:set :frag [:vec4 :v_normal 1.0]]]})

(deftest declarations-are-generated-from-data
  (let [{:keys [vs-src fs-src]} (sh/sources spec)]
    (testing "version directive leads both stages"
      (is (str/starts-with? vs-src "#version 330 core\n"))
      (is (str/starts-with? fs-src "#version 330 core\n")))
    (testing "uniforms declared in both stages"
      (is (str/includes? vs-src "uniform mat4 u_mvp;"))
      (is (str/includes? fs-src "uniform float u_time;")))
    (testing "attributes are GL3 layout-qualified, vertex stage only"
      (is (str/includes? vs-src "layout(location=0) in vec3 a_pos;"))
      (is (str/includes? vs-src "layout(location=1) in vec3 a_normal;"))
      (is (not (str/includes? fs-src "a_pos"))))
    (testing "varying is out in vs, in in fs"
      (is (str/includes? vs-src "out vec3 v_normal;"))
      (is (str/includes? fs-src "in vec3 v_normal;")))
    (testing "compiled main bodies are present"
      (is (str/includes? vs-src "gl_Position = u_mvp * vec4(a_pos, 1.0);"))
      (is (str/includes? fs-src "frag = vec4(v_normal, 1.0);")))))

(deftest sources-compiles-data-main-and-emits-out
  ;; :vs-main / :fs-main are statement vectors compiled to void main();
  ;; :fs-out declares the fragment outputs.
  (let [{:keys [vs-src fs-src]} (sh/sources spec)]
    (testing "fragment outputs declared via :fs-out"
      (is (str/includes? fs-src "out vec4 frag;")))
    (testing "the vertex body compiles to void main()"
      (is (str/includes? vs-src "void main() {"))
      (is (str/includes? vs-src "v_normal = a_normal;")))
    (testing "the fragment body compiles to void main()"
      (is (str/includes? fs-src "frag = vec4(v_normal, 1.0);")))))

(deftest manipulate-spec-with-map-ops
  ;; composition/manipulation is just data: assoc-in a new attribute
  (let [s (assoc-in spec [:attribs :a_color] [:vec4 2])
        {:keys [vs-src]} (sh/sources s)]
    (is (str/includes? vs-src "layout(location=2) in vec4 a_color;"))))

(deftest merge-specs-concatenates-main-statements
  ;; composition: each "module" contributes its own uniforms + data statements;
  ;; :fs-main vectors concatenate in order, so a module's bindings precede the
  ;; main statements that consume them — the basis of reusable shading modules.
  (let [base    {:version "330 core"
                 :uniforms {:u_mvp :mat4}
                 :attribs  {:a_pos [:vec3 0]}
                 :varying  {:v_obj :vec3}
                 :vs-main  [[:set :v_obj :a_pos]
                            [:set :gl_Position [:* :u_mvp [:vec4 :a_pos 1.0]]]]}
        diffuse {:uniforms {:u_color :vec3}
                 :fs-main  [[:let :col :vec3 :u_color]]}     ; module binds col
        main    {:fs-out   {:frag :vec4}
                 :fs-main  [[:set :frag [:vec4 :col 1.0]]]}  ; consumes col
        spec    (sh/merge-specs base diffuse main)
        {:keys [fs-src vs-src]} (sh/sources spec)]
    (testing "uniforms and outputs from every fragment are declared"
      (is (str/includes? vs-src "uniform mat4 u_mvp;"))
      (is (str/includes? fs-src "uniform vec3 u_color;"))
      (is (str/includes? fs-src "out vec4 frag;")))
    (testing "a module's bindings precede the main that consumes them"
      (is (< (str/index-of fs-src "col = u_color")
             (str/index-of fs-src "frag = vec4"))))
    (testing "version carried through"
      (is (str/starts-with? fs-src "#version 330 core\n")))))

(deftest prelude-injected-before-decls
  (let [s (assoc spec :prelude "#define PI 3.14159\n")
        {:keys [fs-src]} (sh/sources s)]
    (is (< (str/index-of fs-src "#define PI")
           (str/index-of fs-src "uniform")))))

;; === shader IR: bodies as composable data expressions =========================
;;
;; A shader body is a vector of *statements*; each statement's expressions are
;; data nodes a pure compiler turns into GLSL. No GLSL strings in specs.

(deftest compile-expr-emits-each-node-form
  (testing "a keyword is a named reference (uniform/varying/local)"
    (is (= "u_mvp" (sh/compile-expr :u_mvp))))
  (testing "a number is a float literal — whole numbers keep the .0"
    (is (= "0.5"  (sh/compile-expr 0.5)))
    (is (= "1.0"  (sh/compile-expr 1)))
    (is (= "32.0" (sh/compile-expr 32.0))))
  (testing "[:. x comps] is a swizzle"
    (is (= "v_lpos.xyz" (sh/compile-expr [:. :v_lpos :xyz])))
    (is (= "sc.xy"      (sh/compile-expr [:. :sc :xy])))
    (testing "a compound base is parenthesized so the swizzle binds to the whole expr"
      (is (= "(u_model * vec4(a_pos, 1.0)).xyz"
             (sh/compile-expr [:. [:* :u_model [:vec4 :a_pos 1.0]] :xyz])))))
  (testing "[:neg x] is a parenthesized unary minus"
    (is (= "(-(u_light_dir))" (sh/compile-expr [:neg :u_light_dir]))))
  (testing "arithmetic infix ops join their args (top level needs no parens)"
    (is (= "u_camera_pos - v_world_pos" (sh/compile-expr [:- :u_camera_pos :v_world_pos])))
    (is (= "a + b + c" (sh/compile-expr [:+ :a :b :c])))
    (testing "an infix operand that is itself infix gets wrapped (precedence)"
      (is (= "u_ambient + (u_light_color * diff)"
             (sh/compile-expr [:+ :u_ambient [:* :u_light_color :diff]])))))
  (testing "comparison + logical ops use GLSL symbols"
    (is (= "sc.x >= 0.0" (sh/compile-expr [:>= [:. :sc :x] 0.0])))
    (is (= "sc.x <= 1.0" (sh/compile-expr [:<= [:. :sc :x] 1.0])))
    (is (= "(sc.x >= 0.0) && (sc.x <= 1.0)"
           (sh/compile-expr [:and [:>= [:. :sc :x] 0.0] [:<= [:. :sc :x] 1.0]]))))
  (testing "any other op is a function call, args comma-separated"
    (is (= "normalize(v_normal)"            (sh/compile-expr [:normalize :v_normal])))
    (is (= "max(dot(N, L), 0.0)"            (sh/compile-expr [:max [:dot :N :L] 0.0])))
    (is (= "vec4(a_pos, 1.0)"               (sh/compile-expr [:vec4 :a_pos 1.0])))
    (is (= "mix(u_fog_color, col, fog)"     (sh/compile-expr [:mix :u_fog_color :col :fog])))
    (is (= "texture(u_shadow_map, vec3(sc.xy, sc.z - u_shadow_bias))"
           (sh/compile-expr [:texture :u_shadow_map
                             [:vec3 [:. :sc :xy] [:- [:. :sc :z] :u_shadow_bias]]])))))

(deftest compile-stmt-emits-each-form
  (testing ":let declares a typed local"
    (is (= "vec3 N = normalize(v_normal);"
           (sh/compile-stmt [:let :N :vec3 [:normalize :v_normal]])))
    (is (= "float diff = max(dot(N, L), 0.0);"
           (sh/compile-stmt [:let :diff :float [:max [:dot :N :L] 0.0]]))))
  (testing ":set assigns an existing (out / varying / local) target"
    (is (= "frag_color = vec4(col, 1.0);"
           (sh/compile-stmt [:set :frag_color [:vec4 :col 1.0]]))))
  (testing ":if is a braced block, body indented one level deeper"
    (is (= (str "if (shadow < 1.0) {\n"
                "  col = vec3(0.0);\n"
                "}")
           (sh/compile-stmt [:if [:< :shadow 1.0]
                             [[:set :col [:vec3 0.0]]]]))))
  (testing ":if with an else branch"
    (is (= (str "if (c) {\n  a = 1.0;\n} else {\n  a = 2.0;\n}")
           (sh/compile-stmt [:if :c [[:set :a 1.0]] [[:set :a 2.0]]])))))

(deftest compile-main-wraps-statements-in-void-main
  (is (= "void main() {}"
         (sh/compile-main [])))
  (is (= (str "void main() {\n"
              "  vec3 N = normalize(v_normal);\n"
              "  frag_color = vec4(col, 1.0);\n"
              "}")
         (sh/compile-main [[:let :N :vec3 [:normalize :v_normal]]
                          [:set :frag_color [:vec4 :col 1.0]]]))))
