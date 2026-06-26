(ns glimmer-gl.bezier-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.bezier :as bz]
            [glimmer-gl.vector :as v3]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (let [fa (flatten a) fb (flatten b)]
     (and (== (count fa) (count fb))
          (every? (fn [[x y]] (< (Math/abs (- (double x) (double y))) eps))
                  (map vector fa fb))))))

(def p0 (v3/vec3 0 0 0))
(def p1 (v3/vec3 0 1 0))
(def p2 (v3/vec3 1 1 0))
(def p3 (v3/vec3 1 0 0))

(deftest cubic-point-endpoints-and-mid
  (is (approx [0 0 0] (v3/->vec (bz/cubic-point p0 p1 p2 p3 0.0))))
  (is (approx [1 0 0] (v3/->vec (bz/cubic-point p0 p1 p2 p3 1.0))))
  (is (approx [0.5 0.75 0] (v3/->vec (bz/cubic-point p0 p1 p2 p3 0.5)))))

(deftest quadratic-point
  (let [a (v3/vec3 0 0 0) b (v3/vec3 0 1 0) c (v3/vec3 1 0 0)]
    (is (approx [0 0 0] (v3/->vec (bz/quadratic-point a b c 0.0))))
    (is (approx [1 0 0] (v3/->vec (bz/quadratic-point a b c 1.0))))
    (is (approx [0.25 0.5 0] (v3/->vec (bz/quadratic-point a b c 0.5))))))

(deftest cubic-tangent
  ;; derivative at t=0 is 3*(p1-p0)
  (is (approx [3 0 0] (v3/->vec (bz/cubic-tangent (v3/vec3 0 0 0) (v3/vec3 1 0 0) p2 p3 0.0))))
  ;; normalized tangent at t=0 for that curve points along +x
  (is (approx [1 0 0] (v3/->vec (v3/normalize (bz/cubic-tangent (v3/vec3 0 0 0) (v3/vec3 1 0 0) p2 p3 0.0))))))

(deftest sample-cubic
  (let [s (bz/sample-cubic p0 p1 p2 p3 8)]
    (is (== 9 (count s)))                       ; res+1 points
    (is (approx [0 0 0] (v3/->vec (first s))))
    (is (approx [1 0 0] (v3/->vec (last s))))))

(deftest arc-length
  (is (approx 7.0 (bz/arc-length [(v3/vec3 0 0 0) (v3/vec3 3 0 0) (v3/vec3 3 4 0)])))
  (is (approx 0.0 (bz/arc-length [(v3/vec3 5 5 5)]))))

(deftest catmull-rom-through-collinear-points
  (let [pts [(v3/vec3 0 0 0) (v3/vec3 1 0 0) (v3/vec3 2 0 0) (v3/vec3 3 0 0)]
        spline (bz/catmull-rom-spline pts 6)]
    (is (> (count spline) (count pts)))
    ;; stays on the x-axis (y,z ~ 0) since the through-points are collinear
    (is (every? (fn [p] (and (approx [0] [(v3/y p)]) (approx [0] [(v3/z p)]))) spline))
    ;; passes through the endpoints
    (is (approx [0 0 0] (v3/->vec (first spline))))
    (is (approx [3 0 0] (v3/->vec (last spline))))
    ;; x is monotonic non-decreasing along the spline (collinear, even spacing)
    (let [xs (map #(v3/x %) spline)]
      (is (every? (fn [[a b]] (>= (+ b 1e-9) a)) (map vector xs (rest xs)))))))
