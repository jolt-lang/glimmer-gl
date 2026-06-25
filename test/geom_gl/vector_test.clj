(ns geom-gl.vector-test
  (:require [clojure.test :refer [deftest is testing]]
            [geom-gl.vector :as v]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (and (== (count a) (count b))
        (every? (fn [[x y]] (< (Math/abs (- x y)) eps))
                (map vector a b)))))

(deftest construct-and-read
  (let [p (v/vec3 1 2 3)]
    (is (== 1.0 (v/x p)))
    (is (== 2.0 (v/y p)))
    (is (== 3.0 (v/z p)))))

(deftest add-sub-scale
  (let [a (v/vec3 1 2 3) b (v/vec3 4 5 6)]
    (is (approx [5 7 9] (v/->vec (v/add a b))))
    (is (approx [3 3 3] (v/->vec (v/sub b a))))
    (is (approx [2 4 6] (v/->vec (v/scale a 2.0))))))

(deftest dot-product
  (is (== 32.0 (v/dot (v/vec3 1 2 3) (v/vec3 4 5 6))))
  (is (== 0.0 (v/dot (v/vec3 1 0 0) (v/vec3 0 1 0)))))

(deftest cross-product-right-handed
  ;; x-hat × y-hat = z-hat
  (is (approx [0 0 1] (v/->vec (v/cross (v/vec3 1 0 0) (v/vec3 0 1 0)))))
  ;; anti-commutative: a×b = -(b×a)
  (is (approx [0 0 -1] (v/->vec (v/cross (v/vec3 0 1 0) (v/vec3 1 0 0))))))

(deftest magnitude-and-normalize
  (is (== 5.0 (v/magnitude (v/vec3 3 4 0))))
  (is (approx [0.6 0.8 0.0] (v/->vec (v/normalize (v/vec3 3 4 0)))))
  (is (== 1.0 (v/magnitude (v/normalize (v/vec3 3 4 0))))))

(deftest distance
  (is (== 5.0 (v/dist (v/vec3 0 0 0) (v/vec3 3 4 0))))
  (is (== 0.0 (v/dist (v/vec3 1 1 1) (v/vec3 1 1 1)))))
