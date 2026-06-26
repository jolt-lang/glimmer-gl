(ns glimmer-gl.rect
  "Axis-aligned 2D rectangle, ported from thi.ng/geom's Rect2.
  Stored as a min corner `p` and an extent `sz` (both Vec2). Plain functions,
  no protocol layer — house style.

  NOTE: the extent field is `sz`, not `size`: in Jolt a defrecord field named
  `size` is shadowed by record-introspection (.-size returns the field count),
  so the public accessor is `size` but the stored field is `sz`."
  (:require [glimmer-gl.vec2 :as v2 :refer [vec2 Vec2]]))

(defrecord Rect2 [^Vec2 p ^Vec2 sz])

(defn rect
  "Construct a Rect2. Arities:
    (rect)            -> unit square at the origin
    (rect w)          -> square of side w at the origin
    (rect p size)     -> from two Vec2 (p = min corner)
    (rect x y w h)    -> from four scalars"
  ([] (Rect2. (vec2 0.0) (vec2 1.0)))
  ([w] (Rect2. (vec2 0.0) (vec2 ^double w)))
  ([^Vec2 p ^Vec2 sz] (Rect2. p sz))
  ([^double x ^double y ^double w ^double h] (Rect2. (vec2 x y) (vec2 w h))))

(defn p    [^Rect2 r] (.-p r))
(defn size [^Rect2 r] (.-sz r))

(defn width  [^Rect2 r] (v2/x (.-sz r)))
(defn height [^Rect2 r] (v2/y (.-sz r)))

(defn left   [^Rect2 r] (v2/x (.-p r)))
(defn bottom [^Rect2 r] (v2/y (.-p r)))
(defn right  [^Rect2 r] (+ (v2/x (.-p r)) (v2/x (.-sz r))))
(defn top    [^Rect2 r] (+ (v2/y (.-p r)) (v2/y (.-sz r))))

(defn bottom-left [^Rect2 r] (.-p r))
(defn top-right   [^Rect2 r] (v2/add (.-p r) (.-sz r)))

(defn area [^Rect2 r]
  (* (v2/x (.-sz r)) (v2/y (.-sz r))))

(defn circumference [^Rect2 r]
  (* 2.0 (+ (v2/x (.-sz r)) (v2/y (.-sz r)))))

(defn centroid [^Rect2 r]
  (v2/add (.-p r) (v2/scale (.-sz r) 0.5)))

(defn bounds [^Rect2 r] r)

(defn contains-point?
  "True when q lies within [p, p+sz] on both axes."
  [^Rect2 r ^Vec2 q]
  (let [px (v2/x (.-p r)) py (v2/y (.-p r))
        w  (v2/x (.-sz r)) h (v2/y (.-sz r))]
    (and (<= px (v2/x q) (+ px w))
         (<= py (v2/y q) (+ py h)))))

(defn vertices
  "Four corners, counter-clockwise from the min corner:
  [bottom-left, bottom-right, top-right, top-left]."
  [^Rect2 r]
  (let [a (.-p r)
        c (v2/add a (.-sz r))]
    [a
     (vec2 (v2/x c) (v2/y a))
     c
     (vec2 (v2/x a) (v2/y c))]))

(defn edges
  "Four boundary segments [[p0 p1] ...], counter-clockwise."
  [^Rect2 r]
  (let [a (.-p r)
        c (v2/add a (.-sz r))
        b (vec2 (v2/x c) (v2/y a))
        d (vec2 (v2/x a) (v2/y c))]
    [[a b] [b c] [c d] [d a]]))

(defn union
  "Smallest Rect2 containing both r1 and r2."
  [^Rect2 r1 ^Rect2 r2]
  (let [p  (v2/min (.-p r1) (.-p r2))
        q  (v2/max (v2/add (.-p r1) (.-sz r1))
                   (v2/add (.-p r2) (.-sz r2)))]
    (Rect2. p (v2/sub q p))))

(defn intersection
  "Overlap of r1 and r2, or nil if they are disjoint."
  [^Rect2 r1 ^Rect2 r2]
  (let [p  (v2/max (.-p r1) (.-p r2))
        q  (v2/min (v2/add (.-p r1) (.-sz r1))
                   (v2/add (.-p r2) (.-sz r2)))
        s  (v2/sub q p)]
    (when (and (>= (v2/x s) 0.0) (>= (v2/y s) 0.0))
      (Rect2. p s))))

(defn translate [^Rect2 r ^Vec2 t]
  (Rect2. (v2/add (.-p r) t) (.-sz r)))

(defn scale
  "Scale p and size about the origin by s."
  [^Rect2 r ^double s]
  (Rect2. (v2/scale (.-p r) s) (v2/scale (.-sz r) s)))

(defn scale-size
  "Resize the rect by s, keeping its centroid fixed."
  [^Rect2 r ^double s]
  (let [s' (v2/scale (.-sz r) s)]
    (Rect2. (v2/sub (centroid r) (v2/scale s' 0.5)) s')))

(defn center
  "Return a rect of the same size centered on the origin (one-arg),
  or centered on o (two-arg)."
  ([^Rect2 r]
   (Rect2. (v2/scale (.-sz r) -0.5) (.-sz r)))
  ([^Rect2 r ^Vec2 o]
   (Rect2. (v2/add (v2/scale (.-sz r) -0.5) o) (.-sz r))))

(defn map-point
  "Map a world point q into the rect's local UV space [0,1]^2
  (p -> 0, p+sz -> 1)."
  [^Rect2 r ^Vec2 q]
  (vec2 (/ (- (v2/x q) (v2/x (.-p r))) (v2/x (.-sz r)))
        (/ (- (v2/y q) (v2/y (.-p r))) (v2/y (.-sz r)))))

(defn unmap-point
  "Inverse of map-point: local UV -> world point."
  [^Rect2 r ^Vec2 q]
  (v2/add (v2/mul (.-sz r) q) (.-p r)))
