(ns glimmer-gl.polyhedra
  "Platonic-solid mesh constructors, ported from thi.ng/geom's polyhedra.
  Each returns a `glimmer-gl.mesh` Mesh of CCW faces (outward normals).
  Octa/icosa/dodeca vertices are normalized onto a sphere of the given radius;
  tetra vertices are scaled directly (regular tetrahedron of edge = radius).
  Default radius is 1.0. Compose with mesh ops: transform/merge/subdivide/->floats."
  (:require [glimmer-gl.vector :as v3 :refer [vec3 Vec3]]
            [glimmer-gl.mesh :as mesh]))

(def ^:private ^double sqrt2 (Math/sqrt 2.0))
(def ^:private ^double sqrt3 (Math/sqrt 3.0))
(def ^:private ^double sqrt6 (Math/sqrt 6.0))
(def ^:private ^double phi (* 0.5 (+ 1.0 (Math/sqrt 5.0)))) ; golden ratio

(defn- ^Vec3 to-radius
  "Scale v onto a sphere of radius r."
  [^Vec3 v ^double r]
  (let [m (v3/magnitude v)]
    (if (zero? m) v (v3/scale v (/ r m)))))

;; ---- vertices (faithful to thi.ng/geom polyhedra) ----

(defn tetrahedron-vertices
  "Four vertices of a regular tetrahedron (edge = scale), wound so the
  centroid is below the origin."
  [^double scale]
  (let [p  (/ sqrt3 3.0)
        q  (/ p -2.0)
        r  (/ sqrt6 6.0)
        r' (- r)]
    (mapv #(v3/scale ^Vec3 % scale)
          [(vec3 p 0.0 r') (vec3 q -0.5 r') (vec3 q 0.5 r') (vec3 0.0 0.0 r)])))

(defn octahedron-vertices
  "Six vertices of an octahedron on a sphere of radius scale."
  [^double scale]
  (let [p  (/ (* 2.0 sqrt2)) p' (- p)
        q  0.5               q' (- q)]
    (mapv #(to-radius ^Vec3 % scale)
          [(vec3 p' 0.0 p) (vec3 p 0.0 p) (vec3 p 0.0 p') (vec3 p' 0.0 p')
           (vec3 0.0 q 0.0) (vec3 0.0 q' 0.0)])))

(defn icosahedron-vertices
  "Twelve vertices of an icosahedron on a sphere of radius scale."
  [^double scale]
  (let [p  0.5                p' (- p)
        q  (/ (* 2.0 phi))    q' (- q)]
    (mapv #(to-radius ^Vec3 % scale)
          [(vec3 0.0 q p')  (vec3 q p 0.0)   (vec3 q' p 0.0)
           (vec3 0.0 q p)   (vec3 0.0 q' p)  (vec3 p' 0.0 q)
           (vec3 p 0.0 q)   (vec3 0.0 q' p') (vec3 p 0.0 q')
           (vec3 p' 0.0 q') (vec3 q p' 0.0)  (vec3 q' p' 0.0)])))

(defn dodecahedron-vertices
  "Twenty vertices of a dodecahedron on a sphere of radius scale."
  [^double scale]
  (let [p  0.5              p' (- p)
        q  (/ 0.5 phi)      q' (- q)
        r  (* 0.5 (- 2.0 phi)) r' (- r)]
    (mapv #(to-radius ^Vec3 % scale)
          [(vec3 r 0.0 p)  (vec3 r' 0.0 p)  (vec3 q' q q)   (vec3 0.0 p r)
           (vec3 q q q)    (vec3 q q' q)    (vec3 0.0 p' r) (vec3 q' q' q)
           (vec3 r 0.0 p') (vec3 r' 0.0 p') (vec3 q' q' q') (vec3 0.0 p' r')
           (vec3 q q' q')  (vec3 q q q')    (vec3 0.0 p r') (vec3 q' q q')
           (vec3 p r 0.0)  (vec3 p' r 0.0)  (vec3 p' r' 0.0) (vec3 p r' 0.0)])))

;; ---- meshes ----

(defn tetrahedron
  ([] (tetrahedron 1.0))
  ([^double scale]
   (let [[a b c d] (tetrahedron-vertices scale)]
     (mesh/mesh [[a b c] [a c d] [a d b] [c b d]]))))

(defn octahedron
  ([] (octahedron 1.0))
  ([^double scale]
   (let [[a b c d e f] (octahedron-vertices scale)]
     (mesh/mesh [[d a e] [c d e] [b c e] [a b e]
                 [d c f] [a d f] [c b f] [b a f]]))))

(defn icosahedron
  ([] (icosahedron 1.0))
  ([^double scale]
   (let [[a b c d e f g h i j k l] (icosahedron-vertices scale)]
     (mesh/mesh
       [[b a c] [c d b] [e d f] [g d e]
        [h a i] [j a h] [k e l] [l h k]
        [f c j] [j l f] [i b g] [g k i]
        [f d c] [b d g] [c a j] [i a b]
        [j h l] [k h i] [l e f] [g e k]]))))

(defn dodecahedron
  ([] (dodecahedron 1.0))
  ([^double scale]
   (let [[a b c d e f g h i j k l m n o p q r s t] (dodecahedron-vertices scale)]
     (mesh/mesh
       [[e d c b a] [h g f a b] [m l k j i] [p o n i j]
        [o d e q n] [d o p r c] [l g h s k] [g l m t f]
        [e a f t q] [m i n q t] [p j k s r] [h b c r s]]))))
