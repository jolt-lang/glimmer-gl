(ns glimmer-gl.polygon-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.polygon :as poly]
            [glimmer-gl.vec2 :as v2]
            [glimmer-gl.rect :as r]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (let [fa (flatten a) fb (flatten b)]
     (and (== (count fa) (count fb))
          (every? (fn [[x y]] (< (Math/abs (- (double x) (double y))) eps))
                  (map vector fa fb))))))

(defn tri-area2 [a b c]
  (* 0.5 (Math/abs (- (* (- (v2/x b) (v2/x a)) (- (v2/y c) (v2/y a)))
                      (* (- (v2/y b) (v2/y a)) (- (v2/x c) (v2/x a)))))))

;; unit square, CCW
(def sq (poly/polygon [(v2/vec2 0 0) (v2/vec2 1 0) (v2/vec2 1 1) (v2/vec2 0 1)]))

(deftest construct-and-access
  (is (== 4 (count (poly/points sq))))
  (is (approx [0 0] (v2/->vec (first (poly/points sq))))))

(deftest area
  (is (approx 1.0 (poly/area sq)))
  (is (approx 2.0 (poly/area (poly/polygon [(v2/vec2 0 0) (v2/vec2 2 0) (v2/vec2 0 2)])))))

(deftest centroid
  (is (approx [0.5 0.5] (v2/->vec (poly/centroid sq)))))

(deftest contains-point
  (is (true?  (poly/contains-point? sq (v2/vec2 0.5 0.5))))
  (is (true?  (poly/contains-point? sq (v2/vec2 0.25 0.75))))
  (is (false? (poly/contains-point? sq (v2/vec2 2 2))))
  (is (false? (poly/contains-point? sq (v2/vec2 -0.5 0.5)))))

(deftest edges-circumference-bounds
  (is (== 4 (count (poly/edges sq))))
  (is (approx 4.0 (poly/circumference sq)))
  (let [b (poly/bounds sq)]
    (is (approx [0 0] (v2/->vec (r/p b))))
    (is (approx [1 1] (v2/->vec (r/size b))))))

(deftest translate-scale
  (let [t (poly/translate sq (v2/vec2 10 20))]
    (is (approx [10 20] (v2/->vec (first (poly/points t)))))
    (is (approx 1.0 (poly/area t))))
  (let [s (poly/scale sq 2.0)]
    (is (approx 4.0 (poly/area s)))))

(deftest flip-reverses-winding
  (let [f (poly/flip sq)]
    (is (approx 1.0 (poly/area f)))               ; abs area unchanged
    (is (approx [0 1] (v2/->vec (first (poly/points f)))))))  ; first vertex is now the old last

(deftest tessellate
  (let [ts (poly/tessellate sq)]
    (is (== 2 (count ts)))                         ; n-2 triangles
    (is (approx 1.0 (reduce + (map #(apply tri-area2 %) ts)))))
  (let [penta (poly/polygon (mapv #(v2/vec2 (Math/cos %) (Math/sin %))
                                  (range 0 (* 2 Math/PI) (/ (* 2 Math/PI) 5))))]
    (is (== 3 (count (poly/tessellate penta))))
    (is (approx (poly/area penta) (reduce + (map #(apply tri-area2 %) (poly/tessellate penta)))))))
