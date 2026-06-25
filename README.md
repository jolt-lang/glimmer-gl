# glimmer-gl

An extension of [glimmer](../glimmer) for working with OpenGL and 3D geometry on
Jolt (a Clojure-like Lisp on Chez Scheme). It does two things:

- **Composable geometry**, ported from [thi.ng/geom](https://thi.ng/geom): build
  3D solids as plain Clojure data, transform and combine them, and tessellate to
  a GL-ready vertex buffer. Vectors, column-major 4×4 matrices, a mesh model, and
  primitive constructors.
- **Shaders as data** (`glimmer-gl.shader`), ported from thi.ng/geom's
  shader-spec model: declare a shader's interface (uniforms, attributes,
  varyings, version) as maps and compose its stages from reusable GLSL snippets;
  the declarations are generated and only emitted as a GLSL string when you
  compile.
- **GL widgets for glimmer**: requiring `glimmer-gl.gtk` registers a `:gl-area`
  (a GtkGLArea drawing surface) and `:scale` (a slider) into glimmer's widget
  registry, so a GL pane lives in the same reactive hiccup tree as the rest of
  your UI.

`glimmer-gl.vector` gives unboxed 3D vectors, `glimmer-gl.matrix` gives
column-major 4×4 matrices (the layout `glUniformMatrix4fv` expects) with inlined
flonum arithmetic, `glimmer-gl.gl` binds the slice of OpenGL you need to compile
shaders and fill buffers/VAOs/uniforms, and `glimmer-gl.mesh` /
`glimmer-gl.primitives` are the thi.ng/geom-style composition layer.

## Geometry

A mesh is just a vector of faces; each face is a vector of CCW-wound Vec3
vertices, so its normal points outward. Build primitives, compose them, then ask
for the interleaved position+normal buffer:

```clojure
(require '[glimmer-gl.primitives :as p]
         '[glimmer-gl.mesh :as mesh])

;; a cube next to a sphere, the sphere smoothed once
(def scene
  (mesh/merge-meshes
    (p/cuboid 1.0)
    (-> (p/sphere 1.0 24 16) (mesh/subdivide) (mesh/translate 2 0 0))))

(mesh/->floats scene {:shading :smooth})
;; => {:data (x y z nx ny nz …) :count <vertex count> :stride 6}
```

- **Primitives** (`glimmer-gl.primitives`): `cuboid`, `tetrahedron`, `sphere`,
  `plane`/`quad` — vertex layouts and face windings taken straight from
  thi.ng/geom.
- **Mesh ops** (`glimmer-gl.mesh`): `transform` (through a Matrix44),
  `translate`, `scale`, `merge-meshes`, `subdivide` (midpoint, ×4 per triangle),
  `face-normal`, `vertex-normals` (smooth), `triangles`, and `->floats` (flat or
  smooth shading).

Feed `(:data …)` to `gl/write-floats` and upload it as a VBO; wind a
model-view-projection from the matrix ops and `->vec` it into a uniform.

## Shaders as data

A shader is a map declaring its interface; the bodies are GLSL, and `:vs`/`:fs`
may be a vector of snippets that compose into one stage. The `uniform` / `in` /
`out` declarations are generated, so you manipulate shaders with plain map ops
and only emit GLSL when you compile.

```clojure
(require '[glimmer-gl.shader :as sh])

(def spec
  {:version  "330 core"
   :uniforms {:u_mvp :mat4, :u_time [:float 0.0]}     ; type, or [type default]
   :attribs  {:a_pos [:vec3 0], :a_normal [:vec3 1]}  ; [type location]
   :varying  {:v_normal :vec3}
   :vs "void main(){ v_normal = a_normal; gl_Position = u_mvp*vec4(a_pos,1.0); }"
   :fs ["vec3 palette(float t){ return vec3(t); }"     ; compose stages from
        "out vec4 frag;"                                ; reusable snippets
        "void main(){ frag = vec4(palette(u_time), 1.0); }"]})

(sh/sources spec)   ; => {:vs-src "#version 330 core\n…" :fs-src "…"}  (pure, no GL)

;; with a current GL context:
(let [prog (sh/program spec)]              ; compiles, links, locates uniforms/attribs
  (sh/set-uniforms! prog {:u_mvp mvp       ; set by name; :mat4 takes a Matrix44
                          :u_time t}))
```

Compose and manipulate with ordinary data: `(assoc-in spec [:uniforms :u_warp] :float)`,
`(update spec :fs conj extra-snippet)`, `(merge base overrides)`.

