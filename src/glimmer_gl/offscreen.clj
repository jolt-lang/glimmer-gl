(ns glimmer-gl.offscreen
  "A current GL context with no window, so GL code can run under a test runner.

  Everything else in glimmer-gl gets its context from a realized GtkGLArea,
  which means GL work can only happen inside a running GTK app: shaders compile
  in :on-realize, draws issue from :on-render. Anything that computes with the
  GPU rather than drawing to the screen — a render-to-texture pass, a transform
  feedback reduction — is then untestable, because a headless runner has no
  widget to realize.

  GDK can hand out a context directly: gdk_display_create_gl_context makes a
  GdkGLContext bound to the display rather than to a surface (GTK 4.6+). Once
  realized and made current, every glimmer-gl.gl entry point works normally.

  The one thing it cannot do is draw to the screen. There is no default
  framebuffer, so framebuffer 0 is incomplete by construction and any draw must
  target an FBO with a texture or renderbuffer attached. That is precisely what
  compute-style passes already do, so it costs them nothing — but it does mean
  glClear against framebuffer 0 silently does nothing rather than clearing.

  Usage — one context per process, created on first use:

    (if-let [err (:error (offscreen/ensure-current!))]
      (println \"no offscreen GL:\" err)      ; headless CI, no display, GL < 3.2
      (run-the-gl-thing))

  Call it from the main thread. GDK contexts are bound to the thread that
  realizes them, and GTK's own thread affinity applies here too."
  (:require [jolt.ffi :as ffi]))

;; --- GDK/GTK entry points ----------------------------------------------------
;; gtk_init_check returns FALSE instead of aborting when there is no display, so
;; a headless runner degrades to a skip rather than dying. GTK is idempotent
;; about repeat init calls.
(ffi/defcfn gtk-init-check "gtk_init_check" [] :int)
(ffi/defcfn gdk-display-get-default "gdk_display_get_default" [] :pointer)

;; The surfaceless context (GTK 4.6+). Returns a GdkGLContext that is NOT yet
;; realized: gdk_gl_context_realize does the actual driver-level creation and is
;; where a "GL 3.2 core unavailable" style failure surfaces.
(ffi/defcfn gdk-display-create-gl-context
  "gdk_display_create_gl_context" [:pointer :pointer] :pointer)
(ffi/defcfn gdk-gl-context-realize "gdk_gl_context_realize" [:pointer :pointer] :int)
(ffi/defcfn gdk-gl-context-make-current "gdk_gl_context_make_current" [:pointer] :void)
(ffi/defcfn gdk-gl-context-clear-current "gdk_gl_context_clear_current" [] :void)
(ffi/defcfn gdk-gl-context-get-version "gdk_gl_context_get_version" [:pointer :pointer :pointer] :void)

;; GdkGLAPI is a flags enum: GDK_GL_API_GL = 1<<0, GDK_GL_API_GLES = 1<<1.
;; Restricting to desktop GL matters on Linux, where GDK will happily give back
;; a GLES context that rejects `#version 330 core` shaders.
(def ^:private GDK-GL-API-GL 1)
(ffi/defcfn gdk-gl-context-set-allowed-apis
  "gdk_gl_context_set_allowed_apis" [:pointer :uint] :void)

(ffi/defcfn g-object-unref "g_object_unref" [:pointer] :void)
(ffi/defcfn g-error-free "g_error_free" [:pointer] :void)

;; --- GError out-params -------------------------------------------------------
;; ffi/alloc hands back uninitialized memory, and GLib asserts *error == NULL on
;; entry to any function taking a GError** before it writes to it — so the slot
;; MUST be zeroed. Garbage that happens to be NULL is the difference between
;; working and a hard crash on the error path.
(defn- error-slot []
  (let [p (ffi/alloc (ffi/sizeof :pointer))]
    (ffi/write p :pointer 0 ffi/null)
    p))

(defn- take-error!
  "Read a GError out-param as a string (nil when unset), freeing both the GError
   and the slot. GError is { guint32 domain; gint32 code; gchar *message; }, so
   the message pointer sits at byte 8."
  [slot]
  (let [err (ffi/read slot :pointer 0)
        msg (when-not (ffi/null? err)
              (let [m (ffi/read err :pointer 8)]
                (when-not (ffi/null? m) (ffi/ptr->string m))))]
    (when-not (ffi/null? err) (g-error-free err))
    (ffi/free slot)
    msg))

;; --- context creation --------------------------------------------------------

(defn create
  "Create, realize, and make current a surfaceless GL context on the default
  display. Returns {:context ptr :version [major minor]} on success, or
  {:error message} when there is no display or the driver refuses the context.

  Prefer `ensure-current!` — a process wants one of these, not several."
  []
  (if (zero? (gtk-init-check))
    {:error "gtk_init_check failed — no display available"}
    (let [display (gdk-display-get-default)]
      (if (ffi/null? display)
        {:error "no default GdkDisplay"}
        (let [slot (error-slot)
              ctx  (gdk-display-create-gl-context display slot)]
          (if (ffi/null? ctx)
            {:error (or (take-error! slot) "gdk_display_create_gl_context returned NULL")}
            (do
              (ffi/free slot)
              (gdk-gl-context-set-allowed-apis ctx GDK-GL-API-GL)
              (let [slot2 (error-slot)
                    ok    (gdk-gl-context-realize ctx slot2)]
                (if (zero? ok)
                  (let [msg (take-error! slot2)]
                    (g-object-unref ctx)
                    {:error (or msg "gdk_gl_context_realize failed")})
                  (do
                    (ffi/free slot2)
                    (gdk-gl-context-make-current ctx)
                    (let [maj (ffi/alloc (ffi/sizeof :int))
                          mnr (ffi/alloc (ffi/sizeof :int))]
                      (gdk-gl-context-get-version ctx maj mnr)
                      (let [v [(ffi/read maj :int 0) (ffi/read mnr :int 0)]]
                        (ffi/free maj) (ffi/free mnr)
                        {:context ctx :version v}))))))))))))

(def ^:private the-context (atom nil))

(defn ensure-current!
  "The process's offscreen context, created on first call and made current on
  every call. Returns the same {:context :version} or {:error} map throughout —
  a failure is cached too, so a headless run pays the failed probe once.

  Callers should branch on :error and skip rather than assume a context: a
  machine with no display is a legitimate outcome, not a broken test."
  []
  (let [cur @the-context]
    (if cur
      (do (when (:context cur) (gdk-gl-context-make-current (:context cur)))
          cur)
      (let [r (create)]
        (reset! the-context r)
        r))))

(defn available?
  "True when an offscreen GL context could be created (and is now current)."
  []
  (some? (:context (ensure-current!))))

(defn release!
  "Drop the process context. Mainly for a test runner that wants to prove
  teardown works; a normal run can just let the process exit."
  []
  (when-let [ctx (:context @the-context)]
    (gdk-gl-context-clear-current)
    (g-object-unref ctx))
  (reset! the-context nil)
  nil)
