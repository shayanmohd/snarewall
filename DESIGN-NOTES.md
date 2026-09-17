# Snarewall design notes

## Design read

Reading this as: a maze-building strategy game for adults who plan before they fight, with a
chalk-quarry masonry language (cut stone blocks, a surveyor's thread, a warden's tally), leaning
toward Bungee plus Lexend on a limestone and snare emerald palette.

## Dials

- **Variance 4.** A strict grid everywhere. The 8 by 12 board is square tiles on a square grid, the
  menus are one straight column. The only asymmetry in the app is the maze the player builds.
- **Motion 4.** Motion answers placement and combat and never idles. One first-run moment: the route
  thread draws gate to keep over 700 ms, once per install. A committed wall settles from 0.9 scale in
  90 ms, the thread redraws when the route changes, sheets slide. `LocalReducedMotion` snaps all UI
  motion and skips the first-run draw. The simulation still runs, because it is the game.
- **Density 6.** The board is busy (walls, traps, enemies, thread, HUD numbers), so the chrome stays
  thin: one HUD strip, one tray, 16dp gutters, no cards around the board.

## The one memorable thing

The route thread. A dashed Route-orange line from each gate to the keep along the shortest path, with
a step chip at the keep. While a wall ghost is dragged, the thread recomputes on every tile it enters
and the chip shows the delta ("+6 steps"). A ghost that would seal every path shows a cross, keeps
the old thread, and the chip reads "Seals the keep". Colour never carries the meaning alone: walls
are filled blocks, the thread is dashed, an invalid ghost adds a cross and words.

## Tokens

| Token | Light | Dark |
|---|---|---|
| Limestone (background, HUD, tray) | #E3E7E1 | #141C19 |
| Flag (board floor, sheets, tiles) | #F3F5F1 | #1E2824 |
| Ink (text, icons, obstacles, enemies) | #17211D | #E2E8E4 |
| Lichen (secondary text, locked, seams) | #4D5B54 | #9DAAA3 |
| Emerald (the one accent) | #1A7550 | #4DBE8B |
| Route (thread, chip, warnings, errors) | #A63F14 | #E68E5A |

Type: Bungee for wordmark, level titles, HUD numbers and the step chip only. Lexend 400, 500, 600 for
everything else. Radius: 2dp tiles and chips, 8dp buttons and tray slots, 16dp sheet tops.

## What shipped against the read

- Board chrome is one HUD strip, the board, a step chip strip under the keep and one tray. The chip sits
  outside the board so it never covers a tile. Coach marks and messages sit one tile below the gates.
- Medals are three stone pips (one, two, three filled) so shape carries the medal, not a new hue.
- The one motion moment is the thread drawing gate to keep once per install; everything else answers a
  placement or a combat event. Under reduced motion the draw is skipped and messages snap.
- At 600dp and wider the board takes the left and a 320dp rail holds the HUD, a labelled trap grid and
  Send wave.
- Store captions use Lexend SemiBold: Bungee is too wide to hold a 30 character caption in two lines.
