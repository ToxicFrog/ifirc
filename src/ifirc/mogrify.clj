(ns ifirc.mogrify
  (:require
    [taoensso.timbre :as log]
    [clojure.core.async :as async :refer [<!!]]))

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

(def ^:dynamic *state* {})
(def ^:dynamic *irc-writer* nil)
(def ^:dynamic *mud-writer* nil)

(defn to-irc [& msg]
  (log/trace (str "IRC " msg))
  (.println *irc-writer* (apply str msg)))

(defn rawlog [dir msg]
  (to-irc ":" dir " PRIVMSG &raw :" msg))

(defn to-mud [& msg]
  (log/trace (str "MUD " msg))
  (.println *mud-writer* (apply str msg))
  (rawlog "<<" (apply str msg)))

(defn get-state
  ([] *state*)
  ([key] (key *state*)))

(defn set-state
  ([state] (set! *state* state))
  ([key value] (set! *state* (assoc *state* key value))))

(defn mogrifier [incoming irc-mogs mud-mogs]
  (doseq [[id msg] (repeatedly #(<!! incoming))]
    (case id
      :irc (domogs irc-mogs msg)
      :mud (do
             (rawlog ">>" (apply str msg))
             (domogs mud-mogs msg)))))
