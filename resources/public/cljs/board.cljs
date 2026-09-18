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
    (doseq [[post x] (map (fn [itm pos] [itm (* w pos)]) posts post-positions)
            [ring y] (map-indexed (fn [idx itm]
                                    [itm (ring-y h (- (count post) idx))])
                                  post)]
      (draw-ring ctx ring x y))

    (.restore ctx)))

(def board
  "Holds the state of the puzzle board, as a vector of three posts, each
  of which is a list of ring numbers from top to bottom."
  (atom []))

(def move-ms
  "How many milliseconds to spend animating the movement of a ring
  from one post to another."
  (atom 1000))

(defn other-post
  "Given a pair of posts, return the one that was not mentioned."
  [x y]
  (first (set/difference #{'A 'B 'C} (set [x y]))))

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

(defn ring-move-animation-step
  "Function used as a browser animation callback to animate the movement
  of a ring moving from one post to another. State is an atom that was
  set up to describe the details of the move and keep track of events
  as it evolves, and timestamp is the browser-reported current
  monotonic time.

  If `completion` is not `nil`, it will be called once animation is
  finished."
  [state completion timestamp]
  (swap! state update :start #(or % timestamp))  ; Capture first timestamp as start.
  (let [{:keys [ring our-board start canvas
                from-x from-y to-x to-y]} @state
        elapsed                           (- timestamp start)
        fraction                          (/ elapsed @move-ms)]
    (if (>= fraction 1.0)
      (do   ; We are all done!
        (draw canvas @board)
        (when completion (completion)))
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
        (draw-ring ctx ring x y)
        (.requestAnimationFrame js/window (partial ring-move-animation-step state completion))))))

(defn move-ring
  "Performs the actual update of the board atom to reflect a ring moving
  from one post to another. Returns the number of the ring that moved."
  [from-index to-index]
  (let [ring (first (get @board from-index))]
    (swap! board (fn [state]
                   (-> state
                       (update from-index rest)
                       (update to-index conj ring))))
    ring))

  (defn animate-ring-move
    "Animate the movement of a ring from one post to another, updating the
  board state to reflect the result.

  If `completion` is not `nil`, it will be called once animation is
  finished. Returns the number of the ring that moved."
    ([from to]
     (animate-ring-move from to nil))
    ([from to completion]
     (let [from-index (post-index from)
           to-index   (post-index to)
           ring       (move-ring from-index to-index)
           canvas     (.getElementById js/document "state")
           w          (.-width canvas)
           h          (.-height canvas)
           our-board  (update @board to-index rest) ; Omit the ring we are moving
           move       (atom {:from-x    (* w (post-positions from-index))
                             :from-y    (ring-y h (inc (count (get our-board from-index))))
                             :to-x      (* w (post-positions to-index))
                             :to-y      (ring-y h (inc (count (get our-board to-index))))
                             :ring      ring
                             :our-board our-board
                             :canvas    canvas})]

       ;; Run the animation of the move in the browser.
       (.requestAnimationFrame js/window (partial ring-move-animation-step move completion))

       ring)))

(defn tower-teleportation-animation-step
  "Function used as a browser animation callback to animate the movement
  of a ring moving from one post to another. State is an atom that was
  set up to describe the details of the move and keep track of events
  as it evolves, and timestamp is the browser-reported current
  monotonic time.

  If `completion` is not `nil`, it will be called once animation is
  finished."
  [state completion timestamp]
  (swap! state update :start #(or % timestamp))  ; Capture first timestamp as start.
  (let [{:keys [rings our-board start canvas
                from-index to-index]} @state
        elapsed                       (- timestamp start)
        fraction                      (/ elapsed @move-ms)]
    (if (>= fraction 1.0)
      (do                               ; We are all done!
        (draw canvas @board)
        (when completion (completion)))
      (let [ctx (.getContext canvas "2d")
            w   (.-width canvas)
            h   (.-height canvas)]
        (draw canvas our-board) ; Start with board minus the ring we are moving.
        (.save ctx)
        (let [x          (* w (get post-positions from-index))  ; Draw the outgoing rings
              ring-count (+ (count rings) (count (get our-board from-index)))]
          (set! (.-globalAlpha ctx) (- 1.0 fraction))
          (doseq [[ring y] (map-indexed (fn [idx itm]
                                          [itm (ring-y h (- ring-count idx))])
                                        rings)]
            (draw-ring ctx ring x y)))
        (let [x          (* w (get post-positions to-index))  ; Draw the incoming rings
              ring-count (+ (count rings) (count (get our-board to-index)))]
          (set! (.-globalAlpha ctx) fraction)
          (doseq [[ring y] (map-indexed (fn [idx itm]
                                          [itm (ring-y h (- ring-count idx))])
                                        rings)]
            (draw-ring ctx ring x y)))
        (.restore ctx)
        (.requestAnimationFrame js/window (partial tower-teleportation-animation-step state completion))))))

(defn animate-tower-teleportation
  "Animate the fading of multiple rings from one post to another, when
  we are exploring our way towards a solution to the puzzle.

  If `completion` is not `nil`, it will be called once animation is
  finished."
  ([from to num-rings]
   (animate-tower-teleportation from to num-rings nil))
  ([from to num-rings completion]
   (let [from-index (post-index from)
         to-index   (post-index to)
         rings      (take num-rings (get @board from-index))]

     ;; Move the rings as specified
     (swap! board (fn [state]
                    (-> state
                        (update from-index (partial drop num-rings))
                        (update to-index (partial concat rings)))))

     ;; Run the animation of that move in the browser.
     (let [canvas    (.getElementById js/document "state")
           our-board (update @board to-index (partial drop num-rings)) ; Omit the rings we are moving
           move      (atom {:from-index from-index
                            :to-index   to-index
                            :rings      rings
                            :our-board  our-board
                            :canvas     canvas})]
       (.requestAnimationFrame js/window (partial tower-teleportation-animation-step move completion))))))

(defn reset-board
  "Helper function to reset the board and draw its initial state, for a
  specified number of rings, which must be from 1 to 10."
  [num-rings]
  (reset! board [(range num-rings) '() '()])
  (draw-current-board-state))
