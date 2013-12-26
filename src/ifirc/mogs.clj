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
      (to-mud "connect " (get-state :nick) " " (get-state :pass))))

  (#"PASS (.*)" [_ pass]
    (set-state :pass pass)
    (println (get-state))
    (if (get-state :nick)
      (to-mud "connect " (get-state :nick) " " (get-state :pass))))

  (#"QUIT :.*" [_]
    (to-mud "quit"))

  (#"JOIN (.*)" [_ chans]
    (println (get-state :nick))
    (println (get-state :channels))
    (->> (clojure.string/split chans #",")
         (map clojure.string/trim)
         (remove empty?)
         (map (fn [chan]
                (to-irc ":" (get-state :nick) " JOIN " chan)
                (set-state :channels (conj (get-state :channels) chan))))
         dorun)
    (println (get-state :channels)))

  (#"PART (.*)" [_ chan]
    (to-irc ":" (get-state :nick) " PART " chan)
    (set-state :channels (disj (get-state :channels) chan)))

  (#"WHO .*" [_]  ; FIXME - there needs to be a proper API for eating unwanted messages
    true)

  (#"MODE .*" [_]
    true)

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
    (to-mud "lounge")
    (to-irc ":IFMUD 001 " (get-state :nick) " :Welcome to ifMUD!")
    (to-irc ":IFMUD 376 " (get-state :nick) " :End of MOTD")
    (to-irc ":" (get-state :nick) " JOIN &IFMUD")
    (to-irc ":" (get-state :nick) " JOIN &raw")
    (to-irc ":" (get-state :nick) " JOIN &channels"))

  ; rawlog message
  (#"^RAW (.+?) (.*)" [_ nick message]
    (to-irc ":" nick " PRIVMSG &raw :" message))

  ; bb message
  ; #666 [alt/satan] From: The Pope
  (#"#\d+ \[[^\]]+\].*" [line]
    (to-irc ":* PRIVMSG &IFMUD :" line))

  ; targeted channel message - [foo] Someone says (to SomeoneElse), "stuff"
  (#"\[(.+?)\] (.+?) (?:says|asks|exclaims) \((?:to|of|at) (\w+)\), \"(.*)\"" [_ chan user target msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true ; eat messages from the user
        ((get-state :channels) chan) (to-irc ":" user " PRIVMSG " chan " :" target ": " msg)
        :else (to-irc ":[" chan "] PRIVMSG &channels :<" user "> " target ": " msg))))

  ; untargeted channel message - [foo] Someone says, "stuff"
  (#"\[(.+?)\] (.+?) (?:says|asks|exclaims), \"(.*)\"" [_ chan user msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true ; eat messages from the user
        ((get-state :channels) chan) (to-irc ":" user " PRIVMSG " chan " :" msg)
        :else (to-irc ":[" chan "] PRIVMSG &channels :<" user "> " msg))))

  ; channel join/part
  (#"\[(.+?)\] \* (.+?) has (joined|left) the channel\." [_ chan user action]
    (let [chan (str "#" chan)
          action (if (= action "joined") "JOIN" "PART")]
      (cond
        ((get-state :channels) chan) (to-irc ":" user " " action " " chan)
        :else true)))

  ; channel action
  (#"\[(.+?)\] (.+?) (.*)" [_ chan user action]
    (let [chan (str "#" chan)]
      (cond
        ((get-state :channels) chan) (to-irc ":" user " PRIVMSG " chan " :\u0001ACTION " action "\u0001")
        :else (to-irc ":[" chan "] PRIVMSG &channels :\u0001ACTION " user " " action "\u0001"))))

  ; user joins channel
  (#"\[(.+?)\] \* (.+?) has joined the channel." [_ chan user]
    (to-irc ":" user " JOIN #" chan))

  ; raw message on channel
  (#"\[(.+?)\] (.*)" [_ chan msg]
    (to-irc ":* PRIVMSG #" chan " :" msg))

  ; targeted user message in local
  (#"(.+?) (?:says|asks|exclaims) \((?:to|of|at) (\w+)\), \"(.*)\"" [_ user target msg]
    (to-irc ":" user " PRIVMSG &IFMUD :" target ": " msg))

  ; user message in local
  (#"(.+?) (?:says|asks|exclaims), \"(.*)\"" [_ user msg]
    (to-irc ":" user " PRIVMSG &IFMUD :" msg))
  
  ; your message in local - eat these, since the IRC client already echoes them
  (#"You (?:say|ask|exclaim).*" [_ msg]
    true)
  
  ; all other MUD traffic
  (#".*" [line]
    (to-irc ":* PRIVMSG &IFMUD :" line)))
