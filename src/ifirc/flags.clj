(ns ifirc.flags
  (:gen-class)
  (:require
    [clojure.tools.cli :as cli]))

(def ^:dynamic *options* nil)

(def flags
  [["-l" "--listen-port PORT" "Port to listen for IRC connections on"
    :default 4000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-p" "--mud-port PORT" "Port to connect to the MUD on"
    :default 4000
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-m" "--mud-host HOSTNAME" "MUD server to connect to"
    :default "ifmud.port4000.com"]
   ["-x" "--autoexec COMMAND" "Command to run upon connecting"
    :default "lounge"]
   ["-j" "--autojoin" "Automatically @joinc channels"]
   ["-J" "--autochan" "Automatically @joinc and @leavec"]
   ["-h" "--help" "This text"]])

(defn parse-opts [argv]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts argv flags)]
    (cond
      (:help options) (do
                        (println summary)
                        (System/exit 0))
      errors (do
               (binding [*out* *err*] (dorun (map println errors))
               (println summary)
               (System/exit 1))))
    options))
