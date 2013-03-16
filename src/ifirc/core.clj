(ns ifirc.core
  (:gen-class)
  (:use [saturnine.core]
        [saturnine.handler])
  (:import [saturnine.core.internal Print]))

; We need two connections. One is a Server, with an upstream line that translates IRC messages into IFMUD ones.
; When connected to, it creates a Client, with an upstream that translates IFMUD messages into IRC ones.
; The topmost handler of each just (write)s to the other.

(defhandler MUD2IRC [irc]
  ""
  (upstream [this msg]
    (write irc msg))
  (disconnect [this]
    (close irc)))

(defhandler IRC2MUD [host port]
  "Given a host and port of a MUD to connect to, proxy IRC connections to that MUD, translating between them."
  (connect [this]
    (let [client (start-client :nonblocking :string (new Print "MUD ") (new MUD2IRC (get-connection)))]
      (assoc this :mud (open client host port))))
  (upstream [this msg]
    (write (:mud this) msg))
  (disconnect [this]
    (close (:mud this))))

(defn -main
  "Proxy between IRC and IFMUD"
  [listen host port]
  (let [listen (Integer. listen)
        port   (Integer. port)]
    (println "Proxy ready to connect to" host ":" port)
    (start-server 1234 :nonblocking :string (new Print "IRC ") (new IRC2MUD host port))))
