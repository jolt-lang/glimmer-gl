# geom-gl

Composable geometry and matrix math for Jolt (a Clojure-like Lisp on Chez
Scheme), with thin native OpenGL bindings — ported from
[thi.ng/geom](https://thi.ng/geom).

`geom-gl.vector` gives unboxed 3D vectors, `geom-gl.matrix` gives column-major
4×4 matrices (the layout `glUniformMatrix4fv` expects) with inlined flonum
arithmetic, and `geom-gl.gl` binds the slice of OpenGL you need to compile
shaders, fill buffers/VAOs, and upload uniforms. Compose a model-view-projection
with the matrix ops, flatten it with `->vec`, and hand it to GL.

## Usage

Build a model-view-projection and upload it:

```clojure
(require '[geom-gl.matrix :as m]
         '[geom-gl.gl :as gl]
         '[jolt.ffi :as ffi])

(let [model (m/mul (m/rotate-y 0.5) (m/scaling 1 1 1))
      view  (m/translation 0 0 -4)
      proj  (m/perspective 60.0 (/ 800.0 600.0) 0.1 100.0)
      mvp   (m/mul proj (m/mul view model))
      buf   (gl/write-floats (m/->vec mvp))]
  (gl/gl-uniform-matrix4fv loc 1 gl/GL-FALSE buf)
  (ffi/free buf))
```

Vector math:

```clojure
(require '[geom-gl.vector :as v])
(v/normalize (v/cross (v/vec3 1 0 0) (v/vec3 0 1 0)))
;; => the +Z axis
```

The GL calls are thin `defcfn` wrappers over the host OpenGL library and need a
**current GL context** to do anything. `gl/make-program` compiles and links a
vertex/fragment pair, printing the info log on failure.

## Install

Local dep (this is how the sibling `gl-demo` project uses it):

```edn
geom-gl/geom-gl {:local/root "../geom-gl"}
```

Jolt also supports git deps (`{:git/url ... :git/sha ...}` with a full sha).

OpenGL is linked via the `:jolt/native` entry in geom-gl's `deps.edn` and merges
into any app that depends on it — no extra setup on the consumer side. macOS
OpenGL (framework) and Linux `libGL` are both covered.

## Run tests

```sh
joltc -M:test
```

## Modules

- `geom-gl.vector` — `Vec3`: `add`, `sub`, `scale`, `dot`, `cross`,
  `magnitude`, `normalize`, `dist`, `dist-squared`, plus `->vec`.
- `geom-gl.matrix` — `Matrix44` (column-major): `mul`, `add`, `sub`,
  `transpose`, `determinant`, `invert`, and constructors `ident`,
  `translation`, `scaling`, `perspective`, `rotate-x`, `rotate-y`, `rotate-z`.
  `->vec` flattens to 16 doubles in GL upload order.
- `geom-gl.gl` — OpenGL bindings: shaders/programs (`make-shader`,
  `make-program`), buffers and VAOs, uniforms (`gl-uniform-matrix4fv`,
  `gl-uniform-1f`, `gl-uniform-3f`, …), `write-floats`, and the `GL_*` enum
  constants.

## License

Apache License 2.0. The matrix arithmetic, cofactor inversion, and constructor
formulas are derived from thi.ng/geom; see `NOTICE`.
