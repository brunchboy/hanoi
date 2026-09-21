(ns not-recursive
  (:require [nav]))

(defn show-step
  "Make the element representing the current step visible."
  []
  (let [elem (.getElementById js/document (str "step-" @nav/step))]
    (set! (.-opacity (.-style elem)) 1)))

(def step-functions
  "The functions to run for each step of the slide."
  (vec (repeat 4 show-step)))

(reset! nav/max-step 4)
(reset! nav/step-functions step-functions)
(nav/run-once-loaded nav/show-step-ui)
