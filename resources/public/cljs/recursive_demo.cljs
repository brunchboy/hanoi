(ns recursive-demo
  (:require [board]
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
                                             (quot @fade-ms 5)))))
    (if all-tasks
      (do
        (swap! tasks #(apply concat all-tasks (list (rest %))))
        (set! (.-opacity (.-style expanding-element)) 0)
        (.setTimeout js/window
                     #(animate-tasks-in [] nil expanding-element)
                     @fade-ms))
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

(defn expand-hanoi
  "Given a hanoi task, return its expansion, either a series of steps
  involving smaller hanoi tasks and a move, or for the base case of
  one ring, a single move."
  [n from to]
  (if (< n 2)
    [(list 'move from to)] ; We have reached the base case; terminate.
    [(list 'hanoi (dec n) from (board/other-post from to))
     (list 'move from to)
     (list 'hanoi (dec n) (board/other-post from to) to)]))

(defn animate-top-move
  "Runs an animation that shows the top task, which is a move, taking
  place."
  [from to]
  (let [running-element (animate-task-starting)
        moves           (.getElementById js/document "moves")]
    (.insertAdjacentHTML moves "beforeend" (str "<li>" from " → " to "</li>"))

    ;; Move the ring as specified
    (board/animate-ring-move from to update-step-button)

    ;; Fade out and drop the task that caused this move.
    (swap! tasks rest)
    (set! (.-opacity (.-style running-element)) 0)
    (.setTimeout js/window
                 (fn [] (.remove running-element))
                 @fade-ms)))

(defn run-top-task
  "Looks at the top of the task stack and takes the appropriate action,
  either recursively expanding it or moving a ring."
  []
  (when-let [[action & args] (first @tasks)]
    (set! (.-disabled (.getElementById js/document "step-button")) true)
    (nav/set-step (inc @nav/step))
    (case action
      hanoi (animate-top-expansion (apply expand-hanoi args))
      move  (apply animate-top-move args))))

(set! (.-nextStep js/window) run-top-task)

(defn reset-board
  "Helper function to reset the board and draw its initial state."
  []
  (let [num (parse-long (.-value (.getElementById js/document "num-rings")))]
    (board/reset-board num)
    (reset! tasks (list (list 'hanoi num 'A 'B))))
  (format-tasks)
  (set! (.-innerHTML (.getElementById js/document "moves")) "")
  (nav/set-step 1)
  (update-step-button))

(set! (.-resetBoard js/window) reset-board)

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
