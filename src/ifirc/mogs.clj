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

  (#"PING (.*)" [_ time]
    (reply ":IFMUD PONG IFMUD :" time))

  (#"PRIVMSG (#.+?) :\u0001ACTION (.*)\u0001" [_ chan action]
    (forward chan " :" action))

  (#"PRIVMSG (#.+?) :(\w+): (.*)" [_ chan target msg]
    (forward chan " .." target " " msg))

  (#"PRIVMSG (#.+?) :(.*)" [_ chan msg]
    (forward chan " " msg))

  (#"PRIVMSG &IFMUD :(.*)" [_ msg]
    (forward msg))

  (#".*" [line]
    (reply "[???] " line)))

(defmogs from-mud
  (#"Login Succeeded" [_]
    (reply "@listc -member")
    (forward ":IFMUD 376 ToxicFrog :End of MOTD")
    (forward ":ToxicFrog JOIN &IFMUD")
    (forward ":ToxicFrog JOIN &channels"))

  ;bb message
  ;#666 [alt/satan] From: The Pope
  (#"#\d+ \[[^\]]+\].*" [line]
    (forward ":* PRIVMSG &IFMUD :" line))

  ;channel topic line from @joinc or @listc
  ;#alt/random/markov-chains: not the face, not the face
  (#"#.*?/([^/]+)\s*:\s*(.*)" [_ chan topic]
    (forward ":ToxicFrog JOIN #" chan)
    (forward ":IFMUD 332 ToxicFrog #" chan " :" topic))

  (#"\[(.+?)\] ToxicFrog (?:says|asks|exclaims) \((?:to|of|at) (\w+)\), \"(.*)\"" [_ chan target msg]
    true)

  (#"\[(.+?)\] ToxicFrog (says|asks|exclaims), \"(.*)\"" [_ chan _ msg]
    true)

  (#"\[(.+?)\] (.+?) (?:says|asks|exclaims) \((?:to|of|at) (\w+)\), \"(.*)\"" [_ chan user target msg]
    (forward ":" user " PRIVMSG #" chan " :" target ": " msg))

  (#"\[(.+?)\] (.+?) (?:says|asks|exclaims), \"(.*)\"" [_ chan user msg]
    (forward ":" user " PRIVMSG #" chan " :" msg))

  (#"\[(.+?)\] (.+?) (.*)" [_ chan user action]
    (forward ":" user " PRIVMSG #" chan " :\u0001ACTION " action "\u0001"))

  (#"\[(.+?)\] \* (.+?) has joined the channel." [_ chan user]
    (forward ":" user " JOIN #" chan))

  (#"\[(.+?)\] (.*)" [_ chan msg]
    (forward ":* PRIVMSG #" chan " :" msg))

  (#".*" [line]
    (forward ":* PRIVMSG &IFMUD :" line)))
