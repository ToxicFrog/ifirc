(ns ifirc.core
  (:gen-class)
  (:require
    [ifirc.net :refer :all]
    [ifirc.mogs :refer :all]
    [ifirc.flags :refer :all]))

(defn -main
  "Proxy between IRC and IFMUD"
  [& argv]
  (binding [*options* (parse-opts argv)]
    (run-proxy (*options* :listen-port) (*options* :mud-host) (*options* :mud-port) from-irc from-mud)))
