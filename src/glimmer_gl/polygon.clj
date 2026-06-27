(ns glimmer-gl.polygon
  "2D polygon, ported from thi.ng/geom's Polygon2. Stored as a vector of Vec2
  vertices in winding order (CCW ⇒ positive area). Plain functions."
  (:require [glimmer-gl.vec2 :as v2 :refer [vec2 Vec2]]
            [glimmer-gl.rect :as r]))

(defrecord Polygon2 [points])

(defn polygon [pts] (Polygon2. (vec pts)))
(defn points   [^Polygon2 p] (.-points p))
(defn vertices [^Polygon2 p] (.-points p))

(defn area
  "Unsigned polygon area via the shoelace formula."
  ^double [^Polygon2 p]
  (let [pts (.-points p) n (count pts)]
    (loop [i 0 acc 0.0]
      (if (== i n)
        (Math/abs (* 0.5 acc))
        (let [a (pts i) b (pts (mod (inc i) n))]
          (recur (inc i)
                 (+ acc (- (* (v2/x a) (v2/y b)) (* (v2/y a) (v2/x b))))))))))

(defn centroid
  "Area-weighted centroid (not the vertex average)."
  [^Polygon2 p]
  (let [pts (.-points p) n (count pts)]
    (loop [i 0 cx 0.0 cy 0.0 a2 0.0]
      (if (== i n)
        (let [a (* 0.5 a2)]
          (vec2 (/ cx (* 6.0 a)) (/ cy (* 6.0 a))))
        (let [pa (pts i) pb (pts (mod (inc i) n))
              cross (- (* (v2/x pa) (v2/y pb)) (* (v2/y pa) (v2/x pb)))]
          (recur (inc i)
                 (+ cx (* (+ (v2/x pa) (v2/x pb)) cross))
                 (+ cy (* (+ (v2/y pa) (v2/y pb)) cross))
                 (+ a2 cross)))))))

(defn contains-point?
  "Point-in-polygon via the PNPOLY even-odd ray-cast (boundary is unreliable)."
  [^Polygon2 p ^Vec2 q]
  (let [pts (.-points p) n (count pts) qx (v2/x q) qy (v2/y q)]
    (loop [i 0 j (dec n) c false]
      (if (== i n)
        c
        (let [pi (pts i) pj (pts j)
              yi (v2/y pi) yj (v2/y pj)
              xi (v2/x pi) xj (v2/x pj)]
          (recur (inc i) i
                 (if (and (not= (> yi qy) (> yj qy))
                          (< qx (+ xi (/ (* (- xj xi) (- qy yi)) (- yj yi)))))
                   (not c) c)))))))

(defn edges
  "Closed boundary as [[a b] ...]."
  [^Polygon2 p]
  (let [pts (.-points p) n (count pts)]
    (mapv (fn [i] [(pts i) (pts (mod (inc i) n))]) (range n))))

(defn circumference ^double [^Polygon2 p]
  (reduce + (map (fn [[a b]] (v2/dist a b)) (edges p))))

(defn bounds [^Polygon2 p]
  (let [pts (.-points p)
        xs (map v2/x pts) ys (map v2/y pts)
        xmin (reduce min xs) ymin (reduce min ys)
        xmax (reduce max xs) ymax (reduce max ys)]
    (r/rect (vec2 xmin ymin) (vec2 (- xmax xmin) (- ymax ymin)))))

(defn translate [^Polygon2 p ^Vec2 t]
  (Polygon2. (mapv #(v2/add ^Vec2 % t) (.-points p))))

(defn scale [^Polygon2 p ^double s]
  (Polygon2. (mapv #(v2/scale ^Vec2 % s) (.-points p))))

(defn flip
  "Reverse the winding (flips orientation)."
  [^Polygon2 p]
  (Polygon2. (vec (rseq (.-points p)))))

;; ---- ear-clipping triangulation ----

(defn- tri-signed-area2 ^double [^Vec2 a ^Vec2 b ^Vec2 c]
  (* 0.5 (- (* (- (v2/x b) (v2/x a)) (- (v2/y c) (v2/y a)))
            (* (- (v2/y b) (v2/y a)) (- (v2/x c) (v2/x a))))))

(defn- point-in-tri2? [^Vec2 q ^Vec2 a ^Vec2 b ^Vec2 c]
  (let [d1 (- (* (- (v2/x b) (v2/x a)) (- (v2/y q) (v2/y a)))
              (* (- (v2/y b) (v2/y a)) (- (v2/x q) (v2/x a))))
        d2 (- (* (- (v2/x c) (v2/x b)) (- (v2/y q) (v2/y b)))
              (* (- (v2/y c) (v2/y b)) (- (v2/x q) (v2/x b))))
        d3 (- (* (- (v2/x a) (v2/x c)) (- (v2/y q) (v2/y c)))
              (* (- (v2/y a) (v2/y c)) (- (v2/x q) (v2/x c))))]
    (or (and (>= d1 0.0) (>= d2 0.0) (>= d3 0.0))
        (and (<= d1 0.0) (<= d2 0.0) (<= d3 0.0)))))

(defn- ear-at
  "If vertex at index position i is an ear of the indexed polygon, return
  [prev-idx cur-idx next-idx]; else nil."
  [pts idx i]
  (let [m (count idx)
        pv (nth idx (mod (dec i) m))
        cv (nth idx i)
        nv (nth idx (mod (inc i) m))
        a (nth pts pv) b (nth pts cv) c (nth pts nv)]
    (when (and (pos? (tri-signed-area2 a b c))
               (not-any? #(point-in-tri2? (nth pts ^long %) a b c)
                         (remove (set [pv cv nv]) idx)))
      [pv cv nv])))

(defn tessellate
  "Ear-clipping triangulation of a simple CCW polygon → list of [a b c] Vec2
  triples (n-2 triangles). Convex polygons fan out; concave ones clip ears."
  [^Polygon2 p]
  (let [pts (.-points p) n (count pts)]
    (if (<= n 3)
      (list pts)
      (loop [idx (vec (range n)) tris ()]
        (cond
          (== 3 (count idx)) (conj tris (mapv pts idx))
          :else
          (let [found (some #(ear-at pts idx ^long %) (range (count idx)))]
            (if (nil? found)
              ;; degenerate fallback: clip the first three indices
              (recur (vec (remove #{(nth idx 1)} idx))
                     (conj tris (mapv pts [(nth idx 0) (nth idx 1) (nth idx 2)])))
              (recur (vec (remove #{(nth found 1)} idx))
                     (conj tris (mapv pts found))))))))))
