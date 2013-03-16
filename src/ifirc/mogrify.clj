(ns ifirc.mogrify
  (:use [saturnine.core]
        [saturnine.handler]
        [clojure.string :only [split-lines join]]))

(defmacro defpatterns [name & patterns]
  (defn defpattern [regex args & body]
    `(fn [line#]
       (case (re-matches ~regex line#)
         nil nil
         (apply (fn ~args ~@body) (flatten (vector (re-matches ~regex line#)))))))
  (let [patterns (map (partial apply defpattern) patterns)]
    `(def ~name [~@patterns])))

(defn dopattern [patterns line]
  (let [output (some #(% line) patterns)]
    (cond
      output output
      :else line)))

