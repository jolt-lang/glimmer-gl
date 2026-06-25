(ns glimmer-gl.gl-test
  (:require [clojure.test :refer [deftest is testing]]))

;; glimmer-gl.gl dlopens the host OpenGL library (:jolt/native in deps.edn) and
;; resolves every defcfn symbol at ns load. jolt's require throws a raw String
;; on failure (e.g. an unresolved C symbol), so we catch and surface it as a
;; clean assertion failure rather than a test-namespace load crash.
(defn- require-gl! []
  (try
    (require '[glimmer-gl.gl] :reload)
    [true nil]
    (catch Throwable e [false (pr-str e)])))

(deftest native-gl-loads-and-resolves
  (let [[ok? err] (require-gl!)]
    (is ok? err)
    (when ok?
      (let [gl (find-ns 'glimmer-gl.gl)]
        (testing "core bindings resolve to C entry points"
          (is (some? (ns-resolve gl 'gl-clear)))
          (is (some? (ns-resolve gl 'gl-clear-color)))
          (is (some? (ns-resolve gl 'gl-viewport)))
          (is (some? (ns-resolve gl 'gl-get-string))))
        (testing "core GL constants"
          (is (= 0x4000 @(ns-resolve gl 'GL-COLOR-BUFFER-BIT)))
          (is (= 0x1F00 @(ns-resolve gl 'GL-VENDOR))))
        (testing "shader/program/uniform/attribute bindings resolve"
          (is (some? (ns-resolve gl 'gl-create-shader)))
          (is (some? (ns-resolve gl 'gl-shader-source)))
          (is (some? (ns-resolve gl 'gl-compile-shader)))
          (is (some? (ns-resolve gl 'gl-get-shaderiv)))
          (is (some? (ns-resolve gl 'gl-create-program)))
          (is (some? (ns-resolve gl 'gl-link-program)))
          (is (some? (ns-resolve gl 'gl-use-program)))
          (is (some? (ns-resolve gl 'gl-uniform-matrix4fv)))
          (is (some? (ns-resolve gl 'gl-uniform-1f)))
          (is (some? (ns-resolve gl 'gl-get-attrib-location)))
          (is (some? (ns-resolve gl 'gl-vertex-attrib-pointer))))
        (testing "vertex array object bindings resolve (core profile requires a VAO)"
          (is (some? (ns-resolve gl 'gl-gen-vertex-arrays)))
          (is (some? (ns-resolve gl 'gl-bind-vertex-array))))
        (testing "new shader/program constants"
          (is (= 0x8B81 @(ns-resolve gl 'GL-COMPILE-STATUS)))
          (is (= 0x8B82 @(ns-resolve gl 'GL-LINK-STATUS)))
          (is (= 0x1406 @(ns-resolve gl 'GL-FLOAT))))))))

;; write-floats is pure FFI (no GL context) so it can be unit-tested directly.
(deftest write-floats-round-trips-through-memory
  (let [gl (find-ns 'glimmer-gl.gl)
        wf (ns-resolve gl 'write-floats)
        ffi (find-ns 'jolt.ffi)
        read (ns-resolve ffi 'read)
        free (ns-resolve ffi 'free)]
    (is (some? wf))
    (let [ptr (wf [1.0 2.5 -3.25 42.0])]
      (is (== 1.0  (read ptr :float 0)))
      (is (== 2.5  (read ptr :float 4)))
      (is (== -3.25 (read ptr :float 8)))
      (is (== 42.0 (read ptr :float 12)))
      (free ptr))))
