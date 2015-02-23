(ns httpcrawler.parser
  (:require [net.cgrand.enlive-html :as html])
  (:use [url-normalizer.core :exclude (resolve)]))

(defn- parse-html [html-source]
  (-> html-source java.io.StringReader. html/html-resource))

(defn extract-urls [base body]
  (let [base-uri (normalize base)
        internal-host (.getHost base-uri)
        internal-port (.getPort base-uri)
        base-str (.toString base-uri)
        parsed-body (parse-html body)]
    (->>
     (concat (->> (html/select parsed-body [:a])
                  (map :attrs)
                  (map :href)
                  (map #(normalize % {:base base-str})))
             (->> (html/select parsed-body [:img])
                  (map :attrs)
                  (map :src)
                  (map #(normalize % {:base base-str}))))
     (filter #(and (= internal-host (.getHost %))
                   (= internal-port (.getPort %))))
     (map #(.toString %)))))

#_(normalize "http://cucu.mucu.com")
#_(normalize "http://cucu.mucu.com/aaaa/../bbb" {:base "https://www.xyz.com/vvvv/xxx"})
#_(normalize "/aaaa/../bbb" {:base "https://www.xyz.com/vvvv/xxx"})
#_(normalize "aaaa/../bbb" {:base "https://www.xyz.com/vvvv/xxx"})
#_(normalize "aaaa/../bbb" {:base "https://www.xyz.com/vvvv/xxx/"})

#_(extract-urls "http://base.url.com" "<html><body><a href=\"test.html\"><img src=\"images/test.jpg\">test doc</a>
              <a href=\"http://alt.site.com/link.html\">alt site</a></body>")

#_(html/select (parse-html "<html><body><a href=\"test.html\">test doc</a></body>") [:a])

#_(->> (html/select (parse-html "<html><body><a href=\"test.html\">test doc</a></body>") [:a])
         (map :attrs)
         (map :href)
         (map #(normalize % {:base "http://xyz.com/"})))

#_(-> "http://test.com/test.html" java.net.URI. .toString)
