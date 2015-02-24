(defproject httpcrawler "0.1.0"
  :description "Very fast web crawler based on http-kit"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [http-kit "2.1.16"]
                 [enlive "1.1.5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [url-normalizer "0.5.3-1"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