For bigger composition, build a shader from reusable **modules** — each a map
carrying its own uniforms plus the GLSL function that uses them — and combine
them with `merge-specs`:

```clojure
(def plasma-module  {:uniforms {:u_scale :float} :fs ["vec3 plasma_color(vec3 p){…}"]})
(def stripes-module {:uniforms {:u_stripes :float} :fs ["vec3 stripe_color(vec3 p){…}"]})
(def main-module    {:uniforms {:u_mix :float}
                     :fs ["out vec4 frag;"
                          "void main(){ frag = vec4(mix(plasma_color(v_obj),
                                                        stripe_color(v_obj), u_mix), 1.0); }"]})

(sh/merge-specs base plasma-module stripes-module main-module)
;; uniforms merge; snippet lists concatenate in order, so each module's
;; functions land ahead of the main() that calls them.
```

See `gl-demo.scene` in the demo for the plasma + stripes shader built this way.

## GL widgets in a glimmer UI

```clojure
(require '[glimmer.core :as ui]
         '[glimmer-gl.gtk])    ; registers :gl-area and :scale

(defn app []
  [:vbox
   [:scale {:min 0 :max 1 :step 0.01 :value 0.5 :on-value #(reset! state %)}]
   [:gl-area {:version [3 2] :depth-buffer true :hexpand true :vexpand true
              :on-realize (fn [area] …)   ; build GL objects (context is current)
              :on-render  (fn [area] …)   ; issue draw calls
              :on-resize  (fn [area w h] …)
              :on-tick    (fn [area] …)}]]) ; per-frame; auto queues a redraw

(ui/run app)
```

The GLArea's realize/render/resize signals don't fit glimmer's uniform
`void(widget, data)` handler shape (render returns a gboolean, resize carries
width/height), so `glimmer-gl.gtk` wires them directly via the widget spec's
`:connect` hook — the extension point glimmer exposes through `register-widget!`.
The `:scale` slider's `value-changed` signal *does* fit, so it is added with
`register-signal!` and needs no special wiring.

See `../examples/glimmer-gl-app` for a complete demo: a rotating, diffuse-lit
cube/sphere/tetrahedron driven by a reactive control panel.

## Install

Local dep (this is how the sibling demo uses it):

```edn
jolt-lang/glimmer-gl {:local/root "../../glimmer-gl"}
```

glimmer-gl depends on glimmer (it extends it). OpenGL is linked via the
`:jolt/native` entry in glimmer-gl's `deps.edn`, and glimmer's GTK4/GLib libs
merge in through the dependency walk — no extra setup on the consumer side. macOS
OpenGL (framework) and Linux `libGL` are both covered. Jolt also supports git
deps (`{:git/url … :git/sha …}` with a full sha).

## Run tests

```sh
joltc -M:test
```

The vector / matrix / mesh / primitives suites are pure and run headlessly; the
GL binding test only dlopens the library and checks symbols resolve.

## Modules

- `glimmer-gl.vector` — `Vec3`: `add`, `sub`, `scale`, `dot`, `cross`,
  `magnitude`, `normalize`, `dist`, `dist-squared`, `mix`, `centroid`, `->vec`.
- `glimmer-gl.matrix` — `Matrix44` (column-major): `mul`, `add`, `sub`,
  `transpose`, `determinant`, `invert`, `transform-point`, and constructors
  `ident`, `translation`, `scaling`, `perspective`, `rotate-x/y/z`. `->vec`
  flattens to 16 doubles in GL upload order.
- `glimmer-gl.mesh` — mesh-as-data: `mesh`, `add-face`, `faces`, `transform`,
  `translate`, `scale`, `merge-meshes`, `subdivide`, `tessellate-face`,
  `triangles`, `face-normal`, `vertex-normals`, `->floats`.
- `glimmer-gl.primitives` — `cuboid`, `tetrahedron`, `sphere`, `plane`, `quad`.
- `glimmer-gl.shader` — shaders as data: `sources` (spec → GLSL strings),
  `program` (compile/link + locate uniforms/attribs), `set-uniform!` /
  `set-uniforms!` (by name), `merge-specs` (compose shader modules),
  `glsl-vars` / `glsl-attribs`.
- `glimmer-gl.gl` — OpenGL bindings: shaders/programs (`make-shader`,
  `make-program`), buffers and VAOs, uniforms, `write-floats`, `GL_*` constants.
- `glimmer-gl.gtk` — the glimmer widget extension: `:gl-area` and `:scale`.

## License

Apache License 2.0. The vector/matrix arithmetic, mesh model, tessellation,
primitive constructors, and shader-spec model are derived from thi.ng/geom; see
`NOTICE`.
