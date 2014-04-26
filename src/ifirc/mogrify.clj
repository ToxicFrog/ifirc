(ns ifirc.mogrify
  (:use [saturnine.core]
        [saturnine.handler]
        [clojure.string :only [split-lines join]])
  (:import [saturnine.core.internal Print]))

(defmacro defmogs
  "Define a list of mogrifications to apply to a stream of lines of text.

  Each mogrification has the form
    (re [args] body)
  and is compiled into a function f that when called as (f line), evaluates (re-matches re line). If the result is nil,
  it returns false. Otherwise, it binds the result(s) to args, evaluates body, and returns true.

  The resulting list is intended for use with (domogs) and (mogrify)."
  [name & mogs]
  (defn defmog [regex args & body]
    (assert (= java.util.regex.Pattern (type regex)) "Wrong pattern type in defmog")
    `(fn [line#]
      (let [match# (re-matches ~regex line#)
            handler# (fn ~args ~@body true)]
        (cond
          (= match# nil) false
          (string? match#) (handler# match#)
          (vector? match#) (apply handler# match#)))))
  (let [mogs (map (partial apply defmog) mogs)]
    `(def ~name [~@mogs])))


(defn domogs
  "Apply a list of mogrifications created with (defmogs) to a line of text.

  Sequentially evaluates mogs to find the first one that matches line, and evaluates that mog's handler. Returns true
  if a mog matched, false otherwise."
  [mogs line]
  (some #(% line) mogs))


(defhandler SplitLines []
  "Splits multi-line messages into individual lines. Use after :string."
  (upstream [this msg]
    (dorun (map send-up (split-lines msg))))
  (downstream [this msg]
    (send-down (str msg "\n"))))


(def ^:dynamic *forward* send-up)
(def ^:dynamic *reply* send-down)
(def ^:dynamic *state* {})

(defn to-irc [& rest]
  (send-down (apply str rest)))

(defn to-mud [& rest]
  (send-up (apply str rest)))

(defn get-state
  ([] *state*)
  ([key] (key *state*)))

(defn set-state
  ([state] (set! *state* state))
  ([key value] (set! *state* (assoc *state* key value))))

(defhandler Mogrifier [up-mogs down-mogs]
  "Bidirectional text filter. up-mogs and down-mogs should be mogrification lists created with (defmogs)."
  ; send errors to the rawlog
  (error [this err]
    (send-down (str ":!! PRIVMSG &raw :" err)))
  (upstream [this msg] ; messages to the MUD server
    (binding [*state* this]
      (domogs (var-get up-mogs) msg)
      *state*))
  (downstream [this msg] ; messages to the IRC client
    (binding [*state* this]
      (domogs (var-get down-mogs) msg)
      *state*)))

(defhandler IFMUDRawLogger []
  "Sits between the main mogrifier and the MUD. Turns MUD messages into RAW messages to the IRC client for debugging."
  (upstream [this msg]
    (send-up msg)
    (if (not= "qidle" msg)
      (send-down (str "RAW >> " msg))))
  (downstream [this msg]
    (send-down msg)
    (send-down (str "RAW << " msg))))

(defhandler MogClientConnector [client]
  "The upstream-most handler in the mogrifier half connected to the server. Forwards messages to the client half."
  (upstream [this msg]
    (write client msg))
  (disconnect [this]
    (close client)))


(defhandler MogServerConnector [host port]
  "The upstream-most handler in the mogrifier half connected to the client. Creates a connection to the server on
  (connect), and forwards messages to it."
  (connect [this]
    (let [to-server (start-client :blocking :string (new SplitLines) (new MogClientConnector (get-connection)))]
      (assoc this :server (open to-server host port))))
  (upstream [this msg]
    (write (:server this) msg))
  (disconnect [this]
    (close (:server this))))


(defn start-mogrifier [listen host port up-mogs down-mogs]
  "Start a proxy for bidirectional line-oriented text filtering based on the defmogs up-mogs and down-mogs. FIXME:
  currently no support for connect/disconnect events in user code."
  (start-server listen :blocking
    :string
    (new SplitLines)
    (new Mogrifier up-mogs down-mogs)
    (new IFMUDRawLogger)
    (new MogServerConnector host port)))
