(ns glimmer-gl.shader-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [glimmer-gl.shader :as sh]))

(def ^:private spec
  {:version  "330 core"
   :uniforms {:u_mvp :mat4 :u_time [:float 0.0]}
   :attribs  {:a_pos [:vec3 0] :a_normal [:vec3 1]}
   :varying  {:v_normal :vec3}
   :vs "void main(){ gl_Position = u_mvp * vec4(a_pos,1.0); v_normal = a_normal; }"
   :fs "void main(){ frag = vec4(v_normal, 1.0); }"})

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
    (testing "bodies are appended"
      (is (str/includes? vs-src "gl_Position"))
      (is (str/includes? fs-src "frag = vec4")))))

(deftest bodies-compose-from-snippets
  ;; :fs as a seq of snippets is joined — this is how reusable GLSL functions
  ;; (a palette fn, the main fn) compose into one stage.
  (let [s (assoc spec :fs ["vec3 palette(float t){ return vec3(t); }"
                           "void main(){ frag = vec4(palette(0.5),1.0); }"])
        {:keys [fs-src]} (sh/sources s)]
    (is (str/includes? fs-src "vec3 palette(float t)"))
    (is (str/includes? fs-src "void main()"))
    ;; the palette declaration must precede its use in main
    (is (< (str/index-of fs-src "vec3 palette")
           (str/index-of fs-src "void main")))))

(deftest manipulate-spec-with-map-ops
  ;; composition/manipulation is just data: assoc-in a new attribute
  (let [s (assoc-in spec [:attribs :a_color] [:vec4 2])
        {:keys [vs-src]} (sh/sources s)]
    (is (str/includes? vs-src "layout(location=2) in vec4 a_color;"))))

(deftest merge-specs-composes-modules
  ;; two self-contained modules (each: its own uniform + a GLSL function) plus a
  ;; main that blends them — combined as data.
  (let [base    {:version "330 core"
                 :uniforms {:u_mvp :mat4}
                 :attribs  {:a_pos [:vec3 0]}
                 :varying  {:v_obj :vec3}
                 :vs "void main(){ v_obj = a_pos; gl_Position = u_mvp*vec4(a_pos,1.0); }"}
        modA    {:uniforms {:u_a :float}
                 :fs ["vec3 colorA(vec3 p){ return vec3(u_a); }"]}
        modB    {:uniforms {:u_b :float}
                 :fs ["vec3 colorB(vec3 p){ return vec3(u_b); }"]}
        main    {:uniforms {:u_mix :float}
                 :fs ["out vec4 frag;"
                      "void main(){ frag = vec4(mix(colorA(v_obj),colorB(v_obj),u_mix),1.0); }"]}
        spec    (sh/merge-specs base modA modB main)
        {:keys [fs-src vs-src]} (sh/sources spec)]
    (testing "uniforms from every module are declared"
      (is (every? #(str/includes? fs-src (str "uniform float " %)) ["u_a" "u_b" "u_mix"]))
      (is (str/includes? vs-src "uniform mat4 u_mvp;")))
    (testing "both module functions and the main are present"
      (is (str/includes? fs-src "vec3 colorA"))
      (is (str/includes? fs-src "vec3 colorB"))
      (is (str/includes? fs-src "void main()")))
    (testing "helper functions precede the main that calls them"
      (is (< (str/index-of fs-src "vec3 colorA") (str/index-of fs-src "void main")))
      (is (< (str/index-of fs-src "vec3 colorB") (str/index-of fs-src "void main"))))
    (testing "version carried through"
      (is (str/starts-with? fs-src "#version 330 core\n")))))

(deftest prelude-injected-before-decls
  (let [s (assoc spec :prelude "#define PI 3.14159\n")
        {:keys [fs-src]} (sh/sources s)]
    (is (< (str/index-of fs-src "#define PI")
           (str/index-of fs-src "uniform")))))
