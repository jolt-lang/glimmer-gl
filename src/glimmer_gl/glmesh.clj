(ns glimmer-gl.glmesh
  "Mesh -> GL draw pipeline, ported from thi.ng/geom gl.glmesh + gl.core draw path.

  A Mesh becomes a pure GL buffer-spec of separate attribute buffers
  (as-gl-buffer-spec); make-buffers-in-spec uploads those buffers;
  make-vertex-array records attribute bindings into a VAO (the core profile
  requires one); draw-with-shader composes a compiled shader + per-frame uniforms
  + the VAO into one draw call. The data is Clojure all the way down — the only
  raw GL lives behind these entry points."
  (:require [glimmer-gl.gl :as gl]
            [glimmer-gl.shader :as shader]
            [glimmer-gl.mesh :as mesh]
            [glimmer-gl.vector :as v]
            [jolt.ffi :as ffi]))

(defn as-gl-buffer-spec
  "Compile `mesh` into a pure GL buffer-spec of separate attribute buffers
  (port of thi.ng.geom.gl.glmesh/as-gl-buffer-spec):
    {:attribs {:position {:data [x y z ...] :size 3}
               :normal   {:data [x y z ...] :size 3}}
     :num-vertices N :mode gl/GL-TRIANGLES}
  `opts`:
    :shading :flat   each triangle's 3 verts carry its face normal (default)
    :shading :smooth each vert carries its averaged vertex normal"
  ([m] (as-gl-buffer-spec m {}))
  ([m {:keys [shading] :or {shading :flat}}]
   (let [tris   (mesh/triangles m)
         vn     (when (= shading :smooth) (mesh/vertex-normals m))
         nrm-of (if vn (fn [_ p] (get vn p)) (fn [tri _] (mesh/face-normal tri)))]
     (loop [ts tris pos [] nrm []]
       (if-let [tri (first ts)]
         (let [[a b c] tri
               na (nrm-of tri a) nb (nrm-of tri b) nc (nrm-of tri c)]
           (recur (next ts)
                  (-> pos
                      (conj (v/x a)) (conj (v/y a)) (conj (v/z a))
                      (conj (v/x b)) (conj (v/y b)) (conj (v/z b))
                      (conj (v/x c)) (conj (v/y c)) (conj (v/z c)))
                  (-> nrm
                      (conj (v/x na)) (conj (v/y na)) (conj (v/z na))
                      (conj (v/x nb)) (conj (v/y nb)) (conj (v/z nb))
                      (conj (v/x nc)) (conj (v/y nc)) (conj (v/z nc)))))
         {:attribs     {:position {:data pos :size 3}
                        :normal   {:data nrm :size 3}}
          :num-vertices (* 3 (count tris))
          :mode         gl/GL-TRIANGLES})))))

(defn- upload-array-buffer
  "Upload `data` (a seq of doubles) into a fresh ARRAY_BUFFER; return its id."
  [data mode]
  (let [buf (gl/gen-one gl/gl-gen-buffers)]
    (gl/gl-bind-buffer gl/GL-ARRAY-BUFFER buf)
    (let [ptr (gl/write-floats data)]
      (gl/gl-buffer-data gl/GL-ARRAY-BUFFER (* (count data) 4) ptr mode)
      (ffi/free ptr))
    buf))

(defn make-buffers-in-spec
  "Upload each attribute's :data to a GL buffer, associng :buffer
  (port of thi.ng.geom.gl.core/make-buffers-in-spec). `mode` is a GL draw-mode
  constant (e.g. gl/GL-STATIC-DRAW). Needs a current GL context."
  [spec mode]
  (update spec :attribs
          (fn [as]
            (into {} (map (fn [[name a]]
                            [name (assoc a :buffer (upload-array-buffer (:data a) mode))]))
                  as))))

(defn make-vertex-array
  "Record attribute bindings into a new VAO (the core profile requires one).
  For each attrib present in both `spec` and the compiled `shader`, enable its
  location and point it at its buffer. Needs a current GL context."
  [spec shader]
  (let [vao (gl/gen-one gl/gl-gen-vertex-arrays)]
    (gl/gl-bind-vertex-array vao)
    (doseq [[name {:keys [buffer size]}] (:attribs spec)]
      (when-let [loc (shader/attrib-loc shader name)]
        (when (>= loc 0)
          (gl/gl-bind-buffer gl/GL-ARRAY-BUFFER buffer)
          (gl/gl-enable-vertex-attrib-array loc)
          (gl/gl-vertex-attrib-pointer loc size gl/GL-FLOAT gl/GL-FALSE 0 ffi/null))))
    (gl/gl-bind-vertex-array 0)
    (assoc spec :vao vao)))

(defn draw
  "Bind the spec's VAO and issue glDrawArrays over its vertices."
  [spec]
  (gl/gl-bind-vertex-array (:vao spec))
  (gl/gl-draw-arrays (:mode spec) 0 (:num-vertices spec))
  spec)

(defn draw-with-shader
  "Draw `spec` with its shader + per-frame uniforms (port of
  thi.ng.geom.gl.core/draw-with-shader):
    {:shader <compiled> :uniforms {..} :vao id :num-vertices n :mode gl/..}
  Uses the program, uploads the uniforms, binds the VAO and draws. Clearing and
  depth-test enable are the caller's job. Needs a current GL context."
  [spec]
  (gl/gl-use-program (:program (:shader spec)))
  (shader/set-uniforms! (:shader spec) (:uniforms spec))
  (draw spec)
  spec)
