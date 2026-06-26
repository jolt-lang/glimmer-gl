(ns glimmer-gl.vec2-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.vec2 :as v2]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (and (== (count a) (count b))
        (every? (fn [[x y]] (< (Math/abs (- x y)) eps))
                 (map vector a b)))))

(deftest construct-and-read
  (let [p (v2/vec2 1 2)]
    (is (== 1.0 (v2/x p)))
    (is (== 2.0 (v2/y p))))
  (is (approx [7 7] (v2/->vec (v2/vec2 7)))))

(deftest add-sub-scale
  (let [a (v2/vec2 1 2) b (v2/vec2 4 5)]
    (is (approx [5 7] (v2/->vec (v2/add a b))))
    (is (approx [3 3] (v2/->vec (v2/sub b a))))
    (is (approx [2 4] (v2/->vec (v2/scale a 2.0))))))

(deftest dot-product
  (is (== 14.0 (v2/dot (v2/vec2 1 2) (v2/vec2 4 5))))
  (is (== 0.0 (v2/dot (v2/vec2 1 0) (v2/vec2 0 1)))))

(deftest magnitude-and-normalize
  (is (== 5.0 (v2/magnitude (v2/vec2 3 4))))
  (is (approx [0.6 0.8] (v2/->vec (v2/normalize (v2/vec2 3 4)))))
  (is (== 1.0 (v2/magnitude (v2/normalize (v2/vec2 3 4))))))

(deftest distance
  (is (== 5.0 (v2/dist (v2/vec2 0 0) (v2/vec2 3 4))))
  (is (== 0.0 (v2/dist (v2/vec2 1 1) (v2/vec2 1 1)))))

(deftest mix
  (is (approx [3 4] (v2/->vec (v2/mix (v2/vec2 1 2) (v2/vec2 5 6) 0.5))))
  (is (approx [1 2] (v2/->vec (v2/mix (v2/vec2 1 2) (v2/vec2 5 6) 0.0)))))

(deftest centroid
  (is (approx [2 3] (v2/->vec (v2/centroid [(v2/vec2 1 2) (v2/vec2 3 4)])))))

(deftest perpendicular
  ;; 90-degree CCW rotation: (x,y) -> (-y,x)
  (is (approx [-2 1] (v2/->vec (v2/perpendicular (v2/vec2 1 2))))))

(deftest rotate
  ;; rotating (1,0) by 90 degrees -> (0,1)
  (let [r (v2/rotate (v2/vec2 1 0) (/ Math/PI 2))]
    (is (approx [0 1] (v2/->vec r)))))

(deftest heading
  (is (approx [(/ Math/PI 4)] [(v2/heading (v2/vec2 1 1))]))
  (is (== 0.0 (v2/heading (v2/vec2 1 0)))))

(deftest cross-scalar
  ;; 2D cross returns a scalar: ax*by - ay*bx
  (is (== 1.0 (v2/cross (v2/vec2 1 0) (v2/vec2 0 1))))
  (is (== -1.0 (v2/cross (v2/vec2 0 1) (v2/vec2 1 0)))))
