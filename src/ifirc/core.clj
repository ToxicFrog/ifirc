(ns ifirc.core
  (:gen-class)
  (:use [ifirc.mogrify]
        [ifirc.mogs])
  (:import [saturnine.core.internal Print]))

(defn -main
  "Proxy between IRC and IFMUD"
  [listen host port]
  (let [listen (Integer. listen)
        port   (Integer. port)]
    (start-mogrifier listen host port #'from-irc #'from-mud)
    (println "Proxy ready to connect to" host ":" port)))
