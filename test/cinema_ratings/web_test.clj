(ns cinema-ratings.web-test
  (:require [cinema-ratings.web :refer [async-handler]]
            [cinema-ratings.fixtures :as fixtures]
            [cinema-ratings.cache :as cache]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [clojure.test :refer [deftest is use-fixtures]]
            [hickory.core :as hickory]
            [hickory.render :as render]
            [hickory.select :as selector]
            [hiccup.core :as hiccup]
            [ring.mock.request :as mock]
            [cinema-ratings.view :as view]))

(defn- get-rated-movies-html [page-html]
  (->> page-html
       (hickory/parse)
       (hickory/as-hickory)
       (selector/select (selector/child (selector/class :rated-movie)))
       (mapv render/hickory-to-html)))

(use-fixtures :each (fn [test-f]
                      (test-f)
                      (cache/reset)))

(deftest sorted-by-rating
  (with-fake-routes-in-isolation
    {fixtures/yorck-list-url       (fixtures/yorck-list-ok)
     fixtures/hateful-8-search-url (fixtures/status-ok fixtures/hateful-8-search-page)
     fixtures/hateful-8-detail-url (fixtures/status-ok fixtures/hateful-8-detail-page)
     fixtures/carol-search-url     (fixtures/status-ok fixtures/carol-search-page)
     fixtures/carol-detail-url     (fixtures/status-ok fixtures/carol-detail-page)}
    (let [expected [(hiccup/html (view/movie-item fixtures/hateful-8-rated-movie))
                    (hiccup/html (view/movie-item fixtures/carol-rated-movie))]
          response (atom {})
          success-handler (fn [actual] (swap! response merge actual))
          error-handler identity]

      (async-handler (mock/request :get "/") success-handler error-handler)
      (Thread/sleep 500)

      (is (= (:status @response) 200))
      (is (= (get-rated-movies-html (:body @response))
             expected)))))
