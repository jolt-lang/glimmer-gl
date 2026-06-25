(ns geom-gl.matrix
  "Column-major 4×4 matrices (OpenGL convention), ported from thi.ng/geom.

  Storage matches the layout glUniformMatrix4fv expects: each block of 4
  values is one column, so m{c}{r} reads as column c, row r. The deftype
  holds 16 unboxed double fields; arithmetic is written as inlining macros
  so the Chez compiler sees straight flonum math with no per-op allocation.")

;; ---------------------------------------------------------------------------
;; Inlined multiply-add / multiply-subtract, copied from thi.ng's math macros.
;; Each consecutive arg pair is one product: madd sums them, msub subtracts
;; every product after the first. Inlined (not fns) so the host compiler sees
;; flat flonum math with no per-op allocation.
(defmacro madd
  ([a b] `(* ~a ~b))
  ([a b c d] `(+ (* ~a ~b) (* ~c ~d)))
  ([a b c d e f] `(+ (* ~a ~b) (* ~c ~d) (* ~e ~f)))
  ([a b c d e f g h] `(+ (* ~a ~b) (* ~c ~d) (* ~e ~f) (* ~g ~h))))

(defmacro msub
  ([a b] `(* ~a ~b))
  ([a b c d] `(- (* ~a ~b) (* ~c ~d)))
  ([a b c d e f] `(- (* ~a ~b) (* ~c ~d) (* ~e ~f))))

;; One inverse cofactor entry: (a*b - c*d + e*f) * invd. Three signed 2x2-minor
;; products in an alternating +,-,+ pattern; thi.ng flips extra signs by passing
;; negated args (e.g. `(- m01)`). Copied from thi.ng.
(defmacro inv-item [a b c d e f invd]
  `(* (+ (* ~a ~b) (- (* ~c ~d)) (* ~e ~f)) ~invd))

;; ---------------------------------------------------------------------------
(deftype Matrix44
  [^double m00 ^double m01 ^double m02 ^double m03
   ^double m10 ^double m11 ^double m12 ^double m13
   ^double m20 ^double m21 ^double m22 ^double m23
   ^double m30 ^double m31 ^double m32 ^double m33])

(defn matrix44
  "Positional constructor taking all 16 components in column-major order."
  ^Matrix44 [m00 m01 m02 m03
             m10 m11 m12 m13
             m20 m21 m22 m23
             m30 m31 m32 m33]
  (Matrix44. m00 m01 m02 m03
             m10 m11 m12 m13
             m20 m21 m22 m23
             m30 m31 m32 m33))

(defn ident ^Matrix44 [] (Matrix44. 1 0 0 0  0 1 0 0  0 0 1 0  0 0 0 1))

(defn ->vec
  "Return the 16 components in column-major order (matches the GL upload layout)."
  [^Matrix44 m]
  [(.-m00 m) (.-m01 m) (.-m02 m) (.-m03 m)
   (.-m10 m) (.-m11 m) (.-m12 m) (.-m13 m)
   (.-m20 m) (.-m21 m) (.-m22 m) (.-m23 m)
   (.-m30 m) (.-m31 m) (.-m32 m) (.-m33 m)])

(defn add ^Matrix44 [^Matrix44 a ^Matrix44 b]
  (Matrix44.
    (+ (.-m00 a) (.-m00 b)) (+ (.-m01 a) (.-m01 b)) (+ (.-m02 a) (.-m02 b)) (+ (.-m03 a) (.-m03 b))
    (+ (.-m10 a) (.-m10 b)) (+ (.-m11 a) (.-m11 b)) (+ (.-m12 a) (.-m12 b)) (+ (.-m13 a) (.-m13 b))
    (+ (.-m20 a) (.-m20 b)) (+ (.-m21 a) (.-m21 b)) (+ (.-m22 a) (.-m22 b)) (+ (.-m23 a) (.-m23 b))
    (+ (.-m30 a) (.-m30 b)) (+ (.-m31 a) (.-m31 b)) (+ (.-m32 a) (.-m32 b)) (+ (.-m33 a) (.-m33 b))))

(defn sub ^Matrix44 [^Matrix44 a ^Matrix44 b]
  (Matrix44.
    (- (.-m00 a) (.-m00 b)) (- (.-m01 a) (.-m01 b)) (- (.-m02 a) (.-m02 b)) (- (.-m03 a) (.-m03 b))
    (- (.-m10 a) (.-m10 b)) (- (.-m11 a) (.-m11 b)) (- (.-m12 a) (.-m12 b)) (- (.-m13 a) (.-m13 b))
    (- (.-m20 a) (.-m20 b)) (- (.-m21 a) (.-m21 b)) (- (.-m22 a) (.-m22 b)) (- (.-m23 a) (.-m23 b))
    (- (.-m30 a) (.-m30 b)) (- (.-m31 a) (.-m31 b)) (- (.-m32 a) (.-m32 b)) (- (.-m33 a) (.-m33 b))))

(defn mul
  "Matrix product a·b (column-major). Bind every field once so the inlined
  madd forms reference plain flonum locals."
  ^Matrix44 [^Matrix44 a ^Matrix44 b]
  (let [a00 (.-m00 a) a01 (.-m01 a) a02 (.-m02 a) a03 (.-m03 a)
        a10 (.-m10 a) a11 (.-m11 a) a12 (.-m12 a) a13 (.-m13 a)
        a20 (.-m20 a) a21 (.-m21 a) a22 (.-m22 a) a23 (.-m23 a)
        a30 (.-m30 a) a31 (.-m31 a) a32 (.-m32 a) a33 (.-m33 a)
        b00 (.-m00 b) b01 (.-m01 b) b02 (.-m02 b) b03 (.-m03 b)
        b10 (.-m10 b) b11 (.-m11 b) b12 (.-m12 b) b13 (.-m13 b)
        b20 (.-m20 b) b21 (.-m21 b) b22 (.-m22 b) b23 (.-m23 b)
        b30 (.-m30 b) b31 (.-m31 b) b32 (.-m32 b) b33 (.-m33 b)]
    (Matrix44.
      (madd a00 b00 a10 b01 a20 b02 a30 b03)
      (madd a01 b00 a11 b01 a21 b02 a31 b03)
      (madd a02 b00 a12 b01 a22 b02 a32 b03)
      (madd a03 b00 a13 b01 a23 b02 a33 b03)

      (madd a00 b10 a10 b11 a20 b12 a30 b13)
      (madd a01 b10 a11 b11 a21 b12 a31 b13)
      (madd a02 b10 a12 b11 a22 b12 a32 b13)
      (madd a03 b10 a13 b11 a23 b12 a33 b13)

      (madd a00 b20 a10 b21 a20 b22 a30 b23)
      (madd a01 b20 a11 b21 a21 b22 a31 b23)
      (madd a02 b20 a12 b21 a22 b22 a32 b23)
      (madd a03 b20 a13 b21 a23 b22 a33 b23)

      (madd a00 b30 a10 b31 a20 b32 a30 b33)
      (madd a01 b30 a11 b31 a21 b32 a31 b33)
      (madd a02 b30 a12 b31 a22 b32 a32 b33)
      (madd a03 b30 a13 b31 a23 b32 a33 b33))))

(defn transpose ^Matrix44 [^Matrix44 a]
  (Matrix44.
    (.-m00 a) (.-m10 a) (.-m20 a) (.-m30 a)
    (.-m01 a) (.-m11 a) (.-m21 a) (.-m31 a)
    (.-m02 a) (.-m12 a) (.-m22 a) (.-m32 a)
    (.-m03 a) (.-m13 a) (.-m23 a) (.-m33 a)))

(defn determinant [^Matrix44 a]
  (let [a00 (.-m00 a) a01 (.-m01 a) a02 (.-m02 a) a03 (.-m03 a)
        a10 (.-m10 a) a11 (.-m11 a) a12 (.-m12 a) a13 (.-m13 a)
        a20 (.-m20 a) a21 (.-m21 a) a22 (.-m22 a) a23 (.-m23 a)
        a30 (.-m30 a) a31 (.-m31 a) a32 (.-m32 a) a33 (.-m33 a)
        b00 (msub a00 a11 a01 a10) b01 (msub a00 a12 a02 a10)
        b02 (msub a00 a13 a03 a10) b03 (msub a01 a12 a02 a11)
        b04 (msub a01 a13 a03 a11) b05 (msub a02 a13 a03 a12)
        b06 (msub a20 a31 a21 a30) b07 (msub a20 a32 a22 a30)
        b08 (msub a20 a33 a23 a30) b09 (msub a21 a32 a22 a31)
        b10 (msub a21 a33 a23 a31) b11 (msub a22 a33 a23 a32)]
    (+ (msub b00 b11 b01 b10 b04 b07)
       (madd b02 b09 b03 b08 b05 b06))))

(defn invert
  "General 4×4 inverse, or nil when singular (determinant 0). Ported verbatim
  from thi.ng/geom's cofactor expansion."
  (^Matrix44 [^Matrix44 a]
   (let [a00 (.-m00 a) a01 (.-m01 a) a02 (.-m02 a) a03 (.-m03 a)
         a10 (.-m10 a) a11 (.-m11 a) a12 (.-m12 a) a13 (.-m13 a)
         a20 (.-m20 a) a21 (.-m21 a) a22 (.-m22 a) a23 (.-m23 a)
         a30 (.-m30 a) a31 (.-m31 a) a32 (.-m32 a) a33 (.-m33 a)
         n00 (msub a00 a11 a01 a10) n01 (msub a00 a12 a02 a10)
         n02 (msub a00 a13 a03 a10) n03 (msub a01 a12 a02 a11)
         n04 (msub a01 a13 a03 a11) n05 (msub a02 a13 a03 a12)
         n06 (msub a20 a31 a21 a30) n07 (msub a20 a32 a22 a30)
         n08 (msub a20 a33 a23 a30) n09 (msub a21 a32 a22 a31)
         n10 (msub a21 a33 a23 a31) n11 (msub a22 a33 a23 a32)
         d   (+ (msub n00 n11 n01 n10 n04 n07)
                (madd n02 n09 n03 n08 n05 n06))]
     (when-not (zero? d)
       (let [invd (/ 1.0 d)]
         (Matrix44.
           (inv-item a11 n11 a12 n10 a13 n09 invd)
           (inv-item a02 n10 a03 n09 (- a01) n11 invd)
           (inv-item a31 n05 a32 n04 a33 n03 invd)
           (inv-item a22 n04 a23 n03 (- a21) n05 invd)

           (inv-item a12 n08 a13 n07 (- a10) n11 invd)
           (inv-item a00 n11 a02 n08 a03 n07 invd)
           (inv-item a32 n02 a33 n01 (- a30) n05 invd)
           (inv-item a20 n05 a22 n02 a23 n01 invd)

           (inv-item a10 n10 a11 n08 a13 n06 invd)
           (inv-item a01 n08 a03 n06 (- a00) n10 invd)
           (inv-item a30 n04 a31 n02 a33 n00 invd)
           (inv-item a21 n02 a23 n00 (- a20) n04 invd)

           (inv-item a11 n07 a12 n06 (- a10) n09 invd)
           (inv-item a00 n09 a01 n07 a02 n06 invd)
           (inv-item a31 n01 a32 n00 (- a30) n03 invd)
           (inv-item a20 n03 a21 n01 a22 n00 invd)))))))

;; ---------------------------------------------------------------------------
;; Constructors. fov in degrees; near/far in world units.
(def ^:private ^double deg->rad (/ Math/PI 180.0))

(defn translation ^Matrix44 [^double tx ^double ty ^double tz]
  (Matrix44. 1 0 0 0  0 1 0 0  0 0 1 0  tx ty tz 1))

(defn scaling ^Matrix44 [^double sx ^double sy ^double sz]
  (Matrix44. sx 0 0 0  0 sy 0 0  0 0 sz 0  0 0 0 1))

(defn perspective
  ^Matrix44 [^double fovy-deg ^double aspect ^double near ^double far]
  (let [f   (/ 1.0 (Math/tan (* 0.5 fovy-deg deg->rad)))
        nf  (/ 1.0 (- near far))]
    (Matrix44.
      (/ f aspect) 0.0 0.0 0.0
      0.0 f 0.0 0.0
      0.0 0.0 (+ (* near nf) far) -1.0
      0.0 0.0 (* 2.0 near far nf) 0.0)))

;; Rotation about a single axis by theta (radians). Column-major storage, so
;; each group of four values below is one matrix column.
(defn rotate-x ^Matrix44 [^double theta]
  (let [c (Math/cos theta) s (Math/sin theta)]
    (Matrix44. 1 0 0 0  0 c s 0  0 (- s) c 0  0 0 0 1)))

(defn rotate-y ^Matrix44 [^double theta]
  (let [c (Math/cos theta) s (Math/sin theta)]
    (Matrix44. c 0 (- s) 0  0 1 0 0  s 0 c 0  0 0 0 1)))

(defn rotate-z ^Matrix44 [^double theta]
  (let [c (Math/cos theta) s (Math/sin theta)]
    (Matrix44. c s 0 0  (- s) c 0 0  0 0 1 0  0 0 0 1)))
