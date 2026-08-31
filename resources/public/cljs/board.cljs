(ns board)

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
    (set! (.-lineWidth ctx) ring-height)
    (doseq [[post x]      (map (fn [itm pos] [itm (* w pos)]) posts post-positions)
            [ring height] (map-indexed (fn [idx itm]
                                         [itm (- h 30 (* ring-height (- (count post) idx)))])
                                       post)]
      (let [scale  (/ ring 9.0)
            offset (+ small-ring (* (- large-ring small-ring) scale 0.5))]
        (.beginPath ctx)
        (set! (.-strokeStyle ctx) (get ring-colors ring))
        (.moveTo ctx (- x offset) height)
        (.lineTo ctx (+ x offset) height))
      (.stroke ctx))

    (.restore ctx)))

(def board
  "Holds the state of the puzzle board, as a vector of three posts, each
  of which is a list of disc numbers from top to bottom."
  (atom nil))

(def tasks
  "Holds the stack of tasks to be performed, as a list of
  s-expressions."
  (atom nil))

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
                                (.remove (.-classList (.-previousElementSibling expanding-element)) "hidden")
                                (.setTimeout js/window
                                             #(animate-tasks-in (rest tasks-left) all-tasks expanding-element)
                                             1000))))
    (if all-tasks
      (do
        (swap! tasks #(concat all-tasks (rest %)))
        (set! (.-opacity (.-style expanding-element)) 0)
        (.setTimeout js/window
                     #(animate-tasks-in [] nil expanding-element)
                     1000))
      (.remove expanding-element))))

(defn expand-top-task
  "Runs an animation that shows the top task is being replaced by a
  recursive expansion, then actually make that replacement in the atom
  and re-render the list from it."
  [expansion]
  (let [tasks-element   (.getElementById js/document "tasks")
        children        (.-children tasks-element)
        running-element (.item children 0)]
    (set! (.-className running-element) "running")
    (animate-tasks-in expansion expansion running-element)))

(defn reset-board
  "Helper function to reset the board and draw its initial state."
  []
  (let [num (parse-long (.-value (.getElementById js/document "numDiscs")))]
    (reset! board [(range num) '() '()])
    (reset! tasks (list (list 'hanoi num 'A 'B))))
  (draw (.getElementById js/document "state") @board)
  (format-tasks)
  (set! (.-innerHTML (.getElementById js/document "moves")) ""))

(set! (.-resetBoard js/window) reset-board)
(reset-board)
