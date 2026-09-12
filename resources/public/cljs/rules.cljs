(ns rules
  (:require [board]
            [nav]))

(defn animate-goal
  "Moves the whole tower without showing how."
  []
  (board/animate-tower-teleportation 'A 'B 10))

(defn animate-legal-moves
  "Moves a couple rings in legal ways."
  []
  (board/reset-board 10)
  (board/animate-ring-move 'A 'B #(board/animate-ring-move 'A 'C)))

(defn mark-b-wrong
  "Draws a red X over the illegally-stacked rings."
  []
  (let [canvas (.getElementById js/document "state")
        ctx    (.getContext canvas "2d")
        w      (.-width canvas)
        h      (.-height canvas)
        x      (* w (board/post-positions 1))
        y      (board/ring-y h 2)]
    (.save ctx)
    (set! (.-strokeStyle ctx) "red")
    (set! (.-lineWidth ctx) 5.0)
    (.beginPath ctx)
    (.moveTo ctx (- x 15) (- y 15))
    (.lineTo ctx (+ x 15) (+ y 15))
    (.moveTo ctx (- x 15) (+ y 15))
    (.lineTo ctx (+ x 15) (- y 15))
    (.stroke ctx)
    (.restore ctx)))

(defn animate-illegal-move
  "Moves a ring in a prohibited way and marks it as wrong."
  []
  (board/animate-ring-move 'A 'B mark-b-wrong))

(board/reset-board 10)

(reset! nav/max-step 4)
(reset! nav/step-highlights [nil "goalStep" "oneRingStep" "sizeStep"])
(reset! nav/step-functions [nil animate-goal animate-legal-moves animate-illegal-move])
