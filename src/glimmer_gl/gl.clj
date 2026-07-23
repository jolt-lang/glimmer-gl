(ns glimmer-gl.gl
  "Minimal native OpenGL bindings via jolt FFI. The host GL library is dlopened
  by the jolt/native entry in deps.edn; each defcfn resolves its C entry point
  at ns load. GL calls need a current context to do work."
  (:require [jolt.ffi :as ffi]))

;; --- enum: string names ------------------------------------------------------
(def GL-VENDOR    0x1F00)
(def GL-RENDERER  0x1F01)
(def GL-VERSION   0x1F02)
(def GL-EXTENSIONS 0x1F03)

;; --- ClearTarget / ClearBufferMask ------------------------------------------
(def GL-DEPTH-BUFFER-BIT   0x00000100)
(def GL-STENCIL-BUFFER-BIT 0x00000400)
(def GL-COLOR-BUFFER-BIT   0x00004000)

;; --- CapabilityName ----------------------------------------------------------
(def GL-DEPTH-TEST 0x0B71)
(def GL-CULL-FACE  0x0B44)
(def GL-BLEND      0x0BE2)
(def GL-SCISSOR-TEST 0x0C11)

;; --- BlendingFactor ----------------------------------------------------------
(def GL-ZERO-FACTOR         0)
(def GL-ONE-FACTOR          1)
(def GL-SRC-ALPHA           0x0302)
(def GL-ONE-MINUS-SRC-ALPHA 0x0303)

;; --- PrimitiveType -----------------------------------------------------------
(def GL-TRIANGLES      0x0004)
(def GL-TRIANGLE-STRIP 0x0005)
(def GL-LINES          0x0001)

;; --- Buffer / shader targets -------------------------------------------------
(def GL-ARRAY-BUFFER         0x8892)
(def GL-ELEMENT-ARRAY-BUFFER 0x8893)
(def GL-STATIC-DRAW          0x88E4)
(def GL-VERTEX-SHADER        0x8B31)
(def GL-FRAGMENT-SHADER      0x8B30)

;; --- Shader / program query enums -------------------------------------------
(def GL-COMPILE-STATUS   0x8B81)
(def GL-LINK-STATUS      0x8B82)
(def GL-INFO-LOG-LENGTH  0x8B84)

;; --- Attribute / uniform plumbing -------------------------------------------
(def GL-FLOAT 0x1406)
(def GL-FALSE 0)
(def GL-TRUE  1)

;; --- face culling ------------------------------------------------------------
(def GL-FRONT 0x0404)
(def GL-BACK  0x0405)
(def GL-CCW   0x0901)
(def GL-CW    0x0900)

;; --- textures ----------------------------------------------------------------
(def GL-TEXTURE0                 0x84C0)
(def GL-TEXTURE-2D               0x0DE1)
(def GL-TEXTURE-MAG-FILTER       0x2800)
(def GL-TEXTURE-MIN-FILTER       0x2801)
(def GL-TEXTURE-WRAP-S           0x2802)
(def GL-TEXTURE-WRAP-T           0x2803)
(def GL-NEAREST                  0x2600)
(def GL-LINEAR                   0x2601)
(def GL-CLAMP-TO-EDGE            0x812F)
(def GL-CLAMP-TO-BORDER          0x812D)
(def GL-TEXTURE-2D-ARRAY         0x9102)
(def GL-TEXTURE-WRAP-R           0x8072)
(def GL-REPEAT                   0x2901)
(def GL-TEXTURE-BORDER-COLOR     0x810C)
(def GL-DEPTH-COMPONENT          0x1902)
(def GL-DEPTH-COMPONENT16        0x81A5)
(def GL-DEPTH-COMPONENT24        0x81A6)
(def GL-TEXTURE-COMPARE-MODE     0x884C)
(def GL-TEXTURE-COMPARE-FUNC     0x884D)
(def GL-COMPARE-REF-TO-TEXTURE   0x884E)
(def GL-LESS                     0x0201)
(def GL-LEQUAL                   0x0203)

;; --- texture internal formats / pixel types ----------------------------------
;; RGBA32F carries arbitrary float payloads as texels — e.g. a 2D gaussian's
;; mean + symmetric covariance + RGB color packed two texels per splat, sampled
;; with texelFetch in the fragment shader. RGBA/RGB + UNSIGNED_BYTE are the
;; 8-bit formats gdk-pixbuf hands us for the source image.
(def GL-RGBA32F        0x8814)
(def GL-RGBA           0x1908)
(def GL-RGB            0x1907)
(def GL-UNSIGNED-BYTE  0x1401)

