(ns recursive-manual
  (:require [board]
            [nav]))

(defn show-card
  "Set the visibility of a particular slide. If `visible?` is passed as
  `false`, the slide is hidden."
  ([n]
   (show-card n true))
  ([n visible?]
   (let [card (.getElementById js/document (str "card-" n))]
     (set! (.-display (.-style card)) (if visible? "block" "none")))))

(defn check-step
  "Set the list item marker for the specified card and step to
  completed, or if `done?` is passed as `false`, to uncompleted."
  ([card step]
   (check-step card step true))
  ([card step done?]
   (let [item (.getElementById js/document (str "step-" card "-" step))]
     (set! (.-className item) (if done? "completed" "")))))

(def step-functions
  "The functions to run for each step of the slide."
  [nil
   #(show-card 1)
   #(show-card 2)
   #(show-card 3)
   #(show-card 4)
   (fn [] (board/animate-ring-move 'A 'B #(check-step 4 1)))
   (fn [] (board/animate-ring-move 'A 'C #(check-step 4 2)))
   (fn [] (board/animate-ring-move 'B 'C #(check-step 4 3)))
   (fn [] (show-card 4 false) (check-step 3 1))
   (fn [] (board/animate-ring-move 'A 'B #(check-step 3 2)))
   (fn [] (board/animate-tower-teleportation 'C 'B 2 #(check-step 3 3)))
   (fn [] (show-card 3 false) (check-step 2 1))
   (fn [] (board/animate-ring-move 'A 'C #(check-step 2 2)))
   (fn [] (board/animate-tower-teleportation 'B 'C 3 #(check-step 2 3)))
   (fn [] (show-card 2 false) (check-step 1 1))
   (fn [] (board/animate-ring-move 'A 'B #(check-step 1 2)))
   (fn [] (board/animate-tower-teleportation 'C 'B 4 #(check-step 1 3)))])


(board/reset-board 5)

(reset! nav/max-step 17)
(reset! nav/step-functions step-functions)
(nav/run-once-loaded nav/show-step-ui)
