(defproject ifirc "0.1.0-SNAPSHOT"
  :description "A bridge between an IRC client and IFMUD"
  :url "telnet://ifmud.port4000.com:4000/"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [saturnine "0.3-SNAPSHOT"]]
  :main ifirc.core)
