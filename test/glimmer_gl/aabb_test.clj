(ns glimmer-gl.aabb-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.aabb :as a]
            [glimmer-gl.vector :as v3]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (and (== (count a) (count b))
        (every? (fn [[x y]] (< (Math/abs (- x y)) eps))
                 (map vector a b)))))

(deftest construct
  (let [x (a/aabb (v3/vec3 1 2 3) (v3/vec3 4 5 6))]
    (is (approx [1 2 3] (v3/->vec (a/p x))))
    (is (approx [4 5 6] (v3/->vec (a/size x)))))
  ;; default = unit cube at origin
  (is (approx [0 0 0] (v3/->vec (a/p (a/aabb)))))
  (is (approx [1 1 1] (v3/->vec (a/size (a/aabb)))))
  ;; three scalars = size at origin
  (is (approx [4 5 6] (v3/->vec (a/size (a/aabb 4 5 6))))))

(deftest from-minmax
  (let [x (a/aabb-from-minmax (v3/vec3 5 5 5) (v3/vec3 1 1 1))]
    (is (approx [1 1 1] (v3/->vec (a/p x))))
    (is (approx [4 4 4] (v3/->vec (a/size x))))))

(deftest accessors
  (let [x (a/aabb 1 2 3 4 5 6)]
    (is (== 4.0 (a/width x)))
    (is (== 5.0 (a/height x)))
    (is (== 6.0 (a/depth x)))
    (is (approx [1 2 3] (v3/->vec (a/min-point x))))
    (is (approx [5 7 9] (v3/->vec (a/max-point x))))))

(deftest volume-and-area
  (is (== 120.0 (a/volume (a/aabb 4 5 6))))
  (is (== 148.0 (a/area (a/aabb 4 5 6))))) ; 2*(4*5 + 5*6 + 4*6) = 2*(20+30+24)

(deftest centroid
  (is (approx [3 4.5 6] (v3/->vec (a/centroid (a/aabb 1 2 3 4 5 6))))))

(deftest contains-point
  (let [x (a/aabb 0 0 0 10 10 10)]
    (is (true?  (a/contains-point? x (v3/vec3 5 5 5))))
    (is (true?  (a/contains-point? x (v3/vec3 10 10 10))))
    (is (false? (a/contains-point? x (v3/vec3 10.0001 5 5))))))

(deftest closest-point
  (let [x (a/aabb 0 0 0 10 10 10)]
    ;; outside in +x clamps to x=10
    (is (approx [10 5 5] (v3/->vec (a/closest-point x (v3/vec3 20 5 5)))))
    ;; inside point is unchanged
    (is (approx [5 5 5] (v3/->vec (a/closest-point x (v3/vec3 5 5 5)))))))

(deftest vertices-eight
  (let [x (a/aabb 0 0 0 10 10 10)
        vs (a/vertices x)]
    (is (== 8 (count vs)))
    (is (approx [0 0 0]  (v3/->vec (nth vs 0))))
    (is (approx [10 10 10] (v3/->vec (nth vs 6))))))

(deftest edges-twelve
  (let [es (a/edges (a/aabb 0 0 0 10 10 10))]
    (is (== 12 (count es)))
    (doseq [e es] (is (== 2 (count e))))))

(deftest union
  (let [u (a/union (a/aabb 0 0 0 10 10 10) (a/aabb 5 5 5 10 10 10))]
    (is (approx [0 0 0] (v3/->vec (a/p u))))
    (is (approx [15 15 15] (v3/->vec (a/size u))))))

(deftest intersection
  (let [i (a/intersection (a/aabb 0 0 0 10 10 10) (a/aabb 5 5 5 10 10 10))]
    (is (some? i))
    (is (approx [5 5 5] (v3/->vec (a/p i))))
    (is (approx [5 5 5] (v3/->vec (a/size i)))))
  (is (nil? (a/intersection (a/aabb 0 0 0 1 1 1) (a/aabb 10 10 10 1 1 1)))))

(deftest translate-scale-center
  (let [x (a/aabb 1 2 3 4 5 6)]
    (is (approx [6 7 8 4 5 6]
                (concat (v3/->vec (a/p (a/translate x (v3/vec3 5 5 5))))
                        (v3/->vec (a/size (a/translate x (v3/vec3 5 5 5)))))))
    (let [s (a/scale x 2.0)]
      (is (approx [2 4 6] (v3/->vec (a/p s))))
      (is (approx [8 10 12] (v3/->vec (a/size s)))))
    (let [ss (a/scale-size x 2.0)]
      (is (approx (v3/->vec (a/centroid x)) (v3/->vec (a/centroid ss))))
      (is (approx [8 10 12] (v3/->vec (a/size ss)))))
    (let [c (a/center x)]
      (is (approx [0 0 0] (v3/->vec (a/centroid c)))))))

(deftest map-point-roundtrip
  (let [x (a/aabb 10 20 30 100 50 60)
        q (v3/vec3 60 45 60)
        uv (a/map-point x q)]
    (is (approx [0.5 0.5 0.5] (v3/->vec uv)))
    (is (approx (v3/->vec q) (v3/->vec (a/unmap-point x uv))))))
