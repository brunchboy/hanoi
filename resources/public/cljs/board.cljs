(ns board
  (:require [clojure.set :as set]))

(defn rounded-rect
  "Creeates a path that is a rectangle with rounded corners of the
  specified radius."
  [ctx x y width height radius]
  (.beginPath ctx)
  (.moveTo ctx x (+ y radius))
  (.arcTo ctx x (+ y height) (+ x radius) (+ y height) radius)
  (.arcTo ctx (+ x width) (+ y height) (+ x width) (- (+ y height) radius) radius)
  (.arcTo ctx (+ x width) y (- (+ x width) radius) y radius)
  (.arcTo ctx x y x (+ y radius) radius))

(def post-positions
  "Specifies how far along the canvas each post appears."
  [0.2 0.5 0.8])

(def post-height
  "Specifies how far down the canvas the posts begin"
  0.35)

(def ring-colors
  "The colors of the rings, in order from smallest to largest."
  ["slateblue" "dodgerblue" "lightskyblue"
   "forestgreen" "limegreen" "yellowgreen"
   "yellow" "gold" "darkorange" "red"])

(def ring-height
  "The height of the rings."
  20)

(def small-ring
  "The width of the smallest ring."
  25)

(def large-ring
  "The width of the largest ring."
  100)

(defn ring-y
  "Figures out the height at which a ring should be drawn if it is nth
  from the bottom, given the height of the canvas."
  [h n]
  (- h 30 (* ring-height n)))

(defn draw-ring
  "Draws the specified ring at the specified coordinates in the supplied
  graphics context."
  [ctx ring x y]
  (.save ctx)
  (set! (.-lineCap ctx) "round")
  (set! (.-lineWidth ctx) ring-height)
  (let [scale  (/ ring 9.0)
        offset (+ small-ring (* (- large-ring small-ring) scale 0.5))]
    (.beginPath ctx)
    (set! (.-strokeStyle ctx) (get ring-colors ring))
    (.moveTo ctx (- x offset) y)
    (.lineTo ctx (+ x offset) y)
    (.stroke ctx)
    (.restore ctx)))

