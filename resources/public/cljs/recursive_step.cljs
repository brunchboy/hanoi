(ns recursive-step
  (:require [board]
            [nav]))

(defn animate-recursive-aside
  "Animates moving the smaller tower out of the way."
  []
  (board/animate-tower-teleportation 'A 'C 9))

(defn animate-move
  "Amimates the single ring move."
  []
  (board/animate-ring-move 'A 'B))

(defn animate-recursive-finale
  "Animates moving the smaller tower to the target post."
  []
  (board/animate-tower-teleportation 'C 'B 9))

(board/reset-board 10)

(reset! nav/max-step 4)
(reset! nav/step-functions [nil animate-recursive-aside animate-move animate-recursive-finale])
(reset! nav/step-highlights [nil "aside" "move" "finale"])

(nav/run-once-loaded nav/show-step-ui)
