; one thread for each socket, reading lines and sending them to the mogrifier
; mogrifier reads messages from its input channel and mogs them
; the output from the mogrifier goes to two channels that write to the sockets

(ns ifirc.net
  (:require
    [clojure.core.async :as async :refer [<!! >!! chan close!]]
    [clojure.java.io :refer [reader writer]]
    [clojure.edn :as edn]
    [taoensso.timbre :as log]
    [ifirc.mogrify :as mogrify])
  (:import
    [java.net Socket ServerSocket]
    [java.io PrintWriter BufferedReader IOException]))

(defmacro try-thread
  "Run a thread in a try-catch block, and react to exceptions by logging them
  and then killing the program rather than the default behaviour, which is to
  swallow them and kill only that thread."
  [name & body]
  (if (and (list? (last body)) (->> body last first (= 'finally)))
    ; It has a "finally" clause that we should splice in after the catch.
    `(async/thread
       (log/infof "Starting thread %s" ~name)
       (try
         ~@(butlast body)
         (catch Throwable e#
           (log/errorf e# "Uncaught exception in thread " ~name)
           (System/exit 1))
         ~(last body)))
    ; No "finally".
    `(async/thread
       (log/infof "Starting thread %s" ~name)
       (try
         ~@body
         (catch Throwable e#
           (log/errorf e# "Uncaught exception in thread " ~name)
           (System/exit 1))))))

(defn reader-seq [reader]
  (take-while
    some?
    (repeatedly #(.readLine reader))))

(defn drain-channel [chan]
  (close! chan)
  (dorun (take-while some? (repeatedly #(<!! chan)))))

(defn socket-thread [id to-mogrifier sock]
  (let [reader (-> sock reader BufferedReader.)
        thread-name (str id " socket reader " (.getInetAddress sock))]
    (try-thread
      thread-name
      (doseq [msg (reader-seq reader)]
        (log/tracef "[%s] >> %s" (str id) msg)
        (>!! to-mogrifier [id msg]))
      (catch IOException e
        (log/error (.getMessage e)))
      (finally
        (drain-channel to-mogrifier)
        (log/infof "Thread %s shutting down" thread-name)))))

(defn mog-thread [client host port irc-mogs mud-mogs]
  (try-thread
    (str "mogrifier thread " (.getInetAddress client))
    (let [server (Socket. host port)
          incoming (chan)]
      (log/infof "Thread %s connected to %s" (.getInetAddress client) (.getInetAddress server))
      (socket-thread :irc incoming client)
      (socket-thread :mud incoming server)
      (binding [mogrify/*irc-writer* (-> client writer (PrintWriter. true))
                mogrify/*mud-writer* (-> server writer (PrintWriter. true))
                mogrify/*state* {}]
        (mogrify/mogrifier incoming irc-mogs mud-mogs)
        ; If the mogrifier returns, it means it got EOF on its incoming channel
        (log/infof "Mogrifier thread for %s got EOF, shutting down." (.getInetAddress client))
        (.close client)
        (.close server)
        (drain-channel incoming)))))

(defn run-proxy [listen host port irc-mogs mud-mogs]
  (log/infof "Opening listen socket on port %d" port)
  (let [sock (ServerSocket. port)]
    (loop []
      (if (not (.isClosed sock))
        (let [client (.accept sock)]
          (log/infof "Accepted client connection from %s" (.getInetAddress client))
          (mog-thread client host port irc-mogs mud-mogs)
          (recur))
        (do
          (log/info "Listen socket closed.")
          (System/exit 0))))))
