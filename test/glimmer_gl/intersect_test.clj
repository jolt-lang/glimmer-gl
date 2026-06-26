(ns glimmer-gl.intersect-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.intersect :as ix]
            [glimmer-gl.vector :as v3]
            [glimmer-gl.plane :as pl]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (let [fa (flatten a) fb (flatten b)]
     (and (== (count fa) (count fb))
          (every? (fn [[x y]] (< (Math/abs (- (double x) (double y))) eps))
                  (map vector fa fb))))))

;; z = 5 plane (normal +z, w = -5)
(def z5 (pl/plane-with-point (v3/vec3 0 0 5) (v3/vec3 0 0 1)))

(deftest ray-sphere
  (is (approx 4.0 (ix/ray-sphere (v3/vec3 0 0 -5) (v3/vec3 0 0 1) (v3/vec3 0 0 0) 1.0)))
  ;; ray origin inside the sphere → returns the far (exit) hit
  (is (some? (ix/ray-sphere (v3/vec3 0 0 0) (v3/vec3 0 0 1) (v3/vec3 0 0 0) 1.0)))
  ;; sphere entirely behind the ray → nil
  (is (nil? (ix/ray-sphere (v3/vec3 0 0 5) (v3/vec3 0 0 1) (v3/vec3 0 0 0) 1.0)))
  ;; ray misses (passes above) → nil
  (is (nil? (ix/ray-sphere (v3/vec3 0 5 -5) (v3/vec3 0 0 1) (v3/vec3 0 0 0) 1.0))))

(deftest ray-plane
  (is (approx 5.0 (ix/ray-plane (v3/vec3 0 0 0) (v3/vec3 0 0 1) z5)))
  ;; plane behind the ray → nil
  (is (nil? (ix/ray-plane (v3/vec3 0 0 10) (v3/vec3 0 0 1) z5)))
  ;; ray parallel to the plane → nil
  (is (nil? (ix/ray-plane (v3/vec3 0 0 0) (v3/vec3 1 0 0) z5))))

(deftest ray-triangle
  (let [a (v3/vec3 0 0 0) b (v3/vec3 2 0 0) c (v3/vec3 0 2 0)]
    ;; hits the interior from above
    (is (approx 5.0 (ix/ray-triangle (v3/vec3 0.5 0.5 5) (v3/vec3 0 0 -1) a b c)))
    ;; misses (outside the triangle)
    (is (nil? (ix/ray-triangle (v3/vec3 3 3 5) (v3/vec3 0 0 -1) a b c)))))

(deftest ray-aabb
  (let [mn (v3/vec3 0 0 0) mx (v3/vec3 2 2 2)]
    (is (approx 1.0 (ix/ray-aabb (v3/vec3 -1 1 1) (v3/vec3 1 0 0) mn mx)))
    ;; ray passes above the box → nil
    (is (nil? (ix/ray-aabb (v3/vec3 -1 5 1) (v3/vec3 1 0 0) mn mx)))
    ;; box behind the ray → nil
    (is (nil? (ix/ray-aabb (v3/vec3 5 1 1) (v3/vec3 1 0 0) mn mx)))))

(deftest sphere-sphere
  (is (true?  (ix/sphere-sphere? (v3/vec3 0 0 0) 1.0 (v3/vec3 1 0 0) 1.0)))  ; touching → overlap
  (is (false? (ix/sphere-sphere? (v3/vec3 0 0 0) 1.0 (v3/vec3 3 0 0) 1.0))))

(deftest aabb-aabb
  (is (true?  (ix/aabb-aabb? (v3/vec3 0 0 0) (v3/vec3 2 2 2)
                             (v3/vec3 1 1 1) (v3/vec3 3 3 3))))
  (is (false? (ix/aabb-aabb? (v3/vec3 0 0 0) (v3/vec3 1 1 1)
                             (v3/vec3 5 5 5) (v3/vec3 6 6 6)))))

(deftest aabb-sphere
  (let [mn (v3/vec3 0 0 0) mx (v3/vec3 2 2 2)]
    (is (true?  (ix/aabb-sphere? mn mx (v3/vec3 3 1 1) 1.0)))   ; touches the face
    (is (false? (ix/aabb-sphere? mn mx (v3/vec3 5 1 1) 1.0)))))
