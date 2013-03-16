(ns ifirc.core
  (:gen-class)
  (:use [saturnine.core]
        [saturnine.handler]
        [clojure.string :only [split-lines]]
        [ifirc.mogrify]
        [ifirc.mogs])
  (:import [saturnine.core.internal Print]))

; We need two connections. One is a Server, with an upstream line that translates IRC messages into IFMUD ones.
; When connected to, it creates a Client, with an upstream that translates IFMUD messages into IRC ones.
; The topmost handler of each just (write)s to the other.

(defhandler SplitLines []
  "Splits multi-line messages into individual lines. Use after :string."
  (upstream [this msg]
    (dorun (map send-up (split-lines msg))))
  (downstream [this msg]
    (send-down (str msg "\n"))))

(defhandler Mogrifier [mogs]
  ""
  (upstream [this msg]
    (cond
      (domogs mogs msg) nil
      :else (send-up msg))))

(defhandler MUD [irc]
  ""
  (upstream [this msg]
    (write irc msg))
  (disconnect [this]
    (write irc ":-IFMUD- NOTICE you :Disconnected by server.")
    (close irc)))

(defhandler IRC [host port]
  "Given a host and port of a MUD to connect to, proxy IRC connections to that MUD, translating between them."
  (connect [this]
    (let [client (start-client :nonblocking :string (new SplitLines) (new Print "MUD ") (new Mogrifier from-mud) (new MUD (get-connection)))]
      (assoc this :mud (open client host port))))
  (upstream [this msg]
    (write (:mud this) msg))
  (disconnect [this]
    (write (:mud this) "quit")
    (close (:mud this))))

(defn -main
  "Proxy between IRC and IFMUD"
  [listen host port]
  (let [listen (Integer. listen)
        port   (Integer. port)]
    (start-server 1234 :nonblocking :string (new SplitLines) (new Print "IRC ") (new Mogrifier from-irc) (new IRC host port)))
    (println "Proxy ready to connect to" host ":" port))
