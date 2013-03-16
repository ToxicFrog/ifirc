(ns ifirc.mogs
  (:use [saturnine.handler]
        [ifirc.mogrify]))

(defn reply [& rest]
  (send-down (apply str rest)))

(defn forward [& rest]
  (send-up (apply str rest)))

(defmogs from-irc
  (#"PASS (.*)" [_ auth]
    (forward "connect " auth))

  (#"QUIT :.*" [_]
    (forward "quit"))

  (#"JOIN (.*)" [_ chan]
    (reply ":ToxicFrog JOIN " chan)
    (forward "@joinc " chan))

  (#"PART (.*)" [_ chan]
    (reply ":ToxicFrog PART " chan)
    (forward "@leavec " chan))

  (#"WHO .*" [_]  ; FIXME - there needs to be a proper API for eating unwanted messages
    true)

  (#"MODE .*" [_]
    true)

  (#"PRIVMSG (#.+?) :(.*)" [_ chan msg]
    (forward chan " " msg))

  (#"PRIVMSG -IFMUD- :(.*)" [_ msg]
    (println ">>" msg)
    (forward msg)))

(defmogs from-mud
  (#"Login Succeeded" [_]
    (reply "@listc -member"))

  ;#alt/random/markov-chains: not the face, not the face
  (#"#.*?/([^/]+)\s*:\s*(.*)" [_ chan topic]
    (forward ":ToxicFrog JOIN #" chan)
    (forward "TOPIC #" chan " :" topic))

  (#"\[(.+?)\] (.+?) says, \"(.*)\"" [_ chan user msg]
    (forward ":" user " PRIVMSG #" chan " :" msg))

  (#"\[(.+?)\] \* (.+?) has joined the channel." [_ chan user]
    (forward ":" user " JOIN #" chan))

  (#"\[(.+?)\] (.*)" [_ chan msg]
    (forward ":-IFMUD- PRIVMSG #" chan " :" msg))

  (#".*" [line]
    (forward ":-IFMUD- PRIVMSG you :" line)))
