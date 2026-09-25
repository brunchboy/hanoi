(ns comparison
  (:require [board]
            [clojure.string :as str]
            [nav]))

(def num-rings
  "Holds the number of rings on the board, so we can know what
  direction the smallest ring needs to rotate."
  (atom 0))

(defn solve
  "Helper function to quickly get to a solved state."
  []
  (let [num       (parse-long (.-value (.getElementById js/document "num-rings")))
        recursive (.getElementById js/document "recursiveMoves")
        iterative (.getElementById js/document "iterativeMoves")]
    (board/reset-board num)
    (set! (.-innerHTML recursive) (board/hanoi-recursive-fast num 'A 'B))
    (board/reset-board num)
    (set! (.-innerHTML iterative) (board/hanoi-nonrecursive-fast num))
    (dotimes [i (.-childElementCount recursive)]
      (let [r-move (.item (.-children recursive) i)
            i-move (.item (.-children iterative) i)
            i-html (.-innerHTML i-move)]
        (if (= (.-innerHTML r-move) i-html)
          (set! (.-innerHTML i-move) (str i-html "<span class=\"match\"> ✓</span>"))
          (set! (.-innerHTML i-move) (str i-html "<span class=\"fail\"> ×</span>")))))))

(set! (.-solve js/window) solve)

(def show-rings
  "Indicates whether ring identities should be displayed in the moves
  list."
  (atom false))

(defn toggle-rings-in-moves
  "Helper function to show or hide the ring identities in the moves
  list."
  []
  (let [show (swap! show-rings not)]
    (doseq [style-sheet (.-styleSheets js/document)
            rule        (.-cssRules style-sheet)]
      (when (= (str/lower-case (.-selectorText rule)) ".scrollablemoves li .ring-moved")
        (set! (.-display (.-style rule)) (if show "inline" "none"))))))

(set! (.-toggleRings js/window) toggle-rings-in-moves)
(toggle-rings-in-moves)
(solve)
