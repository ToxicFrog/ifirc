(ns ifirc.mogrify
  (:use [saturnine.core]
        [saturnine.handler]
        [clojure.string :only [split-lines join]]))

(defmacro defmogs [name & mogs]
  (defn defmog [regex args & body]
    `(fn [line#]
       (case (re-matches ~regex line#)
         nil nil
         (apply (fn ~args ~@body) (flatten (vector (re-matches ~regex line#)))))))
  (let [mogs (map (partial apply defmog) mogs)]
    `(def ~name [~@mogs])))

(defn domogs [mogs line]
  (let [output (some #(% line) mogs)]
    (cond
      output output
      :else line)))

