(ns ifirc.mogs
  (:use [saturnine.handler]
        [ifirc.mogrify]))

(def channels #{
  "#videogames"
  "#programming"
  "#apropros-of-nothing"
  "#tangent"
  "#tangent-tangent"
  "#books"
  "#gender"
  "#minecraft"
  "#google"
  })

(defmogs from-irc
  (#"NICK (.*)" [_ nick]
    (let [state (assoc (get-state) :nick nick)]
      (println state)
      (cond
        (:pass state) (forward "connect " nick " " (:pass state)))
      (set-state state)))

  (#"PASS (.*)" [_ pass]
    (let [state (assoc (get-state) :pass pass)]
      (cond
        (:nick state) (forward "connect " (:nick state) " " pass))
      (set-state state)))

  (#"QUIT :.*" [_]
    (forward "quit"))

  (#"JOIN (.*)" [_ chan]
    (reply ":" (get-state :nick) " JOIN " chan)
    (forward "@joinc " chan))

  (#"PART (.*)" [_ chan]
    (reply ":" (get-state :nick) " PART " chan)
    (forward "@leavec " chan))

  (#"WHO .*" [_]  ; FIXME - there needs to be a proper API for eating unwanted messages
    true)

  (#"MODE .*" [_]
    true)

  (#"PING (.*)" [_ time]
    (forward "x ping reflector")
    (reply ":IFMUD PONG IFMUD :" time))

  (#"PRIVMSG (#.+?) :\u0001ACTION (.*)\u0001" [_ chan action]
    (forward chan " :" action))

  (#"PRIVMSG (#.+?) :(\w+): (.*)" [_ chan target msg]
    (forward chan " .." target " " msg))

  (#"PRIVMSG (#.+?) :(.*)" [_ chan msg]
    (forward chan " " msg))

  (#"PRIVMSG &(?:IFMUD|channels) :(.*)" [_ msg]
    (forward msg))

  (#".*" [line]
    (reply "[???] " line)))

(defmogs from-mud
  (#"Login Succeeded" [_]
    (reply "lounge")
    (reply "@listc -member")
    (forward ":IFMUD 376 " (get-state :nick) " :End of MOTD")
    (forward ":" (get-state :nick) " JOIN &IFMUD")
    (forward ":" (get-state :nick) " JOIN &raw")
    (forward ":" (get-state :nick) " JOIN &channels"))

  (#"It reflects pings to keep someone from disconnecting." [_]
    true)

  ;bb message
  ;#666 [alt/satan] From: The Pope
  (#"#\d+ \[[^\]]+\].*" [line]
    (forward ":* PRIVMSG &IFMUD :" line))

  ;channel topic line from @joinc or @listc
  ;#alt/random/markov-chains: not the face, not the face
  ;used to determine which channels the user is in
  (#"#.*?/([^/]+)\s*:\s*(.*)" [_ chan topic]
    (let [chan (str "#" (.trim chan))]
      (cond
        (channels chan) (do
          (forward ":" (get-state :nick) " JOIN " chan)
          (forward ":IFMUD 332 " (get-state :nick) " " chan " :" topic))
        :else (forward ":[" chan "] PRIVMSG &channels :Joined."))))

  ;targeted channel message - [foo] Someone says (to SomeoneElse), "stuff"
  (#"\[(.+?)\] (.+?) (?:says|asks|exclaims) \((?:to|of|at) (\w+)\), \"(.*)\"" [_ chan user target msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true
        :else (cond
          (channels chan) (forward ":" user " PRIVMSG " chan " :" target ": " msg)
          :else (forward ":[" chan "] PRIVMSG &channels :<" user "> " target ": " msg)))))

  ;untargeted channel message - [foo] Someone says, "stuff"
  (#"\[(.+?)\] (.+?) (?:says|asks|exclaims), \"(.*)\"" [_ chan user msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true
        :else (cond
          (channels chan) (forward ":" user " PRIVMSG " chan " :" msg)
          :else (forward ":[" chan "] PRIVMSG &channels :<" user "> " msg)))))

  ;channel action
  (#"\[(.+?)\] (.+?) (.*)" [_ chan user action]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true
        :else (cond
          (channels chan) (forward ":" user " PRIVMSG " chan " :\u0001ACTION " action "\u0001")
          :else (forward ":[" chan "]<" user "> PRIVMSG &channels :\u0001ACTION " action "\u0001")))))

  (#"\[(.+?)\] \* (.+?) has joined the channel." [_ chan user]
    (forward ":" user " JOIN #" chan))

  (#"\[(.+?)\] (.*)" [_ chan msg]
    (forward ":* PRIVMSG #" chan " :" msg))

  (#".*" [line]
    (forward ":* PRIVMSG &IFMUD :" line)))
