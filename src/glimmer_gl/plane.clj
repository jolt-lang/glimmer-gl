(ns glimmer-gl.plane
  "3D plane, ported from thi.ng/geom's Plane. Stored as a unit Vec3 normal `n`
  and a ^double `w` offset, with the plane equation n·x + w = 0 (so a point x
  is on the plane when dot(n,x) + w = 0). Plain functions."
  (:require [glimmer-gl.vector :as v3 :refer [vec3 Vec3]]
            [glimmer-gl.line :as line]))

(def ^:private ^double eps 1e-6)

(defrecord Plane [^Vec3 n ^double w])

(defn plane
  "Plane from a normal `n` and offset `w`. The normal is normalized; w is kept
  as given (i.e. w must already be consistent with the unit normal)."
  [^Vec3 n ^double w]
  (Plane. (v3/normalize n) w))

(defn plane-with-point
  "Plane through point `p` with normal `n` (normalized). w = -(n·p)."
  [^Vec3 p ^Vec3 n]
  (let [nn (v3/normalize n)]
    (Plane. nn (- (v3/dot nn p)))))

(defn plane-from-points
  "Plane through three points (winding: normal via (b-a) × (c-a), right-handed)."
  [^Vec3 a ^Vec3 b ^Vec3 c]
  (let [n (v3/normalize (v3/cross (v3/sub b a) (v3/sub c a)))]
    (Plane. n (- (v3/dot n a)))))

(defn normal [^Plane p] (.-n p))
(defn w      [^Plane p] (.-w p))

(defn centroid
  "Point of the plane closest to the origin: -w * n."
  [^Plane p]
  (v3/scale (.-n p) (- (.-w p))))

(defn dist
  "Signed distance from q to the plane (positive on the +n side)."
  [^Plane p ^Vec3 q]
  (+ (v3/dot (.-n p) q) (.-w p)))

(defn classify-point
  "Classify q relative to the plane: +1 in front (+n side), -1 behind, 0 on it."
  ([^Plane p ^Vec3 q] (classify-point p q eps))
  ([^Plane p ^Vec3 q ^double eps']
   (let [d (dist p q)]
     (cond (< d (- eps')) -1
           (> d eps')      1
           :else           0))))

(defn closest-point
  "Orthogonal projection of q onto the plane."
  [^Plane p ^Vec3 q]
  (v3/sub q (v3/scale (.-n p) (dist p q))))

(defn translate
  "Translate the plane by vector t (moves the centroid; rebuilds w)."
  [^Plane p ^Vec3 t]
  (let [newc (v3/add (centroid p) t)
        n    (.-n p)]
    (Plane. n (- (v3/dot n newc)))))

(defn flip
  "Flip the plane's orientation (same geometric plane)."
  [^Plane p]
  (Plane. (v3/scale (.-n p) -1.0) (- (.-w p))))

(defn intersect-line
  "Intersection of the plane with the infinite line through segment `ln`.
  Returns the hit Vec3, or nil when the line is parallel to the plane."
  [^Plane p ^line/Line3 ln]
  (let [dir (v3/sub (line/end ln) (line/start ln))
        denom (v3/dot (.-n p) dir)]
    (when (> (Math/abs denom) eps)
      (let [t (/ (- (+ (v3/dot (.-n p) (line/start ln)) (.-w p))) denom)]
        (v3/add (line/start ln) (v3/scale dir t))))))
