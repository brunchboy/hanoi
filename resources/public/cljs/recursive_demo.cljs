(ns recursive-demo
  (:require [board]
            [clojure.string :as str]
            [nav]))

(def tasks
  "Holds the stack of tasks to be performed, as a list of
  s-expressions."
  (atom '()))

(def fade-ms
  "How many milliseconds to wait for values to fade in or out when
  animating steps."
  (atom 500))  ;; TODO, when changing this, change CSS to match; maybe tie with code?

(def auto-step
  "Controls whether the next step is automatically run when one ends."
  (atom false))

(declare run-top-task)

(defn update-step-button
  "Makes sure the Step button is enabled if there are tasks in the
  stack, and disabled otherwise. If auto-step is true, run the next
  task."
  []
  (let [button (.getElementById js/document "step-button")
        done   (empty? @tasks)]
    (set! (.-disabled button) done)
    (when (and @auto-step (not done)) (run-top-task))))

(defn format-tasks
  "Formats the task list as a series of list items on the page."
  []
  (set! (.-innerHTML (.getElementById js/document "tasks"))
        (reduce (fn [acc task] (str acc "<li>" task "</li>"))
                ""
                @tasks)))

(defn animate-task-in
  "The animation which causes a new task to appear at the top of the
  stack. For convenience in manipulating the document,
  `running-element` holds the element representing task that is
  currently running. If `completion` is not `nil`, it is a function to
  be called when the animation is finished."
  ([task running-element]
   (animate-task-in task running-element nil))
  ([task running-element completion]
   (swap! tasks conj task)
   (.insertAdjacentHTML running-element "beforebegin" (str "<li class=\"hidden\"><span class=\"calling\">"
                                                           task "</span></li>"))
   (.requestAnimationFrame js/window
                           (fn [_]
                             (set! (.-opacity (.-style (.-previousElementSibling running-element))) 1)
                             (.setTimeout js/window (fn []
                                                      (update-step-button)
                                                      (when completion (completion)))
                                          (quot @fade-ms 3))))))

(defn animate-task-starting
  "Changes the appearance of the top task to show that it is running,
  and return that DOM element for the use of later animations."
  []
  (let [tasks-element   (.getElementById js/document "tasks")
        children        (.-children tasks-element)
        running-element (.item children 0)]
    (set! (.-className running-element) "running")
    (set! (.-innerHTML running-element) (.-innerText running-element))  ; Get rid of calling span.
    running-element))

(defn animate-task-suspending
  "Changes the appearance of the supplied task element to show that it
  is suspended until another function returns, and the line number
  after which it will resume."
  [element line]
  (let [text (first (str/split (.-innerText element) #":"))]
    (set! (.-innerText element) (str text ": " line))
    (set! (.-className element) "suspended")))

(defn set-running-state
  "Updates the Running: text to reflect the step we are now executing,
   and resets the code window to its initial styles.

  `call` is the function invocation being run, if any.
  `line`, if not `nil` is the line we are resuming after."
  [call line]
  (let [running (.getElementById js/document "running")]
    (set! (.-innerHTML running) (if (str/blank? call)
                                  (str "<em>nothing, "
                                       (if (empty? @tasks) "solved" "ready to start")
                                       "</em>")

                                  (str "<code>" call "</code>"
                                       (when line (str " and resuming after line " line))))))
  (doseq [id ["all-hanoi" "lines-2-6" "lines-2-3" "test-2" "line-3" "line-4" "test-5" "line-6"]]
    (let [elem (.getElementById js/document id)]
      (set! (.-className elem) ""))))

(defn format-move
  "Helper function to generate the HTML describing a ring move."
  [from to ring]
  (str "<li><span class=\"ring-moved\"><span style=\"color:" (get board/ring-colors ring)
       "\">Ring " (inc ring) ": </span></span>" from " → " to "</li>"))

(defn animate-top-move
  "Runs an animation that shows the top task, which is a move, taking
  place."
  [from to]
  (set-running-state (str "<code>(move " from " " to ")</code>") nil)
  (let [running-element (animate-task-starting)
        moves           (.getElementById js/document "moves")
        lines-2-3       (.getElementById js/document "lines-2-3")
        line-4          (.getElementById js/document "line-4")]

    (set! (.-className lines-2-3) "finished")
    (set! (.-className line-4) "inside")

    ;; Move the ring as specified, and record it.
    (let [ring (board/animate-ring-move from to update-step-button)]
      (.insertAdjacentHTML moves "beforeend" (format-move from to ring)))

    ;; Fade out and drop the task that caused this move.
    (swap! tasks rest)
    (set! (.-opacity (.-style running-element)) 0)
    (.setTimeout js/window
                 (fn [] (.remove running-element))
                 @fade-ms)))

(defn set-top-task-line
  "Helper function to indicate that the top task in the stack has been
  suspended to call another function at the specified line. Called in
  the context of a `swap!` with the current state of the stack, and
  only for `hanoi` entries, so the line number will be the fifth
  element in the top task's list."
  [line stack]
  (let [top (take 4 (first stack))]  ; Get rid of any existing line number.
    (conj (rest stack) (concat top (list line)))))

(defn finished-top-hanoi
  "Helper function to fade out and remove the topmost hanoi invocation,
  which has completed, and prepare for the next step."
  [running-element]
  (swap! tasks rest)
  (set! (.-opacity (.-style running-element)) 0)
  (.setTimeout js/window
               (fn []
                 (.remove running-element)
                 (update-step-button))
               @fade-ms))

(defn animate-top-hanoi
  "Runs an animation that shows the next step of the recursive hanoi
  algorithm. If `line` is not `nil`, we are resuming a suspended
  invocation after the specified line."
  ([n from to]
   (animate-top-hanoi n from to nil))
  ([n from to line]
   (let [running-element (animate-task-starting)]
     (set-running-state (str "(hanoi " n " " from " " to ")") line)
     (case line
    nil (let [test   (.getElementById js/document "test-2")
              line-3 (.getElementById js/document "line-3")]
          (set! (.-className test) (if (> n 1) "success" "failure"))
          (set! (.-className line-3) (if (> n 1) "running" "skipped"))
          (if (> n 1)
            (do
              (swap! tasks (partial set-top-task-line 3))
              (animate-task-in (list 'hanoi (dec n) from (board/other-post from to)) running-element
                               #(animate-task-suspending running-element 3)))
            (let [line-4 (.getElementById js/document "line-4")]
              (swap! tasks (partial set-top-task-line 4))
              (set! (.-className line-4) "running")
              (animate-task-in (list 'move from to) running-element
                               #(animate-task-suspending running-element 4)))))
    3   (let [lines-2-3 (.getElementById js/document "lines-2-3")
              line-4    (.getElementById js/document "line-4")]
          (swap! tasks (partial set-top-task-line 4))
          (set! (.-className lines-2-3) "finished")
          (set! (.-className line-4) "running")
          (animate-task-in (list 'move from to) running-element
                           #(animate-task-suspending running-element 4)))
    4   (let [lines-2-3 (.getElementById js/document "lines-2-3")
              line-4    (.getElementById js/document "line-4")
              test      (.getElementById js/document "test-5")
              line-6    (.getElementById js/document "line-6")]
          (set! (.-className lines-2-3) "finished")
          (set! (.-className line-4) "finished")
          (set! (.-className test) (if (> n 1) "success" "failure"))
          (set! (.-className line-6) (if (> n 1) "running" "skipped"))
          (if (> n 1)
            (do
              (swap! tasks (partial set-top-task-line 6))
              (animate-task-in (list 'hanoi (dec n) (board/other-post from to) to) running-element
                               #(animate-task-suspending running-element 6)))
            (finished-top-hanoi running-element)))
    6   (let [lines-2-6 (.getElementById js/document "lines-2-6")]
          (set! (.-className lines-2-6) "finished")
          (finished-top-hanoi running-element))))))

(defn run-top-task
  "Looks at the top of the task stack and takes the appropriate action,
  either recursively expanding it or moving a ring."
  []
  (when-let [[action & args] (first @tasks)]
    (set! (.-disabled (.getElementById js/document "step-button")) true)
    (nav/set-step (inc @nav/step))
    (case action
      hanoi (apply animate-top-hanoi args)
      move  (apply animate-top-move args))))

(set! (.-nextStep js/window) run-top-task)

(defn reset-board
  "Helper function to reset the board and draw its initial state."
  []
  (let [num   (parse-long (.-value (.getElementById js/document "num-rings")))
        moves (.getElementById js/document "moves")]
    (board/reset-board num)
    (reset! tasks (list (list 'hanoi num 'A 'B)))
    (set! (.-innerHTML moves) ""))
  (format-tasks)
  (set-running-state "" nil)
  (nav/set-step 1)
  (update-step-button))

(set! (.-resetBoard js/window) reset-board)

(defn hanoi-fast
  "Performs a non-animated solution, returning the formatted move list."
  [num-rings from to]
  (str
   (when (> num-rings 1) (hanoi-fast (dec num-rings) from (board/other-post from to)))
   (let [ring (board/move-ring (board/post-index from) (board/post-index to))]
     (format-move from to ring))
   (when (> num-rings 1) (hanoi-fast (dec num-rings) (board/other-post from to) to))))

(defn solve
  "Helper function to quickly get to a solved state."
  []
  (let [num   (parse-long (.-value (.getElementById js/document "num-rings")))
        moves (.getElementById js/document "moves")]
    (board/reset-board num)
    (reset! tasks '())
    (set-running-state "" nil)
    (set! (.-innerHTML moves) (hanoi-fast num 'A 'B))
    (board/draw-current-board-state)
    (nav/set-step (* 4 (dec (.pow js/Math 2 num))))
    (update-step-button)))

(set! (.-solve js/window) solve)

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
