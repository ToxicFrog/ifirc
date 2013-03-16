(ns ifirc.mogrify
  (:use [saturnine.core]
        [saturnine.handler]
        [clojure.string :only [split-lines join]]))

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

; (mogrify listen host port upstream downstream)
(defn mogrify
  "Start a proxy that bidirectionally mogrifies text passing through it.

  When started, will listen on listen-port for client connections. Each such connection will be paired to an outgoing
  connection to server-host:server-port. Messages from the client to the server will have (domogs upstream) applied to
  them; messages from the server to the client will have (domogs downstream).

  Internally, uses saturnine for networking and filter chaining; up-filters and down-filters are sequences of additional
  saturnine filters to apply. The first two filters are always String and SplitLines, as without those (domogs) won't
  work; you might want to add something like Print for logging."
  ([listen-port server-host server-port upstream downstream]
    (mogrify listen-port server-host server-port upstream downstream [] []))
  ([listen-port server-host server-port upstream downstream up-filters down-filters]
    nil))
