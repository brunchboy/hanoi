(ns non-recursive
  (:require [board]
            [clojure.string :as str]
            [nav]))

(def fade-ms
  "How many milliseconds to wait for values to fade in or out when
  animating steps."
  (atom 500))  ;; TODO, when changing this, change CSS to match; maybe tie with code?

(def auto-step
  "Controls whether the next step is automatically run when one ends."
  (atom false))

(def num-rings
  "Holds the number of rings on the board, so we can know what
  direction the smallest ring needs to rotate."
  (atom 0))

(declare run-top-task)

(defn done?
  "Checks whether we have reached the goal state, where all rings are on
  post B."
  []
  (empty? (concat (first @board/board) (first (rest (rest @board/board))))))

(defn update-step-button
  "Makes sure the Step button is enabled if there are tasks in the
  stack, and disabled otherwise. If auto-step is true, run the next
  task."
  []
  (let [button (.getElementById js/document "step-button")
        done   (done?)]
    (set! (.-disabled button) done)
    (when (and @auto-step (not done)) (run-top-task))))

(defn show-direction
  "Indicate which direction the smallest ring rotates, given the number
  of rings in the tower."
  []
  (let [odd-elem (.getElementById js/document "odd-rings")
        even-elem (.getElementById js/document "even-rings")]
    (set! (.-className odd-elem) (if (odd? @num-rings) "rotation-chosen" ""))
    (set! (.-className even-elem) (if (even? @num-rings) "rotation-chosen" ""))))

(def post-symbol
  "Maps from post index to the symbol by which we refer to that post."
  {0 'A
   1 'B
   2 'C})

(defn post-index-of-ring
  "Given the number of a ring we know is at the top of a post, returns
  the index of the post it is on."
  [n]
  (let [top-rings (map first @board/board)]
    (second (first (drop-while (fn [[top _]] (not= top n))
                               (partition 2 (interleave top-rings (range))))))))

(defn animate-and-record-move
  "Given a pair of post indices, animate moving the top ring as
  indicated, and add it to the move list. Finish by updating the state
  of the step button."
  [from-index to-index ring]
  (let [moves (.getElementById js/document "moves")
        from  (post-symbol from-index)
        to    (post-symbol to-index)]
    (.insertAdjacentHTML moves "beforeend"
                         (str "<li><span class=\"ring-moved\"><span style=\"color:" (get board/ring-colors ring)
                              "\">Ring " (inc ring) ": </span></span>" from " → " to "</li>"))
    (board/animate-ring-move from to (update-step-button))))

(defn rotate-smallest-ring
  "Animate rotating the smallest in the direction needed to solve the
  puzzle."
  []
  (let [from-index (post-index-of-ring 0)
        to-index   (mod (+ from-index (if (odd? @num-rings)  1 -1)) 3)]
    (animate-and-record-move from-index to-index 0)))

(defn make-other-legal-move
  "Make (and animate) the only legal move that does not involve the
  smallest ring."
  []
  (update-step-button)
  (let [other-rings (sort (filter pos? (filter identity (map first @board/board))))
        from-index  (post-index-of-ring (first other-rings))
        to-index    (if (= 1 (count other-rings)) (post-index-of-ring nil) (post-index-of-ring (second other-rings)))]
    (animate-and-record-move from-index to-index (first other-rings))))

(defn next-algorithm-step
  []
  (set! (.-disabled (.getElementById js/document "step-button")) true)
  (nav/set-step (inc @nav/step))
  (let [step (+ (mod (- @nav/step 2) 3) 2)]
    (doseq [i (range 2 5)]
      (set! (.-className (.getElementById js/document (str "step-" i))) (if (= i step) "stepHighlight" "")))
    (case step
      2 (rotate-smallest-ring)
      3 (if (done?)
          (set! (.-className (.getElementById js/document "step-3")) "rotation-chosen")
          (update-step-button))
      4 (make-other-legal-move))))

(set! (.-nextStep js/window) next-algorithm-step)

(defn reset-board
  "Helper function to reset the board and draw its initial state."
  []
  (let [num   (parse-long (.-value (.getElementById js/document "num-rings")))
        moves (.getElementById js/document "moves")]
    (board/reset-board num)
    (set! (.-innerHTML moves) "")
    (reset! num-rings num)
    (show-direction))
  (nav/set-step 1)
  (update-step-button))

(set! (.-resetBoard js/window) reset-board)

(defn hanoi-fast
  "Performs a non-animated solution, returning the formatted move list."
  [num-rings from to]
  (str
   (when (> num-rings 1) (hanoi-fast (dec num-rings) from (board/other-post from to)))
   (let [ring (board/move-ring (board/post-index from) (board/post-index to))]
     (board/format-move from to ring))
   (when (> num-rings 1) (hanoi-fast (dec num-rings) (board/other-post from to) to))))

(defn solve
  "Helper function to quickly get to a solved state."
  []
  (let [num   (parse-long (.-value (.getElementById js/document "num-rings")))
        moves (.getElementById js/document "moves")]
    (board/reset-board num)
    (set! (.-innerHTML moves) (hanoi-fast num 'A 'B))
    (board/draw-current-board-state)
    (nav/set-step (* 3 (dec (.pow js/Math 2 num))))
    (update-step-button)))

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

;; Connect the speed slider and auto-step checkbox.
(let [slider (.getElementById js/document "fade-ms")]
  (.addEventListener slider "input"
                     (fn [event]
                       (let [val (parse-long (.-value (.-target event)))]
                         (reset! fade-ms val)
                         (reset! board/move-ms (* 2 val))))))
(let [checkbox (.getElementById js/document "auto-step")]
  (.addEventListener checkbox "input"
                     (fn [event]
                       (reset! auto-step (.-checked (.-target event))))))

;; Let the navigation interface know there are an indeterminate number of steps to run, which we control.
(reset! nav/max-step nil)
(nav/run-once-loaded nav/show-step-ui)
(nav/run-once-loaded reset-board)
