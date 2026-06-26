(ns glimmer-gl.quaternion-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer-gl.quaternion :as q]
            [glimmer-gl.vector :as v3]))

(defn approx
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (let [fa (flatten a) fb (flatten b)]
     (and (== (count fa) (count fb))
          (every? (fn [[x y]] (< (Math/abs (- (double x) (double y))) eps))
                  (map vector fa fb))))))

(def z90 (q/from-axis-angle (v3/vec3 0 0 1) (/ Math/PI 2))) ; 90° about +z

(deftest construct-and-access
  (let [qq (q/quat 1 2 3 4)]
    (is (== 1.0 (q/x qq)))
    (is (== 2.0 (q/y qq)))
    (is (== 3.0 (q/z qq)))
    (is (== 4.0 (q/w qq))))
  (is (approx [0 0 0 1] [(q/x (q/identity)) (q/y (q/identity))
                         (q/z (q/identity)) (q/w (q/identity))])))

(deftest from-axis-angle-normalizes-axis
  ;; axis need not be unit; result still rotates correctly
  (let [qq (q/from-axis-angle (v3/vec3 0 0 5) (/ Math/PI 2))]
    (is (approx [0 0 1] (v3/->vec (v3/normalize (q/axis qq)))))
    (is (approx [0 0 (Math/sin (/ Math/PI 4))] [(q/x qq) (q/y qq) (q/z qq)]))
    (is (approx [(Math/cos (/ Math/PI 4))] [(q/w qq)]))))

(deftest rotate-vector
  (is (approx [1 0 0] (v3/->vec (q/rotate-vector (q/identity) (v3/vec3 1 0 0)))))
  ;; 90° about +z: +x -> +y
  (is (approx [0 1 0] (v3/->vec (q/rotate-vector z90 (v3/vec3 1 0 0)))))
  ;; 90° about +y: +z -> +x
  (let [y90 (q/from-axis-angle (v3/vec3 0 1 0) (/ Math/PI 2))]
    (is (approx [1 0 0] (v3/->vec (q/rotate-vector y90 (v3/vec3 0 0 1)))))))

(deftest multiply-composes
  ;; two 90° about +z == one 180° about +z
  (let [q180 (q/mul z90 z90)
        v    (q/rotate-vector q180 (v3/vec3 1 0 0))]
    (is (approx [-1 0 0] (v3/->vec v))))
  ;; non-commutativity: z90 * y90 != y90 * z90 (sanity, just must differ)
  (let [y90 (q/from-axis-angle (v3/vec3 0 1 0) (/ Math/PI 2))]
    (is (not (approx (v3/->vec (q/axis (q/mul z90 y90)))
                     (v3/->vec (q/axis (q/mul y90 z90))))))))

(deftest conjugate-and-invert
  (let [qq (q/from-axis-angle (v3/vec3 0 0 1) (/ Math/PI 4))
        p  (q/mul qq (q/invert qq))]
    (is (approx [0 0 0 1] [(q/x p) (q/y p) (q/z p) (q/w p)]))))

(deftest normalize
  (let [qq (q/normalize (q/quat 0 0 0.5 0.5))]
    (is (approx 1.0 (q/magnitude qq)))))

(deftest slerp
  ;; halfway between identity and 90°-about-z is 45°-about-z
  (let [m  (q/slerp (q/identity) z90 0.5)
        v  (q/rotate-vector m (v3/vec3 1 0 0))]
    (is (approx [(Math/cos (/ Math/PI 4)) (Math/sin (/ Math/PI 4)) 0] (v3/->vec v))))
  ;; endpoints
  (let [a (q/slerp (q/identity) z90 0.0)
        b (q/slerp (q/identity) z90 1.0)]
    (is (approx [0 0 0 1] [(q/x a) (q/y a) (q/z a) (q/w a)]))
    (is (approx [(q/x z90) (q/y z90) (q/z z90) (q/w z90)]
                [(q/x b) (q/y b) (q/z b) (q/w b)]))))

(deftest as-matrix
  (let [mat (q/as-matrix z90)]
    (is (approx [0 1 0] (glimmer-gl.matrix/transform-point mat [1 0 0])))))

(deftest alignment-rotation
  (let [rot (q/from-rotation-between (v3/vec3 1 0 0) (v3/vec3 0 1 0))]
    (is (approx [0 1 0] (v3/->vec (q/rotate-vector rot (v3/vec3 1 0 0)))))
    ;; parallel vectors → identity
    (let [id (q/from-rotation-between (v3/vec3 1 0 0) (v3/vec3 2 0 0))]
      (is (approx [0 0 0 1] [(q/x id) (q/y id) (q/z id) (q/w id)])))))
