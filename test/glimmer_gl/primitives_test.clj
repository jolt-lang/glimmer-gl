(ns glimmer-gl.primitives-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.vector :as v]
            [glimmer-gl.mesh :as mesh]
            [glimmer-gl.primitives :as p]))

(defn- approx
  ([a b] (approx a b 1e-9))
  ([a b eps] (< (Math/abs (- (double a) (double b))) eps)))

(defn- outward?
  "For a convex solid centered at the origin, every face normal should point
  away from the centroid, i.e. dot(normal, face-centroid) > 0."
  [m]
  (every? (fn [f]
            (pos? (v/dot (mesh/face-normal f) (v/centroid f))))
          (mesh/faces m)))

(deftest cuboid-shape
  (let [m (p/cuboid 2.0)]
    (is (= 6 (count (mesh/faces m))) "6 quad faces")
    (is (= 8 (count (set (mapcat identity (mesh/faces m))))) "8 unique vertices")
    (is (= 12 (count (mesh/triangles m))) "12 triangles after tessellation")
    (is (outward? m) "all face normals point outward")))

(deftest cuboid-min-corner-form
  (let [m (p/cuboid [0 0 0] [2 4 6])
        verts (set (mapcat identity (mesh/faces m)))]
    (is (contains? verts (v/vec3 0 0 0)))
    (is (contains? verts (v/vec3 2 4 6)))))

(deftest tetrahedron-shape
  (let [m (p/tetrahedron 1.0)]
    (is (= 4 (count (mesh/faces m))) "4 triangular faces")
    (is (every? #(= 3 (count %)) (mesh/faces m)))
    (is (outward? m) "auto-oriented to wind outward")
    (testing "regular: every vertex is radius 1 from the origin"
      (is (every? #(approx 1.0 (v/magnitude %) 1e-9)
                  (set (mapcat identity (mesh/faces m))))))))

(deftest plane-grid
  (is (= 1 (count (mesh/faces (p/plane 1.0)))))
  (is (= 9 (count (mesh/faces (p/plane 3.0 3)))) "res*res quads")
  (testing "quad normal points +Z"
    (is (approx 1.0 (v/z (mesh/face-normal (first (mesh/faces (p/quad 2.0)))))))))

(deftest sphere-shape
  (let [slices 8 stacks 6
        m (p/sphere 2.0 slices stacks)]
    (testing "all vertices lie on the radius-2 sphere"
      (is (every? #(approx 2.0 (v/magnitude %) 1e-9)
                  (set (mapcat identity (mesh/faces m))))))
    (testing "face normals point outward"
      (is (outward? m)))
    (testing "produces a non-trivial triangle buffer"
      (is (pos? (:count (mesh/->floats m {:shading :smooth})))))))

;; ->floats accepts shading as a keyword arg via the map; check both modes run.
(deftest floats-shading-modes
  (let [m (p/cuboid 1.0)]
    (is (= 36 (:count (mesh/->floats m))))
    (is (= 36 (:count (mesh/->floats m {:shading :smooth}))))))
