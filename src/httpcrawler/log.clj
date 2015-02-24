(ns httpcrawler.log)

(defmacro with-stderr [& exprs]
  `(binding [*out* *err*]
    (do ~@exprs)))


#_(with-stderr (println 1) (println 2) (println 3))
