(ns glimmer-gl.polyhedra-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.polyhedra :as poly]
            [glimmer-gl.mesh :as mesh]
            [glimmer-gl.vector :as v3]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (let [fa (flatten a) fb (flatten b)]
     (and (== (count fa) (count fb))
          (every? (fn [[x y]] (< (Math/abs (- (double x) (double y))) eps))
                  (map vector fa fb))))))

(defn unique-verts [m]
  (into #{} (apply concat (mesh/faces m))))

(defn all-on-sphere? [m radius]
  (every? #(approx [radius] [(v3/magnitude %)]) (unique-verts m)))

(deftest tetrahedron
  (let [m (poly/tetrahedron 1.0)]
    (is (== 4 (count (mesh/faces m))))
    (is (== 4 (count (unique-verts m))))
    (is (every? #(== 3 (count %)) (mesh/faces m)))
    ;; regular tetrahedron: all 6 edges equal the scale
    (let [vs (vec (unique-verts m))
          edges (for [i (range 4) j (range i)] (v3/dist (vs i) (vs j)))]
      (is (approx (repeat 6 1.0) edges))))
  ;; default arg = scale 1.0
  (is (== 4 (count (mesh/faces (poly/tetrahedron))))))

(deftest octahedron
  (let [m (poly/octahedron 1.0)]
    (is (== 8 (count (mesh/faces m))))
    (is (== 6 (count (unique-verts m))))
    (is (all-on-sphere? m 1.0))))

(deftest icosahedron
  (let [m (poly/icosahedron 2.0)]
    (is (== 20 (count (mesh/faces m))))
    (is (== 12 (count (unique-verts m))))
    (is (all-on-sphere? m 2.0))))

(deftest dodecahedron
  (let [m (poly/dodecahedron 1.5)]
    (is (== 12 (count (mesh/faces m))))
    (is (== 20 (count (unique-verts m))))
    (is (every? #(== 5 (count %)) (mesh/faces m)))   ; pentagonal faces
    (is (all-on-sphere? m 1.5))))
