(ns glimmer-gl.circle-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.circle :as c]
            [glimmer-gl.vec2 :as v2]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (and (== (count a) (count b))
        (every? (fn [[x y]] (< (Math/abs (- x y)) eps))
                 (map vector a b)))))

(deftest construct
  (let [x (c/circle (v2/vec2 1 2) 5)]
    (is (approx [1 2] (v2/->vec (c/p x))))
    (is (== 5.0 (c/r x))))
  ;; default = unit circle at origin
  (is (approx [0 0] (v2/->vec (c/p (c/circle)))))
  (is (== 1.0 (c/r (c/circle)))))

(deftest measures
  (let [x (c/circle 3.0)]
    (is (approx [(* Math/PI 9)] [(c/area x)]))
    (is (approx [(* 2.0 Math/PI 3.0)] [(c/circumference x)]))
    (is (== 6.0 (c/width x)))
    (is (== 6.0 (c/height x)))))

(deftest contains-point
  (let [x (c/circle 3.0)]
    (is (true?  (c/contains-point? x (v2/vec2 1 0))))   ; inside
    (is (true?  (c/contains-point? x (v2/vec2 3 0))))   ; on boundary (inclusive)
    (is (false? (c/contains-point? x (v2/vec2 3.0001 0))))))

(deftest centroid-and-center
  (is (approx [5 5] (v2/->vec (c/centroid (c/circle (v2/vec2 5 5) 2)))))
  (is (approx [0 0] (v2/->vec (c/p (c/center (c/circle (v2/vec2 5 5) 2)))))))

(deftest point-at-and-vertices
  (let [x (c/circle 2.0)]
    ;; t=0 -> (r, 0); t=0.25 -> (0, r); t=0.5 -> (-r, 0); t=0.75 -> (0, -r)
    (is (approx [2 0]   (v2/->vec (c/point-at x 0.0))))
    (is (approx [0 2]   (v2/->vec (c/point-at x 0.25))))
    (is (approx [-2 0]  (v2/->vec (c/point-at x 0.5))))
    (is (approx [0 -2]  (v2/->vec (c/point-at x 0.75))))
    ;; vertices returns res distinct points around the circle
    (let [vs (c/vertices x 8)]
      (is (== 8 (count vs)))
      (is (approx [2 0] (v2/->vec (first vs)))))))

(deftest closest-point
  ;; outside point projects to the boundary in its direction
  (let [x (c/circle 1.0)]
    (is (approx [1 0] (v2/->vec (c/closest-point x (v2/vec2 5 0)))))
    ;; inside point also projects to the boundary (thi.ng behavior: always length r)
    (is (approx [1 0] (v2/->vec (c/closest-point x (v2/vec2 0.2 0)))))))

(deftest translate-scale
  (let [x (c/circle (v2/vec2 1 1) 2)]
    (is (approx [4 6] (v2/->vec (c/p (c/translate x (v2/vec2 3 5))))))
    (let [s (c/scale x 3.0)]
      (is (approx [3 3] (v2/->vec (c/p s))))
      (is (== 6.0 (c/r s))))
    ;; scale-size grows radius, keeps center
    (let [ss (c/scale-size x 2.0)]
      (is (approx [1 1] (v2/->vec (c/p ss))))
      (is (== 4.0 (c/r ss))))))
