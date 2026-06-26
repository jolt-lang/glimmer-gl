(ns glimmer-gl.intersect
  "3D intersection tests for interactive scenes (picking, collision), ported
  from thi.ng/geom's utils.intersect. Ray tests return the parametric distance
  `t` along the (assumed unit) direction vector — nil when there's no forward
  hit — so the hit point is `(+ origin (scale dir t))`. Decoupled from the
  shape records: only vector + plane."
  (:require [glimmer-gl.vector :as v3 :refer [vec3 Vec3]]
            [glimmer-gl.plane :as pl]))

(def ^:private ^double eps 1e-9)

(defn ray-sphere
  "Nearest forward t where the ray hits the sphere, else nil."
  [^Vec3 origin ^Vec3 dir ^Vec3 center ^double radius]
  (let [m    (v3/sub origin center)
        b    (v3/dot m dir)
        c    (- (v3/dot m m) (* radius radius))
        disc (- (* b b) c)]
    (when (>= disc 0.0)
      (let [sq (Math/sqrt disc)
            t0 (- (- b) sq)   ; near root
            t1 (+ (- b) sq)]  ; far root
        (cond
          (>= t0 0.0) t0
          (>= t1 0.0) t1      ; origin inside the sphere
          :else nil)))))

(defn ray-plane
  "Forward t where the ray crosses the plane, else nil (parallel or behind)."
  [^Vec3 origin ^Vec3 dir plane]
  (let [n (pl/normal plane)
        denom (v3/dot n dir)]
    (when (> (Math/abs denom) eps)
      (let [t (/ (- (pl/dist plane origin)) denom)]
        (when (>= t 0.0) t)))))

(defn ray-triangle
  "Forward t where the ray crosses triangle (a b c) (double-sided,
  Möller–Trumbore), else nil."
  [^Vec3 origin ^Vec3 dir ^Vec3 a ^Vec3 b ^Vec3 c]
  (let [e1 (v3/sub b a)
        e2 (v3/sub c a)
        p  (v3/cross dir e2)
        det (v3/dot e1 p)]
    (when (> (Math/abs det) eps)
      (let [inv (v3/sub origin a)
            u   (* (v3/dot inv p) (/ 1.0 det))]
        (when (and (>= u 0.0) (<= u 1.0))
          (let [q  (v3/cross inv e1)
                v  (* (v3/dot dir q) (/ 1.0 det))]
            (when (and (>= v 0.0) (<= (+ u v) 1.0))
              (let [t (* (v3/dot e2 q) (/ 1.0 det))]
                (when (>= t 0.0) t)))))))))

(defn ray-aabb
  "Forward t where the ray enters the axis-aligned box [min-corner,max-corner],
  else nil (slab method)."
  [^Vec3 origin ^Vec3 dir ^Vec3 mn ^Vec3 mx]
  (loop [axis 0 tmin -1e30 tmax 1e30]
    (if (== axis 3)
      (cond (>= tmin 0.0) tmin
            (>= tmax 0.0) tmax
            :else nil)
      (let [o (case axis 0 (v3/x origin) 1 (v3/y origin) (v3/z origin))
            d (case axis 0 (v3/x dir)    1 (v3/y dir)    (v3/z dir))
            lo (case axis 0 (v3/x mn)    1 (v3/y mn)    (v3/z mn))
            hi (case axis 0 (v3/x mx)    1 (v3/y mx)    (v3/z mx))]
        (if (< (Math/abs d) eps)
          ;; parallel to this slab: must already be within the slab
          (if (or (< o lo) (> o hi))
            nil
            (recur (inc axis) tmin tmax))
          (let [inv (/ 1.0 d)
                t1 (* (- lo o) inv)
                t2 (* (- hi o) inv)
                [t1 t2] (if (> t1 t2) [t2 t1] [t1 t2])
                tn (Math/max tmin t1)
                tx (Math/min tmax t2)]
            (if (> tn tx)
              nil
              (recur (inc axis) tn tx))))))))

(defn sphere-sphere?
  "True when two spheres overlap or touch."
  [^Vec3 c1 ^double r1 ^Vec3 c2 ^double r2]
  (let [rr (+ r1 r2)]
    (<= (v3/dist-squared c1 c2) (* rr rr))))

(defn aabb-aabb?
  "True when two axis-aligned boxes [min-a,max-a] and [min-b,max-b] overlap."
  [^Vec3 min-a ^Vec3 max-a ^Vec3 min-b ^Vec3 max-b]
  (and (<= (v3/x min-a) (v3/x max-b)) (<= (v3/x min-b) (v3/x max-a))
       (<= (v3/y min-a) (v3/y max-b)) (<= (v3/y min-b) (v3/y max-a))
       (<= (v3/z min-a) (v3/z max-b)) (<= (v3/z min-b) (v3/z max-a))))

(defn aabb-sphere?
  "True when the box [min-corner,max-corner] overlaps the sphere."
  [^Vec3 mn ^Vec3 mx ^Vec3 center ^double radius]
  (let [cx (Math/max (v3/x mn) (Math/min (v3/x center) (v3/x mx)))
        cy (Math/max (v3/y mn) (Math/min (v3/y center) (v3/y mx)))
        cz (Math/max (v3/z mn) (Math/min (v3/z center) (v3/z mx)))
        dx (- (v3/x center) cx)
        dy (- (v3/y center) cy)
        dz (- (v3/z center) cz)]
    (<= (+ (* dx dx) (* dy dy) (* dz dz)) (* radius radius))))
