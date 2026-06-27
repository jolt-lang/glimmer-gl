(ns glimmer-gl.rect-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.rect :as r]
            [glimmer-gl.vec2 :as v2]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (and (== (count a) (count b))
        (every? (fn [[x y]] (< (Math/abs (- x y)) eps))
                 (map vector a b)))))

(defn rect-points-approx [a b]
  (and (approx (v2/->vec (r/p a)) (v2/->vec (r/p b)))
       (approx (v2/->vec (r/size a)) (v2/->vec (r/size b)))))

(deftest construct
  (let [x (r/rect 1 2 3 4)]
    (is (approx [1 2] (v2/->vec (r/p x))))
    (is (approx [3 4] (v2/->vec (r/size x)))))
  (is (rect-points-approx (r/rect (v2/vec2 1 2) (v2/vec2 3 4)) (r/rect 1 2 3 4)))
  ;; default rect is unit square at origin
  (is (approx [0 0] (v2/->vec (r/p (r/rect)))))
  (is (approx [1 1] (v2/->vec (r/size (r/rect))))))

(deftest accessors
  (let [x (r/rect 1 2 3 4)]
    (is (== 3.0 (r/width x)))
    (is (== 4.0 (r/height x)))
    (is (== 1.0 (r/left x)))
    (is (== 4.0 (r/right x)))
    (is (== 2.0 (r/bottom x)))
    (is (== 6.0 (r/top x)))
    (is (approx [1 2] (v2/->vec (r/bottom-left x))))
    (is (approx [4 6] (v2/->vec (r/top-right x))))))

(deftest area-and-centroid
  (is (== 12.0 (r/area (r/rect 1 2 3 4))))
  (is (approx [2.5 4.0] (v2/->vec (r/centroid (r/rect 1 2 3 4)))))
  (is (== 14.0 (r/circumference (r/rect 1 2 3 4)))))

(deftest contains-point
  (let [x (r/rect 0 0 10 10)]
    (is (true?  (r/contains-point? x (v2/vec2 5 5))))
    (is (true?  (r/contains-point? x (v2/vec2 0 0))))   ; min corner inclusive
    (is (true?  (r/contains-point? x (v2/vec2 10 10)))) ; max corner inclusive (thi.ng m/in-range? is closed)
    (is (false? (r/contains-point? x (v2/vec2 -1 5))))
    (is (false? (r/contains-point? x (v2/vec2 10.0001 5))))))

(deftest vertices-ccw
  ;; [bottom-left, bottom-right, top-right, top-left] (counter-clockwise from p)
  (let [x (r/rect 0 0 10 10)
        vs (r/vertices x)]
    (is (== 4 (count vs)))
    (is (approx [0 0] (v2/->vec (nth vs 0))))
    (is (approx [10 0] (v2/->vec (nth vs 1))))
    (is (approx [10 10] (v2/->vec (nth vs 2))))
    (is (approx [0 10] (v2/->vec (nth vs 3))))))

(deftest edges
  (let [es (r/edges (r/rect 0 0 10 10))]
    (is (== 4 (count es)))
    (doseq [e es] (is (== 2 (count e))))))

(deftest union
  (let [u (r/union (r/rect 0 0 10 10) (r/rect 5 5 10 10))]
    (is (approx [0 0] (v2/->vec (r/p u))))
    (is (approx [15 15] (v2/->vec (r/size u))))))

(deftest intersection
  (let [i (r/intersection (r/rect 0 0 10 10) (r/rect 5 5 10 10))]
    (is (some? i))
    (is (approx [5 5] (v2/->vec (r/p i))))
    (is (approx [5 5] (v2/->vec (r/size i)))))
  ;; disjoint rects don't overlap -> nil
  (is (nil? (r/intersection (r/rect 0 0 1 1) (r/rect 10 10 1 1)))))

(deftest translate-scale-center
  (let [x (r/rect 1 2 3 4)]
    (is (approx [6 7 3 4]
                (concat (v2/->vec (r/p (r/translate x (v2/vec2 5 5))))
                        (v2/->vec (r/size (r/translate x (v2/vec2 5 5)))))))
    ;; scale moves p and size by the factor (about origin)
    (is (approx [2 4 6 8]
                (concat (v2/->vec (r/p (r/scale x 2.0)))
                        (v2/->vec (r/size (r/scale x 2.0))))))
    ;; scale-size keeps centroid fixed, resizes the box
    (let [ss (r/scale-size x 2.0)]
      (is (approx (v2/->vec (r/centroid x)) (v2/->vec (r/centroid ss))))
      (is (approx [6 8] (v2/->vec (r/size ss)))))
    ;; center at origin: p = -size/2
    (let [c (r/center x)]
      (is (approx [0 0] (v2/->vec (r/centroid c)))))))

(deftest map-point-roundtrip
  (let [x (r/rect 10 20 100 50)
        q (v2/vec2 60 45)
        uv (r/map-point x q)]
    ;; q is at the center -> uv ~ (0.5, 0.5)
    (is (approx [0.5 0.5] (v2/->vec uv)))
    ;; unmap of uv returns the original point
    (is (approx (v2/->vec q) (v2/->vec (r/unmap-point x uv))))))