;; --- framebuffers ------------------------------------------------------------
(def GL-FRAMEBUFFER            0x8D40)
(def GL-FRAMEBUFFER-BINDING    0x8CA6)
(def GL-DEPTH-ATTACHMENT       0x8D00)
(def GL-COLOR-ATTACHMENT0      0x8CE0)
(def GL-NONE                   0)
(def GL-FRAMEBUFFER-COMPLETE   0x8CD5)

;; --- core GL entry points ----------------------------------------------------
;; Returns a pointer to a NUL-terminated vendor/renderer/version string.
;; NULL until a context is current.
(ffi/defcfn gl-get-string   "glGetString"   [:uint] :pointer)
(ffi/defcfn gl-clear-color  "glClearColor"  [:float :float :float :float] :void)
(ffi/defcfn gl-clear        "glClear"       [:uint] :void)
(ffi/defcfn gl-viewport     "glViewport"    [:int :int :int :int] :void)
(ffi/defcfn gl-enable       "glEnable"      [:uint] :void)
(ffi/defcfn gl-disable      "glDisable"     [:uint] :void)
(ffi/defcfn gl-blend-func   "glBlendFunc"   [:uint :uint] :void)
(ffi/defcfn gl-scissor      "glScissor"     [:int :int :int :int] :void)

;; --- vertex buffer objects ---------------------------------------------------
(ffi/defcfn gl-gen-buffers  "glGenBuffers"  [:int :pointer] :void)
(ffi/defcfn gl-bind-buffer  "glBindBuffer"  [:uint :uint] :void)
(ffi/defcfn gl-buffer-data  "glBufferData"  [:uint :ssize_t :pointer :uint] :void)
(ffi/defcfn gl-draw-arrays  "glDrawArrays"  [:uint :int :int] :void)

;; --- vertex array objects (required in core profile to make attrib state usable)
(ffi/defcfn gl-gen-vertex-arrays  "glGenVertexArrays"  [:int :pointer] :void)
(ffi/defcfn gl-bind-vertex-array  "glBindVertexArray"  [:uint] :void)
(ffi/defcfn gl-delete-vertex-arrays "glDeleteVertexArrays" [:int :pointer] :void)

;; --- shaders & programs -----------------------------------------------------
(ffi/defcfn gl-create-shader        "glCreateShader"        [:uint] :uint)
(ffi/defcfn gl-shader-source        "glShaderSource"        [:uint :int :pointer :pointer] :void)
(ffi/defcfn gl-compile-shader       "glCompileShader"       [:uint] :void)
(ffi/defcfn gl-get-shaderiv         "glGetShaderiv"         [:uint :uint :pointer] :void)
(ffi/defcfn gl-get-shader-info-log  "glGetShaderInfoLog"    [:uint :int :pointer :pointer] :void)
(ffi/defcfn gl-delete-shader        "glDeleteShader"        [:uint] :void)
(ffi/defcfn gl-create-program       "glCreateProgram"       [] :uint)
(ffi/defcfn gl-attach-shader        "glAttachShader"        [:uint :uint] :void)
(ffi/defcfn gl-link-program         "glLinkProgram"         [:uint] :void)
(ffi/defcfn gl-get-programiv        "glGetProgramiv"        [:uint :uint :pointer] :void)
(ffi/defcfn gl-get-program-info-log "glGetProgramInfoLog"   [:uint :int :pointer :pointer] :void)
(ffi/defcfn gl-delete-program       "glDeleteProgram"       [:uint] :void)
(ffi/defcfn gl-use-program          "glUseProgram"          [:uint] :void)

