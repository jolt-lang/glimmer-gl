(ns glimmer-gl.circle
  "2D circle, ported from thi.ng/geom's Circle2. Plain functions on a
  defrecord with a Vec2 center `p` and a ^double radius `r`."
  (:require [glimmer-gl.vec2 :as v2 :refer [vec2 Vec2]]
            [glimmer-gl.rect :as rect :refer [Rect2]]))

(def ^:private ^double two-pi (* 2.0 Math/PI))

(defrecord Circle2 [^Vec2 p ^double r])

(defn circle
  "Construct a Circle2. Arities:
    (circle)        -> unit circle at the origin
    (circle r)      -> radius r at the origin
    (circle p r)    -> center p (Vec2), radius r
    (circle x y r)  -> center (x,y), radius r"
  ([] (Circle2. (vec2 0.0) 1.0))
  ([^double r] (Circle2. (vec2 0.0) r))
  ([^Vec2 p ^double r] (Circle2. p r))
  ([^double x ^double y ^double r] (Circle2. (vec2 x y) r)))

(defn p [^Circle2 c] (.-p c))
(defn r [^Circle2 c] (.-r c))

(defn width  [^Circle2 c] (* 2.0 (.-r c)))
(defn height [^Circle2 c] (* 2.0 (.-r c)))

(defn area [^Circle2 c] (* Math/PI (.-r c) (.-r c)))

(defn circumference [^Circle2 c] (* two-pi (.-r c)))

(defn centroid [^Circle2 c] (.-p c))

(defn bounds [^Circle2 c]
  (Rect2. (v2/sub (.-p c) (vec2 (.-r c)))
          (vec2 (* 2.0 (.-r c)))))

(defn contains-point?
  "True when q is within or on the circle (inclusive boundary)."
  [^Circle2 c ^Vec2 q]
  (<= (v2/dist-squared (.-p c) q) (* (.-r c) (.-r c))))

(defn closest-point
  "Boundary point of c in the direction of q (always at radius r from p)."
  [^Circle2 c ^Vec2 q]
  (let [d (v2/sub q (.-p c))
        m (v2/magnitude d)]
    (if (zero? m)
      (v2/add (.-p c) (vec2 (.-r c) 0.0))
      (v2/add (.-p c) (v2/scale d (/ (.-r c) m))))))

(defn point-at
  "Point on the circle at parameter t in [0,1) (t maps to angle 2*PI*t,
  measured CCW from the +x axis)."
  [^Circle2 c ^double t]
  (let [a (* two-pi t)]
    (v2/add (.-p c) (vec2 (* (.-r c) (Math/cos a)) (* (.-r c) (Math/sin a))))))

(defn vertices
  "res points sampled evenly around the circle (CCW from +x). Defaults to
  `default-resolution` (20)."
  ([^Circle2 c] (vertices c 20))
  ([^Circle2 c ^long res]
   (mapv #(point-at c (/ ^double % ^double res)) (range res))))

(defn center
  "One-arg: same circle centered on the origin. Two-arg: centered on o."
  ([^Circle2 c] (Circle2. (vec2 0.0) (.-r c)))
  ([^Circle2 c ^Vec2 o] (Circle2. o (.-r c))))

(defn translate [^Circle2 c ^Vec2 t]
  (Circle2. (v2/add (.-p c) t) (.-r c)))

(defn scale
  "Scale center and radius about the origin by s."
  [^Circle2 c ^double s]
  (Circle2. (v2/scale (.-p c) s) (* (.-r c) s)))

(defn scale-size
  "Grow the radius by s, keeping the center fixed."
  [^Circle2 c ^double s]
  (Circle2. (.-p c) (* (.-r c) s)))
