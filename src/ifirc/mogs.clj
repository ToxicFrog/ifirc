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

  (#"JOIN (.*)" [_ chans]
    (println (get-state :nick))
    (println (get-state :channels))
    (->> (clojure.string/split chans #",")
         (map clojure.string/trim)
         (remove empty?)
         (map (fn [chan]
                (reply ":" (get-state :nick) " JOIN " chan)
                (set-state :channels (conj (get-state :channels) chan))))
         dorun)
    (println (get-state :channels)))

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

  (#"PRIVMSG &IFMUD :\u0001ACTION (.*)\u0001" [_ action]
    (forward " :" action))

  (#"PRIVMSG &IFMUD :(\w+): (.*)" [_  target msg]
    (forward ".." target " " msg))

  (#"PRIVMSG &IFMUD :(.*)" [_ msg]
    (forward "\"" msg))

  (#".*" [line]
    (reply "[???] " line)))

(defmogs from-mud
  (#"Login Succeeded" [_]
    (set-state :channels #{})
    (reply "lounge")
    (reply "@listc -member")
    (forward ":IFMUD 001 " (get-state :nick) " :Welcome to ifMUD!")
    (forward ":IFMUD 376 " (get-state :nick) " :End of MOTD")
    (forward ":" (get-state :nick) " JOIN &IFMUD")
    (forward ":" (get-state :nick) " JOIN &raw")
    (forward ":" (get-state :nick) " JOIN &channels"))

  (#"It reflects pings to keep someone from disconnecting." [_]
    true)
  
  ; rawlog message
  (#"^RAW (.+?) (.*)" [_ nick message]
    (forward ":" nick " PRIVMSG &raw :" message))

  ; bb message
  ; #666 [alt/satan] From: The Pope
  (#"#\d+ \[[^\]]+\].*" [line]
    (forward ":* PRIVMSG &IFMUD :" line))

  ; targeted channel message - [foo] Someone says (to SomeoneElse), "stuff"
  (#"\[(.+?)\] (.+?) (?:says|asks|exclaims) \((?:to|of|at) (\w+)\), \"(.*)\"" [_ chan user target msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true ; eat messages from the user
        ((get-state :channels) chan) (forward ":" user " PRIVMSG " chan " :" target ": " msg)
        :else (forward ":[" chan "] PRIVMSG &channels :<" user "> " target ": " msg))))

  ; untargeted channel message - [foo] Someone says, "stuff"
  (#"\[(.+?)\] (.+?) (?:says|asks|exclaims), \"(.*)\"" [_ chan user msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true ; eat messages from the user
        ((get-state :channels) chan) (forward ":" user " PRIVMSG " chan " :" msg)
        :else (forward ":[" chan "] PRIVMSG &channels :<" user "> " msg))))

  ; channel action
  (#"\[(.+?)\] (.+?) (.*)" [_ chan user action]
    (let [chan (str "#" chan)]
      (cond
        ((get-state :channels) chan) (forward ":" user " PRIVMSG " chan " :\u0001ACTION " action "\u0001")
        :else (forward ":[" chan "/" user "] PRIVMSG &channels :\u0001ACTION " action "\u0001"))))

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
  
  ; your message in local - eat these, since the IRC client already echoes them
  (#"You (say|ask|exclaim), \"(.*)\"" [_ _ msg]
    true)
  
  ; all other MUD traffic
  (#".*" [line]
    (forward ":* PRIVMSG &IFMUD :" line)))
