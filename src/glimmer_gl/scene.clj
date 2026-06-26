(ns glimmer-gl.scene
  "Declarative 3D scene graph — the GL analogue of glimmer's widget tree.

  A scene is a hiccup tree of nodes; `flatten` compiles it into a pure render
  plan (threaded world matrices, collected lights, a single camera) that a
  renderer later realizes into GL draw calls. Content is data and interpretation
  is separate, mirroring how glimmer keeps the hiccup tree apart from the
  reconciler. This separation is what lets a reactive cell change one node and
  have only the compiled plan (not the GL plumbing) be recomputed.

  Node shapes (glimmer-style hiccup vectors):
    [:camera {:eye :target :up :fov :near :far}]   — exactly one
    [:light  {:dir [x y z] :color [r g b]}]        — directional, collected anywhere
    [:group  {:transform <Matrix44>} & children]   — threads world matrix
    [:mesh   {:geom <Mesh> :material <kw> :cast-shadow <bool>}]
  The render plan: {:camera <props|nil> :lights [...] :items [...]}, each item
  {:world <Matrix44> :geom <Mesh> :material <kw> :cast-shadow <bool>}."
  (:require [glimmer-gl.matrix :as m]))

;; --- node constructors (readable scene building) -----------------------------
(defn camera [props]            [:camera props])
(defn light  [props]            [:light props])
(defn mesh   [props]            [:mesh props])
;; NB: must use `into`, NOT `(vec (list* …))` — list* splices a seqable final
;; child (every child node is a vector), flattening its contents into siblings.
(defn group  [transform & kids] (into [:group {:transform transform}] kids))

;; --- compiler ----------------------------------------------------------------
;; Pure recursive walk threading an accumulated world matrix (parent*local
;; composition, matching m/mul's a-after-b semantics) and collecting items,
;; lights and the camera. Returns [items lights camera]; flatten wraps it.
(defn- walk [node world items lights camera]
  (let [tag (nth node 0)]
    (case tag
      :group (let [local  (get (nth node 1) :transform (m/ident))
                   world' (m/mul world local)]
               (reduce (fn [acc c]
                         (walk c world' (acc 0) (acc 1) (acc 2)))
                       [items lights camera]
                       (drop 2 node)))
      :mesh  (let [p (nth node 1)]
               [(conj items {:world        world
                             :geom         (:geom p)
                             :material     (:material p)
                             :cast-shadow  (if (false? (:cast-shadow p)) false true)})
                lights camera])
      :light [items (conj lights (nth node 1)) camera]
      :camera [items lights (nth node 1)]
      [items lights camera])))

(defn flatten
  "Compile a declarative scene tree into a render plan. The root may be any
  node; wrap several top-level nodes in a :group."
  [node]
  (let [[items lights camera] (walk node (m/ident) [] [] nil)]
    {:camera camera :lights lights :items items}))
