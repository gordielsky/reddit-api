(ns reddit-viewer.core
  (:require
    [reagent.core :as r]
    [reddit-viewer.chart :as chart]
    [reddit-viewer.controllers :as controllers]
    [re-frame.core :as rf]
    [clojure.string :as string]))

;; -------------------------
;; Views

(defn display-post [{:keys [permalink subreddit title score url]}]
  [:div.card.m-2
   [:div.card-block
    [:h4.card-title
     [:a {:href (str "https://reddit.com" permalink)} title " "]]
    [:div [:span.badge.badge-info {:color "info"} "Sweet sweet " subreddit " Karma: " score]]
    [:img {:width "300px" :src url}]]])

(defn display-posts [post-array]
  (let [posts (take @(rf/subscribe [:get-num-posts]) post-array)]
    (let [subreddit @(rf/subscribe [:get-subreddit @(rf/subscribe [:get-active-tab-id])])]
      (cond
        (not (empty? posts))
        [:div
         (for [posts-row (partition-all 3 posts)]
           ^{:key posts-row}
           [:div.row
            (for [post posts-row]
              ^{:key post}
              [:div.col-4 [display-post post]])])]
        (string/includes? subreddit " ")
        [:div (str "Enter the subreddit of your choice in the textbox!")]
        :else
        [:div (str "loading posts from r/" subreddit)]))))

(defn sort-posts [title sort-key]
  [:button.btn.btn-secondary
   {:on-click #(rf/dispatch [:sort-posts sort-key @(rf/subscribe [:get-active-tab-id])])}
   (str "sort posts by " title)])

(defn navitem [title view id]
  [:li.nav-item
   {:class-name (when (= id view) "active")}
   [:a.nav-link
    {:href     "#"
     :on-click #(rf/dispatch [:select-view id])}
    title]])

(defn navbar [view]
  [:nav.navbar.navbar-toggleable-md.navbar-light.bg-faded
   [:ul.navbar-nav.mr-auto.nav
    {:className "navbar-nav mr-auto"}
    [navitem "Posts" view :posts]
    [navitem "Chart" view :chart]]])

(defn num-post-button [num]
  [:button.btn.btn-secondary
   {:on-click #(rf/dispatch [:select-num-posts num])}
   (str num)])

(defn load-subreddit [id subreddit]
  (when (not (string/includes? subreddit " "))
    (rf/dispatch [:load-posts id (str "https://www.reddit.com/r/" subreddit ".json?sort=new&limit=50")])))

(defn dispatch-subreddit [tab-id]
  (let [new-sub @(rf/subscribe [:get-textbox tab-id])]
    #(do (load-subreddit tab-id new-sub))
    (rf/dispatch [:select-subreddit tab-id new-sub])))

(defn enter-text []
  (let [active-id @(rf/subscribe [:get-active-tab-id])]
    [:input.m-2
     {:type         "text"
      :name         :input-subreddit
      :value        @(rf/subscribe [:get-textbox active-id])
      :on-change    #(do (rf/dispatch [:update-textbox active-id (-> % .-target .-value)]))
      :on-key-press #(do (when (= (.-charCode %) 13) (dispatch-subreddit active-id)))
      :style        {:color           "#292b2c"
                     :backgroundColor "#ffffff"
                     :border-color    "#e3e5e8"}}]))

(defn submit-subreddit []
  (let [active-id @(rf/subscribe [:get-active-tab-id])]
    [:button.btn.btn-secondary
     {:on-click #(do (dispatch-subreddit active-id))}
     (str "Submit Subreddit")]))

(defn create-tab []
  #(rf/dispatch [:create-tab]))

(defn change-tab [id]
  (rf/dispatch [:change-tab id]))

(defn close-tab [id]
  #(rf/dispatch [:close-tab id]))

(defn add-active-class [html-str tab]
  (if (:active tab)
    (keyword (str html-str ".active"))
    (keyword html-str)))

(defn display-tab [tab-vector li-key]
  (let [tab (second tab-vector)]
    (let [id (first tab-vector)]
      [:div
       {:key   (str "div-" (str id))
        :style {:display "flex"}}
       [li-key {:key      (str id)
                :on-click #(do (change-tab id))}
        [(add-active-class "a.nav-link" tab)
         {:key         (str "a-" id)
          :data-toggle "tab"
          :href        (str "#t" id)
          :role        "tab"}
         (str (:subreddit tab))]]
       [:button.btn.btn-secondary
        {:key      (str "b-" id)
         :on-click (close-tab id)}
        "X"]])))

(defn setup-tab-contents [tab-vector]
  (let [tab (second tab-vector)]
    #_(println "Tab:" tab-vector)
    (let [id (first tab-vector)]
      (let [subreddit (:subreddit tab)]
        [(add-active-class "div.tab-pane" tab)
         {:key  id
          :id   (str "t" id)
          :role "tabpanel"}
         [load-subreddit id subreddit]]))))

(defn display-tabs [tabs]
  [:div
   {:style {:display "flex"}}
   [:ul.nav.nav-tabs
    {:key  tabs
     :role "tablist"
     :id   "navtabs"}
    (for [tab tabs] ^{:key tab} (display-tab tab "li.nav-item"))]
   [:button.btn.btn-secondary
    {:on-click (create-tab)}
    "+"]])

(defn display-tab-contents [tabs]
  [:div.tab-content
   {:key "content"
    :id  "content"}
   (for [tab tabs] ^{:key tab} (setup-tab-contents tab))])

(defn home-page []
  (let [tabs @(rf/subscribe [:get-tabs])]
    (let [view @(rf/subscribe [:view])]
      [:div
       [display-tabs tabs]
       [navbar view]
       [:div.card>div.card-block
        [:div.row
         [:div.col-4
          [:div "Enter a subreddit: "
           [enter-text]
           [submit-subreddit]]]
         [:div.btn-group.col-4
          [sort-posts "score" :score]
          [sort-posts "comments" :num_comments]]
         [:br]
         [:div.btn-group.col-4
          [:h4.m-1 "Number of posts: "]
          [num-post-button 10]
          [num-post-button 25]
          [num-post-button 50]]]

        [display-tab-contents tabs]
        (case view
          :chart [chart/chart-posts-by-votes]
          :posts [display-posts @(rf/subscribe [:posts @(rf/subscribe [:get-active-tab-id])])]
          :no-tabs [:p "You have no tabs open. Click the '+' to create a new one!"]
          (println (str "view in home page:") view))]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (r/render [home-page] (.getElementById js/document "app")))

(defn init! []
  (rf/dispatch-sync [:initialize-db])
  (mount-root))