(ns yorck-ratings.yorck-test
  (:require [yorck-ratings.yorck :as yorck]
            [clj-http.fake :refer [with-fake-routes-in-isolation]]
            [midje.sweet :refer [fact =>]]
            [clojure.core.async :as async]
            [yorck-ratings.rated-movie :as rated-movie]
            [yorck-ratings.fixtures :as fixtures]))

(def a-title "a title")
(def a-url "a url")
(defn- make-get-page-stub [yorck-info]
  (fn [] (async/to-chan [yorck-info])))

(fact "returns rated-movies with yorck info"
      (let [get-page-stub (make-get-page-stub [["a title" a-url]])
            result-channel (async/chan)]

        (yorck/infos get-page-stub result-channel)

        (async/<!! result-channel) =>
        [(rated-movie/from-yorck-info [a-title a-url])]))

(fact "removes sneak previews"
      (let [get-page-stub (make-get-page-stub [["a title" a-url] ["a sneak preview" "another url"]])
            result-channel (async/chan)
            _ (yorck/infos get-page-stub result-channel)]

        (async/<!! result-channel) => [(rated-movie/from-yorck-info [a-title a-url])]))

(defn without-dimension [title]
  (let [get-page-stub (make-get-page-stub [[title a-url]])
        result-channel (async/chan)]
    (yorck/infos get-page-stub result-channel)
    (async/<!! result-channel)))

(fact "removes dimension from title"
      (without-dimension "Pets - 2D") => [(rated-movie/from-yorck-info ["Pets" a-url])]

      (without-dimension "Ice Age - Kollision voraus! 2D!") =>
      [(rated-movie/from-yorck-info ["Ice Age - Kollision voraus!" a-url])])

(defn- with-rotated-article [title]
  (let [get-page-stub (make-get-page-stub [[title a-url]])
        result-channel (async/chan)]
    (yorck/infos get-page-stub result-channel)
    (async/<!! result-channel)))

(fact "rotates articles"
      (with-rotated-article "Hateful 8, The") =>
      [(rated-movie/from-yorck-info ["The Hateful 8" a-url])]

      (with-rotated-article "Hail, Caesar!") =>
      [(rated-movie/from-yorck-info ["Hail, Caesar!" a-url])]

      (with-rotated-article "Brandneue Testament, Das") =>
      [(rated-movie/from-yorck-info ["Das Brandneue Testament" a-url])]

      (with-rotated-article "Unterhändler, Der") =>
      [(rated-movie/from-yorck-info ["Der Unterhändler" a-url])]

      (with-rotated-article "Winzlinge, Die - Operation Zuckerdose") =>
      [(rated-movie/from-yorck-info ["Die Winzlinge - Operation Zuckerdose" a-url])])

(fact "pulls titles and urls from yorck page"
      (with-fake-routes-in-isolation
        {fixtures/yorck-list-url (fixtures/yorck-list-ok)}
        (let [expected [[fixtures/carol-yorck-title fixtures/carol-yorck-url]
                        [fixtures/hateful-8-yorck-unfiltered-title fixtures/hateful-8-yorck-url]
                        ["Sneak FAF" "https://www.yorck.de/filme/sneak-faf"]]]

          (async/<!! (yorck/get-yorck-infos-async)) => expected)))
