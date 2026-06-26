(ns glimmer-gl.plane-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.plane :as pl]
            [glimmer-gl.line :as l]
            [glimmer-gl.vector :as v3]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (let [fa (flatten a) fb (flatten b)]
     (and (== (count fa) (count fb))
          (every? (fn [[x y]] (< (Math/abs (- x y)) eps))
                  (map vector fa fb))))))

;; z = 5 plane: normal +z, w = -5
(def z5 (pl/plane-with-point (v3/vec3 0 0 5) (v3/vec3 0 0 1)))

(deftest plane-with-point
  (is (approx [0 0 1] (v3/->vec (pl/normal z5))))
  (is (== -5.0 (pl/w z5))))

(deftest plane-normalizes-normal
  ;; plane constructor normalizes the normal
  (let [p (pl/plane (v3/vec3 0 0 5) -5.0)]
    (is (approx [0 0 1] (v3/->vec (pl/normal p))))))

(deftest plane-from-points
  ;; three points in the z=5 plane
  (let [p (pl/plane-from-points (v3/vec3 1 0 5) (v3/vec3 0 1 5) (v3/vec3 -1 0 5))]
    (is (approx [0 0 1] (v3/->vec (pl/normal p))))
    (is (== -5.0 (pl/w p)))))

(deftest centroid
  ;; closest point of the plane to the origin
  (is (approx [0 0 5] (v3/->vec (pl/centroid z5)))))

(deftest classify-and-dist
  ;; point above the plane (+n side)
  (is (== 1 (pl/classify-point z5 (v3/vec3 0 0 6))))
  ;; point below (-n side)
  (is (== -1 (pl/classify-point z5 (v3/vec3 0 0 4))))
  ;; on the plane
  (is (== 0 (pl/classify-point z5 (v3/vec3 9 9 5))))
  (is (== 1.0 (pl/dist z5 (v3/vec3 0 0 6))))
  (is (== -1.0 (pl/dist z5 (v3/vec3 0 0 4)))))

(deftest closest-point
  (is (approx [3 3 5] (v3/->vec (pl/closest-point z5 (v3/vec3 3 3 9))))))

(deftest translate
  ;; move the z=5 plane up by 2 along its normal -> z=7
  (let [p (pl/translate z5 (v3/vec3 0 0 2))]
    (is (== -7.0 (pl/w p)))
    (is (approx [0 0 7] (v3/->vec (pl/centroid p))))))

(deftest flip
  (let [p (pl/flip z5)]
    (is (approx [0 0 -1] (v3/->vec (pl/normal p))))
    (is (== 5.0 (pl/w p)))
    ;; same geometric plane: centroid unchanged
    (is (approx [0 0 5] (v3/->vec (pl/centroid p))))))

(deftest intersect-line
  ;; line piercing z=5
  (let [hit (pl/intersect-line z5 (l/line3 0 0 0 0 0 10))]
    (is (some? hit))
    (is (approx [0 0 5] (v3/->vec hit))))
  ;; a line parallel to the plane (no hit)
  (is (nil? (pl/intersect-line z5 (l/line3 0 0 0 1 0 0)))))
