(ns ifirc.mogs
  (:use [saturnine.handler]
        [ifirc.mogrify]))

(defmogs from-irc
  ; ignore capability requests from ZNC
  (#"CAP .*" [_]
    true)
  
  (#"NICK (.*)" [_ nick]
    (set-state :nick nick)
    (println (get-state))
    (if (get-state :pass)
      (forward "connect " (get-state :nick) " " (get-state :pass))))

  (#"PASS (.*)" [_ pass]
    (set-state :pass pass)
    (println (get-state))
    (if (get-state :nick)
      (forward "connect " (get-state :nick) " " (get-state :pass))))

  (#"QUIT :.*" [_]
    (forward "quit"))

  (#"JOIN (.*)" [_ chan]
    (reply ":" (get-state :nick) " JOIN " chan)
    (set-state :channels (conj (get-state :channels) chan)))

  (#"PART (.*)" [_ chan]
    (reply ":" (get-state :nick) " PART " chan)
    (set-state :channels (disj (get-state :channels) chan)))

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

  (#"PRIVMSG &channels :(.*)" [_ msg]
    (forward msg))

  (#"PRIVMSG &IFMUD :\\(.*)" [_ msg]
    (forward msg))

  (#"PRIVMSG &IFMUD :(.*)" [_ msg]
    (forward "\"" msg))

  (#".*" [line]
    (reply "[???] " line)))

(defmogs from-mud
  (#"Login Succeeded" [_]
    (reply "lounge")
    (reply "@listc -member")
    (forward ":IFMUD 376 " (get-state :nick) " :End of MOTD")
    (forward ":" (get-state :nick) " JOIN &IFMUD")
    (forward ":" (get-state :nick) " JOIN &channels"))

  (#"It reflects pings to keep someone from disconnecting." [_]
    true)

  ; bb message
  ; #666 [alt/satan] From: The Pope
  (#"#\d+ \[[^\]]+\].*" [line]
    (forward ":* PRIVMSG &IFMUD :" line))

  ; channel topic line from @joinc or @listc
  ; #alt/random/markov-chains: not the face, not the face
  ; used to determine which channels the user is in
  (#"#.*?/([^/\s]+).*?:\s*(.*)" [_ chan topic]
    (let [chan (str "#" (.trim chan))]
      (cond
        ((get-state :channels) chan) (do
          (forward ":" (get-state :nick) " JOIN " chan)
          (forward ":IFMUD 332 " (get-state :nick) " " chan " :" topic))
        :else (forward ":[" chan "] PRIVMSG &channels :" topic))))

  ; channel departure message
  (#"You are no longer on (.*)\." [_ chan]
    (cond
      ((get-state :channels) chan) (do
        (forward ":" (get-state :nick) " PART " chan))
      :else (forward ":[" chan "] PRIVMSG &channels :Parted.")))

  ; targeted channel message - [foo] Someone says (to SomeoneElse), "stuff"
  (#"\[(.+?)\] (.+?) (?:says|asks|exclaims) \((?:to|of|at) (\w+)\), \"(.*)\"" [_ chan user target msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true
        :else (cond
          ((get-state :channels) chan) (forward ":" user " PRIVMSG " chan " :" target ": " msg)
          :else (forward ":[" chan "] PRIVMSG &channels :<" user "> " target ": " msg)))))

  ; untargeted channel message - [foo] Someone says, "stuff"
  (#"\[(.+?)\] (.+?) (?:says|asks|exclaims), \"(.*)\"" [_ chan user msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true
        :else (cond
          ((get-state :channels) chan) (forward ":" user " PRIVMSG " chan " :" msg)
          :else (forward ":[" chan "] PRIVMSG &channels :<" user "> " msg)))))

  ; channel action
  (#"\[(.+?)\] (.+?) (.*)" [_ chan user action]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true
        :else (cond
          ((get-state :channels) chan) (forward ":" user " PRIVMSG " chan " :\u0001ACTION " action "\u0001")
          :else (forward ":[" chan "]<" user "> PRIVMSG &channels :\u0001ACTION " action "\u0001")))))

  ; user joins channel
  (#"\[(.+?)\] \* (.+?) has joined the channel." [_ chan user]
    (forward ":" user " JOIN #" chan))

  ; raw message on channel
  (#"\[(.+?)\] (.*)" [_ chan msg]
    (forward ":* PRIVMSG #" chan " :" msg))

  ; targeted user message in local
  (#"(\w+) (?:says|asks|exclaims) \((?:to|of|at) (\w+)\), \"(.*)\"" [_ user target msg]
    (forward ":" user " PRIVMSG &IFMUD :" target ": " msg))

  ; user message in local
  (#"(\w+) (?:says|asks|exclaims), \"(.*)\"" [_ user msg]
    (forward ":" user " PRIVMSG &IFMUD :" msg))
  
  ; your message in local
  (#"You (say|ask|exclaim), \"(.*)\"" [_ _ msg]
    (forward ":" (get-state :nick) " PRIVMSG &IFMUD :" msg))
  
  ; all other MUD traffic
  (#".*" [line]
    (forward ":* PRIVMSG &IFMUD :" line)))
