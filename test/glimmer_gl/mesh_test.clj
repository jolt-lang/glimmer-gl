(ns glimmer-gl.mesh-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.vector :as v]
            [glimmer-gl.mesh :as mesh]))

(defn- approx
  ([a b] (approx a b 1e-9))
  ([a b eps] (< (Math/abs (- (double a) (double b))) eps)))

(defn- unit? [n]
  (approx 1.0 (v/magnitude n) 1e-9))

;; a unit quad in the XY plane, wound CCW (normal +Z)
(def ^:private xy-quad
  [(v/vec3 0 0 0) (v/vec3 1 0 0) (v/vec3 1 1 0) (v/vec3 0 1 0)])

(deftest add-and-read-faces
  (let [m (-> (mesh/mesh) (mesh/add-face xy-quad))]
    (is (= 1 (count (mesh/faces m))))
    (is (= 4 (count (first (mesh/faces m)))))))

(deftest tessellate-quad-into-two-tris
  (is (= 2 (count (mesh/tessellate-face xy-quad))))
  (is (= 1 (count (mesh/tessellate-face [(v/vec3 0 0 0) (v/vec3 1 0 0) (v/vec3 0 1 0)]))))
  (testing "n-gon fans around centroid -> n triangles"
    (let [pent [(v/vec3 0 0 0) (v/vec3 2 0 0) (v/vec3 3 2 0) (v/vec3 1 3 0) (v/vec3 -1 2 0)]]
      (is (= 5 (count (mesh/tessellate-face pent)))))))

(deftest face-normal-ccw-is-outward
  (let [n (mesh/face-normal xy-quad)]
    (is (unit? n))
    (is (approx 0.0 (v/x n)))
    (is (approx 0.0 (v/y n)))
    (is (approx 1.0 (v/z n)))))

(deftest transform-moves-vertices
  (let [m  (mesh/mesh [xy-quad])
        m' (mesh/translate m 10 0 0)
        p  (first (first (mesh/faces m')))]
    (is (approx 10.0 (v/x p)))
    (is (approx 0.0 (v/y p))))
  (testing "uniform scale"
    (let [p (-> (mesh/mesh [xy-quad]) (mesh/scale 3.0) mesh/faces first (nth 2))]
      (is (approx 3.0 (v/x p)))
      (is (approx 3.0 (v/y p))))))

(deftest merge-concatenates-faces
  (let [a (mesh/mesh [xy-quad])
        b (mesh/translate a 5 0 0)]
    (is (= 2 (count (mesh/faces (mesh/merge-meshes a b)))))))

(deftest subdivide-quadruples-triangles
  (let [m (mesh/mesh [xy-quad])]            ; 1 quad -> 2 tris
    (is (= 2 (count (mesh/triangles m))))
    (is (= 8 (count (mesh/triangles (mesh/subdivide m)))))))   ; 2 * 4

(deftest floats-flat-layout
  (let [m   (mesh/mesh [xy-quad])
        buf (mesh/->floats m)]
    (testing "interleaved pos+normal, 6 floats per vertex, 3 verts per tri"
      (is (= 6 (:stride buf)))
      (is (= 2 (count (mesh/triangles m))) "quad -> 2 tris")
      (is (= 6 (:count buf)) "2 tris * 3 verts")
      (is (= 36 (count (:data buf))) "6 verts * 6 floats"))
    (testing "first vertex is (0,0,0) with +Z normal"
      (is (= [0.0 0.0 0.0 0.0 0.0 1.0] (mapv double (take 6 (:data buf))))))))

(deftest smooth-normals-are-unit
  ;; two coplanar quads share an edge; the shared verts average to the same +Z
  (let [m  (mesh/merge-meshes (mesh/mesh [xy-quad])
                              (mesh/translate (mesh/mesh [xy-quad]) 1 0 0))
        vn (mesh/vertex-normals m)]
    (is (every? unit? (vals vn)))
    (is (every? #(approx 1.0 (v/z %)) (vals vn)))))
