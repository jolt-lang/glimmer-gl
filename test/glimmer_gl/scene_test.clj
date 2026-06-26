(ns glimmer-gl.scene-test
  (:require [clojure.test :refer [deftest is testing]]
            [glimmer.ratom :as r]
            [glimmer-gl.matrix :as m]
            [glimmer-gl.scene :as scene]))

;; The scene module is the GL analogue of glimmer's widget tree: a declarative
;; hiccup tree of groups/meshes/lights/camera that a pure compiler flattens into
;; a render plan (threaded world matrices, collected lights, single camera).
;; Like glimmer, content is data; interpretation is separate.

(defn tx [mat] (mapv double (subvec (m/->vec mat) 12 15)))

(deftest single-mesh-gets-identity-world
  (let [plan (scene/flatten [:mesh {:geom ::cube :material :stone}])]
    (is (= 1 (count (:items plan))))
    (let [item (first (:items plan))]
      (is (= ::cube (:geom item)))
      (is (= :stone (:material item)))
      (is (true? (:cast-shadow item)))                 ; default on
      (is (= [0.0 0.0 0.0] (tx (:world item)))))))     ; identity transform

(deftest group-transform-threads-to-children
  (let [plan (scene/flatten
               [:group {:transform (m/translation 1 2 3)}
                [:mesh {:geom ::cube :material :stone}]])]
    (is (= [1.0 2.0 3.0] (tx (:world (first (:items plan))))))))

(deftest nested-groups-compose-matrices
  ;; root translates by (1,0,0), child by (0,2,0): mesh world = translate(1,2,0).
  (let [plan (scene/flatten
               [:group {:transform (m/translation 1 0 0)}
                [:group {:transform (m/translation 0 2 0)}
                 [:mesh {:geom ::cube :material :stone}]]])]
    (is (= [1.0 2.0 0.0] (tx (:world (first (:items plan))))))))

(deftest multiple-meshes-collect-in-order
  (let [plan (scene/flatten
               [:group {:transform (m/ident)}
                [:mesh {:geom ::a :material :stone}]
                [:mesh {:geom ::b :material :metal :cast-shadow false}]
                [:mesh {:geom ::c :material :stone}]])]
    (is (= [::a ::b ::c] (map :geom (:items plan))))
    (is (= [true false true] (map :cast-shadow (:items plan))))))

(deftest camera-and-lights-extracted
  (let [plan (scene/flatten
               [:camera {:eye [0 0 10] :target [0 0 0] :up [0 1 0]
                         :fov 50 :near 0.1 :far 100}]
               ;; camera may be wrapped or standalone; also test lights
               )]
    (is (= {:eye [0 0 10] :target [0 0 0] :up [0 1 0]
            :fov 50 :near 0.1 :far 100}
           (:camera plan)))))

(deftest lights-collect-from-anywhere-in-tree
  (let [plan (scene/flatten
               [:group {:transform (m/ident)}
                [:light {:dir [1 0 0] :color [1 1 1]}]
                [:group {:transform (m/ident)}
                 [:light {:dir [0 -1 0] :color [0.2 0.2 0.4]}]
                 [:mesh {:geom ::cube :material :stone}]]])]
    (is (= 2 (count (:lights plan))))
    (is (= [[1 0 0] [0 -1 0]] (map :dir (:lights plan))))))

(deftest empty-plan-has-no-items
  (is (empty? (:items (scene/flatten [:group {:transform (m/ident)}])))))

(deftest group-constructor-keeps-vector-children-whole
  ;; the constructor must `into` children, not `list*` them: list* splices a
  ;; seqable final child (every child node is a vector) into siblings, which
  ;; corrupts the tree. A two-child group must flatten to two items, in order.
  (let [node (scene/group (m/ident)
               [:mesh {:geom ::a :material :stone}]
               [:mesh {:geom ::b :material :stone}])
        items (:items (scene/flatten node))]
    (is (= [::a ::b] (map :geom items)))))

;; --- component expansion ([fn args...] -> native hiccup) ---------------------
;; A component is a fn returning hiccup; [box :stone] is a reagent-style
;; component invocation that expands to the hiccup (box :stone) returns. This is
;; what lets a scene be authored exactly like reagent web UI.

(deftest component-invocation-expands-to-native-hiccup
  (let [box (fn [material] [:mesh {:geom ::cube :material material}])
        items (:items (scene/flatten (scene/expand [box :stone])))]
    (is (= [::cube] (map :geom items)))
    (is (= [:stone] (map :material items)))))

(deftest nested-components-compose
  ;; a component may itself return a tree containing more component invocations;
  ;; expansion recurses until only native nodes remain, and group transforms
  ;; still thread through to the leaf mesh.
  (let [cube (fn [mat] [:mesh {:geom ::c :material mat}])
        box  (fn [mat] [:group {:transform (m/translation 1 0 0)} [cube mat]])]
    (let [items (:items (scene/flatten (scene/expand [box :stone])))
          item  (first items)]
      (is (= [::c] (map :geom items)))
      (is (= [1.0 0.0 0.0] (tx (:world item)))))))

(deftest seq-children-splice-into-multiple-nodes
  ;; (for ...) yields one node per item; a bare vector is a single child.
  (let [p (scene/flatten
            (scene/expand
              [:group {:transform (m/ident)}
               (for [i [::a ::b ::c]] [:mesh {:geom i :material :stone}])]))]
    (is (= [::a ::b ::c] (map :geom (:items p))))))

(deftest nil-children-are-dropped
  (let [p (scene/flatten
            (scene/expand
              [:group {:transform (m/ident)}
               [:mesh {:geom ::a :material :stone}]
               nil
               (when false [:mesh {:geom ::x :material :stone}])
               [:mesh {:geom ::b :material :stone}]]))]
    (is (= [::a ::b] (map :geom (:items p))))))

;; --- reactive plan -----------------------------------------------------------
;; scene/plan is a reaction over the scene; atoms dereffed while a component
;; expands register the plan as a watcher, so resetting a cell recomputes the
;; plan — exactly reagent's model applied to a 3D scene.

(deftest plan-recomputes-when-a-cell-changes
  (let [xform (r/atom (m/ident))
        box   (fn [xa] [:group {:transform @xa}
                       [:mesh {:geom ::cube :material :stone}]])
        p     (scene/plan (fn [] [box xform]))]
    (is (= [0.0 0.0 0.0] (tx (:world (first (:items @p))))))
    (r/reset! xform (m/translation 5 0 0))
    (is (= [5.0 0.0 0.0] (tx (:world (first (:items @p))))))))

(deftest plan-reflects-the-latest-of-many-changes
  (let [mat  (r/atom :stone)
        cube (fn [ma] [:mesh {:geom ::c :material @ma}])
        p    (scene/plan (fn [] [cube mat]))]
    (is (= [:stone] (map :material (:items @p))))
    (r/reset! mat :metal)
    (r/reset! mat :glass)
    (is (= [:glass] (map :material (:items @p))))))
