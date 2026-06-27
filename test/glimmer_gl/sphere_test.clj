(ns glimmer-gl.sphere-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.sphere :as s]
            [glimmer-gl.vector :as v3]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (and (== (count a) (count b))
        (every? (fn [[x y]] (< (Math/abs (- x y)) eps))
                 (map vector a b)))))

(deftest construct
  (let [x (s/sphere (v3/vec3 1 2 3) 5)]
    (is (approx [1 2 3] (v3/->vec (s/p x))))
    (is (== 5.0 (s/r x))))
  (is (approx [0 0 0] (v3/->vec (s/p (s/sphere)))))
  (is (== 1.0 (s/r (s/sphere)))))

(deftest measures
  (let [x (s/sphere 3.0)]
    (is (approx [(* 4.0 Math/PI 9.0)] [(s/area x)]))
    (is (== 6.0 (s/width x)))
    (is (== 6.0 (s/height x)))
    (is (== 6.0 (s/depth x)))))

(deftest contains-point
  (let [x (s/sphere 3.0)]
    (is (true?  (s/contains-point? x (v3/vec3 1 0 0))))
    (is (true?  (s/contains-point? x (v3/vec3 3 0 0))))   ; boundary inclusive
    (is (false? (s/contains-point? x (v3/vec3 3.0001 0 0))))))

(deftest centroid-and-center
  (is (approx [5 5 5] (v3/->vec (s/centroid (s/sphere (v3/vec3 5 5 5) 2)))))
  (is (approx [0 0 0] (v3/->vec (s/p (s/center (s/sphere (v3/vec3 5 5 5) 2)))))))

(deftest closest-point
  (let [x (s/sphere 1.0)]
    (is (approx [1 0 0] (v3/->vec (s/closest-point x (v3/vec3 5 0 0)))))
    (is (approx [1 0 0] (v3/->vec (s/closest-point x (v3/vec3 0.2 0 0)))))))

(deftest translate-scale
  (let [x (s/sphere (v3/vec3 1 1 1) 2)]
    (is (approx [4 6 1] (v3/->vec (s/p (s/translate x (v3/vec3 3 5 0))))))
    (let [s' (s/scale x 3.0)]
      (is (approx [3 3 3] (v3/->vec (s/p s'))))
      (is (== 6.0 (s/r s'))))
    (let [ss (s/scale-size x 2.0)]
      (is (approx [1 1 1] (v3/->vec (s/p ss))))
      (is (== 4.0 (s/r ss))))))

(deftest bounds
  (let [b (s/bounds (s/sphere (v3/vec3 0 0 0) 5.0))]
    (is (approx [-5 -5 -5] (v3/->vec (:p b))))
    (is (approx [10 10 10] (v3/->vec (:sz b))))))
