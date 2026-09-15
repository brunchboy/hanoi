(ns base-case
  (:require [board]
            [nav]))

(defn animate-move
  "Animates the move for the base case."
  []
  (board/animate-ring-move 'A 'B))

(board/reset-board 1)

(reset! nav/max-step 2)
(reset! nav/step-functions [nil animate-move])
(reset! nav/step-highlights [nil "code"])

(nav/run-once-loaded nav/show-step-ui)
