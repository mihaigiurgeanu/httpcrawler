(ns httpcrawler.core
  (:require [org.httpkit.client :as http]
            [clojure.core.async :refer [chan go go-loop <! close! put! <!!]]
            [httpcrawler.timer :as timer])
  (:use [httpcrawler.parser :only [extract-urls]]))

(def ^:dynamic *permissions-channel*)
(def ^:dynamic *http-options* {})
(def ^:dynamic *pending-requests*)
(def ^:dynamic *responses-count*)
(def ^:dynamic *no-urls-left*)
(def ^:dynamic *added-urls*)

(defn- send-request! [url save-fn]
  (let [permissions-channel *permissions-channel*
        pending-requests *pending-requests*
        responses-count *responses-count*
        no-urls-left *no-urls-left*
        out *out*
        err *err*]
    (swap! pending-requests + 1)
    (try
      (http/get
       url
       *http-options*
       (fn [{:keys [body error] :as response}]
         (binding [*permissions-channel* permissions-channel
                   *responses-count* responses-count
                   *no-urls-left* no-urls-left
                   *pending-requests* pending-requests
                   *out* out
                   *err* err]
           (if error
             (put! permissions-channel {:url url :error error})
             (put! permissions-channel {:url url :urls (extract-urls body)}))
           (send responses-count
                 (fn [crt-rsp-no]
                   (let [this-rsp-no (+ crt-rsp-no 1)]
                     (save-fn url response)
                     this-rsp-no))))))
      (catch Exception e
        (put! permissions-channel {:url url :error e})
        (send responses-count
              (fn [crt-rsp-no]
                (save-fn url {:error e})
                (+ 1 crt-rsp-no)))))))

(defn- add-urls-from-permission [current-urls {:keys [error urls url]}]
  (when error
    (binding [*out* *err*]
      (if (instance? Exception error)
        (do
          (println "Exception for" url (.getMessage error))
          (.printStrackTrace error *err*))
        (println "Error for" url error))))

  (if (empty? urls)
    current-urls
    (loop [resulting-urls (transient current-urls) to-add urls]
      (if (empty? to-add)
        (persistent! resulting-urls)
        (let [[next-to-add & rest-to-add] to-add]
          (recur
           (if (contains? @*added-urls* next-to-add)
             resulting-urls
             (do
               (swap! *added-urls* conj next-to-add)
               (conj! resulting-urls next-to-add)))
           rest-to-add))))))

#_(binding [*added-urls* (atom #{"http://server1"})]
  (add-urls-from-permission #{"http://server2"} {:urls ["http://server1" "http://server2" "http://server3"]})
  (add-urls-from-permission #{} {:urls ["http://server1" "http://server2" "http://server3"]}))

(defn- send-requests! [urls save-fn]
  (doseq [url urls] (send-request! url)))

(defn crawl
  "Crawls all the internal links of a site, calling save-fn function for
  each response retrieved. The save-fn function is called passing the url
  and the http-kit's response."
  [start-page-url save-fn batch-size http-options]
  (let [urls #{start-page-url}]
    (binding [*permissions-channel* (chan batch-size)
              *pending-requests* (atom 0)
              *http-options* http-options
              *responses-count* (agent 0)
              *no-urls-left* (atom false)
              *added-urls* (atom urls)]

      (send-request! start-page-url save-fn)
      ;;send the rest of the request
      (loop [unprocessed-urls []]
        (when-let [permission (<!! *permissions-channel*)]
          (swap! *pending-requests* - 1)
          (let [unprocessed-urls- (add-urls-from-permission unprocessed-urls permission)]
            (if (not-empty unprocessed-urls)
              (let [to-send [- batch-size @*pending-requests*]
                    us (take to-send unprocessed-urls-)
                    us-rest (drop to-send unprocessed-urls-)]
                (reset! *no-urls-left* false)
                (send-requests! us save-fn)
                (recur us-rest))
              (do
                (reset! *no-urls-left* true)
                (when (<= @*pending-requests* 0) (close! *permissions-channel*))
                (recur []))))))

      (println (timer/ms) "Done!" @*responses-count* "urls processed"))))
