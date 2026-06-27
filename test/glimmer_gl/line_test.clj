(ns glimmer-gl.line-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.line :as l]
            [glimmer-gl.vector :as v3]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (let [fa (flatten a) fb (flatten b)]
     (and (== (count fa) (count fb))
          (every? (fn [[x y]] (< (Math/abs (- x y)) eps))
                  (map vector fa fb))))))

(deftest construct
  (let [x (l/line3 (v3/vec3 1 2 3) (v3/vec3 4 6 3))]
    (is (approx [1 2 3] (v3/->vec (l/start x))))
    (is (approx [4 6 3] (v3/->vec (l/end x))))
    (is (approx [[1 2 3] [4 6 3]] (mapv v3/->vec (l/points x)))))
  ;; 6-scalar ctor
  (let [x (l/line3 1 2 3 4 6 3)]
    (is (approx [1 2 3] (v3/->vec (l/start x))))))

(deftest length-and-direction
  (let [x (l/line3 0 0 0 3 4 0)]
    (is (== 5.0 (l/length x)))
    (is (== 25.0 (l/length-squared x)))
    (is (approx [0.6 0.8 0] (v3/->vec (l/direction x))))))

(deftest midpoint-and-point-at
  (let [x (l/line3 0 0 0 10 0 0)]
    (is (approx [5 0 0] (v3/->vec (l/midpoint x))))
    (is (approx [5 0 0] (v3/->vec (l/centroid x))))
    (is (approx [7 0 0] (v3/->vec (l/point-at x 0.7))))))

(deftest closest-point
  (let [x (l/line3 0 0 0 10 0 0)]
    ;; perpendicular drop onto the segment
    (is (approx [3 0 0] (v3/->vec (l/closest-point x (v3/vec3 3 5 0)))))
    ;; before the start clamps to start
    (is (approx [0 0 0] (v3/->vec (l/closest-point x (v3/vec3 -5 5 0)))))
    ;; after the end clamps to end
    (is (approx [10 0 0] (v3/->vec (l/closest-point x (v3/vec3 15 5 0)))))))

(deftest contains-point
  (let [x (l/line3 0 0 0 10 0 0)]
    (is (true?  (l/contains-point? x (v3/vec3 5 0 0))))
    (is (false? (l/contains-point? x (v3/vec3 5 1 0))))))

(deftest flip-and-normalize
  (let [x (l/line3 1 2 3 4 6 3)]
    (is (approx [4 6 3] (v3/->vec (l/start (l/flip x)))))
    (is (approx [1 2 3] (v3/->vec (l/end (l/flip x))))))
  (let [x (l/line3 0 0 0 5 0 0)
        n (l/normalize x)]
    (is (== 1.0 (l/length n)))
    (is (approx [0 0 0] (v3/->vec (l/start n))))))

(deftest translate-scale
  (let [x (l/line3 1 1 1 2 2 2)]
    (is (approx [4 4 4 5 5 5] (mapv v3/->vec (l/points (l/translate x (v3/vec3 3 3 3))))))
    (let [s (l/scale x 2.0)]
      (is (approx [2 2 2] (v3/->vec (l/start s))))
      (is (approx [4 4 4] (v3/->vec (l/end s)))))))
