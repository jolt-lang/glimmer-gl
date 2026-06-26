(ns glimmer-gl.triangle
  "3D triangle, ported from thi.ng/geom's Triangle3. Stored as three hinted
  Vec3 vertices a, b, c (CCW winding yields the outward normal). Plain functions."
  (:require [glimmer-gl.vector :as v3 :refer [vec3 Vec3]]
            [glimmer-gl.line :as line]))

(def ^:private ^double eps 1e-6)

(defrecord Triangle3 [^Vec3 a ^Vec3 b ^Vec3 c])

(defn triangle3 [^Vec3 a ^Vec3 b ^Vec3 c] (Triangle3. a b c))

(defn a [^Triangle3 t] (.-a t))
(defn b [^Triangle3 t] (.-b t))
(defn c [^Triangle3 t] (.-c t))
(defn points [^Triangle3 t] [(.-a t) (.-b t) (.-c t)])

(defn area
  "Twice the area of the cross product of two edges."
  ^double [^Triangle3 t]
  (* 0.5 (v3/magnitude (v3/cross (v3/sub (.-b t) (.-a t))
                                 (v3/sub (.-c t) (.-a t))))))

(defn normal
  "Unit normal from CCW winding: normalize((b-a) × (c-a))."
  [^Triangle3 t]
  (v3/normalize (v3/cross (v3/sub (.-b t) (.-a t))
                          (v3/sub (.-c t) (.-a t)))))

(defn centroid [^Triangle3 t]
  (v3/scale (v3/add (v3/add (.-a t) (.-b t)) (.-c t)) (/ 1.0 3.0)))

(defn edges [^Triangle3 t]
  [[(.-a t) (.-b t)] [(.-b t) (.-c t)] [(.-c t) (.-a t)]])

(defn perimeter ^double [^Triangle3 t]
  (+ (v3/dist (.-a t) (.-b t))
     (v3/dist (.-b t) (.-c t))
     (v3/dist (.-c t) (.-a t))))

(defn barycentric
  "Return [u v w] with u+v+w=1 and u·a + v·b + w·c = p (p need not be coplanar;
  it is projected along the edge vectors). Degenerate triangles return [0 0 0]."
  [^Triangle3 t ^Vec3 p]
  (let [a  (.-a t)
        v0 (v3/sub (.-b t) a)
        v1 (v3/sub (.-c t) a)
        v2 (v3/sub p a)
        d00 (v3/dot v0 v0)
        d01 (v3/dot v0 v1)
        d11 (v3/dot v1 v1)
        d20 (v3/dot v2 v0)
        d21 (v3/dot v2 v1)
        denom (- (* d00 d11) (* d01 d01))]
    (if (< (Math/abs denom) eps)
      [0.0 0.0 0.0]
      (let [vv (/ (- (* d11 d20) (* d01 d21)) denom)
            ww (/ (- (* d00 d21) (* d01 d20)) denom)]
        [(- 1.0 vv ww) vv ww]))))

(defn contains-point?
  "True when p lies in the triangle's plane (within eps) and its barycentric
  coordinates are all non-negative."
  [^Triangle3 t ^Vec3 p]
  (let [n (normal t)
        d (Math/abs (v3/dot n (v3/sub p (.-a t))))]
    (and (<= d eps)
         (let [[u v w] (barycentric t p)]
           (and (>= u (- eps)) (>= v (- eps)) (>= w (- eps)))))))

(defn closest-point
  "Nearest point of the triangle to p: project p onto the plane; if the
  projection is inside the triangle return it, else return the nearest point
  of the three boundary edges."
  [^Triangle3 t ^Vec3 p]
  (let [a  (.-a t)
        n  (normal t)
        q  (v3/sub p (v3/scale n (v3/dot n (v3/sub p a))))]
    (let [[u v w] (barycentric t q)]
      (if (and (>= u 0.0) (>= v 0.0) (>= w 0.0))
        q
        (reduce
          (fn [best [s e]]
            (let [cp (line/closest-point (line/line3 s e) p)]
              (if (< (v3/dist-squared p cp) (v3/dist-squared p best)) cp best)))
          (line/closest-point (line/line3 (.-a t) (.-b t)) p)
          [[(.-b t) (.-c t)] [(.-c t) (.-a t)]])))))

(defn flip
  "Reverse the winding (swap b and c), flipping the normal."
  [^Triangle3 t] (Triangle3. (.-a t) (.-c t) (.-b t)))

(defn translate [^Triangle3 t ^Vec3 d]
  (Triangle3. (v3/add (.-a t) d) (v3/add (.-b t) d) (v3/add (.-c t) d)))

(defn scale
  "Scale all vertices about the origin by s."
  [^Triangle3 t ^double s]
  (Triangle3. (v3/scale (.-a t) s) (v3/scale (.-b t) s) (v3/scale (.-c t) s)))
