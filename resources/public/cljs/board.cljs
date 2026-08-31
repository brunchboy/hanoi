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

    ;; Draw the base
    (.save ctx)
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
      (let [scale (/ ring 9.0)
            offset (+ small-ring (* (- large-ring small-ring) scale 0.5))]
        (.beginPath ctx)
        (set! (.-strokeStyle ctx) (get ring-colors ring))
        (.moveTo ctx (- x offset) height)
        (.lineTo ctx (+ x offset) height))
      (.stroke ctx))

    (.restore ctx)))

(defn reset-board
  "Helper function to reset the board and draw its initial state."
  []
  (draw (.getElementById js/document "state") [(range 10) [] []]))

(set! (.-resetBoard js/window) reset-board)
(reset-board)
