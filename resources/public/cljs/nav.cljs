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
  (atom (identify-page)))

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
    (let [page-name (str "partials/notes/" (first (get index (dec @page))))]
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
            (.catch (fn [e] (set-notes-body (str "<h1>Problem Loading notes</h1><p>" e "</p>")))))))))

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
