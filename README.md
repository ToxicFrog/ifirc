# ifirc

Runs a proxy that lets you connect to IFMUD using an IRC client.

Run with `lein run` or `java -jar`. Use `lein run -- --help` for options. Running with no arguments will give you a proxy listening on port 4000, connecting to ifmud.port4000.com:4000 and automatically running the `lounge` command on connect.

Connect with an IRC client using NICK and PASS matching your username and password on IFMUD. If all goes well, it will autojoin three channels - &IFMUD, &raw, and &channels. (If all does *not* go well -- in particular, if the login fails -- you will be disconnected with an error message).

&raw is the raw log; anything you type there is sent, unmodified, to the MUD. It contains all traffic to (indicated with <<) and from (>>) the MUD.

&IFMUD is the "main channel". Most non-channel traffic from the MUD will be sent there. Local conversation and emotes will be translated into IRC messages; other MUD traffic shows up as messages from the user "*". Anything you say here will be turned into speech in the mud (with the " command), and /me will be turned into emotes. To send a raw command without switching back to &raw, prefix it with \, as in `\look`.

&channels contains all channel traffic not routed to a separate channel (see below). Channel messages appear to come from an IRC user with the same name as the channel, and are prefixed with the name of the person speaking. (I am open to suggestions on how to make this more readable.) Commands you type here are sent directly to the MUD, the same as with &raw.

If you /join a channel on IRC, *and you are already in that channel on the MUD*, traffic on that channel will be split off from the &channels channel, making it behave like a normal IRC channel. To merge it with &channels again, simply /part it. This will not automatically @joinc or @leavec on the MUD; you must do this yourself. (The reason it does this rather than simply autojoining you to all of your MUD channels is that it is common to be in a large number of low-traffic channels on the MUD, at least for me.)

It should handle multiple concurrent users just fine, but this has not been rigorously tested.

## --autoexec

If the `--autoexec=CMD` option is provided, the proxy will send `CMD` to the mud, verbatim, whenever a user connects. To send multiple commands, separate them with newlines, as in:

    lein run -- --autoexec='lounge
    say I HAVE RETURNED!
    :bows.'

(Note: you should probably not actually do the above. It is provided solely for illustrative purposes.)

## --autojoin and --autochan

These options change how the proxy handles IRC /join and /part commands. By default, they change the routing of channel messages but will not automatically join or part channels on the MUD proper.

If `--autojoin` is set, /joining a channel in IRC automatically issues the corresponding `@joinc` command in the MUD. This will probably become the default behaviour at some point.

If `--autochan` is set, /join works the same as with `--autojoin` and, furthermore, /part will cause a `@leavec` command to be issued.

## Known Issues

There is no support for /list.

It does not handle user lists particularly well. It tries to guess who is in your vicinity (in &IFMUD) or in the same channel (in individual channels), but it often gets it wrong and is not good at keeping track of people joining or leaving.

## License

Copyright © 2014 Ben "ToxicFrog" Kelly, Google Inc.

Distributed under the Apache License v2; see the file COPYING for details.

### Disclaimer

This is not an official Google product and is not supported by Google.
