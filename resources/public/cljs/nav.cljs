(ns nav
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(def index
  "Lists the pages that make up the presentation, along with their titles."
  [["index.html" "Title"]
   ["rules.html" "Rules"]
   ["background.html" "Background"]
   ["recursive_demo.html" "The Recursive Solution"]])

(defn identify-page
  "Locates the current page in the index so we can determine our page
  number."
  []
  (let [filename (last (str/split (.-href (.-location js/window)) "/"))
        indexed  (map-indexed (fn [idx [f]] [idx f]) index)]
    ((fnil inc 0) (first (first (drop-while (fn [[_ f]] (not= f filename)) indexed))))))

(def page
  "Holds the current page number we are viewing."
  (identify-page))

(def step
  "Holds the step number we have reached, for multi-step pages."
  (atom 1))

(def max-step
  "Holds the maximum step number, for multi-step pages."
  (atom 1))

(def step-highlights
  "Holds an array of element IDs to be highlighted for each step,
  starting with step 1, for multi-step pages. This is done by adding
  the CSS class stepHighlight to the document element with that ID
  when the step is reached, and removing that class from the other
  elements in the list. A list position can be `nil` if no
  highlighting should take place for that step.

  If an element should be highlighted when the page first loads, that
  should be arranged in the initial HTML, or `highlight-step` should
  be called once the page has fully loaded."
  (atom []))

(def step-functions
  "Holds an array of functions to be called for each step, for example
  to perform animations. A list position can be `nil` if no function
  need be called for that step.

  As with step-highlights, if an animation is needed for the first
  step, the function can be called directly by the page, or
  `animate-step` should be called once the page has fully loaded."
  (atom []))

(def notes-window
  "Will hold the speaker notes window when created."
  (atom nil))

(defn set-notes-body
  "Sets the body of the notes window to the supplied string."
  [body]
  (when-let [window @notes-window]
    (let [notes (.getElementById (.-document window) "notes")]
      (set! (.-innerHTML notes) body))))

(defn update-notes
  "If the notes window is open, update it to reflect the current page
  or step."
  []
  (when (and @notes-window (not (.-closed @notes-window)))
    (let [page-name (str "partials/notes/" (first (get index (dec page))))]
      (if (= @step 1)
        (-> (js/fetch page-name)
            (.then (fn [response]
                     (if (.-ok response)
                       (.text response)
                       (js/Promise.resolve "<p>Perfect, no notes.</p>"))))
            (.then (fn [text] (set-notes-body text)))
            (.catch (fn [e] (set-notes-body (str "<h2>Problem loading notes:</h2><p>" e "</p>")))))
        (-> (js/fetch (str "partials/" (str/replace page-name ".html"
                                                    (str "." @step ".html"))))
            (.then (fn [response]
                     (if (.-ok response)
                       (.text response)
                       (js/Promise.resolve nil))))  ; No notes change for this step.
            (.then (fn [text] (when text (set-notes-body text))))
            (.catch (fn [e] (set-notes-body (str "<h2>Problem Loading notes</h2><p>" e "</p>")))))))))

(defn show-notes
  "Make sure the notes window is showing, creating if needed."
  []
  (if (or (nil? @notes-window)
          (.-closed @notes-window))
    (.open js/window "notes.html" "notes" "popup,width=600,height=400")
    (.focus @notes-window)))

(defn set-notes
  "Helper function for notes window to call to reattach itself after we
  reload or navigate to a new page."
  [notes]
  (let [old-val @notes-window]
    (reset! notes-window notes)
    (when (not= old-val notes)
      (.log js/console "notes window reattached")
      (update-notes))))

(defn highlight-step
  "Arranges for the appropriate page element to be highlighted given the
  current step number."
  []
  (let [current-id (get @step-highlights (dec @step))
        other-ids  (set/difference (set (filter identity @step-highlights)) (when current-id #{current-id}))]
    (when current-id
      (when-let [current (.getElementById js/document current-id)]
        (.add (.-classList current) "stepHighlight")))
    (doseq [id other-ids]
      (when-let [other (.getElementById js/document id)]
        (.remove (.-classList other) "stepHighlight")))))

(defn animate-step
  "Calls the step function configured for the current step, if there is
  one."
  []
  (when-let [f (get @step-functions (dec @step))]
    (f)))

(defn next-step
  "Move to the next step of a multi-step page."
  []
  (if @max-step
    (let [old @step  ; We are handling the steps.
          new (swap! step (fn [n] (min @max-step (inc n))))]
      (when (not= old new)
        (highlight-step)
        (let [step-number (.getElementById js/document "step-number")]
          (set! (.-innerText step-number) new))
        (let [step-button (.getElementById js/document "step-button")]
          (set! (.-disabled step-button) (>= new @max-step)))
        (animate-step)))
    (.nextStep js/window)))  ; Custom page code is handling the steps.

(defn set-step
  "Set the step number, for a page that is doing this manually."
  [n]
  (reset! step n)
  (let [step-number (.getElementById js/document "step-number")]
    (set! (.-innerText step-number) n)))

;; export functions to use from JavaScript:
(set! (.-showNotes js/window) show-notes)
(set! (.-setNotes js/window) set-notes)


;; Install the navigation section in the page.

(def load-state
  "Keeps track of whether the section has finished loading, and allows
  functions to be queued for calling once loading completes."
  (atom {:loaded false
         :fns    []}))

(defn run-once-loaded
  "Calls a function once the navigation section has finished loading."
  [f]
  (let [loaded? (:loaded (swap! load-state (fn [state]
                                             (if (:loaded state)
                                               state
                                               (update state :fns conj f)))))]
    (when loaded? (f))))  ; It was already loaded, we just call the function now.

(defn show-step-ui
  "Reveal the step navigation UI if the page has multiple steps."
  []
  (when (not= 1 @max-step)
    (let [step-count (.getElementById js/document "step-count")]
      (set! (.-innerText step-count)
            (if @max-step
              (str " of " @max-step)
              "")))
    (when @max-step (set! (.-nextStep js/window) next-step)) ; We can implement the Next button.
    (.remove (.-classList (.getElementById js/document "step-row")) "hidden")))

(defn create-navigation
  "Loads the HTML of the navigation section into the page."
  [html]
  (if-let [navigation (.getElementById js/document "navigation")]
    (if html
      (do
        (set! (.-innerHTML navigation) html)
        ;; Hide next or Previous buttons if we are at either end, otherwise show and configure where they go.
        (let [button (.getElementById js/document "previous-button")]
          (if (= 1 page)
            (set! (.-disabled button) true)
            (let [[file title] (get index (dec (dec page)))]
              (.setAttribute button "onclick" (str "window.location.href='" file "'"))
              (let [label (.getElementById js/document "previous-title")]
                (set! (.-innerHTML label) (str "<a href=\"" file "\">" title "</a>"))))))
        (when (> 1 page)
          (let [label (.getElementById js/document "previous-label")]
            (set! (.-innerHTML label) (get-in index [(dec (dec page)) 1]))))
        (let [button (.getElementById js/document "next-button")]
          (if (= (count index) page)
            (set! (.-disabled button) true)
            (let [[file title] (get index page)]
              (.setAttribute button "onclick" (str "window.location.href='" file "'"))
              (let [label (.getElementById js/document "next-title")]
                (set! (.-innerHTML label) (str "<a href=\"" file "\">" title "</a>"))))))

        ;; Populate the page selection menu.
        (let [select (.getElementById js/document "page-select")]
          (doseq [[idx [file title]] (map-indexed (fn [idx entry] [(inc idx) entry]) index)]
            (let [option (.createElement js/document "option")]
              (set! (.-value option) file)
              (set! (.-innerHTML option) title)
              (when (= idx page) (set! (.-selected option) true))
              (.appendChild select option)))
          (.addEventListener select "change"
                             (fn [event]
                               (set! (.-href (.-location js/window)) (.-value (.-target event))))))

        ;; Indicate we have finished loading, and run any deferred functions that may have been registered.
        (let [fns (:fns (swap! load-state update :loaded (constantly true)))]
          (doseq [f fns] (f))))
      (set! (.-innerHTML navigation) "Unable to load navigation section."))
    (.log js/console "Unable to find navigation div!")))

(-> (js/fetch "partials/nav.html")
    (.then (fn [response]
             (if (.-ok response)
               (.text response)
               (js/promise resolve nil))))
    (.then (fn [text] (create-navigation text)))
    (.catch (fn [e] (.log js/console e "Problem loading navigation."))))

(defn move-page
  "Helper function to navigate to a relative page, used by arrow key
  support below."
  [offset]
  (when-let [[file] (get index (+ page offset -1))]
    (set! (.-href (.-location js/window)) file)))

;; Add keyboard navigation listeners.
(.addEventListener js/window "keydown"
                   (fn [event]
                     (case (.-code event)
                       "Home"       (show-notes)
                       "ArrowLeft"  (move-page -1)
                       "ArrowRight" (move-page 1)
                       "Enter"      (next-step)
                       (.log js/console event))))
