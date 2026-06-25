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

;; --- core GL entry points ----------------------------------------------------
;; Returns a pointer to a NUL-terminated vendor/renderer/version string.
;; NULL until a context is current.
(ffi/defcfn gl-get-string   "glGetString"   [:uint] :pointer)
(ffi/defcfn gl-clear-color  "glClearColor"  [:float :float :float :float] :void)
(ffi/defcfn gl-clear        "glClear"       [:uint] :void)
(ffi/defcfn gl-viewport     "glViewport"    [:int :int :int :int] :void)
(ffi/defcfn gl-enable       "glEnable"      [:uint] :void)
(ffi/defcfn gl-disable      "glDisable"     [:uint] :void)

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
(ffi/defcfn gl-uniform-2f            "glUniform2f"          [:int :float :float] :void)
(ffi/defcfn gl-uniform-3f            "glUniform3f"          [:int :float :float :float] :void)
(ffi/defcfn gl-uniform-4f            "glUniform4f"          [:int :float :float :float :float] :void)
(ffi/defcfn gl-get-attrib-location   "glGetAttribLocation"  [:uint :string] :int)
(ffi/defcfn gl-enable-vertex-attrib-array "glEnableVertexAttribArray" [:uint] :void)
(ffi/defcfn gl-vertex-attrib-pointer "glVertexAttribPointer"
  [:uint :int :uint :uint8 :int :pointer] :void)
(ffi/defcfn gl-get-error             "glGetError"           [] :uint)

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
