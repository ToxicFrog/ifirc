(ns ifirc.core
  (:gen-class)
  (:use [saturnine.core]
        [saturnine.handler]))

(defhandler Test [host port]
  "Given a host and port of a MUD to connect to, proxy IRC connections to that MUD, translating between them."
  (connect [this] (println this (connection)))
  (disconnect [this] (println this))
  (upstream [this msg] (println this msg) (assoc this :server "aaaa"))
  (downstream [this msg] (println this msg))
  (error [this msg] (println this msg)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (start-server 1234 :nonblocking :string :print (new Test "localhost" 1234)))
