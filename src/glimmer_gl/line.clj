(ns glimmer-gl.line
  "3D line segment, ported from thi.ng/geom's Line3. Stored as two hinted
  Vec3 endpoints `a` (start) and `b` (end). Plain functions."
  (:require [glimmer-gl.vector :as v3 :refer [vec3 Vec3]]))

(defrecord Line3 [^Vec3 a ^Vec3 b])

(defn line3
  "Construct a Line3. Arities:
    (line3 p q)         -> two Vec3 endpoints
    (line3 [p q])       -> pair of points
    (line3 px py qx qy) -> four scalars (z=0 both ends)
    (line3 px py pz qx qy qz) -> six scalars"
  ([^Vec3 p ^Vec3 q] (Line3. p q))
  ([[p q]] (Line3. (vec3 p) (vec3 q)))
  ([^double px ^double py ^double qx ^double qy] (Line3. (vec3 px py 0.0) (vec3 qx qy 0.0)))
  ([^double px ^double py ^double pz ^double qx ^double qy ^double qz]
   (Line3. (vec3 px py pz) (vec3 qx qy qz))))

(defn start  [^Line3 l] (.-a l))
(defn end    [^Line3 l] (.-b l))
(defn points [^Line3 l] [(.-a l) (.-b l)])

(defn length         [^Line3 l] (v3/dist (.-a l) (.-b l)))
(defn length-squared [^Line3 l] (v3/dist-squared (.-a l) (.-b l)))

(defn direction
  "Unit vector from start to end."
  [^Line3 l]
  (v3/normalize (v3/sub (.-b l) (.-a l))))

(defn midpoint [^Line3 l] (v3/mix (.-a l) (.-b l)))
(defn centroid [^Line3 l] (v3/mix (.-a l) (.-b l)))

(defn point-at
  "Point at parameter t along the segment (0 = start, 1 = end)."
  [^Line3 l ^double t]
  (v3/mix (.-a l) (.-b l) t))

(defn closest-coeff
  "Clamped projection coefficient of q onto the segment, in [0,1]."
  [^Line3 l ^Vec3 q]
  (let [ab (v3/sub (.-b l) (.-a l))
        denom (v3/dot ab ab)]
    (if (zero? denom)
      0.0
      (let [t (/ (v3/dot (v3/sub q (.-a l)) ab) denom)]
        (Math/max 0.0 (Math/min t 1.0))))))

(defn closest-point
  "Nearest point of the segment to q (clamped to the endpoints)."
  [^Line3 l ^Vec3 q]
  (v3/add (.-a l) (v3/scale (v3/sub (.-b l) (.-a l)) (closest-coeff l q))))

(defn contains-point?
  "True when q lies on the segment (projection coefficient in [0,1])."
  [^Line3 l ^Vec3 q]
  (< (v3/magnitude (v3/sub (closest-point l q) q)) 1e-6))

(defn flip [^Line3 l] (Line3. (.-b l) (.-a l)))

(defn normalize
  "Reshape into a unit-length segment starting at the original start,
  pointing toward the original end."
  [^Line3 l]
  (Line3. (.-a l) (v3/add (.-a l) (direction l))))

(defn translate [^Line3 l ^Vec3 t]
  (Line3. (v3/add (.-a l) t) (v3/add (.-b l) t)))

(defn scale
  "Scale both endpoints about the origin by s."
  [^Line3 l ^double s]
  (Line3. (v3/scale (.-a l) s) (v3/scale (.-b l) s)))

(defn scale-size
  "Scale the segment length by s, keeping its midpoint fixed."
  [^Line3 l ^double s]
  (let [c (centroid l)
        d (v3/scale (v3/sub (.-b l) (.-a l)) (* 0.5 s))]
    (Line3. (v3/sub c d) (v3/add c d))))
