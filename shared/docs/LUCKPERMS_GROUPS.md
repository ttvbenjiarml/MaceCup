# LuckPerms Groups

default: stats, leaderboards, practice, cosmetics, hats, standard emotes
vip: default plus custom emotes and `macecup.purge.vip` for 37.5% event-entry pity steps
vipplus: vip plus `macecup.purge.guaranteed` and `macecup.event.guaranteed` to bypass purge selection
host: host/cancel/manage events and staff TAB section, but no admin build/arena permissions
admin: host plus admin, bypass, arena save/regen/wand, build, emote approvals, selftest, proxy admin
owner: admin plus full owner prefix and all owner/server management permissions

Tebex should run the matching LuckPerms command for the paid package:

`lp user <username> parent add vip`

`lp user <username> parent add vipplus`

VIP and VIP+ do not receive `macecup.host`; only Host, Admin, and Owner can host games.
