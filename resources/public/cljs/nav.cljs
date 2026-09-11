(ns nav
  (:require [clojure.string :as str]))

(def index
  "Lists the pages that make up the presentation, along with their titles."
  [["index.html" "Title"]
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
            (.catch (fn [e] (set-notes-body (str "<h2>Problem Loading notes</h2><p>" e "</p>")))))
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

;; export functions to use from JavaScript:
(set! (.-showNotes js/window) show-notes)
(set! (.-setNotes js/window) set-notes)


;; Install the navigation section in the page.

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
                               (set! (.-href (.-location js/window)) (.-value (.-target event)))))))
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

(.addEventListener js/window "keydown"
                   (fn [event]
                     (case (.-code event)
                       "Home"       (show-notes)
                       "ArrowLeft"  (move-page -1)
                       "ArrowRight" (move-page 1)
                       (.log js/console event))))
