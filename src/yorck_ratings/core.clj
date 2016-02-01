(ns yorck-ratings.core
  (:require [org.httpkit.client :as http]
            [clojure.core.async :as a]
            [hickory.select :as h]
            [clojure.string :as str])
  (:use [hickory.core])
  (:import (java.util.regex Pattern)
           (java.net URLEncoder)))

(defrecord RatedMovie [rating rating-count imdb-title yorck-title])

(defn- add-imdb-title [movie title]
  (assoc movie :imdb-title title))

(def DEFAULT-TIMEOUT 60000)

(defn- error-message [url cause]
  (str "Error fetching URL \"" url "\": " cause))

(defn async-get [url result-ch error-ch]
  (http/get url {:timeout DEFAULT-TIMEOUT}
            (fn [{:keys [status body error]}]
              (a/go
                (if error
                  (let [{cause :cause} (Throwable->map error)]
                    (a/>! error-ch (error-message url cause)))
                  (if (> status 399)
                    (a/>! error-ch (error-message url status))
                    (a/>! result-ch (as-hickory (parse body)))))))))

(defn fetch-yorck-list [result-ch error-ch]
  (async-get "https://www.yorck.de/filme?filter_today=true" result-ch error-ch))

(defn fetch-imdb-info [error-ch title result-ch]
  (let [enc-title (URLEncoder/encode title "UTF-8")
        url (str "https://m.imdb.com/find?q=" enc-title)]
    (async-get url result-ch error-ch)))

(defn rotate-article [title]
  (let [pattern (Pattern/compile "^([\\w\\s]+), (\\w{3})", Pattern/UNICODE_CHARACTER_CLASS)]
    (str/replace-first title pattern "$2 $1")))

(defn yorck-titles [yorck-page]
  (->> yorck-page
       (h/select (h/descendant
                   (h/class :movie-details)
                   (h/tag :h2)))
       (mapcat :content)
       (map rotate-article)
       (map #(RatedMovie. nil nil nil %))
       vec))

(defn imdb-title [sp]
  (->> sp
       (h/select (h/descendant
                   (h/class :posters)
                   (h/class :poster)
                   (h/class :title)
                   (h/tag :a)))
       first
       :content
       first))

(defn imdb-titles [yorck-infos fetch-f]
  (let [chs (repeatedly (partial a/chan 1))]
    (map (fn [movie ch]
           (fetch-f (:yorck-title movie) ch)
           (a/go (add-imdb-title movie (imdb-title (a/<! ch)))))
         yorck-infos chs)))

(defn rated-movies []
  (let [result-ch (a/chan 1 (map yorck-titles))
        error-ch (a/chan 1)]
    (fetch-yorck-list result-ch error-ch)
    (a/go
      (imdb-titles (a/<! result-ch) (partial fetch-imdb-info error-ch)))))