# glimmer-gl

An extension of [glimmer](https://github.com/jolt-lang/glimmer) for working with
OpenGL and 3D geometry on
Jolt (a Clojure-like Lisp on Chez Scheme). It does two things:

- **Composable geometry**, ported from [thi.ng/geom](https://thi.ng/geom): build
  3D solids as plain Clojure data, transform and combine them, and tessellate to
  a GL-ready vertex buffer. Vectors, column-major 4×4 matrices, a mesh model, and
  primitive constructors.
- **Shaders as data** (`glimmer-gl.shader`), ported from thi.ng/geom's
  shader-spec model: declare a shader's interface (uniforms, attributes,
  varyings, version) as maps and write the stage bodies as data statements;
  the declarations and bodies are only emitted as GLSL when you compile.
- **GL widgets for glimmer**: requiring `glimmer-gl.gtk` installs the
  [glimmer-gtk](https://github.com/jolt-lang/glimmer-gtk) backend and registers a
  `:gl-area` (a GtkGLArea drawing surface) and `:scale` (a slider) into its widget
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

A shader is a map declaring its interface plus its `main()` bodies. The
`uniform` / `in` / `out` declarations are generated from the maps, and the
bodies (`:vs-main` / `:fs-main`) are data too: vectors of statements built from
expression nodes, compiled to GLSL by `compile-stmt` / `compile-expr`. Helper
functions that the IR can't express go in `:prelude` as a GLSL string. You
manipulate shaders with plain map ops and only emit GLSL when you compile.

```clojure
(require '[glimmer-gl.shader :as sh])

(def spec
  {:version  "330 core"
   :uniforms {:u_mvp :mat4, :u_time [:float 0.0]}     ; type, or [type default]
   :attribs  {:a_pos [:vec3 0], :a_normal [:vec3 1]}  ; [type location]
   :varying  {:v_normal :vec3}                        ; out in vs, in in fs
   :fs-out   {:frag :vec4}                            ; fragment outputs
   :prelude  "vec3 palette(float t){ return vec3(t); }\n"
   :vs-main  [[:set :v_normal :a_normal]
              [:set :gl_Position [:* :u_mvp [:vec4 :a_pos 1.0]]]]
   :fs-main  [[:let :n :vec3 [:normalize :v_normal]]
              [:set :frag [:vec4 [:* [:palette :u_time] [:. :n :z]] 1.0]]]})

(sh/sources spec)   ; => {:vs-src "#version 330 core\n…" :fs-src "…"}  (pure, no GL)

;; with a current GL context:
(let [prog (sh/program spec)]              ; compiles, links, locates uniforms/attribs
  (sh/set-uniforms! prog {:u_mvp mvp       ; set by name; :mat4 takes a Matrix44
                          :u_time t}))
```

Statements are `[:let name type expr]`, `[:set target expr]` and
`[:if cond [then…] [else…]]`; expressions are keywords (names), numbers,
`[:. x :xyz]` swizzles, `[:neg x]`, infix ops (`:+ :- :* :/`, comparisons,
`:and`/`:or`), and any other `[:fn arg …]` as a call or constructor. `sources`
throws on a key it doesn't know (so a typo such as `:uniform`, or GLSL strings
under `:vs`/`:fs`, fails instead of compiling to an empty `main()`); namespaced
keys are ignored.

Compose and manipulate with ordinary data: `(assoc-in spec [:uniforms :u_warp] :float)`,
`(update spec :fs-main conj [:set :frag [:vec4 1.0]])`, `(merge base overrides)`.

For bigger composition, build a shader from reusable **modules** — each a map
carrying its own uniforms plus the GLSL helper (in `:prelude`) or statements that
use them — and combine them with `merge-specs`:

```clojure
(def plasma-module  {:uniforms {:u_scale :float} :prelude "vec3 plasma_color(vec3 p){…}\n"})
(def stripes-module {:uniforms {:u_stripes :float} :prelude "vec3 stripe_color(vec3 p){…}\n"})
(def main-module    {:uniforms {:u_mix :float}
                     :fs-out   {:frag :vec4}
                     :fs-main  [[:let :col :vec3 [:mix [:plasma_color :v_obj]
                                                      [:stripe_color :v_obj] :u_mix]]
                                [:set :frag [:vec4 :col 1.0]]]})

(sh/merge-specs base plasma-module stripes-module main-module)
;; uniforms merge; preludes and statement vectors concatenate in order, so each
;; module's helpers land ahead of the main() that calls them.
```

See `gl-demo.scene` in the demo for the plasma + stripes shader built this way.

## GL widgets in a glimmer UI

```clojure
(require '[glimmer.core :as ui]
         '[glimmer-gl.gtk])    ; installs the GTK4 backend, registers :gl-area and :scale

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

The GLArea's realize/render/resize signals don't fit glimmer-gtk's uniform
`void(widget, data)` handler shape (render returns a gboolean, resize carries
width/height), so `glimmer-gl.gtk` wires them directly via the widget spec's
`:connect` hook — the extension point glimmer-gtk exposes through
`register-widget!`.
The `:scale` slider's `value-changed` signal *does* fit, so it is added with
`register-signal!` and needs no special wiring.

See `../examples/glimmer-gl-app` for a complete demo: a rotating, diffuse-lit
cube/sphere/tetrahedron driven by a reactive control panel.

## GL without a window

A GLArea only has a context once it's realized, so GL code normally can't run
outside a running app — which makes render-to-texture passes and other
compute-style GPU work untestable. `glimmer-gl.offscreen` asks GDK for a context
bound to the display rather than to a surface:

```clojure
(require '[glimmer-gl.offscreen :as off])

(let [ctx (off/ensure-current!)]
  (if-let [err (:error ctx)]
    (println "no offscreen GL:" err)   ; no display, or GL too old — skip
    (do (compile-and-run-passes!) …)))
```

One context per process, created on first call and made current on every call;
a failure is cached, so a headless run pays the probe once. Call it from the
main thread — GDK contexts have the same thread affinity as the rest of GTK.

There is no default framebuffer, so framebuffer 0 is incomplete and every draw
must target an FBO. Compute passes already do that, but it does mean a `glClear`
against framebuffer 0 quietly does nothing instead of clearing.

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
jolt -M:test
```

The vector / matrix / mesh / primitives suites are pure and run headlessly; the
GL binding test only dlopens the library and checks symbols resolve. The
offscreen test does run real GL — it renders a pass into a float texture and
reads it back — and skips with a printed reason when no display is available.

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
  `compile-stmt` / `compile-expr` (the body IR), `glsl-vars` / `glsl-attribs`.
- `glimmer-gl.gl` — OpenGL bindings: shaders/programs (`make-shader`,
  `make-program`), buffers and VAOs, uniforms, `write-floats`, `GL_*` constants.
- `glimmer-gl.gtk` — the glimmer widget extension: `:gl-area` and `:scale`.
- `glimmer-gl.offscreen` — a current GL context with no window, for headless
  render-to-texture work: `ensure-current!`, `available?`, `release!`.

## License

MIT (see `LICENSE`). The vector/matrix arithmetic, mesh model, tessellation,
primitive constructors, and shader-spec model are derived from thi.ng/geom and
remain licensed under the Apache License 2.0; see `NOTICE`.
