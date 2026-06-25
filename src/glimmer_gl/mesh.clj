(ns glimmer-gl.mesh
  "Composable 3D geometry as plain data, ported from thi.ng/geom's mesh model
  (basicmesh + the relevant bits of geom.utils).

  A mesh is a sequence of faces; each face is a vector of `glimmer-gl.vector`
  Vec3 vertices wound counter-clockwise, so its face normal points outward.
  Faces may be triangles, quads, or larger convex polygons. The ops compose:
  build primitives (see `glimmer-gl.primitives`), `transform`/`merge`/`subdivide`
  them, then `->floats` to get a GL-ready interleaved position+normal buffer.

  Vertices are value-equal Vec3 records, so a shared corner hashes to one map key
  — that is what lets `vertex-normals` average face normals for smooth shading."
  (:require [glimmer-gl.vector :as v]
            [glimmer-gl.matrix :as mat]))

(defrecord Mesh [faces])

(defn mesh
  "A mesh from a seq of faces (each a seq of Vec3), or empty."
  ([] (Mesh. []))
  ([faces] (Mesh. (mapv vec faces))))

(defn faces [m] (:faces m))

(defn add-face
  "Append one face (a seq of Vec3) to the mesh."
  [m face] (update m :faces conj (vec face)))

(defn merge-meshes
  "Concatenate the faces of several meshes into one. Geometry composition is just
  set union of faces — no vertex welding (smooth shading still finds shared
  corners by value at tessellation time)."
  [& meshes]
  (Mesh. (vec (mapcat :faces meshes))))

;; --- transforms --------------------------------------------------------------
(defn- tx-vertex [mat p]
  (let [[x y z] (mat/transform-point mat [(v/x p) (v/y p) (v/z p)])]
    (v/vec3 x y z)))

(defn transform
  "Push every vertex through a `glimmer-gl.matrix` Matrix44 (thi.ng's
  gu/transform-mesh). Compose model transforms by passing the product matrix."
  [m mat]
  (Mesh. (mapv (fn [face] (mapv #(tx-vertex mat %) face)) (:faces m))))

(defn translate [m tx ty tz] (transform m (mat/translation tx ty tz)))

(defn scale
  "Uniform (one-arg) or per-axis scale about the origin."
  ([m s] (transform m (mat/scaling s s s)))
  ([m sx sy sz] (transform m (mat/scaling sx sy sz))))

;; --- normals -----------------------------------------------------------------
(defn face-normal
  "Outward unit normal of a face, from its first three vertices
  (thi.ng gu/ortho-normal): normalize((b-a) × (c-a))."
  [[a b c]]
  (v/normalize (v/cross (v/sub b a) (v/sub c a))))

;; --- tessellation ------------------------------------------------------------
(defn tessellate-face
  "Fan one polygon face into triangles (thi.ng gu/tessellate-3): a triangle is
  itself, a quad splits into two triangles sharing a diagonal, and an n-gon fans
  around its centroid."
  [face]
  (condp == (count face)
    3 [face]
    4 (let [[a b c d] face] [[a b c] [a c d]])
    (let [c (v/centroid face)]
      (mapv (fn [[p q]] [c p q])
            (partition 2 1 (concat face [(first face)]))))))

(defn triangles
  "All triangles of the mesh, each a 3-vector of Vec3."
  [m] (vec (mapcat tessellate-face (:faces m))))

;; --- subdivision -------------------------------------------------------------
(defn- subdivide-tri
  "Split a triangle into four by its edge midpoints
  (thi.ng gu/tessellate-tri-with-midpoints)."
  [[a b c]]
  (let [ab (v/mix a b) bc (v/mix b c) ca (v/mix c a)]
    [[a ab ca] [ab b bc] [bc c ca] [ab bc ca]]))

(defn subdivide
  "Triangulate, then split every triangle into four. Each call quadruples the
  triangle count; useful to smooth a coarse mesh before smooth-shaded upload."
  [m]
  (Mesh. (vec (mapcat subdivide-tri (triangles m)))))

;; --- GL buffer output --------------------------------------------------------
(defn vertex-normals
  "Map of Vec3 vertex -> averaged unit normal, summing the face normals of every
  triangle that touches the vertex (thi.ng compute-vertex-normals). Vertices
  compare by value, so a corner shared across faces collapses to one entry."
  [m]
  (let [acc (reduce (fn [acc tri]
                      (let [n (face-normal tri)]
                        (reduce (fn [a p] (update a p (fnil v/add (v/vec3 0.0)) n))
                                acc tri)))
                    {} (triangles m))]
    (into {} (map (fn [[p s]] [p (v/normalize s)])) acc)))

(defn- interleave-tris
  "Build the {:data :count :stride} buffer; `normal-of` maps (triangle, vertex)
  to the normal to emit for that vertex."
  [tris normal-of]
  (let [data (vec (mapcat
                    (fn [tri]
                      (mapcat (fn [p]
                                (let [n (normal-of tri p)]
                                  [(v/x p) (v/y p) (v/z p) (v/x n) (v/y n) (v/z n)]))
                              tri))
                    tris))]
    {:data data :count (* 3 (count tris)) :stride 6}))

(defn ->floats
  "Tessellate to an interleaved [x y z nx ny nz] vertex buffer ready for a VBO.
  Returns {:data <seq of doubles> :count <vertex count> :stride 6}.

  opts:
    :shading :flat (default) — one face normal per triangle (faceted look)
             :smooth         — per-vertex averaged normals (rounded look)"
  ([m] (->floats m {}))
  ([m {:keys [shading] :or {shading :flat}}]
   (let [tris (triangles m)]
     (if (= shading :smooth)
       (let [vn (vertex-normals m)]
         (interleave-tris tris (fn [_ p] (get vn p))))
       (interleave-tris tris (fn [tri _] (face-normal tri)))))))
