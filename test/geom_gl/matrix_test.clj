(ns geom-gl.matrix-test
  (:require [clojure.test :refer [deftest is testing]]
            [geom-gl.matrix :as m]))

;; Column-major 4×4 (OpenGL convention): each successive 4 values are one column.
;; ->vec returns the 16 components in that order, so tests assert on raw storage.

(defn approx
  "Elementwise fuzzy equality of two 16-seqs within eps (float math drift)."
  ([a b] (approx a b 1e-9))
  ([a b eps]
   (and (== 16 (count a) (count b))
        (every? (fn [[x y]] (< (Math/abs (- x y)) eps))
                (map vector a b)))))

(deftest ident-is-column-major-diagonal
  (is (approx [1 0 0 0  0 1 0 0  0 0 1 0  0 0 0 1]
           (m/->vec (m/ident)))))

(deftest multiply-by-identity-is-noop
  (let [id (m/ident)
        t  (m/translation 1 2 3)]
    (testing "identity * identity = identity"
      (is (approx [1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1]
               (m/->vec (m/mul id id)))))
    (testing "identity * t = t"
      (is (approx (m/->vec t) (m/->vec (m/mul id t)))))
    (testing "t * identity = t"
      (is (approx (m/->vec t) (m/->vec (m/mul t id)))))))

(deftest translation-populates-last-column
  ;; column-major: col3 = [tx ty tz 1]
  (is (approx [1 0 0 0  0 1 0 0  0 0 1 0  1 2 3 1]
           (m/->vec (m/translation 1 2 3)))))

(deftest multiply-composes-translations
  (let [a (m/translation 1 0 0)
        b (m/translation 0 2 0)
        ab (m/mul a b)]
    (is (approx [1 0 0 0  0 1 0 0  0 0 1 0  1 2 0 1]
             (m/->vec ab)))))

(deftest scaling-scales-the-rotation-block
  (is (approx [2 0 0 0  0 3 0 0  0 0 4 0  0 0 0 1]
           (m/->vec (m/scaling 2 3 4)))))

(deftest perspective-known-answer
  ;; fovy=90°, aspect=1, near=0.1, far=100.
  ;; f = 1/tan(45°) ≈ 1; nf = 1/(0.1-100) = 1/-99.9 = -0.010010010...
  ;; m00 = f/aspect ≈ 1, m11 = f ≈ 1,
  ;; m22 = near*nf + far ≈ 99.998998999, m23 = -1,
  ;; m32 = 2*near*far*nf ≈ -0.2002002002
  (let [p (m/perspective 90.0 1.0 0.1 100.0)
        v (m/->vec p)]
    (is (approx [1.0 0 0 0
              0 1.0 0 0
              0 0 99.998998999 -1.0
              0 0 -0.2002002002 0]
             v 1e-6))))

(deftest invert-roundtrips
  ;; M * invert(M) ≈ identity for an invertible M.
  (let [mat (m/mul (m/translation 1 2 3)
                   (m/scaling 2 2 2))
        inv (m/invert mat)]
    (is (some? inv))
    (is (approx [1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1]
             (m/->vec (m/mul mat inv)) 1e-7))))

(deftest transpose-double-transpose-is-identity-op
  (let [mat (m/mul (m/translation 1 0 0) (m/scaling 5 1 1))]
    (is (approx (m/->vec mat)
             (m/->vec (m/transpose (m/transpose mat)))))))

;; --- rotation ---------------------------------------------------------------

(deftest rotate-zero-is-identity
  (is (approx [1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1] (m/->vec (m/rotate-x 0))))
  (is (approx [1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1] (m/->vec (m/rotate-y 0))))
  (is (approx [1 0 0 0 0 1 0 0 0 0 1 0 0 0 0 1] (m/->vec (m/rotate-z 0)))))

(deftest rotate-z-quarter-turn-maps-x-to-y
  ;; cos=0, sin=1 -> +X axis ends up on +Y. Storage is column-major.
  ;; col0 = [0 1 0 0], col1 = [-1 0 0 0], col2/col3 untouched.
  (let [m (m/->vec (m/rotate-z (/ Math/PI 2)))]
    (is (approx [0 1 0 0  -1 0 0 0  0 0 1 0  0 0 0 1] m 1e-9))))

(deftest rotate-x-quarter-turn-maps-y-to-z
  ;; around X: +Y -> +Z. col1 = [0 0 1 0], col2 = [0 -1 0 0].
  (let [m (m/->vec (m/rotate-x (/ Math/PI 2)))]
    (is (approx [1 0 0 0  0 0 1 0  0 -1 0 0  0 0 0 1] m 1e-9))))

(deftest rotate-y-quarter-turn-maps-z-to-x
  ;; around Y: +Z -> +X. col0 = [0 0 -1 0], col2 = [1 0 0 0].
  (let [m (m/->vec (m/rotate-y (/ Math/PI 2)))]
    (is (approx [0 0 -1 0  0 1 0 0  1 0 0 0  0 0 0 1] m 1e-9))))

(deftest rotate-z-half-turn-negates-xy
  (is (approx [-1 0 0 0  0 -1 0 0  0 0 1 0  0 0 0 1]
           (m/->vec (m/rotate-z Math/PI)) 1e-9)))
