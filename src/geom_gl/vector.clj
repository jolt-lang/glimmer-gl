(ns geom-gl.vector
  "Lean 3D vector math, ported from thi.ng/geom's Vec3. Uses unboxed
  ^double fields (geom itself uses a boxed double-array for swizzles).")

;; Faster than (Math/sqrt ...) when the host maps it to the FPU sqrt insn.
(def ^:private sqrt #(Math/sqrt %))

(defrecord Vec3 [^double x ^double y ^double z])

(defn vec3
  ([^double x ^double y ^double z] (Vec3. x y z))
  ([^double s] (Vec3. s s s)))

(defn x [^Vec3 v] (.-x v))
(defn y [^Vec3 v] (.-y v))
(defn z [^Vec3 v] (.-z v))

(defn ->vec [^Vec3 v] [(.-x v) (.-y v) (.-z v)])

(defn add [^Vec3 a ^Vec3 b]
  (Vec3. (+ (.-x a) (.-x b))
         (+ (.-y a) (.-y b))
         (+ (.-z a) (.-z b))))

(defn sub [^Vec3 a ^Vec3 b]
  (Vec3. (- (.-x a) (.-x b))
         (- (.-y a) (.-y b))
         (- (.-z a) (.-z b))))

(defn scale [^Vec3 v ^double s]
  (Vec3. (* (.-x v) s) (* (.-y v) s) (* (.-z v) s)))

(defn dot [^Vec3 a ^Vec3 b]
  (+ (+ (* (.-x a) (.-x b)) (* (.-y a) (.-y b))) (* (.-z a) (.-z b))))

(defn cross [^Vec3 a ^Vec3 b]
  (let [ax (.-x a) ay (.-y a) az (.-z a)
        bx (.-x b) by (.-y b) bz (.-z b)]
    (Vec3. (- (* ay bz) (* by az))
           (- (* az bx) (* bz ax))
           (- (* ax by) (* bx ay)))))

(defn magnitude [^Vec3 v]
  (sqrt (dot v v)))

(defn normalize [^Vec3 v]
  (let [m (magnitude v)]
    (if (zero? m) v (scale v (/ 1.0 m)))))

(defn dist-squared [^Vec3 a ^Vec3 b]
  (let [dx (- (.-x a) (.-x b))
        dy (- (.-y a) (.-y b))
        dz (- (.-z a) (.-z b))]
    (+ (+ (* dx dx) (* dy dy)) (* dz dz))))

(defn dist [^Vec3 a ^Vec3 b]
  (sqrt (dist-squared a b)))
