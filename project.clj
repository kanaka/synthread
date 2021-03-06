(defproject lonocloud/synthread "1.0.1"
  :description "Syntax Threading library"
  :url "http://github.com/lonocloud/synthread/"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :min-lein-version "2.0.0"
  :profiles {:1.2 {:dependencies [[org.clojure/clojure "1.2.0"]]}
             :1.3 {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.0-RC2"]]}}
  :aliases {"1.2" ["with-profile" "1.2"]
            "1.3" ["with-profile" "1.3"]
            "1.4" ["with-profile" "1.4"]
            "1.5" ["with-profile" "1.5"]})
