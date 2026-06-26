(ns glimmer-gl.gtk
  "glimmer-gl's bridge into glimmer's GTK4 widget tree: the GTK widgets glimmer
  itself omits but a GL app needs — GtkGLArea (the OpenGL drawing surface) and
  GtkScale (a slider). Requiring this namespace registers two hiccup tags into
  glimmer's widget registry so the whole UI, GL pane included, is one reactive
  glimmer tree:

    [:gl-area {:version [3 2] :on-realize f :on-render f :on-resize f
               :on-tick f :on-motion (fn [area x y])}]
    [:scale   {:min 0 :max 1 :step 0.01 :value 0.5 :digits 2 :on-value f}]

  The GLArea is inherently imperative — its realize/render/resize signals build
  and drive raw GL objects, and render returns a gboolean — so those handlers are
  wired directly (see `gl-area-spec`'s :connect) rather than through glimmer's
  uniform void(widget,data) signal path. The slider's value-changed signal does
  fit that path, so it is added with `w/register-signal!` and needs no custom
  wiring."
  (:require [glimmer.ffi :as g]
            [glimmer.widget :as w]
            [jolt.ffi :as ffi]))

;; --- GtkGLArea ---------------------------------------------------------------
(ffi/defcfn gtk-gl-area-new
  "gtk_gl_area_new" [] :pointer)
(ffi/defcfn gtk-gl-area-make-current
  "gtk_gl_area_make_current" [:pointer] :void)
(ffi/defcfn gtk-gl-area-queue-render
  "gtk_gl_area_queue_render" [:pointer] :void)
(ffi/defcfn gtk-gl-area-set-required-version
  "gtk_gl_area_set_required_version" [:pointer :int :int] :void)
(ffi/defcfn gtk-gl-area-get-error
  "gtk_gl_area_get_error" [:pointer] :pointer)
(ffi/defcfn gtk-gl-area-set-has-depth-buffer
  "gtk_gl_area_set_has_depth_buffer" [:pointer :int] :void)

;; gtk_widget_add_tick_callback registers a callback synced to the widget's
;; GdkFrameClock; GTK passes the widget pointer on every invocation and only
;; fires while the widget is mapped + realized.
(ffi/defcfn gtk-widget-add-tick-callback
  "gtk_widget_add_tick_callback" [:pointer :pointer :pointer :pointer] :uint)

;; --- GtkEventControllerMotion (pointer tracking) -----------------------------
;; GTK4's way to follow the pointer over a widget: a dedicated event controller
;; attached via gtk_widget_add_controller; its "motion" signal fires with
;; widget-relative x,y on every pointer move.
(ffi/defcfn gtk-event-controller-motion-new
  "gtk_event_controller_motion_new" [] :pointer)
(ffi/defcfn gtk-widget-add-controller
  "gtk_widget_add_controller" [:pointer :pointer] :void)
(ffi/defcfn gtk-widget-get-width
  "gtk_widget_get_width" [:pointer] :int)
(ffi/defcfn gtk-widget-get-height
  "gtk_widget_get_height" [:pointer] :int)

;; --- GtkScale / GtkRange (sliders) -------------------------------------------
(ffi/defcfn gtk-scale-new-with-range
  "gtk_scale_new_with_range" [:int :double :double :double] :pointer)
(ffi/defcfn gtk-range-get-value
  "gtk_range_get_value" [:pointer] :double)
(ffi/defcfn gtk-range-set-value
  "gtk_range_set_value" [:pointer :double] :void)
(ffi/defcfn gtk-scale-set-digits
  "gtk_scale_set_digits" [:pointer :int] :void)

(def ^:private ORIENTATION-HORIZONTAL 0)

;; --- helpers -----------------------------------------------------------------
(defn gl-area-error-message
  "Decode a GtkGLArea's GError (if any) to a string; nil when there is no error.
   GError is { guint32 domain; gint32 code; gchar *message; } — message at byte 8."
  [area]
  (let [err (gtk-gl-area-get-error area)]
    (when-not (ffi/null? err)
      (let [msg (ffi/read err :pointer 8)]
        (when-not (ffi/null? msg)
          (ffi/ptr->string msg))))))

(defn make-current
  "Make the GLArea's GL context current. Call before issuing GL on realize."
  [area] (gtk-gl-area-make-current area))

(defn queue-render
  "Ask the GLArea to redraw on the next frame."
  [area] (gtk-gl-area-queue-render area))

(defn widget-width
  "A widget's current allocated width, in pixels (GTK4)."
  [w] (gtk-widget-get-width w))

(defn widget-height
  "A widget's current allocated height, in pixels (GTK4)."
  [w] (gtk-widget-get-height w))

(defn- connect!
  "Wire `cb` (a foreign-callable pointer) to `signal` on `widget`, retaining it
  for the process lifetime so GTK's raw pointer never dangles."
  [widget signal cb]
  (w/retain-callable! cb)
  (g/g-signal-connect-data widget signal cb ffi/null ffi/null g/CONNECT-DEFAULT))

;; --- :gl-area widget ---------------------------------------------------------
;; The handler props, each called with the GLArea pointer GTK hands us:
;;   :on-realize (fn [area])        build GL objects (context is current)
;;   :on-render  (fn [area])        issue draw calls; we always return TRUE
;;   :on-resize  (fn [area w h])    glViewport, store aspect, …
;;   :on-tick    (fn [area])        per-frame; we auto queue-render afterwards
;;   :on-motion  (fn [area x y])    pointer move (widget-relative px); a
;;                                  GtkEventControllerMotion we attach here
(defn- gl-area-connect! [area props]
  (let [{:keys [on-realize on-render on-resize on-tick on-motion]} props]
    (when on-realize
      (connect! area "realize"
        (ffi/foreign-callable (fn [a _] (on-realize a))
                              [:pointer :pointer] :void :collect-safe)))
    (when on-render
      (connect! area "render"
        (ffi/foreign-callable (fn [a _] (on-render a) 1)
                              [:pointer :pointer] :int :collect-safe)))
    (when on-resize
      (connect! area "resize"
        (ffi/foreign-callable (fn [a w h _] (on-resize a w h))
                              [:pointer :int :int :pointer] :void :collect-safe)))
    (when on-tick
      ;; GTK passes the area on every tick, so the redraw target is always the
      ;; right (mapped, realized) widget. Auto queue-render keeps it animating.
      (gtk-widget-add-tick-callback area
        (let [cb (ffi/foreign-callable
                   (fn [a _clock _data] (on-tick a) (queue-render a) 1)
                   [:pointer :pointer :pointer] :int :collect-safe)]
          (w/retain-callable! cb) cb)
        ffi/null ffi/null))
    (when on-motion
      ;; Attach a motion event controller to the area; GTK emits "motion" with
      ;; widget-relative x,y on every pointer move over it.
      (let [ctl (gtk-event-controller-motion-new)]
        (gtk-widget-add-controller area ctl)
        (connect! ctl "motion"
          (ffi/foreign-callable (fn [_ x y _] (on-motion area (double x) (double y)))
                                [:pointer :double :double :pointer] :void :collect-safe))))))

(defn gl-area-spec []
  {:ctor      (fn [props]
                (let [area (gtk-gl-area-new)
                      [maj min] (or (:version props) [3 2])]
                  (gtk-gl-area-set-required-version area maj min)
                  ;; a depth buffer is needed to draw solid 3D geometry; default on
                  (gtk-gl-area-set-has-depth-buffer
                    area (if (false? (:depth-buffer props)) 0 1))
                  area))
   :apply     (fn [_ _])     ; signals wire once via :connect; nothing to re-apply
   :connect   gl-area-connect!
   :container :none})

;; --- :scale widget -----------------------------------------------------------
(defn scale-spec []
  {:ctor  (fn [{:keys [min max step value]}]
            (let [scl (gtk-scale-new-with-range
                        ORIENTATION-HORIZONTAL
                        (double (or min 0.0)) (double (or max 1.0)) (double (or step 0.01)))]
              (when value (gtk-range-set-value scl (double value)))
              scl))
   :apply (fn [w {:keys [digits value]}]
            (when digits (gtk-scale-set-digits w digits))
            ;; set value only when it differs, so a re-render that re-applies the
            ;; same value doesn't churn a spurious value-changed emission.
            (when (and value (not (== (double value) (gtk-range-get-value w))))
              (gtk-range-set-value w (double value))))
   :container :none})

;; --- registration ------------------------------------------------------------
;; Registering at load time means a simple `(require '[glimmer-gl.gtk])` in an
;; app makes [:gl-area ...] and [:scale ...] usable in glimmer hiccup.
(w/register-widget! :gl-area (gl-area-spec))
(w/register-widget! :scale   (scale-spec))
;; The slider's value-bearing signal: handler gets the current double.
(w/register-signal! :on-value "value-changed" gtk-range-get-value)
