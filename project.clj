(defproject ifirc "0.1.0"
  :description "A bridge between an IRC client and IFMUD"
  :url "telnet://ifmud.port4000.com:4000/"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.301.0-deb34a-alpha"]
                 [com.taoensso/timbre "3.1.6"]]
  :main ^:skip-aot ifirc.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
