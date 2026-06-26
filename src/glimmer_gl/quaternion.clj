(ns glimmer-gl.quaternion
  "Unit quaternions for 3D rotation, ported from thi.ng/geom's Quaternion.
  Stored as (x y z w): w is the scalar (cos θ/2), xyz the imaginary part
  (axis·sin θ/2). deftype with ^double fields for hot-path rotation; plain
  functions, house style."
  (:require [glimmer-gl.vector :as v3 :refer [vec3 Vec3]]
            [glimmer-gl.matrix :as mat]))

(deftype Quaternion [^double x ^double y ^double z ^double w])

(defn quat ^Quaternion [^double x ^double y ^double z ^double w]
  (Quaternion. x y z w))

(defn identity ^Quaternion [] (Quaternion. 0.0 0.0 0.0 1.0))

(defn x ^double [^Quaternion q] (.-x q))
(defn y ^double [^Quaternion q] (.-y q))
(defn z ^double [^Quaternion q] (.-z q))
(defn w ^double [^Quaternion q] (.-w q))

(defn axis
  "Imaginary part (xyz) as a Vec3. Not necessarily unit unless q is unit."
  [^Quaternion q]
  (vec3 (.-x q) (.-y q) (.-z q)))

(defn magnitude-squared ^double [^Quaternion q]
  (+ (* (.-x q) (.-x q)) (* (.-y q) (.-y q))
     (* (.-z q) (.-z q)) (* (.-w q) (.-w q))))

(defn magnitude ^double [^Quaternion q]
  (Math/sqrt (magnitude-squared q)))

(defn normalize ^Quaternion [^Quaternion q]
  (let [m (magnitude q)]
    (if (zero? m)
      q
      (Quaternion. (/ (.-x q) m) (/ (.-y q) m) (/ (.-z q) m) (/ (.-w q) m)))))

(defn conjugate ^Quaternion [^Quaternion q]
  (Quaternion. (- (.-x q)) (- (.-y q)) (- (.-z q)) (.-w q)))

(defn invert ^Quaternion [^Quaternion q]
  (let [m2 (magnitude-squared q)
        i  (if (zero? m2) 0.0 (/ 1.0 m2))]
    (Quaternion. (* (- (.-x q)) i) (* (- (.-y q)) i)
                 (* (- (.-z q)) i) (* (.-w q) i))))

(defn dot ^double [^Quaternion a ^Quaternion b]
  (+ (* (.-x a) (.-x b)) (* (.-y a) (.-y b))
     (* (.-z a) (.-z b)) (* (.-w a) (.-w b))))

(defn mul
  "Hamilton product a⊗b (applies a's rotation, then b's)."
  ^Quaternion [^Quaternion a ^Quaternion b]
  (let [ax (.-x a) ay (.-y a) az (.-z a) aw (.-w a)
        bx (.-x b) by (.-y b) bz (.-z b) bw (.-w b)]
    (Quaternion.
      (+ (* aw bx) (* ax bw) (* ay bz) (- (* az by)))
      (+ (* aw by) (- (* ax bz)) (* ay bw) (* az bx))
      (+ (* aw bz) (* ax by) (- (* ay bx)) (* az bw))
      (- (* aw bw) (* ax bx) (* ay by) (* az bz)))))

(defn from-axis-angle
  "^double theta is radians. Axis is normalized internally."
  ^Quaternion [^Vec3 axis ^double theta]
  (let [h (* 0.5 theta)
        s (Math/sin h)
        u (v3/normalize axis)]
    (Quaternion. (* (v3/x u) s) (* (v3/y u) s) (* (v3/z u) s) (Math/cos h))))

(defn rotate-vector
  "Rotate v by the unit quaternion q (optimized: no matrix). For a non-unit q
  the result is scaled by |q|², so callers should normalize first."
  [^Quaternion q ^Vec3 v]
  (let [qv (vec3 (.-x q) (.-y q) (.-z q))
        t  (v3/scale (v3/cross qv v) 2.0)]
    (v3/add v (v3/add (v3/scale t (.-w q)) (v3/cross qv t)))))

(defn slerp
  "Spherical linear interpolation along the shortest great-circle arc from
  a to b. t=0 → a, t=1 → b."
  ^Quaternion [^Quaternion a ^Quaternion b ^double t]
  (let [ax (.-x a) ay (.-y a) az (.-z a) aw (.-w a)
        bx (.-x b) by (.-y b) bz (.-z b) bw (.-w b)
        dot0 (dot a b)
        flip (neg? dot0)
        bx (if flip (- bx) bx)
        by (if flip (- by) by)
        bz (if flip (- bz) bz)
        bw (if flip (- bw) bw)
        d (if flip (- dot0) dot0)]
    (if (> d 0.9995)
      ;; nearly parallel: cheap lerp + normalize
      (normalize (Quaternion. (+ ax (* (- bx ax) t))
                              (+ ay (* (- by ay) t))
                              (+ az (* (- bz az) t))
                              (+ aw (* (- bw aw) t))))
      (let [theta0 (Math/acos d)
            theta  (* theta0 t)
            st     (Math/sin theta)
            st0    (Math/sin theta0)
            s1     (/ st st0)
            s0     (- (Math/cos theta) (* d s1))]
        (Quaternion. (+ (* s0 ax) (* s1 bx))
                     (+ (* s0 ay) (* s1 by))
                     (+ (* s0 az) (* s1 bz))
                     (+ (* s0 aw) (* s1 bw)))))))

(defn as-matrix
  "Convert q (unit) to a rotation Matrix44 (no translation)."
  [^Quaternion q]
  (let [x (.-x q) y (.-y q) z (.-z q) w (.-w q)
        x2 (* 2.0 x) y2 (* 2.0 y) z2 (* 2.0 z)
        xx (* x x2) xy (* x y2) xz (* x z2)
        yy (* y y2) yz (* y z2) zz (* z z2)
        wx (* w x2) wy (* w y2) wz (* w z2)]
    (mat/matrix44
      (- 1.0 (+ yy zz)) (+ xy wz) (- xz wy) 0.0
      (- xy wz) (- 1.0 (+ xx zz)) (+ yz wx) 0.0
      (+ xz wy) (- yz wx) (- 1.0 (+ xx yy)) 0.0
      0.0 0.0 0.0 1.0)))

(defn from-rotation-between
  "Shortest-arc quaternion rotating unit-vector a onto unit-vector b.
  Parallel → identity; antiparallel → 180° about any perpendicular axis."
  ^Quaternion [^Vec3 a ^Vec3 b]
  (let [a (v3/normalize a)
        b (v3/normalize b)
        d (v3/dot a b)]
    (cond
      (>= d 1.0) (identity)
      (<= d -1.0)
      (let [ref  (if (zero? (v3/x a)) (vec3 1 0 0) (vec3 0 1 0))
            axis (v3/normalize (v3/cross a ref))]
        (from-axis-angle axis Math/PI))
      :else
      (let [axis (v3/cross a b)
            s    (Math/sqrt (* (+ 1.0 d) 2.0))   ; = 2·cos(half)
            invs (/ 1.0 s)]
        (normalize (Quaternion. (* (v3/x axis) invs) (* (v3/y axis) invs)
                                (* (v3/z axis) invs) (* 0.5 s)))))))