;; --- uniforms & attributes --------------------------------------------------
(ffi/defcfn gl-get-uniform-location  "glGetUniformLocation" [:uint :string] :int)
(ffi/defcfn gl-uniform-matrix4fv     "glUniformMatrix4fv"   [:int :int :uint8 :pointer] :void)
(ffi/defcfn gl-uniform-1f            "glUniform1f"          [:int :float] :void)
(ffi/defcfn gl-uniform-1i            "glUniform1i"          [:int :int] :void)
(ffi/defcfn gl-uniform-1fv           "glUniform1fv"         [:int :int :pointer] :void)
(ffi/defcfn gl-uniform-1iv           "glUniform1iv"         [:int :int :pointer] :void)
(ffi/defcfn gl-uniform-2f            "glUniform2f"          [:int :float :float] :void)
(ffi/defcfn gl-uniform-3f            "glUniform3f"          [:int :float :float :float] :void)
(ffi/defcfn gl-uniform-3fv           "glUniform3fv"         [:int :int :pointer] :void)
(ffi/defcfn gl-uniform-4f            "glUniform4f"          [:int :float :float :float :float] :void)
(ffi/defcfn gl-get-attrib-location   "glGetAttribLocation"  [:uint :string] :int)
(ffi/defcfn gl-enable-vertex-attrib-array "glEnableVertexAttribArray" [:uint] :void)
(ffi/defcfn gl-vertex-attrib-pointer "glVertexAttribPointer"
  [:uint :int :uint :uint8 :int :pointer] :void)
(ffi/defcfn gl-get-error             "glGetError"           [] :uint)

;; --- face culling ------------------------------------------------------------
(ffi/defcfn gl-cull-face   "glCullFace"  [:uint] :void)
(ffi/defcfn gl-front-face  "glFrontFace" [:uint] :void)

;; --- textures ----------------------------------------------------------------
(ffi/defcfn gl-active-texture      "glActiveTexture"      [:uint] :void)
(ffi/defcfn gl-gen-textures        "glGenTextures"        [:int :pointer] :void)
(ffi/defcfn gl-bind-texture        "glBindTexture"        [:uint :uint] :void)
(ffi/defcfn gl-tex-image-2d        "glTexImage2D"
  [:uint :int :int :int :int :int :uint :uint :pointer] :void)
(ffi/defcfn gl-tex-image-3d        "glTexImage3D"
  [:uint :int :int :int :int :int :int :uint :uint :pointer] :void)
(ffi/defcfn gl-tex-parameter-i     "glTexParameteri"      [:uint :uint :int] :void)
(ffi/defcfn gl-tex-parameter-fv    "glTexParameterfv"     [:uint :uint :pointer] :void)

;; --- framebuffers (render-to-texture for shadow mapping) ---------------------
(ffi/defcfn gl-gen-framebuffers        "glGenFramebuffers"        [:int :pointer] :void)
(ffi/defcfn gl-bind-framebuffer        "glBindFramebuffer"        [:uint :uint] :void)
(ffi/defcfn gl-framebuffer-texture-2d  "glFramebufferTexture2D"
  [:uint :uint :uint :uint :int] :void)
(ffi/defcfn gl-check-framebuffer-status "glCheckFramebufferStatus" [:uint] :uint)
(ffi/defcfn gl-draw-buffer             "glDrawBuffer"             [:uint] :void)
(ffi/defcfn gl-read-buffer             "glReadBuffer"             [:uint] :void)
(ffi/defcfn gl-read-pixels             "glReadPixels"
  [:int :int :int :int :uint :uint :pointer] :void)
(ffi/defcfn gl-get-integerv            "glGetIntegerv"            [:uint :pointer] :void)

;; --- helpers ------------------------------------------------------------------
(defn gl-get-string*
  "Decode glGetString(name) to a jolt string; nil when no context is current
  (glGetString returns NULL then)."
  [^long name]
  (let [p (gl-get-string name)]
    (when-not (ffi/null? p)
      (ffi/ptr->string p))))

(defn write-floats
  "Allocate a fresh byte buffer, write each of `xs` as a 4-byte float at stride
  4, and return the pointer (caller owns it; ffi/free when done). Used to feed
  VBO and uniform data into GL."
  [xs]
  (let [n   (count xs)
        ptr (ffi/alloc (* n (ffi/sizeof :float)))]
    (loop [i 0, s (seq xs)]
      (when s
        (ffi/write ptr :float (* i 4) (double (first s)))
        (recur (inc i) (next s))))
    ptr))

(defn write-ints
  "Allocate a byte buffer and write each of `xs` as a 4-byte GLint at stride 4;
  return the pointer (caller owns it). For glUniform1iv and integer attributes."
  [xs]
  (let [n   (count xs)
        ptr (ffi/alloc (* (max 1 n) (ffi/sizeof :int)))]
    (loop [i 0, s (seq xs)]
      (when s
        (ffi/write ptr :int (* i 4) (long (first s)))
        (recur (inc i) (next s))))
    ptr))

(defn gen-one
  "Call a GL `glGen*`-style fn (count, out-ptr) once and return the single id it wrote."
  [f]
  (let [p (ffi/alloc (ffi/sizeof :int))]
    (f 1 p)
    (let [v (ffi/read p :int)]
      (ffi/free p)
      v)))

