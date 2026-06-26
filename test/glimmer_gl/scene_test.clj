(ns glimmer-gl.scene-test
  (:require [clojure.test :refer [deftest is testing]]
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
