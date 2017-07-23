(ns ifirc.mogs
  (:require [ifirc.mogrify :refer :all]
            [ifirc.flags :refer [*options*]]
            [taoensso.timbre :as log]))

(defn- login [user pass]
  (set-state :current-channel "#")
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

(defn- part-channels [channels]
  (->> (clojure.string/split channels #",")
       (map clojure.string/trim)
       (remove empty?)
       (remove #(= \& (first %))) ; skip channels starting with "&", like "##raw"
       (map (fn [chan]
              (if (*options* :autochan)
                (to-mud "@leavec " chan))
              (to-irc ":" (get-state :nick) " PART " chan)
              (set-state :channels (disj (get-state :channels) chan))))))

(defn- reduce-names [names name]
  (cond
    (< 384 (count (first names))) (reduce-names (conj names "") name)
    :else (conj (rest names) (str (first names) " " name))))

; Send a series of 353 messages containing the listed users.
(defn- send-names [chan users]
  (let [messages (reduce reduce-names '("") users)]
    (dorun
      (map #(to-irc ":IFMUD 353 " (get-state :nick) " = " chan " :" %)
           messages))))

; Given a list of comma-or-whitespace-separated users, some of whom may be in
; (parens), split it into a list of user names.
(defn- split-users [users]
  (map #(clojure.string/replace % #"[()]" "")
       (clojure.string/split users #"[, ]\s*")))

(defmogs from-irc
  ; ignore capability requests from ZNC
  (#"CAP .*" [_]
    true)

  ; ignore USER command
  (#"USER .*" [_]
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

  (#"JOIN (.*)" [_ chans]
    (->> (clojure.string/split chans #",")
         (map clojure.string/trim)
         (remove empty?)
         ; skip channels starting with "##", like "##raw"
         (remove #(clojure.string/starts-with? % "##"))
         (map (fn [chan]
                (if (or (*options* :autojoin) (*options* :autochan))
                  (to-mud "@joinc " chan))
                (to-irc ":" (get-state :nick) " JOIN " chan)
                (to-mud "@statc " chan)
                (set-state :channels (conj (get-state :channels) chan))))
         dorun)
    (log/debug "Joined channels:" (get-state :channels)))

  (#"PART (.+?) :(.*)" [_ chans reason]
    (part-channels chans))
  (#"PART (.*)" [_ chans]
    (part-channels chans))

  (#"WHO (.*)" [_ chan]
    (report-who chan (get-state :players)))

  (#"NAMES (.*)" [_ chan]
    ; TODO: DTRT here if chan == ##ifmud
    (to-mud "@statc " chan))

  (#"MODE (.*)" [_ chan]
    (to-irc ":IFMUD 324 " (get-state :nick) " " chan " +ntr")
    (to-irc ":IFMUD 329 " (get-state :nick) " " chan " 0"))

  (#"PING (.*)" [_ time]
    (to-mud-silent "qidle")
    (to-irc ":IFMUD PONG IFMUD :" time))

  ; Messages on ##channels or ##IFMUD starting with \ are raw messages.
  (#"PRIVMSG ##(?:channels|IFMUD) :\\(.*)" [_ msg]
    (to-mud msg))

  ; So is everything sent to ##raw.
  (#"PRIVMSG ##raw :(.*)" [_ msg]
    (to-mud msg))

  ; Messages on the channels control channel, ##channels.
  (#"PRIVMSG ##channels :(#[^ ]+)" [_ chan]
    (set-state :current-channel chan)
    (to-irc ":IFMUD TOPIC ##channels :" chan))

  (#"PRIVMSG ##channels :(#[^ ]+) +(.*)" [_ chan msg]
    (set-state :current-channel chan)
    (to-irc ":IFMUD TOPIC ##channels  :" chan)
    (to-mud chan " " msg))

  (#"PRIVMSG ##channels :\u0001ACTION (.*)\u0001" [_ action]
    (to-mud (get-state :current-channel) " :" action))

  (#"PRIVMSG ##channels :(\w+): (.*)" [_ target msg]
    (to-mud (get-state :current-channel) " .." target " " msg))

  (#"PRIVMSG ##channels :(.*)" [_ msg]
    (to-mud (get-state :current-channel) " " msg))

  ; Chatting in ##IFMUD turns into speech or emotes in the current room.
  (#"PRIVMSG ##IFMUD :\u0001ACTION (.*)\u0001" [_ action]
    (to-mud ":" action))

  (#"PRIVMSG ##IFMUD :(\w+): (.*)" [_ target msg]
    (to-mud ".." target " " msg))

  (#"PRIVMSG ##IFMUD :(.*)" [_ msg]
    (to-mud "\"" msg))

  ; Messages on split-out (starting with #) channels.
  (#"PRIVMSG (#.+?) :\u0001ACTION (.*)\u0001" [_ chan action]
    (to-mud chan " :" action))

  (#"PRIVMSG (#.+?) :(\w+): (.*)" [_ chan target msg]
    (to-mud chan " .." target " " msg))

  (#"PRIVMSG (#.+?) :(.*)" [_ chan msg]
    (to-mud chan " " msg))

  ; Outgoing whispers.
  (#"PRIVMSG (\w+) :(.*)" [_ target msg]
    (to-mud "." target " " msg))

  (#".*" [line]
    (to-irc "[???] " line)))

(defmogs from-mud
  (#"Login Failed" [_]
    (log/infof "Login as %s failed, disconnecting user." (get-state :nick))
    (to-irc ":IFMUD 464 " (get-state :nick) " :Login failed")
    :exit)

  (#"Login Succeeded" [_]
    (set-state :channels #{})
    (log/infof "Login as %s successful." (get-state :nick))
    (log/debug "State:" (get-state))
    (if (get-state :nick)
     (do
       (if-let [autoexec (*options* :autoexec)]
         (to-mud autoexec))
       (to-irc ":IFMUD 001 " (get-state :nick) " :Welcome to ifMUD!")
       (to-irc ":IFMUD 376 " (get-state :nick) " :End of MOTD")
       (to-irc ":" (get-state :nick) " JOIN ##IFMUD")
       (to-irc ":" (get-state :nick) " JOIN ##raw")
       (to-irc ":" (get-state :nick) " JOIN ##channels"))
     (do
       (log/error "Error: no valid nick after login, bailing")
       :exit)))

  ; local player list
  (#"Players: (.*)" [_ plist]
    (let [players (set (split-users plist))]
      (log/debug "Got local player list: " players)
      (set-state :players players)
      (send-names "##IFMUD" players)
      (to-irc ":IFMUD 366 " (get-state :nick) " ##IFMUD :No more NAMES."))
      )

  ; "You are already on channel" message -- IRC traditionally ignores redundant JOINs
  (#"You are already on #.*" [_] true)

  ; bb message
  ; #666 [alt/satan] From: The Pope
  (#"#\d+ \[[^\]]+\].*" [line]
    (to-irc ":* PRIVMSG ##IFMUD :" line))

  ; targeted channel message - [foo] Someone says (to SomeoneElse), "stuff"
  (#"\[(\S+)\] (\S+) (?:says|asks|exclaims) \((?:to|of|at) (\S+)\), \"(.*)\"" [_ chan user target msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true ; eat messages from the user
        ((get-state :channels) chan) (to-irc ":" user " PRIVMSG " chan " :" target ": " msg)
        :else (to-irc ":[" chan "] PRIVMSG ##channels :<" user "> " target ": " msg))))

  ; untargeted channel message - [foo] Someone says, "stuff"
  (#"\[(\S+)\] (\S+) (?:says|asks|exclaims), \"(.*)\"" [_ chan user msg]
    (let [chan (str "#" chan)]
      (cond
        (= user (get-state :nick)) true ; eat messages from the user
        ((get-state :channels) chan) (to-irc ":" user " PRIVMSG " chan " :" msg)
        :else (to-irc ":[" chan "] PRIVMSG ##channels :<" user "> " msg))))

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
        :else (to-irc ":[" chan "] PRIVMSG ##channels :\u0001ACTION " user " " action "\u0001"))))

  ; raw message on channel
  (#"\[(\S+)\] (.*)" [_ chan msg]
    (to-irc ":* PRIVMSG #" chan " :" msg))

  ; targeted user message in local
  (#"(\S+) (?:says|asks|exclaims) \((?:to|of|at) (\S+)\), \"(.*)\"" [_ user target msg]
    (to-irc ":" user " PRIVMSG ##IFMUD :" target ": " msg))

  ; user message in local
  (#"(\S+) (?:says|asks|exclaims), \"(.*)\"" [_ user msg]
    (to-irc ":" user " PRIVMSG ##IFMUD :" msg))

  ; user whispers to you
  (#"(\S+) whispers, \"(.*)\"" [_ user msg]
    (to-irc ":" user " PRIVMSG " (get-state :nick) " :" msg))

  ; user disconnects
  (#"</(\S+)> (.*)" [_ user msg]
    (to-irc ":" user " PART ##IFMUD :" msg))
  (#"(\S+) has disconnected\." [_ user]
    (to-irc ":" user " PART ##IFMUD :disconnect" ))

  ; user connects
  (#"<(\S+)> (.*)" [_ user msg]
    (to-irc ":" user " JOIN ##IFMUD"))

  ; user action in local
  (#"(\S+) (.*)" [_ user msg]
    (cond
      ; Skip our own actions, since the IRC client will already have echoed them.
      (= (get-state :nick) user) true
      (contains? (get-state :players) user) (to-irc ":" user " PRIVMSG ##IFMUD :\u0001ACTION " msg "\u0001")
      :else :continue))

  ; your message in local - eat these, since the IRC client already echoes them
  (#"You (?:say|ask|exclaim|whisper).*" [_]
    true)

  ; the output of a statc looks something like:
  ; <fully qualified name>: <topic>
  ; Created: <datetime> Last message: <datetime>
  ; Users: <\s-separated list of online users>
  ; Also: <\s-separated list of offline users, each name in (parens)>
  ; There are (no/N) new messages on <FQCN>.
  ; Note that:
  ; the "no new messages" item only appears if you're in the channel;
  ; The FQCN is prefixed with '#' if you're in the channel, and ' ' otherwise.
  ; So this will only work for channels that you're already in.
  (#"#[a-z!]+/[^ ]+/([^/ ]+): (.*)" [_ chan topic]
    (set-state :statting (str "#" chan))
    (to-irc ":IFMUD 332 " (get-state :nick) " #" chan ":" topic))

  (#"Users: (.*)" [_ users]
    (if (get-state :statting)
      (send-names (get-state :statting) (split-users users))))

  (#"Also: (.*)" [_ users]
    (if (get-state :statting)
      (do
        (send-names (get-state :statting) (split-users users))
        (to-irc ":IFMUD 366 " (get-state :nick) " " (get-state :statting) " :No more NAMES."))
        (set-state :statting nil)))

  ; all other MUD traffic
  (#".*" [line]
    (to-irc ":* PRIVMSG ##IFMUD :" line)))
