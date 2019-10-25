(ns ifirc.mogrify
  (:require
    [taoensso.timbre :as log]
    [clojure.core.async :as async :refer [<!!]]))

(defmacro defmogs
  "Define a list of mogrifications to apply to a stream of lines of text.

  Each mogrification has the form
    (re [args] body)
  and is compiled into a function f that when called as (f line), evaluates (re-matches re line). If the result is nil,
  it returns false. Otherwise, it binds the result(s) to args and evaluates body.

  There are two special values that body can evaluate to. :continue causes the mogrification to evaluate to false,
  causing (domogs) to fall through to the next one. :exit causes the entire mogrifier to exit, disconnecting the
  associated user.

  If the body evaluates to neither of these values, it is forced to true.

  The resulting list is intended for use with (domogs) and (mogrify)."
  [name & mogs]
  (defn defmog [regex args & body]
    (assert (= java.util.regex.Pattern (type regex)) "Wrong pattern type in defmog")
    `(fn [line#]
      (let [match# (re-matches ~regex line#)
            handler# (fn ~args ~@body)
            ]
        (cond
          (= match# nil) :continue
          (string? match#) (handler# match#)
          (vector? match#) (apply handler# match#)))))
  (let [mogs (map (partial apply defmog) mogs)]
    `(def ~name [~@mogs])))

(defn- mog-return [value]
  (case value
    :continue false
    :exit :exit
    true))

(defn- domogs
  "Apply a list of mogrifications created with (defmogs) to a line of text.

  Sequentially evaluates mogs to find the first one that matches line, and evaluates that mog's handler. Returns true
  if a mog matched, false otherwise."
  [mogs line]
  (log/trace line)
  (let [result (some #(-> line % mog-return) mogs)]
    (log/trace "mog result" result)
    (not= result :exit)))

(def ^:dynamic *state* {})
(def ^:dynamic *irc-writer* nil)
(def ^:dynamic *mud-writer* nil)

(defn to-irc [& msg]
  (log/info "<<" (apply str msg))
  (.println *irc-writer* (apply str msg)))

(defn- rawlog [dir msg]
  (to-irc ":" dir " PRIVMSG ##raw :" msg))

; like to-mud, but doesn't log
(defn to-mud-silent [& msg]
  (.println *mud-writer* (apply str msg)))

(defn to-mud [& msg]
  (apply to-mud-silent msg)
  (rawlog "<<" (apply str msg)))

(defn get-state
  ([] *state*)
  ([key] (key *state*)))

(defn set-state
  ([state] (set! *state* state))
  ([key value] (set! *state* (assoc *state* key value))))

(defn mogrifier [incoming irc-mogs mud-mogs]
  (loop [[id msg] (<!! incoming)]
    (if (case id
          :irc (do
                 (log/info ">>" (apply str msg))
                 (domogs irc-mogs msg))
          :mud (do
                 (rawlog ">>" (apply str msg))
                 (domogs mud-mogs msg))
          false)
      (recur (<!! incoming)))))