(defn- shader-status
  "Read a single GLint GL_*_STATUS for `shader-or-program` via `iv-fn`. Frees its
  scratch pointer. Returns the int."
  [iv-fn ^long obj ^long what]
  (let [p (ffi/alloc (ffi/sizeof :int))]
    (iv-fn obj what p)
    (let [v (ffi/read p :int)]
      (ffi/free p)
      v)))

(defn- dump-info-log
  "Fetch and print a shader/program info log via `log-fn`."
  [log-fn ^long obj]
  (let [buf (ffi/alloc 4096)
        len (ffi/alloc (ffi/sizeof :int))]
    (log-fn obj 4096 len buf)
    (println "  GL info log:" (ffi/ptr->string buf))
    (ffi/free buf)
    (ffi/free len)))

(defn make-shader
  "Compile a GL shader of `shader-type` (e.g. GL-VERTEX-SHADER) from `source`.
  Returns the shader id, or nil (after printing the info log) on failure.
  Needs a current GL context."
  [^long shader-type ^String source]
  (let [sh     (gl-create-shader shader-type)
        src    (ffi/string->ptr source)
        arr    (ffi/alloc (ffi/sizeof :pointer))]
    ;; glShaderSource(shader, count=1, &src, lengths=NULL)
    (ffi/write arr :pointer 0 src)
    (gl-shader-source sh 1 arr ffi/null)
    (ffi/free src)
    (ffi/free arr)
    (gl-compile-shader sh)
    (if (zero? (shader-status gl-get-shaderiv sh GL-COMPILE-STATUS))
      (do (dump-info-log gl-get-shader-info-log sh)
          (gl-delete-shader sh)
          nil)
      sh)))

(defn make-program
  "Link a GL program from vertex + fragment GLSL source. Returns the program id,
  or nil (after printing the link log) on failure. Needs a current GL context."
  [^String vs-source ^String fs-source]
  (let [vs (make-shader GL-VERTEX-SHADER vs-source)]
    (if-not vs
      nil
      (let [fs (make-shader GL-FRAGMENT-SHADER fs-source)]
        (if-not fs
          (do (gl-delete-shader vs) nil)
          (let [prog (gl-create-program)]
            (gl-attach-shader prog vs)
            (gl-attach-shader prog fs)
            (gl-link-program prog)
            (gl-delete-shader vs)
            (gl-delete-shader fs)
            (if (zero? (shader-status gl-get-programiv prog GL-LINK-STATUS))
              (do (dump-info-log gl-get-program-info-log prog)
                  (gl-delete-program prog)
                  nil)
              prog)))))))

;; ============================================================================
;; Transform feedback + geometry shaders + texture buffers + query objects
;; ----------------------------------------------------------------------------
;; GPU stream generation: a vertex+geometry program computes per-primitive data
;; and the geometry shader conditionally EmitVertex()s, so the survivors are
;; CAPTURED into a buffer via transform feedback (variable-count compaction with
;; no compute shaders / SSBOs — the tools macOS GL 4.1 lacks). A query object
;; reports how many primitives were written; the buffer is then bound as a
;; TEXTURE BUFFER (samplerBuffer) so a fragment shader can texelFetch it with no
;; GL_MAX_TEXTURE_SIZE ceiling (a flat 1D stream, no tiling).
;; ============================================================================

;; --- enums -------------------------------------------------------------------
(def GL-POINTS                              0x0000)
(def GL-GEOMETRY-SHADER                     0x8DD9)
(def GL-TRANSFORM-FEEDBACK-BUFFER           0x8C8E)
(def GL-INTERLEAVED-ATTRIBS                 0x8C8C)
(def GL-SEPARATE-ATTRIBS                    0x8C8D)
(def GL-RASTERIZER-DISCARD                  0x8C89)
(def GL-TRANSFORM-FEEDBACK-PRIMITIVES-WRITTEN 0x8C88)
(def GL-QUERY-RESULT                        0x8866)
(def GL-QUERY-RESULT-AVAILABLE              0x8867)
(def GL-TEXTURE-BUFFER                      0x8C2A)
(def GL-R32F                                0x822E)
(def GL-RG32F                               0x8230)
(def GL-DYNAMIC-COPY                        0x88EA)
(def GL-DYNAMIC-DRAW                        0x88E8)
(def GL-STREAM-DRAW                         0x88E0)
(def GL-DYNAMIC-READ                        0x88E9)

