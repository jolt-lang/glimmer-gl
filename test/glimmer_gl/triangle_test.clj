(ns glimmer-gl.triangle-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.triangle :as t]
            [glimmer-gl.vector :as v3]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (let [fa (flatten a) fb (flatten b)]
     (and (== (count fa) (count fb))
          (every? (fn [[x y]] (< (Math/abs (- (double x) (double y))) eps))
                  (map vector fa fb))))))

;; unit right triangle in the z=0 plane, CCW (+z normal)
(def tri (t/triangle3 (v3/vec3 0 0 0) (v3/vec3 2 0 0) (v3/vec3 0 2 0)))

(deftest construct-and-access
  (is (approx [0 0 0] (v3/->vec (t/a tri))))
  (is (approx [2 0 0] (v3/->vec (t/b tri))))
  (is (approx [0 2 0] (v3/->vec (t/c tri))))
  (is (== 3 (count (t/points tri)))))

(deftest area-and-normal
  (is (approx 2.0 (t/area tri)))                       ; 1/2 * 2 * 2
  (is (approx [0 0 1] (v3/->vec (t/normal tri))))      ; CCW → +z
  ;; reversed winding → -z normal
  (is (approx [0 0 -1] (v3/->vec (t/normal (t/flip tri))))))

(deftest centroid-edges-perimeter
  (is (approx [(/ 2 3) (/ 2 3) 0] (v3/->vec (t/centroid tri))))
  (is (== 3 (count (t/edges tri))))
  (is (approx (+ 2 2 (* 2 (Math/sqrt 2))) (t/perimeter tri))))

(deftest barycentric
  (let [[u v w] (t/barycentric tri (t/centroid tri))]
    (is (approx [(/ 1 3) (/ 1 3) (/ 1 3)] [u v w])))
  (is (approx [1 0 0] (t/barycentric tri (v3/vec3 0 0 0))))
  (is (approx [0 0.5 0.5] (t/barycentric tri (v3/vec3 1 1 0))))) ; midpoint of bc

(deftest contains-point
  (is (true?  (t/contains-point? tri (v3/vec3 0.5 0.5 0))))  ; inside, in-plane
  (is (true?  (t/contains-point? tri (v3/vec3 0 0 0))))      ; vertex
  (is (false? (t/contains-point? tri (v3/vec3 2 2 0))))      ; outside, in-plane
  (is (false? (t/contains-point? tri (v3/vec3 0.5 0.5 5))))) ; off-plane

(deftest closest-point
  ;; above the centroid projects straight down
  (is (approx [0.5 0.5 0] (v3/->vec (t/closest-point tri (v3/vec3 0.5 0.5 5)))))
  ;; past vertex b along +x clamps to b
  (is (approx [2 0 0] (v3/->vec (t/closest-point tri (v3/vec3 3 0 0))))))

(deftest flip-translate-scale
  (let [f (t/flip tri)]
    (is (approx [0 0 0] (v3/->vec (t/a f))))
    (is (approx [0 2 0] (v3/->vec (t/b f))))   ; b and c swapped
    (is (approx [2 0 0] (v3/->vec (t/c f)))))
  (let [m (t/translate tri (v3/vec3 10 20 30))]
    (is (approx [10 20 30] (v3/->vec (t/a m))))
    (is (approx [12 20 30] (v3/->vec (t/b m)))))
  (let [s (t/scale tri 2.0)]
    (is (approx [4 0 0] (v3/->vec (t/b s))))))
