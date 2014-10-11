(ns ifirc.core
  (:gen-class)
  (:require
    [ifirc.net :refer :all]
    [ifirc.mogs :refer :all]))

; Main thread listens for incoming connections. When it gets one, it connects
; to ifmud, and then spawns two threads for each socket plus a fifth for the
; mogrifier, interconnected by sockets?

(defn -main
  "Proxy between IRC and IFMUD"
  [listen host port]
  (let [listen (Integer. listen)
        port   (Integer. port)]
    (run-proxy listen host port from-irc from-mud)))