;; --- entry points ------------------------------------------------------------
(ffi/defcfn gl-delete-buffers   "glDeleteBuffers"   [:int :pointer] :void)
(ffi/defcfn gl-buffer-sub-data  "glBufferSubData"   [:uint :ssize_t :ssize_t :pointer] :void)
(ffi/defcfn gl-get-buffer-sub-data "glGetBufferSubData" [:uint :ssize_t :ssize_t :pointer] :void)

(ffi/defcfn gl-transform-feedback-varyings "glTransformFeedbackVaryings"
  [:uint :int :pointer :uint] :void)
(ffi/defcfn gl-bind-buffer-base            "glBindBufferBase"  [:uint :uint :uint] :void)
(ffi/defcfn gl-begin-transform-feedback    "glBeginTransformFeedback" [:uint] :void)
(ffi/defcfn gl-end-transform-feedback      "glEndTransformFeedback"   [] :void)

(ffi/defcfn gl-gen-queries          "glGenQueries"          [:int :pointer] :void)
(ffi/defcfn gl-delete-queries       "glDeleteQueries"       [:int :pointer] :void)
(ffi/defcfn gl-begin-query          "glBeginQuery"          [:uint :uint] :void)
(ffi/defcfn gl-end-query            "glEndQuery"            [:uint] :void)
(ffi/defcfn gl-get-query-object-uiv "glGetQueryObjectuiv"   [:uint :uint :pointer] :void)

(ffi/defcfn gl-tex-buffer           "glTexBuffer"           [:uint :uint :uint] :void)
(ffi/defcfn gl-flush                "glFlush"               [] :void)
(ffi/defcfn gl-finish               "glFinish"              [] :void)

;; --- helpers -----------------------------------------------------------------
(defn get-query-object-uiv
  "Read a single GLuint query result (e.g. GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN
  via GL_QUERY_RESULT). Frees its scratch pointer."
  [^long id ^long pname]
  (let [p (ffi/alloc (ffi/sizeof :int))]
    (gl-get-query-object-uiv id pname p)
    (let [v (ffi/read p :int)] (ffi/free p) v)))

(defn read-floats
  "Read `n` 4-byte floats out of a byte-buffer pointer into a Clojure vector."
  [ptr n]
  (mapv (fn [i] (ffi/read ptr :float (* i 4))) (range n)))

(defn- write-cstr-array
  "Allocate an array of `n` char* pointers, one per string in `strs` (each a
  freshly allocated NUL-terminated C string). Returns [array-ptr [str-ptrs...]];
  the caller frees the array and every str-ptr."
  [strs]
  (let [n    (count strs)
        arr  (ffi/alloc (* n (ffi/sizeof :pointer)))
        sps  (mapv ffi/string->ptr strs)]
    (dotimes [i n] (ffi/write arr :pointer (* i (ffi/sizeof :pointer)) (nth sps i)))
    [arr sps]))

(defn make-tf-program
  "Link a transform-feedback program from vertex + (optional) geometry GLSL, with
  `varyings` (a seq of out-variable names) captured into ONE interleaved buffer.
  No fragment shader — pair with glEnable(GL_RASTERIZER_DISCARD) when running it.
  glTransformFeedbackVaryings MUST be set before linking, so this can't reuse
  make-program. Returns the program id or nil (after printing the log) on failure."
  [^String vs-source gs-source varyings]
  (let [vs (make-shader GL-VERTEX-SHADER vs-source)
        gs (when gs-source (make-shader GL-GEOMETRY-SHADER gs-source))]
    (cond
      (not vs) nil
      (and gs-source (not gs)) (do (gl-delete-shader vs) nil)
      :else
      (let [prog (gl-create-program)
            [arr sps] (write-cstr-array varyings)]
        (gl-attach-shader prog vs)
        (when gs (gl-attach-shader prog gs))
        (gl-transform-feedback-varyings prog (count varyings) arr GL-INTERLEAVED-ATTRIBS)
        (ffi/free arr)
        (doseq [sp sps] (ffi/free sp))
        (gl-link-program prog)
        (gl-delete-shader vs)
        (when gs (gl-delete-shader gs))
        (if (zero? (shader-status gl-get-programiv prog GL-LINK-STATUS))
          (do (dump-info-log gl-get-program-info-log prog)
              (gl-delete-program prog)
              nil)
          prog)))))
