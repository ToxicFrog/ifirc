# ifirc

Runs a server bridge that lets you connect to IFMUD using an IRC client.

Run with: lein run <listen port> <mud host> <mud port>
E.g. lein run 4000 ifmud.port4000.com 4000

Connect with an IRC client using NICK and PASS matching your username and password on IFMUD. If it doesn't greet you correctly or thinks your name is ":Welcome", disconnect and reconnect - something has gone wrong.

&IFMUD is the main game state. Typing here talks. "Foo:" IRC prefix translates into "..foo" MUD prefix. /me works. To send raw commands, prefix with \.
&raw is the rawlog. &channels is the channel multiplexer. Typing in either of those channels is sent to the MUD unmodified.
/join a channel to split it off from the multiplexer. If you aren't already in that channel on the MUD (@joinc) this has no effect. Once split you can talk in it like a normal IRC channel.

Note: mostly untested, never tested with more than one user, never tested on any client other than xchat. Use at your own risk.
