(ns glimmer-gl.test-runner
  "Entry point for `joltc -M:test`. Requires each glimmer-gl test namespace and
  runs clojure.test against it. Exits non-zero on any failure."
  (:require [clojure.test :as t]))

(defmethod t/report :error [m]
  (t/with-test-out
    (t/inc-report-counter :error)
    (println "\nERROR in" (t/testing-vars-str m))
    (when (seq t/*testing-contexts*) (println (t/testing-contexts-str)))
    (when-let [message (:message m)] (println message))
    (when-let [e (:actual m)]
      (if (instance? Throwable e)
        (do (println "  ->" (.getName (class e)) ":" (ex-message e))
            (when-let [d (ex-data e)] (prn d))
            (when-let [c (ex-cause e)]
              (println "  caused by:" (.getName (class c)) ":" (ex-message c))))
        (prn e)))))

(defn -main [& _]
   (let [namespaces '[glimmer-gl.matrix-test glimmer-gl.vector-test glimmer-gl.vec2-test glimmer-gl.gl-test
                      glimmer-gl.mesh-test glimmer-gl.primitives-test
                      glimmer-gl.shader-test glimmer-gl.rect-test glimmer-gl.circle-test
                      glimmer-gl.aabb-test glimmer-gl.sphere-test glimmer-gl.line-test
                      glimmer-gl.plane-test glimmer-gl.quaternion-test
                      glimmer-gl.triangle-test glimmer-gl.polyhedra-test
                      glimmer-gl.bezier-test glimmer-gl.polygon-test
                      glimmer-gl.intersect-test glimmer-gl.scene-test
                       glimmer-gl.glmesh-test glimmer-gl.offscreen-test]]
    ;; A namespace that fails to load registers no tests, so run-tests alone
    ;; would report it as a clean run; count it as a failure.
    (let [load-errors (count (for [ns namespaces
                                   :let [e (try (require ns :reload) nil
                                                (catch Throwable e e))]
                                   :when e]
                               (println "ERROR requiring" ns ":" (pr-str e))))
          results (apply t/run-tests namespaces)
          failed (+ (:fail results 0) (:error results 0) load-errors)]
      (println "----")
      (println "tests:" (:test results 0)
               "assertions:" (:pass results 0) "passed /"
               failed "failed")
      (when (pos? failed) (System/exit 1)))))
