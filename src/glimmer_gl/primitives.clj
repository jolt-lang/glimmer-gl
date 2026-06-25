(ns glimmer-gl.primitives
  "Primitive solids as composable meshes, ported from thi.ng/geom (cuboid,
  tetrahedron, sphere, plane). Each returns a `glimmer-gl.mesh` whose faces wind
  counter-clockwise so face normals point outward. Compose them with
  `mesh/transform`, `mesh/merge-meshes`, and `mesh/subdivide`."
  (:require [glimmer-gl.vector :as v]
            [glimmer-gl.mesh :as mesh]))

;; --- cuboid ------------------------------------------------------------------
;; Vertex layout and face winding are thi.ng/geom's (thi.ng.geom.cuboid):
;;
;;   e +----+ h        a=(x1,y1,z1) b=(x1,y1,z2) c=(x2,y1,z2) d=(x2,y1,z1)
;;     |\   :\         e=(x1,y2,z1) f=(x1,y2,z2) g=(x2,y2,z2) h=(x2,y2,z1)
;;     |f+----+ g
;;     | |  : |        faces (CCW outward): east west north south front back
;;   a +-|--+d|
;;      \|   \|
;;     b +----+ c
(defn cuboid
  "Axis-aligned box. `(cuboid size)` is centered at the origin with edge length
  `size`; `(cuboid [ox oy oz] [sx sy sz])` puts its min corner at o with the
  given extent. 8 vertices, 6 quad faces."
  ([] (cuboid 1.0))
  ([size]
   (let [h (* 0.5 (double size))]
     (cuboid [(- h) (- h) (- h)] [size size size])))
  ([[ox oy oz] [sx sy sz]]
   (let [x1 (double ox) y1 (double oy) z1 (double oz)
         x2 (+ x1 (double sx)) y2 (+ y1 (double sy)) z2 (+ z1 (double sz))
         a  (v/vec3 x1 y1 z1) b (v/vec3 x1 y1 z2) c (v/vec3 x2 y1 z2) d (v/vec3 x2 y1 z1)
         e  (v/vec3 x1 y2 z1) f (v/vec3 x1 y2 z2) g (v/vec3 x2 y2 z2) h (v/vec3 x2 y2 z1)]
     (mesh/mesh
       [[c d h g]    ;; east  (+X)
        [a b f e]    ;; west  (-X)
        [f g h e]    ;; north (+Y)
        [a d c b]    ;; south (-Y)
        [b c g f]    ;; front (+Z)
        [d a e h]])))) ;; back  (-Z)

;; --- tetrahedron -------------------------------------------------------------
(defn- ortho-normal [a b c]
  (v/normalize (v/cross (v/sub b a) (v/sub c a))))

(defn- orient-tetra
  "Order 4 points so the last is on the negative side of the plane of the first
  three — i.e. the faces wind outward (thi.ng.geom.tetrahedron/orient-tetra)."
  [[a b c d]]
  (let [dp (v/dot (v/normalize (v/sub d a)) (ortho-normal a b c))]
    (if (neg? dp) [a b c d] [a c b d])))

(defn tetrahedron
  "Tetrahedron from a seq of 4 points (auto-oriented to wind outward), or a
  regular tetrahedron inscribed in a sphere of radius `r` centered at the origin
  when given a number / no args. 4 triangular faces (thi.ng.geom.tetrahedron)."
  ([] (tetrahedron 1.0))
  ([r-or-points]
   (if (number? r-or-points)
     (let [s (/ (double r-or-points) (Math/sqrt 3.0))]
       (tetrahedron (mapv (fn [[x y z]] (v/vec3 (* s x) (* s y) (* s z)))
                          [[1 1 1] [1 -1 -1] [-1 1 -1] [-1 -1 1]])))
     (let [[a b c d] (orient-tetra (vec r-or-points))]
       (mesh/mesh [[a b c] [a d b] [b d c] [c d a]])))))

;; --- plane / quad ------------------------------------------------------------
(defn plane
  "A flat grid in the XY plane (z=0), centered at the origin, `size` on a side,
  subdivided into res×res quad faces. Normals point +Z. `(plane size)` is one
  quad; bump `res` to feed mesh/subdivide-free smooth surfaces."
  ([] (plane 1.0 1))
  ([size] (plane size 1))
  ([size res]
   (let [size (double size) res (long res)
         step (/ size res) h (* 0.5 size)
         vert (fn [i j] (v/vec3 (- (* i step) h) (- (* j step) h) 0.0))]
     (mesh/mesh
       (for [i (range res) j (range res)]
         [(vert i j) (vert (inc i) j) (vert (inc i) (inc j)) (vert i (inc j))])))))

(defn quad
  "A single quad face in the XY plane, centered at the origin, `size` on a side.
  Normal points +Z."
  ([] (quad 1.0))
  ([size] (plane size 1)))

;; --- sphere ------------------------------------------------------------------
(defn- norm-range
  "n+1 evenly spaced values from 0.0 to 1.0 inclusive (thi.ng m/norm-range)."
  [n] (mapv #(/ (double %) n) (range (inc n))))

(defn sphere
  "UV sphere of radius `r` centered at the origin, `slices` longitudinal and
  `stacks` latitudinal divisions. Quad faces between stacks, triangle fans at the
  two poles. Ported from thi.ng.geom.sphere's as-mesh."
  ([] (sphere 1.0 16 16))
  ([r] (sphere r 16 16))
  ([r slices stacks]
   (let [r       (double r)
         rsl     (norm-range slices)
         rst     (norm-range stacks)
         st      (mapv #(Math/sin (* 2.0 Math/PI %)) rsl)
         ct      (mapv #(Math/cos (* 2.0 Math/PI %)) rsl)
         sp      (mapv #(Math/sin (* Math/PI %)) rst)
         cp      (mapv #(Math/cos (* Math/PI %)) rst)
         stacks' (dec stacks)
         pt      (fn [u w]
                   (v/vec3 (* (nth ct u) (nth sp w) r)
                           (* (nth cp w) r)
                           (* (nth st u) (nth sp w) r)))]
     (mesh/mesh
       (for [j (range stacks)
             i (range slices)
             :let [ii (inc i) jj (inc j)
                   idx (cond
                         (zero? j)         [[i j] [ii jj] [i jj]]      ;; south pole fan
                         (< j stacks')     [[i j] [ii j] [ii jj] [i jj]] ;; quad band
                         :else             [[i j] [ii j] [i jj]])]]     ;; north pole fan
         (mapv (fn [[u w]] (pt u w)) idx))))))
