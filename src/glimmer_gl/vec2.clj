(ns glimmer-gl.vec2
  "Lean 2D vector math, ported from thi.ng/geom's Vec2. Uses unboxed
  ^double fields. 2D shapes (rect, circle, line) build on this.")

(def ^:private sqrt #(Math/sqrt %))

(defrecord Vec2 [^double x ^double y])

(defn vec2
  ([^double x ^double y] (Vec2. x y))
  ([^double s] (Vec2. s s)))

(defn x [^Vec2 v] (.-x v))
(defn y [^Vec2 v] (.-y v))

(defn ->vec [^Vec2 v] [(.-x v) (.-y v)])

(defn add [^Vec2 a ^Vec2 b]
  (Vec2. (+ (.-x a) (.-x b))
         (+ (.-y a) (.-y b))))

(defn sub [^Vec2 a ^Vec2 b]
  (Vec2. (- (.-x a) (.-x b))
         (- (.-y a) (.-y b))))

(defn scale [^Vec2 v ^double s]
  (Vec2. (* (.-x v) s) (* (.-y v) s)))

(defn mul
  "Componentwise product."
  [^Vec2 a ^Vec2 b]
  (Vec2. (* (.-x a) (.-x b)) (* (.-y a) (.-y b))))

(defn min
  "Componentwise min."
  [^Vec2 a ^Vec2 b]
  (Vec2. (Math/min (.-x a) (.-x b)) (Math/min (.-y a) (.-y b))))

(defn max
  "Componentwise max."
  [^Vec2 a ^Vec2 b]
  (Vec2. (Math/max (.-x a) (.-x b)) (Math/max (.-y a) (.-y b))))

(defn dot [^Vec2 a ^Vec2 b]
  (+ (* (.-x a) (.-x b)) (* (.-y a) (.-y b))))

(defn mag-squared [^Vec2 v]
  (dot v v))

(defn magnitude [^Vec2 v]
  (sqrt (dot v v)))

(defn normalize [^Vec2 v]
  (let [m (magnitude v)]
    (if (zero? m) v (scale v (/ 1.0 m)))))

(defn dist-squared [^Vec2 a ^Vec2 b]
  (let [dx (- (.-x a) (.-x b))
        dy (- (.-y a) (.-y b))]
    (+ (* dx dx) (* dy dy))))

(defn dist [^Vec2 a ^Vec2 b]
  (sqrt (dist-squared a b)))

(defn mix
  "Linear interpolation from a to b. The two-arg form is the midpoint (t=0.5),
  matching thi.ng's m/mix."
  ([^Vec2 a ^Vec2 b] (mix a b 0.5))
  ([^Vec2 a ^Vec2 b ^double t]
   (Vec2. (+ (.-x a) (* (- (.-x b) (.-x a)) t))
          (+ (.-y a) (* (- (.-y b) (.-y a)) t)))))

(defn centroid
  "Average of a non-empty seq of Vec2."
  [vs]
  (scale (reduce add (Vec2. 0.0 0.0) vs) (/ 1.0 (count vs))))

(defn perpendicular
  "90-degree counter-clockwise rotation: (x,y) -> (-y,x)."
  [^Vec2 v]
  (Vec2. (- (.-y v)) (.-x v)))

(defn rotate
  "Rotate v about the origin by theta radians (CCW)."
  [^Vec2 v ^double theta]
  (let [c (Math/cos theta) s (Math/sin theta)
        x (.-x v) y (.-y v)]
    (Vec2. (- (* x c) (* y s))
           (+ (* x s) (* y c)))))

(defn heading
  "Angle of v from the +x axis, in radians (atan2 y x)."
  [^Vec2 v]
  (clojure.math/atan2 (.-y v) (.-x v)))

(defn cross
  "2D scalar cross product: ax*by - ay*bx. Positive when b is CCW from a."
  [^Vec2 a ^Vec2 b]
  (- (* (.-x a) (.-y b)) (* (.-y a) (.-x b))))
