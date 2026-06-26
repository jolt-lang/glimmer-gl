(ns glimmer-gl.aabb
  "Axis-aligned 3D bounding box, ported from thi.ng/geom's AABB.
  Stored as a min corner `p` and an extent `sz` (both Vec3). Plain functions.

  NOTE: the extent field is `sz`, not `size` — in Jolt a defrecord field named
  `size` is shadowed by record-introspection (returns the field count). The
  public accessor is `size`; the stored field is `sz`."
  (:require [glimmer-gl.vector :as v3 :refer [vec3 Vec3]]))

(defrecord AABB [^Vec3 p ^Vec3 sz])

(defn aabb
  "Construct an AABB. Arities:
    (aabb)          -> unit cube at the origin
    (aabb size)     -> Vec3 extent at the origin
    (aabb o size)   -> origin o (Vec3), extent size (Vec3)
    (aabb sx sy sz) -> extent (sx,sy,sz) at the origin"
  ([] (AABB. (vec3 0.0) (vec3 1.0)))
  ([^Vec3 sz] (AABB. (vec3 0.0) sz))
  ([^Vec3 o ^Vec3 sz] (AABB. o sz))
  ([^double sx ^double sy ^double sz'] (AABB. (vec3 0.0) (vec3 sx sy sz')))
  ([^double x ^double y ^double z ^double sx ^double sy ^double sz']
   (AABB. (vec3 x y z) (vec3 sx sy sz'))))

(defn aabb-from-minmax
  "Build an AABB from two opposite corners (order-independent)."
  [^Vec3 a ^Vec3 b]
  (let [p (v3/min a b)]
    (AABB. p (v3/sub (v3/max a b) p))))

(defn p    [^AABB b] (.-p b))
(defn size [^AABB b] (.-sz b))

(defn width  [^AABB b] (v3/x (.-sz b)))
(defn height [^AABB b] (v3/y (.-sz b)))
(defn depth  [^AABB b] (v3/z (.-sz b)))

(defn min-point [^AABB b] (.-p b))
(defn max-point [^AABB b] (v3/add (.-p b) (.-sz b)))

(defn volume [^AABB b]
  (let [s (.-sz b)] (* (v3/x s) (v3/y s) (v3/z s))))

(defn area
  "Surface area of the box: 2*(w*h + h*d + w*d)."
  [^AABB b]
  (let [s (.-sz b)
        w (v3/x s) h (v3/y s) d (v3/z s)]
    (* 2.0 (+ (* w h) (* h d) (* w d)))))

(defn centroid [^AABB b]
  (v3/add (.-p b) (v3/scale (.-sz b) 0.5)))

(defn bounds [^AABB b] b)

(defn contains-point?
  "True when q is within [p, p+sz] on all three axes (inclusive)."
  [^AABB b ^Vec3 q]
  (let [px (v3/x (.-p b)) py (v3/y (.-p b)) pz (v3/z (.-p b))
        s  (.-sz b)
        w  (v3/x s) h (v3/y s) d (v3/z s)]
    (and (<= px (v3/x q) (+ px w))
         (<= py (v3/y q) (+ py h))
         (<= pz (v3/z q) (+ pz d)))))

(defn closest-point
  "Clamp q componentwise to the box's extent."
  [^AABB b ^Vec3 q]
  (let [px (v3/x (.-p b)) py (v3/y (.-p b)) pz (v3/z (.-p b))
        s  (.-sz b)
        mx (+ px (v3/x s)) my (+ py (v3/y s)) mz (+ pz (v3/z s))]
    (vec3 (Math/max px (Math/min (v3/x q) mx))
          (Math/max py (Math/min (v3/y q) my))
          (Math/max pz (Math/min (v3/z q) mz)))))

(defn center
  "One-arg: same box centered on the origin. Two-arg: centered on o."
  ([^AABB b]
   (AABB. (v3/scale (.-sz b) -0.5) (.-sz b)))
  ([^AABB b ^Vec3 o]
   (AABB. (v3/add (v3/scale (.-sz b) -0.5) o) (.-sz b))))

(defn vertices
  "Eight corners in thi.ng's canonical order:
  [a b c d e f g h], where a = p (min corner) and g = p+sz (max corner)."
  [^AABB b]
  (let [a (.-p b)
        x1 (v3/x a) y1 (v3/y a) z1 (v3/z a)
        g (v3/add a (.-sz b))
        x2 (v3/x g) y2 (v3/y g) z2 (v3/z g)]
    [a
     (vec3 x1 y1 z2)
     (vec3 x2 y1 z2)
     (vec3 x2 y1 z1)
     (vec3 x1 y2 z1)
     (vec3 x1 y2 z2)
     g
     (vec3 x2 y2 z1)]))

(defn edges
  "Twelve boundary segments (4 bottom, 4 top, 4 vertical), as [[p0 p1] ...]."
  [^AABB b]
  (let [[a b' c d e f g h] (vertices b)]
    [[a b'] [b' c] [c d] [d a]
     [e f] [f g] [g h] [h e]
     [a e] [b' f]
     [c g] [d h]]))

(defn union
  "Smallest AABB containing both b1 and b2."
  [^AABB b1 ^AABB b2]
  (let [p (v3/min (.-p b1) (.-p b2))
        q (v3/max (v3/add (.-p b1) (.-sz b1))
                  (v3/add (.-p b2) (.-sz b2)))]
    (AABB. p (v3/sub q p))))

(defn intersection
  "Overlap of b1 and b2, or nil if they are disjoint."
  [^AABB b1 ^AABB b2]
  (let [p (v3/max (.-p b1) (.-p b2))
        q (v3/min (v3/add (.-p b1) (.-sz b1))
                  (v3/add (.-p b2) (.-sz b2)))
        s (v3/sub q p)]
    (when (and (>= (v3/x s) 0.0) (>= (v3/y s) 0.0) (>= (v3/z s) 0.0))
      (AABB. p s))))

(defn translate [^AABB b ^Vec3 t]
  (AABB. (v3/add (.-p b) t) (.-sz b)))

(defn scale
  "Scale p and size about the origin by s."
  [^AABB b ^double s]
  (AABB. (v3/scale (.-p b) s) (v3/scale (.-sz b) s)))

(defn scale-size
  "Resize the box by s, keeping its centroid fixed."
  [^AABB b ^double s]
  (let [s' (v3/scale (.-sz b) s)]
    (AABB. (v3/sub (centroid b) (v3/scale s' 0.5)) s')))

(defn map-point
  "Map a world point q into the box's local UVW space [0,1]^3
  (p -> 0, p+sz -> 1)."
  [^AABB b ^Vec3 q]
  (let [p (.-p b) s (.-sz b)]
    (vec3 (/ (- (v3/x q) (v3/x p)) (v3/x s))
          (/ (- (v3/y q) (v3/y p)) (v3/y s))
          (/ (- (v3/z q) (v3/z p)) (v3/z s)))))

(defn unmap-point
  "Inverse of map-point: local UVW -> world point."
  [^AABB b ^Vec3 q]
  (v3/add (v3/mul (.-sz b) q) (.-p b)))
