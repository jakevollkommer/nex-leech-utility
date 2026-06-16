# Nex Leech Utility

A RuneLite plugin that helps you efficiently leech loot from Nex by hitting the
minimum **25 damage** with the least effort, while staying safe.

## Features

- **Per-kill damage tracker** — an overlay showing your own damage this kill.
  It turns **green once you reach 25** (the loot-eligibility threshold), red until then.
- **Minion vulnerability highlighting** — Fumus, Umbra, Cruor and Glacies are
  outlined: faint **red** while invulnerable, hard **green** the moment they
  become attackable (driven by Nex's `"<minion>, don't fail me!"` callouts), and
  reset when the kill ends.
- **Leech rotation warnings** — pick the **starting minion** you want to attack.
  When that minion (or any later one, if you still need damage) is about to
  become vulnerable, a centred warning appears and is dismissed once it's
  attackable. Optionally **grabs client focus** so you don't miss the hit while
  tabbed out. Warnings stop automatically once you've reached 25 damage.
- **Optional blood-reaver highlighting** — reavers also count towards your damage.
- **Low HP / prayer screen flash** — optionally flash the screen with a
  configurable message (e.g. `EAT!` / `DRINK PRAYER POT`) when HP or prayer drop
  below configurable thresholds (default 60 HP / 50 prayer).

## Credits

- Nex fight detection and per-kill damage tracking adapted from the
  [Nex Droprate Calculator](https://github.com/Worley03/nex-droprate-calculator)
  plugin (© Smug Pepe, BSD 2-Clause).
- Nex chat-line and NPC-id conventions from the community "Nex Extended" plugin
  (BSD 2-Clause).

## Building

Requires JDK 11.

```
./gradlew build
```
