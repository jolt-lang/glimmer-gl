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
  (:require [glimmer-gl.matrix :as m]
            [glimmer.ratom :as r]))

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

;; --- component expansion -----------------------------------------------------
;; A component is a fn returning hiccup; [box :stone] is a reagent-style component
;; invocation. expand replaces each [fn args...] with (apply fn args) and recurses
;; until only native nodes (:group/:mesh/:light/:camera) remain. Seq children
;; splice (for/map yield one node each) and nils drop, mirroring glimmer's child
;; flattening. Atoms dereffed during a component's expansion register the
;; enclosing reactive context (scene/plan's reaction) as a watcher — the same
;; auto-dependency tracking reagent uses.
(declare expand)

(defn- expand-children
  "Normalize a parent's child forms into a flat vector of expanded hiccup
  elements: splice (possibly nested) seqs, drop nils, expand each survivor. A
  bare vector is one child, not spliced (standard hiccup)."
  [xs]
  (letfn [(walk [acc x]
            (cond
              (nil? x) acc
              (seq? x) (reduce walk acc x)
              :else    (conj acc (expand x))))]
    (reduce walk [] xs)))

(defn expand
  "Expand component invocations ([fn args...] -> (apply fn args)) in a hiccup
  scene tree down to native nodes, recursively, returning a tree of native
  [:tag props children...] nodes that flatten can compile. A component is marked
  by a fn head (fn?, not ifn? — hiccup vectors are themselves callable, so ifn?
  would misclassify native nodes)."
  [node]
  (cond
    (nil? node) nil
    (vector? node)
    (let [head (first node)]
      (cond
        (keyword? head)
        (let [body   (next node)
              props? (and (seq body) (map? (first body)))
              props  (if props? (first body) {})
              kids   (if props? (rest body) body)]
          (into [head props] (expand-children kids)))
        (fn? head) (expand (apply head (rest node)))
        :else (throw (ex-info (str "unsupported scene node: " (pr-str node))
                              {:node node}))))
    :else (throw (ex-info (str "unsupported scene node: " (pr-str node))
                          {:node node}))))

(defn plan
  "Build a reactive render plan from `root`, a zero-arg scene component (a fn
  returning hiccup, possibly mixing native nodes and [fn args...] component
  invocations). Returns a glimmer reaction (read with @) whose value is the
  flattened plan; any reactive cell dereffed while the scene expands registers
  this plan as a watcher, so reset!/swap! on that cell recomputes the plan. The
  GL analogue of a reagent component: the scene is data, cells drive
  re-renders, flatten is the pure compiler."
  [root]
  (r/reaction (flatten (expand (root)))))

;; --- render-time camera (ported from thi.ng.geom.gl.camera) ------------------

(defn perspective-camera
  "Build a camera map carrying :view and :proj matrices from the eye/target/up
  basis and a perspective frustum (fov degrees, aspect, near, far). The result
  feeds a renderer's per-frame uniforms; apply-camera injects :view/:proj into a
  draw-spec. Ported from thi.ng.geom.gl.camera/perspective-camera."
  [{:keys [eye target up fov aspect near far]}]
  (let [view (m/look-at eye target up)
        proj (m/perspective fov aspect near far)]
    {:eye eye :target target :up up
     :fov fov :aspect aspect :near near :far far
     :view view :proj proj}))

(defn apply-camera
  "Merge a camera's :view/:proj into a draw-spec's :uniforms (port of
  thi.ng.geom.gl.camera/apply). geom-style lit shaders read :view :proj :model
  directly; a flat shader that wants a combined :u_mvp computes it in the app and
  skips this."
  [spec cam]
  (-> spec
      (assoc-in [:uniforms :view] (:view cam))
      (assoc-in [:uniforms :proj] (:proj cam))))
