(ns glimmer-gl.offscreen-test
  "Proves the surfaceless context can do real work, not just exist: compile a
  program, render a fullscreen triangle into an RGBA32F texture, read the pixels
  back. That is the whole compute-pass shape, so if this passes, render-to-
  texture GPU code is testable headlessly.

  Skips (with a printed reason) when no display is available — a CI box without
  one is a legitimate environment, not a failure."
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.gl :as gl]
            [glimmer-gl.offscreen :as off]
            [jolt.ffi :as ffi]))

(def ^:private vs
  "#version 330 core
void main(){
  vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
  gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}")

;; A constant colour is enough — the point is that the pass ran at all, and an
;; exact float is a sharper assertion than anything interpolated.
(def ^:private fs
  "#version 330 core
out vec4 frag;
uniform float u_v;
void main(){ frag = vec4(u_v, u_v * 2.0, u_v * 3.0, 1.0); }")

(defn- render-and-read
  "Render one pass of a constant colour into a 4x4 RGBA32F texture and return the
   first texel as [r g b a]."
  [v]
  (let [prog (gl/make-program vs fs)
        tex  (gl/gen-one gl/gl-gen-textures)
        fbo  (gl/gen-one gl/gl-gen-framebuffers)
        vao  (gl/gen-one gl/gl-gen-vertex-arrays)
        w 4 h 4]
    (gl/gl-bind-texture gl/GL-TEXTURE-2D tex)
    (gl/gl-tex-parameter-i gl/GL-TEXTURE-2D gl/GL-TEXTURE-MIN-FILTER gl/GL-NEAREST)
    (gl/gl-tex-parameter-i gl/GL-TEXTURE-2D gl/GL-TEXTURE-MAG-FILTER gl/GL-NEAREST)
    (gl/gl-tex-image-2d gl/GL-TEXTURE-2D 0 gl/GL-RGBA32F w h 0
                        gl/GL-RGBA gl/GL-FLOAT ffi/null)
    (gl/gl-bind-framebuffer gl/GL-FRAMEBUFFER fbo)
    (gl/gl-framebuffer-texture-2d gl/GL-FRAMEBUFFER gl/GL-COLOR-ATTACHMENT0
                                  gl/GL-TEXTURE-2D tex 0)
    (let [status (gl/gl-check-framebuffer-status gl/GL-FRAMEBUFFER)]
      (if (not= status gl/GL-FRAMEBUFFER-COMPLETE)
        {:error (str "framebuffer incomplete: 0x" (format "%x" status))}
        (do
          (gl/gl-viewport 0 0 w h)
          (gl/gl-use-program prog)
          (gl/gl-uniform-1f (gl/gl-get-uniform-location prog "u_v") (double v))
          (gl/gl-bind-vertex-array vao)
          (gl/gl-draw-arrays gl/GL-TRIANGLES 0 3)
          (let [ptr (ffi/alloc (* w h 4 (ffi/sizeof :float)))]
            (gl/gl-read-pixels 0 0 w h gl/GL-RGBA gl/GL-FLOAT ptr)
            (let [texel (mapv #(double (ffi/read ptr :float (* % 4))) (range 4))]
              (ffi/free ptr)
              {:texel texel})))))))

(deftest offscreen-context-runs-a-render-to-texture-pass
  (let [ctx (off/ensure-current!)]
    (if-let [err (:error ctx)]
      (println "SKIP offscreen GL:" err)
      (testing "a surfaceless context is current and usable"
        (let [[maj mnr] (:version ctx)]
          (println "offscreen GL" maj "." mnr
                   (or (gl/gl-get-string* gl/GL-RENDERER) ""))
          ;; The shaders below are #version 330 core; anything older can't run them.
          (is (or (> (long maj) 3) (and (= 3 (long maj)) (>= (long mnr) 3)))
              "context is at least GL 3.3"))
        (testing "glGetString works, i.e. a context really is current"
          (is (some? (gl/gl-get-string* gl/GL-VERSION))))
        (testing "render-to-texture round-trips exact float values"
          (let [{:keys [texel error]} (render-and-read 0.25)]
            (is (nil? error) error)
            (when-not error
              ;; 0.25/0.5/0.75 are exact in binary32, so this is an equality test.
              (is (= [0.25 0.5 0.75 1.0] texel)))))))))

(deftest ensure-current-is-idempotent
  (let [a (off/ensure-current!)
        b (off/ensure-current!)]
    (if (:error a)
      (is (= (:error a) (:error b)) "a failure is cached, not re-probed differently")
      (is (= (:context a) (:context b)) "the same context comes back"))))
