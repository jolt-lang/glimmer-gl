(ns glimmer-gl.app
  "Reactive mount point for a glimmer-gl scene — the layer that keeps GL out of
  application code.

  This is the GL analogue of glimmer's top-level mount: the app supplies a scene
  component (a fn returning hiccup, exactly like a reagent component) and a few
  render-environment values, and gets back a `:gl-area` widget prop map whose
  lifecycle callbacks are owned by the library. The scene is a reaction built by
  glimmer-gl.scene/plan; cells dereffed while the scene expands register the plan
  as a watcher, so reset!/swap! on those cells recomputes only the plan — the GL
  plumbing (renderer state, mesh cache, two-pass shadow draw) is untouched.

  Camera and lights are read back out of the plan the scene declared — the
  application declares them once as nodes, and the matrices (view/proj for the
  camera, the light's view/proj for the shadow frustum) are derived here. Nothing
  the app writes ever calls a gl-* function."
  (:require [glimmer-gl.gl       :as gl]
            [glimmer-gl.gtk      :as gtk]
            [glimmer-gl.matrix   :as m]
            [glimmer-gl.scene    :as scene]
            [glimmer-gl.renderer :as renderer]))

;; glimmer cells are tagged maps, not IDeref instances (glimmer.ratom deliberately
;; dispatches on :glimmer/kind), so instance? won't recognize them.
(defn- reactive-cell? [x]
  (and (map? x) (#{:ratom :cursor :reaction} (:glimmer/kind x))))

(defn- cell
  "Resolve an opts entry to its current value: reactive cells (atoms/cursors/
  reactions) deref each frame, so a slider cell drives the next frame without
  rebuilding the loop. The 2-arg form supplies a default when the key is absent."
  ([x] (if (reactive-cell? x) @x x))
  ([x default] (cell (if (nil? x) default x))))

;; Shadow frustum for a directional light. The light node carries the direction
;; the light travels (`:dir`) plus optional frustum tuning; `target` is the scene
;; center the camera looks at. The light's eye sits back along -dir from target
;; and looks at it through an ortho box. Returns {:lview :lproj}.
(defn- light-frustum [{:keys [dir eye-dist bounds near far]} target]
  (let [ed (double (or eye-dist 40.0))
        b  (double (or bounds 30.0))
        n  (double (or near 1.0))
        f  (double (or far 150.0))
        ;; normalize dir to unit length
        mag (Math/sqrt (+ (* (nth dir 0) (nth dir 0))
                          (* (nth dir 1) (nth dir 1))
                          (* (nth dir 2) (nth dir 2))))
        inv (if (zero? mag) 0.0 (/ 1.0 mag))
        dx (* (nth dir 0) inv)
        dy (* (nth dir 1) inv)
        dz (* (nth dir 2) inv)
        ;; eye sits back along -dir from the target (toward the sun)
        leye [(+ (nth target 0) (* (- dx) ed))
              (+ (nth target 1) (* (- dy) ed))
              (+ (nth target 2) (* (- dz) ed))]]
    {:lview (m/look-at leye target [0.0 1.0 0.0])
     :lproj (m/ortho (- b) b (- b) b n f)}))

(defn reactive-area
  "Return a `:gl-area` widget prop map that mounts `scene-fn` behind a lib-owned
  reactive GL render loop.

  scene-fn is a zero-arg component returning a hiccup scene tree (one `:camera`,
  one or more `:light`, and geometry). It may deref reactive cells — those become
  the plan's dependencies (glimmer-gl.scene/plan), so changing one recomputes
  only the plan, never the renderer.

  opts (any value may instead be a cell, dereffed each frame):
    :bg [r g b]              clear color (default near-black)
    :ambient float|[r g b]   ambient term (scalar -> [s s s])
    :shadow-bias float       shadow depth bias (default 0.002)
    :fog {:near :far :color} distance fog
    :materials {}            material-kw -> [r g b] (defaults to the renderer's)
    :depth-spec/:lit-spec    alternative shader specs (default: shadow + Blinn-Phong)
    :version [maj min]       GL version required (default [3 2])
    :depth-buffer bool       request a depth buffer (default true)
    :on-tick (fn [area])     optional app animation policy (e.g. advance an
                             orbit cell); runs each frame before render. No gl-*
                             call belongs here — it mutates scene-driving cells.
    :on-motion (fn [nx ny])  optional pointer-move handler; nx,ny are the pointer
                             position normalized to [-1,1] across the GL area
                             (origin at centre). Mutate scene-driving cells here —
                             the plan recomputes; no gl-* call belongs here."
  ([scene-fn] (reactive-area scene-fn {}))
  ([scene-fn opts]
   (let [plan-r (atom nil)     ; the scene reaction, built on realize
         st     (atom nil)     ; renderer state, built on realize
         vp     (atom [960 600])]
     {:version     (or (:version opts) [3 2])
      :depth-buffer (if (nil? (:depth-buffer opts)) true (:depth-buffer opts))
      :hexpand true :vexpand true
      :on-realize
      (fn [_area]
        (gl/gl-enable gl/GL-DEPTH-TEST)
        (gl/gl-enable gl/GL-CULL-FACE)
        (gl/gl-front-face gl/GL-CCW)
        (reset! plan-r (scene/plan scene-fn))
        (reset! st (renderer/make-renderer!
                     (select-keys opts [:depth-spec :lit-spec]))))
      :on-resize
      (fn [_area w h] (reset! vp [(max (long w) 1) (max (long h) 1)]))
      :on-tick
      (or (:on-tick opts) (fn [_area]))
      :on-motion
      (when-let [m (:on-motion opts)]
        ;; normalize raw widget px to [-1,1] across the GL area before handing
        ;; the position to app code — keeps the demo free of GTK/GDK calls.
        (fn [area x y]
          (let [w (double (max (long (gtk/widget-width area)) 1))
                h (double (max (long (gtk/widget-height area)) 1))]
            (m (- (/ (* 2.0 (double x)) w) 1.0)
               (- (/ (* 2.0 (double y)) h) 1.0)))))
      :on-render
      (fn [_area]
        (when-let [s @st]
          (let [rxn  @plan-r
                plan (when rxn @rxn)]
            (when plan
              (let [cam    (:camera plan)
                    [cw ch] @vp
                    cam2   (scene/perspective-camera
                             (assoc cam :aspect (/ (double cw) (double ch))))
                    light  (first (:lights plan))
                    lf     (when light (light-frustum light (:target cam)))
                    bg     (cell (:bg opts [0.0 0.0 0.0]))
                    amb    (cell (:ambient opts) 0.1)
                    amb3   (if (number? amb) [amb amb amb] amb)
                    bias   (cell (:shadow-bias opts) 0.002)
                    fog    (cell (:fog opts) {:near 8.0 :far 50.0 :color [0.0 0.0 0.0]})]
                (renderer/draw! s
                  {:plan        plan
                   :view        (:view cam2)
                   :proj        (:proj cam2)
                   :eye         (:eye cam2)
                   :canvas      [cw ch]
                   :bg          bg
                   :light       (when light
                                  (assoc (select-keys light [:dir :color])
                                    :lview (:lview lf) :lproj (:lproj lf)))
                   :ambient     amb3
                   :shadow-bias bias
                   :fog         fog
                   :materials   (:materials opts)}))))))})))
