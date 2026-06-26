(ns glimmer-gl.glmesh-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.glmesh :as glmesh]
            [glimmer-gl.gl :as gl]
            [glimmer-gl.matrix :as m]
            [glimmer-gl.primitives :as prim]
            [glimmer-gl.scene :as scene]))

;; glmesh is the GL pipeline ported from thi.ng/geom gl.glmesh + gl.core: a mesh
;; becomes a pure GL buffer-spec of separate attribute buffers; make-buffers-in-spec
;; uploads them; make-vertex-array records attrib bindings into a VAO; draw-with-shader
;; composes a compiled shader + per-frame uniforms + the VAO into a draw call. The GL
;; side needs a context, so only the pure data transform (as-gl-buffer-spec) and the
;; camera matrices are unit-tested here; the draw path is exercised by the demo.

(deftest as-gl-buffer-spec-cuboid
  (testing "flat-shaded cuboid: 6 quads -> 12 tris -> 36 verts, separate pos+norm"
    (let [spec (glmesh/as-gl-buffer-spec (prim/cuboid 1.0))]
      (is (= 36 (:num-vertices spec)))
      (is (= gl/GL-TRIANGLES (:mode spec)))
      (is (= 3 (get-in spec [:attribs :position :size])))
      (is (= 3 (get-in spec [:attribs :normal :size])))
      (is (= 108 (count (get-in spec [:attribs :position :data]))))
      (is (= 108 (count (get-in spec [:attribs :normal :data]))))
      (testing "flat shading gives each triangle one face normal (verts 0,1,2 share it)"
        (let [n (get-in spec [:attribs :normal :data])]
          (is (= (subvec n 0 3) (subvec n 3 6) (subvec n 6 9))))))))

(deftest as-gl-buffer-spec-smooth
  (testing "smooth shading still produces 36 verts but per-vertex normals"
    (let [spec (glmesh/as-gl-buffer-spec (prim/cuboid 1.0) {:shading :smooth})]
      (is (= 36 (:num-vertices spec)))
      (is (= 108 (count (get-in spec [:attribs :normal :data])))))))

;; camera is a scene fn (ported from thi.ng.geom.gl.camera); tested here alongside
;; the draw spec it feeds.
(deftest perspective-camera-builds-matrices
  (let [cam (scene/perspective-camera
              {:eye [4.0 3.0 6.0] :target [0.0 0.0 0.0] :up [0.0 1.0 0.0]
               :fov 45.0 :aspect (/ 4.0 3.0) :near 0.1 :far 100.0})]
    (is (= 16 (count (m/->vec (:view cam)))))
    (is (= 16 (count (m/->vec (:proj cam)))))
    (is (= 0.1 (:near cam)))
    (testing "apply-camera injects :view/:proj into a draw-spec's :uniforms"
      (let [with-cam (scene/apply-camera {:uniforms {:u_color [1 1 1]}} cam)]
        (is (contains? (:uniforms with-cam) :view))
        (is (contains? (:uniforms with-cam) :proj))
        (is (contains? (:uniforms with-cam) :u_color))))))
