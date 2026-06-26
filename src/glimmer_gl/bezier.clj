(ns glimmer-gl.bezier
  "Bézier curves and Catmull-Rom splines over Vec3, ported from thi.ng/geom's
  bezier module. Curves sample into polylines that compose into ribbons, tubes,
  and paths. 2D scenes can use Vec3 with z=0."
  (:require [glimmer-gl.vector :as v3 :refer [vec3 Vec3]]))

(defn cubic-point
  "Point on the cubic Bézier (p0 p1 p2 p3) at parameter t∈[0,1]."
  [^Vec3 p0 ^Vec3 p1 ^Vec3 p2 ^Vec3 p3 ^double t]
  (let [u (- 1.0 t)
        a (* u u u)
        b (* 3.0 u u t)
        c (* 3.0 u t t)
        d (* t t t)]
    (v3/add (v3/add (v3/scale p0 a) (v3/scale p1 b))
            (v3/add (v3/scale p2 c) (v3/scale p3 d)))))

(defn quadratic-point
  "Point on the quadratic Bézier (p0 p1 p2) at parameter t∈[0,1]."
  [^Vec3 p0 ^Vec3 p1 ^Vec3 p2 ^double t]
  (let [u (- 1.0 t)
        a (* u u)
        b (* 2.0 u t)
        c (* t t)]
    (v3/add (v3/add (v3/scale p0 a) (v3/scale p1 b)) (v3/scale p2 c))))

(defn cubic-tangent
  "Derivative (direction, unnormalized) of the cubic Bézier at t. Normalize for
  a unit tangent — useful for ribbon/frame orientation along the curve."
  [^Vec3 p0 ^Vec3 p1 ^Vec3 p2 ^Vec3 p3 ^double t]
  (let [u (- 1.0 t)]
    (v3/add (v3/add (v3/scale (v3/sub p1 p0) (* 3.0 u u))
                    (v3/scale (v3/sub p2 p1) (* 6.0 u t)))
            (v3/scale (v3/sub p3 p2) (* 3.0 t t)))))

(defn sample-cubic
  "res+1 evenly-parameterized points along the cubic Bézier (p0 p1 p2 p3)."
  [^Vec3 p0 ^Vec3 p1 ^Vec3 p2 ^Vec3 p3 ^long res]
  (mapv #(cubic-point p0 p1 p2 p3 (/ ^double % ^double res))
        (range (inc res))))

(defn sample-quadratic
  "res+1 evenly-parameterized points along the quadratic Bézier (p0 p1 p2)."
  [^Vec3 p0 ^Vec3 p1 ^Vec3 p2 ^long res]
  (mapv #(quadratic-point p0 p1 p2 (/ ^double % ^double res))
        (range (inc res))))

(defn arc-length
  "Polyline length: sum of consecutive point distances."
  ^double [points]
  (let [ps (seq points)]
    (loop [rest ps total 0.0]
      (if (or (nil? rest) (nil? (next rest)))
        total
        (recur (next rest)
               (+ total (v3/dist (first rest) (second rest))))))))

(defn catmull-rom-spline
  "Smooth C1 polyline passing through every point in `points`, sampling `res`
  points per segment (uniform Catmull-Rom → cubic Bézier conversion, clamped
  at both ends). Fewer than 2 points returns the input unchanged."
  [points ^long res]
  (let [pts (vec points)
        n   (count pts)]
    (cond
      (< n 2) pts
      (== n 2) (let [[a b] pts]
                 (sample-cubic a (v3/mix a b (/ 1.0 3.0)) (v3/mix a b (/ 2.0 3.0)) b res))
      :else
      (let [ext  (vec (concat [(first pts)] pts [(last pts)]))
            segs (for [i (range (dec n))]
                   (let [p0 (ext i) p1 (ext (inc i)) p2 (ext (+ 2 i)) p3 (ext (+ 3 i))
                         b1 (v3/add p1 (v3/scale (v3/sub p2 p0) (/ 1.0 6.0)))
                         b2 (v3/sub p2 (v3/scale (v3/sub p3 p1) (/ 1.0 6.0)))]
                     (sample-cubic p1 b1 b2 p2 res)))]
        ;; first segment in full; drop the duplicated first point of each later segment
        (into [] (concat (first segs) (mapcat #(subvec % 1) (rest segs))))))))