(defn draw
  "Draws the current board state, given the canvas and the contents of
  the posts (a list of lists of ring identifiers from 0 to 9."
  [canvas posts]
  (let [ctx (.getContext canvas "2d")
        w   (.-width canvas)
        h   (.-height canvas)]
    (.clearRect ctx 0 0 w h)

    ;; Draw the post labels
    (.save ctx)
    (set! (.-fillStyle ctx) "gray")
    (set! (.-font ctx) "24px sans-serif")
    (set! (.-textAlign ctx) "center")
    (dotimes [i 3]
      (let [x     (* w (get post-positions i))
            label (char (+ 65 i))]
        (.fillText ctx label x (- (* h post-height) 20))))

    ;; Draw the base
    (set! (.-fillStyle ctx) "tan")
    (rounded-rect ctx 10 (- h 40) (- w 20) 40 5)
    (.fill ctx)

    ;; Draw the posts
    (set! (.-strokeStyle ctx) "tan")
    (set! (.-lineWidth ctx) 15.0)
    (set! (.-lineCap ctx) "round")
    (.beginPath ctx)
    (doseq [x (map (partial * w) post-positions)]
      (.moveTo ctx x (* post-height h))
      (.lineTo ctx x (- h 20)))
    (.stroke ctx)

    ;; Draw the rings
    (doseq [[post x]      (map (fn [itm pos] [itm (* w pos)]) posts post-positions)
            [ring height] (map-indexed (fn [idx itm]
                                         [itm (ring-y h (- (count post) idx))])
                                       post)]
      (draw-ring ctx ring x height))

    (.restore ctx)))

(def board
  "Holds the state of the puzzle board, as a vector of three posts, each
  of which is a list of disc numbers from top to bottom."
  (atom []))

(def tasks
  "Holds the stack of tasks to be performed, as a list of
  s-expressions."
  (atom '()))

(def fade-ms
  "How many milliseconds to wait for values to fade in or out when
  animating steps."
  500)  ;; TODO, when changing this, change CSS to match; maybe tie with code?

(def move-ms
  "How many milliseconds to spend animating the movement of a disk
  from one post to another."
  (* 2 fade-ms))

(defn update-step-button
  "Makes sure the Step button is enabled if there are tasks in the
  stack, and disabled otherwise."
  []
  (let [button (.getElementById js/document "stepButton")]
    (set! (.-disabled button) (empty? @tasks))))

(defn format-tasks
  "Formats the task list as a series of list items on the page."
  []
  (set! (.-innerHTML (.getElementById js/document "tasks"))
        (reduce (fn [acc task] (str acc "<li>" task "</li>"))
                ""
                @tasks)))

(defn animate-tasks-in
  "The animation task which causes a set of expanded tasks to appear
  one-by-one in front of the expanding element, which then fades out.
  tasks-left holds the remaining tasks to be added, for recursive
  calls, while all-tasks retains the entire list, so they can be
  actually grafted into the atom once the animation completes. During
  the final stage, one last recursive call with a `nil` value for
  `all-tasks` actually removes the faded-out expanding element."
  [tasks-left all-tasks expanding-element]
  (if-let [task (first tasks-left)]
    (do
      (.insertAdjacentHTML expanding-element "beforebegin" (str "<li class=hidden>" task "</li>"))
      (.requestAnimationFrame js/window
                              (fn [_]
                                (set! (.-opacity (.-style (.-previousElementSibling expanding-element))) 1)
                                (.setTimeout js/window
                                             #(animate-tasks-in (rest tasks-left) all-tasks expanding-element)
                                             (quot fade-ms 5)))))
    (if all-tasks
      (do
        (swap! tasks #(apply concat all-tasks (list (rest %))))
        (set! (.-opacity (.-style expanding-element)) 0)
        (.setTimeout js/window
                     #(animate-tasks-in [] nil expanding-element)
                     fade-ms))
      (do (.remove expanding-element)
          (update-step-button)))))

(defn animate-task-starting
  "Changes the appearance of the top task to show that it is running,
  and return that DOM element for the use of later animations."
  []
  (let [tasks-element   (.getElementById js/document "tasks")
        children        (.-children tasks-element)
        running-element (.item children 0)]
    (set! (.-className running-element) "running")
    running-element))

(defn animate-top-expansion
  "Runs an animation that shows the top task is being replaced by a
  recursive expansion, then actually make that replacement in the atom
  and re-render the list from it."
  [expansion]
  (let [running-element (animate-task-starting)]
    (animate-tasks-in expansion expansion running-element)))

(defn other-post
  "Given a pair of posts, return the one that was not mentioned."
  [x y]
  (first (set/difference #{'A 'B 'C} (set [x y]))))

(defn expand-hanoi
  "Given a hanoi task, return its expansion, either a series of steps
  involving smaller hanoi tasks and a move, or for the base case of
  one disk, a single move."
  [n from to]
  (if (< n 2)
    [(list 'move from to)] ; We have reached the base case; terminate.
    [(list 'hanoi (dec n) from (other-post from to))
     (list 'move from to)
     (list 'hanoi (dec n) (other-post from to) to)]))

(def post-index
  "The map from post symbols to their indices within the board atom."
  {'A 0
   'B 1
   'C 2})

(defn draw-current-board-state
  "Renders the current state of the board atom into the canvas."
  []
  (draw (.getElementById js/document "state") @board))

(defn ramp
  "Calculates a shifted cosine ramp from the starting to ending value
  at the specified fraction of the path."
  [start end fraction]
  (let [scale (- 1.0 (js/Math.cos (* fraction js/Math.PI)))]
    (+ start (* (- end start) scale 0.5))))

(defn disk-move-animation-step
  "Function used as a browser animation callback to animate the movement
  of a disk moving from one post to another. State is an atom that was
  set up to describe the details of the move and keep track of events
  as it evolves, and timestamp is the browser-reported current
  monotonic time."
  [state timestamp]
  (swap! state update :start #(or % timestamp))  ; Capture first timestamp as start.
  (let [{:keys [disk our-board start canvas
                from-x from-y to-x to-y]} @state
        elapsed                           (- timestamp start)
        fraction                          (/ elapsed move-ms)]
    (if (>= fraction 1.0)
      (draw canvas @board)  ; We are all done!
      (let [ctx (.getContext canvas "2d")
            x   (if (< fraction 0.2)
                   from-x
                   (if (> fraction 0.8)
                     to-x
                     (ramp from-x to-x (/ (- fraction 0.2) 0.6))))
            y   (if (< fraction 0.3)
                (ramp from-y 25 (/ fraction 0.3))
                (if (> fraction 0.7)
                  (ramp 25 to-y (/ (- fraction 0.7) 0.3))
                  25))]
        (draw canvas our-board)  ; Start with board minus the ring we are moving.
        (draw-ring ctx disk x y)
        (.requestAnimationFrame js/window (partial disk-move-animation-step state))))))


(defn animate-top-move
  "Runs an animation that shows the top task, which is a move, taking
  place."
  [from to]
  (let [running-element (animate-task-starting)
        from-index      (post-index from)
        to-index        (post-index to)
        disk            (first (get @board from-index))
        moves           (.getElementById js/document "moves")]
    (.insertAdjacentHTML moves "beforeend" (str "<li>" from " → " to "</li>"))

    ;; Move the disk as specified
    (swap! board (fn [state]
                   (-> state
                       (update from-index rest)
                       (update to-index conj disk))))

    ;; Run the animation of that move in the browser.
    (let [canvas    (.getElementById js/document "state")
          w         (.-width canvas)
          h         (.-height canvas)
          our-board (update @board to-index rest) ; Omit the disk we are moving
          move      (atom {:from-x    (* w (post-positions from-index))
                           :from-y    (ring-y h (inc (count (get our-board from-index))))
                           :to-x      (* w (post-positions to-index))
                           :to-y      (ring-y h (inc (count (get our-board to-index))))
                           :disk      disk
                           :our-board our-board
                           :canvas    canvas})]
      (.requestAnimationFrame js/window (partial disk-move-animation-step move)))

    ;; Fade out and drop the task that caused this move.
    (swap! tasks rest)
    (set! (.-opacity (.-style running-element)) 0)
    (.setTimeout js/window
                 (fn []
                     (.remove running-element)
                   (update-step-button))
                 fade-ms)))

(defn run-top-task
  "Looks at the top of the task stack and takes the appropriate action,
  either recursively expanding it or moving a disk."
  []
  (when-let [[action & args] (first @tasks)]
    (case action
      hanoi (animate-top-expansion (apply expand-hanoi args))
      move  (apply animate-top-move args))))

(set! (.-runStep js/window) run-top-task)

(defn reset-board
  "Helper function to reset the board and draw its initial state."
  []
  (let [num (parse-long (.-value (.getElementById js/document "numDiscs")))]
    (reset! board [(range num) '() '()])
    (reset! tasks (list (list 'hanoi num 'A 'B))))
  (draw-current-board-state)
  (format-tasks)
  (set! (.-innerHTML (.getElementById js/document "moves")) "")
  (update-step-button))

(set! (.-resetBoard js/window) reset-board)
(reset-board)
