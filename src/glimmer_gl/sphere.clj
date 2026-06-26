(ns glimmer-gl.sphere
  "3D sphere, ported from thi.ng/geom's Sphere. Plain functions on a
  defrecord with a Vec3 center `p` and a ^double radius `r`."
  (:require [glimmer-gl.vector :as v3 :refer [vec3 Vec3]]
            [glimmer-gl.aabb :refer [AABB]]))

(defrecord Sphere [^Vec3 p ^double r])

(defn sphere
  "Construct a Sphere. Arities:
    (sphere)     -> unit sphere at the origin
    (sphere r)   -> radius r at the origin
    (sphere p r) -> center p (Vec3), radius r"
  ([] (Sphere. (vec3 0.0) 1.0))
  ([^double r] (Sphere. (vec3 0.0) r))
  ([^Vec3 p ^double r] (Sphere. p r)))

(defn p [^Sphere s] (.-p s))
(defn r [^Sphere s] (.-r s))

(defn width  [^Sphere s] (* 2.0 (.-r s)))
(defn height [^Sphere s] (* 2.0 (.-r s)))
(defn depth  [^Sphere s] (* 2.0 (.-r s)))

(defn area [^Sphere s]
  (* 4.0 Math/PI (.-r s) (.-r s)))

(defn centroid [^Sphere s] (.-p s))

(defn bounds [^Sphere s]
  (AABB. (v3/sub (.-p s) (vec3 (.-r s)))
         (vec3 (* 2.0 (.-r s)))))

(defn contains-point?
  "True when q is within or on the sphere (inclusive boundary)."
  [^Sphere s ^Vec3 q]
  (<= (v3/dist-squared (.-p s) q) (* (.-r s) (.-r s))))

(defn closest-point
  "Surface point of s in the direction of q (always at radius r from p)."
  [^Sphere s ^Vec3 q]
  (let [d (v3/sub q (.-p s))
        m (v3/magnitude d)]
    (if (zero? m)
      (v3/add (.-p s) (vec3 (.-r s) 0.0 0.0))
      (v3/add (.-p s) (v3/scale d (/ (.-r s) m))))))

(defn center
  "One-arg: same sphere centered on the origin. Two-arg: centered on o."
  ([^Sphere s] (Sphere. (vec3 0.0) (.-r s)))
  ([^Sphere s ^Vec3 o] (Sphere. o (.-r s))))

(defn translate [^Sphere s ^Vec3 t]
  (Sphere. (v3/add (.-p s) t) (.-r s)))

(defn scale
  "Scale center and radius about the origin by s."
  [^Sphere s ^double s']
  (Sphere. (v3/scale (.-p s) s') (* (.-r s) s')))

(defn scale-size
  "Grow the radius by s, keeping the center fixed."
  [^Sphere s ^double s']
  (Sphere. (.-p s) (* (.-r s) s')))
