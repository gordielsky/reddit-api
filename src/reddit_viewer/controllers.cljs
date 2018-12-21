(ns reddit-viewer.controllers
  (:require
    [ajax.core :as ajax]
    [re-frame.core :as rf]))

;; Unrelated methods

(defn printer [x]
  (println x)
  x)

(defn index-of [item coll]
  (count (take-while (partial not= item) coll)))

(defn get-new-tab-id [id db]
  (let [keys (keys (:tabs db)) active-tab-id (:active-tab-id db) closing-index (index-of id keys)]
    (if (= id active-tab-id)
      (cond
        (= (count keys) 1)
        nil
        (= closing-index (- (count keys) 1))
        (nth keys (- closing-index 1))
        :else
        (nth keys (+ closing-index 1)))
      active-tab-id)))

(defn change-num-tabs [db op amount]
  (assoc db :num-tabs-created (op (get db :num-tabs-created) amount)))

;; Initializer

(rf/reg-event-db
  :initialize-db
  (fn [_ _]
    {:view             :posts
     :sort-key         :score
     :num-posts        50
     :active-tab-id    0
     :num-tabs-created 3
     :tabs             {0 {:subreddit "mildlyinteresting" :textbox "mildlyinteresting" :posts [] :active true}
                        1 {:subreddit "pics" :textbox "pics" :posts [] :active false}
                        2 {:subreddit "aww" :textbox "aww" :posts [] :active false}}}))

;; Events

(defn find-posts-with-preview [posts]
  (filter #(= (:post_hint %) "image") posts))

(rf/reg-event-db
  :set-posts
  (fn [db [_ id posts]]
    (assoc-in
      db
      [:tabs id :posts]
      (->> (get-in posts [:data :children])
           (map :data)
           (find-posts-with-preview)))))

(rf/reg-event-db
  :sort-posts
  (fn [db [_ sort-key tab-id]]
    (assoc-in
      db
      [:tabs tab-id]
      (update
        (get (:tabs db) tab-id)
        :posts
        (partial sort-by sort-key >)))))

(rf/reg-event-db
  :select-view
  (fn [db [_ view]]
    (assoc
      db
      :view
      view)))

(rf/reg-event-db
  :select-num-posts
  (fn [db [_ num-posts]]
    (assoc
      db
      :num-posts
      num-posts)))

(rf/reg-event-db
  :create-tab
  (fn [db [_ _]]
    (let [new-id (+ (:num-tabs-created db) 1)]
      (-> (change-num-tabs db + 1)
          (assoc-in [:tabs new-id] {:subreddit "New Tab" :textbox "" :posts [] :active false})
          ((fn [updated-db]
             (if (= 1 (count (:tabs updated-db)))
               (-> (assoc updated-db :active-tab-id new-id :view :posts)
                   (assoc-in [:tabs new-id :active] true))
               updated-db)))))))

(rf/reg-event-db
  :select-subreddit
  (fn [db [_ id subreddit]]
    (assoc-in
      db
      [:tabs id :subreddit]
      subreddit)))

(rf/reg-event-db
  :update-textbox
  (fn [db [_ id text]]
    (assoc-in
      db
      [:tabs id :textbox]
      text)))

(rf/reg-event-db
  :change-tab
  (fn [db [_ id]]
    (if (not (nil? id))
      (-> (assoc-in db [:tabs (:active-tab-id db) :active] false)
          (assoc-in [:tabs id :active] true)
          (assoc :active-tab-id id))
      (assoc db :view :no-tabs))))

(rf/reg-event-db
  :remove-tab
  (fn [db [_ id]]
    (-> (update
          db
          :tabs
          dissoc
          id))))

;; Effects

(rf/reg-fx
  :ajax-get
  (fn [[url handler]]
    (ajax/GET url
              {:handler         handler
               :response-format :json
               :keywords?       true
               :origin          "http://localhost:3449"})))

(rf/reg-event-fx
  :load-posts
  (fn [_ [_ id url]]
    {:ajax-get [url #(rf/dispatch [:set-posts id %])]}))

(rf/reg-event-fx
  :close-tab
  (fn [{db :db} [_ id]]
    {:dispatch-n (list [:change-tab (get-new-tab-id id db)] [:remove-tab id])}))

;; Subscriptions

(rf/reg-sub
  :view
  (fn [db _]
    (:view db)))

(rf/reg-sub
  :posts
  (fn [db [_ id]]
    (:posts (get (:tabs db) id))))

(rf/reg-sub
  :get-num-posts
  (fn [db _]
    (:num-posts db)))

(rf/reg-sub
  :get-tabs
  (fn [db _]
    (:tabs db)))

(rf/reg-sub
  :get-subreddit
  (fn [db [_ id]]
    (:subreddit (get (:tabs db) id))))

(rf/reg-sub
  :get-textbox
  (fn [db [_ id]]
    (:textbox (get (:tabs db) id))))

(rf/reg-sub
  :get-active-tab-id
  (fn [db _]
    (:active-tab-id db)))

(rf/reg-sub
  :cheating-the-db
  (fn [db _]
    db))