(ns ifirc.mogs
  (:require [ifirc.mogrify :refer :all]
            [taoensso.timbre :as log]))

(defn- login [user pass]
  (log/info "Logging in to MUD as" user)
  (to-mud "connect " user " " pass))

(defn- report-who [chan users]
  (dorun (map
           #(to-irc ":IFMUD " 352 " "
                    (get-state :nick) " "
                    chan " "
                    %1 " ifmud.user IFMUD "
                    %1 " H :0 " %1)
           users))
  (to-irc ":IFMUD " 315 " " (get-state :nick) " " chan " :End of WHO."))

(defn- report-names [chan users]
  (to-irc ":IFMUD 353 " (get-state :nick) " = " chan " :" (apply str (interpose " " users)))
  (to-irc ":IFMUD 366 " (get-state :nick) " " chan " :No more NAMES."))

(defmogs from-irc
  ; ignore capability requests from ZNC
  (#"CAP .*" [_]
    true)

  (#"NICK (.*)" [_ nick]
    (set-state :nick nick)
    (if (get-state :pass)
      (login (get-state :nick) (get-state :pass))))

  (#"PASS (.*)" [_ pass]
    (set-state :pass pass)
    (if (get-state :nick)
      (login (get-state :nick) (get-state :pass))))

  (#"QUIT :.*" [_]
    (to-mud "quit"))

  ; TODO: use @statc <channel> to get channel topic and userlist
  (#"JOIN (.*)" [_ chans]
    (->> (clojure.string/split chans #",")
         (map clojure.string/trim)
         (remove empty?)
         (map (fn [chan]
                (to-irc ":" (get-state :nick) " JOIN " chan)
                (report-names chan (get-state :players))
                (set-state :channels (conj (get-state :channels) chan))))
         dorun)
    (log/debug "Joined channels:" (get-state :channels)))

  (#"PART (.*)" [_ chan]
    (to-irc ":" (get-state :nick) " PART " chan)
    (set-state :channels (disj (get-state :channels) chan)))

  (#"WHO (.*)" [_ chan]
    (report-who chan (get-state :players)))

  (#"NAMES (.*)" [_ chan]
    (report-names chan (get-state :players)))

  (#"MODE (.*)" [_ chan]
    (to-irc ":IFMUD 324 " (get-state :nick) " " chan " +ntr")
    (to-irc ":IFMUD 329 " (get-state :nick) " " chan " 0"))

  (#"PING (.*)" [_ time]
    (to-mud "qidle")
    (to-irc ":IFMUD PONG IFMUD :" time))

  (#"PRIVMSG (#.+?) :\u0001ACTION (.*)\u0001" [_ chan action]
    (to-mud chan " :" action))

  (#"PRIVMSG (#.+?) :(\w+): (.*)" [_ chan target msg]
    (to-mud chan " .." target " " msg))

  (#"PRIVMSG (#.+?) :(.*)" [_ chan msg]
    (to-mud chan " " msg))

  (#"PRIVMSG &(?:channels|raw) :(.*)" [_ msg]
    (to-mud msg))

  (#"PRIVMSG &IFMUD :\\(.*)" [_ msg]
    (to-mud msg))

  (#"PRIVMSG &IFMUD :\u0001ACTION (.*)\u0001" [_ action]
    (to-mud " :" action))

  (#"PRIVMSG &IFMUD :(\w+): (.*)" [_  target msg]
    (to-mud ".." target " " msg))

  (#"PRIVMSG &IFMUD :(.*)" [_ msg]
    (to-mud "\"" msg))

  (#".*" [line]
    (to-irc "[???] " line)))

(defmogs from-mud
  (#"Login Succeeded" [_]
    (set-state :channels #{})
    (log/infof "Login as %s successful." (get-state :nick))
    (log/debug "State:" (get-state))
    (if (get-state :nick)
     (do
       ;(to-mud "lounge")
       (to-irc ":IFMUD 001 " (get-state :nick) " :Welcome to ifMUD!")
       (to-irc ":IFMUD 376 " (get-state :nick) " :End of MOTD")
       (to-irc ":" (get-state :nick) " JOIN &IFMUD")
       (to-irc ":" (get-state :nick) " JOIN &raw")
       (to-irc ":" (get-state :nick) " JOIN &channels"))
     (do
       (log/error "Error: no valid nick after login, bailing")
       (System/exit 1))))

  ; local player list
  (#"Players: (.*)" [_ plist]
    (let [players (clojure.string/split plist #",\s*")]
      (log/debug "Got local player list: " players)
      (set-state :players players)
      (report-names "&IFMUD" players)))

  ; bb message
  ; #666 [alt/satan] From: The Pope
  (#"#\d+ \[[^\]]+\].*" [line]
    (to-irc ":* PRIVMSG &IFMUD :" line))

  ; targeted channel message - [foo] Someone says (to SomeoneElse), "stuff"
  (#"\[(\S+)\] (\S+) (?:says|asks|exclaims) \((?:to|of|at) (\S+)\), \"(.*)\"" [_ chan user target msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true ; eat messages from the user
        ((get-state :channels) chan) (to-irc ":" user " PRIVMSG " chan " :" target ": " msg)
        :else (to-irc ":[" chan "] PRIVMSG &channels :<" user "> " target ": " msg))))

  ; untargeted channel message - [foo] Someone says, "stuff"
  (#"\[(\S+)\] (\S+) (?:says|asks|exclaims), \"(.*)\"" [_ chan user msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true ; eat messages from the user
        ((get-state :channels) chan) (to-irc ":" user " PRIVMSG " chan " :" msg)
        :else (to-irc ":[" chan "] PRIVMSG &channels :<" user "> " msg))))

  ; channel join/part
  (#"\[(\S+)\] \* (\S+) has (joined|left) the channel\." [_ chan user action]
    (let [chan (str "#" chan)
          action (if (= action "joined") "JOIN" "PART")]
      (cond
        ((get-state :channels) chan) (to-irc ":" user " " action " " chan)
        :else true)))

  ; channel action
  (#"\[(\S+)\] (\S+) (.*)" [_ chan user action]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true
        ((get-state :channels) chan) (to-irc ":" user " PRIVMSG " chan " :\u0001ACTION " action "\u0001")
        :else (to-irc ":[" chan "] PRIVMSG &channels :\u0001ACTION " user " " action "\u0001"))))

  ; raw message on channel
  (#"\[(\S+)\] (.*)" [_ chan msg]
    (to-irc ":* PRIVMSG #" chan " :" msg))

  ; targeted user message in local
  (#"(\S+) (?:says|asks|exclaims) \((?:to|of|at) (\S+)\), \"(.*)\"" [_ user target msg]
    (to-irc ":" user " PRIVMSG &IFMUD :" target ": " msg))

  ; user message in local
  (#"(\S+) (?:says|asks|exclaims), \"(.*)\"" [_ user msg]
    (to-irc ":" user " PRIVMSG &IFMUD :" msg))

  ; user disconnects
  (#"</(\S+)> (.*)" [_ user msg]
    (to-irc ":" user " PART &IFMUD :" msg))
  (#"(\S+) has disconnected\." [_ user]
    (to-irc ":" user " PART &IFMUD :disconnect" ))

  ; user connects
  (#"<(\S+)> (.*)" [_ user msg]
    (to-irc ":" user " JOIN &IFMUD"))

  ; user | action
  (#"(\S+) \| (.*)" [_ user msg]
    (to-irc ":" user " PRIVMSG &IFMUD :\u0001ACTION | " msg "\u0001"))

  ; user action -- do we actually want this mapping?
  ;(#"(\S+) (.*)\." [_ user msg]
  ;  (to-irc ":" user " PRIVMSG &IFMUD :\u0001ACTION " msg ".\u0001"))

  ; your message in local - eat these, since the IRC client already echoes them
  (#"You (?:say|ask|exclaim).*" [_]
    true)

  ; all other MUD traffic
  (#".*" [line]
    (to-irc ":* PRIVMSG &IFMUD :" line)))
