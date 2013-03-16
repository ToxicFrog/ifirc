(ns ifirc.core
  (:gen-class)
  (:use [saturnine.core]
        [saturnine.handler]
        [clojure.string :only [split-lines]]
        [ifirc.mogrify])
  (:import [saturnine.core.internal Print]))

; We need two connections. One is a Server, with an upstream line that translates IRC messages into IFMUD ones.
; When connected to, it creates a Client, with an upstream that translates IFMUD messages into IRC ones.
; The topmost handler of each just (write)s to the other.

(defpatterns irc-patterns
  (#"PASS (.*)" [_ auth]
    (str "connect " auth))
  (#"PRIVMSG -IFMUD- :(.*)" [_ msg]
    msg))

(defpatterns mud-patterns
  (#".*" [line]
    (str ":-IFMUD- PRIVMSG you :" line)))

(defn irc-to-mud [msg]
  (dopattern irc-patterns msg))

(defn mud-to-irc [msg]
  (dopattern mud-patterns msg))

(defhandler SplitLines []
  "Splits multi-line messages into individual lines. Use after :string."
  (upstream [this msg]
    (dorun (map send-up (split-lines msg))))
  (downstream [this msg]
    (send-down (str msg "\n"))))

(defhandler MUD [irc]
  ""
  (upstream [this msg]
    (write irc (mud-to-irc msg)))
  (disconnect [this]
    (close irc)))

(defhandler IRC [host port]
  "Given a host and port of a MUD to connect to, proxy IRC connections to that MUD, translating between them."
  (connect [this]
    (let [client (start-client :nonblocking :string (new SplitLines) (new Print "MUD ") (new MUD (get-connection)))]
      (assoc this :mud (open client host port))))
  (upstream [this msg]
    (write (:mud this) (irc-to-mud msg)))
  (disconnect [this]
    (close (:mud this))))

(defn -main
  "Proxy between IRC and IFMUD"
  [listen host port]
  (let [listen (Integer. listen)
        port   (Integer. port)]
    (println "Proxy ready to connect to" host ":" port)
    (start-server 1234 :nonblocking :string (new SplitLines) (new Print "IRC ") (new IRC host port))))
