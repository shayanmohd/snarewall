# Snarewall

Snarewall is a paid, fully offline maze tower defense for Android, for players who like to plan before they fight. Every level is open ground with a few fixed rocks: the player places walls, a live route preview (the exact path the enemies will walk) redraws while the wall is still under the finger, and ten traps chain into four visible combos against twelve enemy types, including diggers and jumpers that punish thin mazes. Fifteen handmade levels in three regions, three difficulties with par medals, a daily endless map generated from the UTC date, save file export and import, light and dark themes and a two-pane tablet layout.

Everything runs on the device. The app declares no network permission and sends nothing anywhere.

## Build

Requires JDK 17 and the Android SDK with platform 36.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew bundleRelease       # needs keystore.properties, see below
```

`keystore.properties` and the `.jks` are not committed. Without them the release build stays
unsigned instead of failing:

```properties
storeFile=<slug>-upload.jks
storePassword=...
keyAlias=<slug>
keyPassword=...
```

## Layout

```
app/src/main/kotlin/com/mohdshayan/snarewall/
  App.kt, MainActivity.kt   process and activity entry points
  game/                     the whole rule set in plain Kotlin, no Android imports: Grid, Pathing
                            (DistanceField, AStar, Placement), Sim (fixed 60 tick step, enemies, traps),
                            ComboResolver, Content, DailyGenerator, SaveCodec, ProgressRules, StepClock
  content/                  ContentLoader, reads assets/levels and assets/tables
  data/                     Room (level progress, resume slot, daily scores), DataStore settings,
                            ProgressRepository, SaveTransfer (export through the share sheet, import)
  audio/                    Sfx on SoundPool
  di/                       ServiceLocator, the manual dependency container
  ui/theme/                 limestone and emerald tokens, Bungee and Lexend, radius scale, motion
  ui/nav/                   AppNav and the type-safe routes
  ui/home, levels, board, result, daily, settings   one package per screen
  ui/components/            buttons, state panels, sprite drawing
app/src/main/assets/        15 level JSON files, trap, enemy, combo, region and daily tables, licence texts
app/src/test/               JVM tests: every level beatable at every difficulty by a committed reference
                            maze, empty boards lose, A* and the distance field agree under fuzzed
                            placements, enemy traits, combos, resume and corrupt saves, fuzzed play
                            always ends, 1x equals 3x, allocation-free stepping, 1,000 daily maps
                            LevelForge and ShotSeeds are authoring tools that run only behind flag files
tools/build_atlas.py        builds the sprite atlas from the Kenney 1-Bit Pack
store/                      Play listing copy, icon, feature graphic, screenshots
docs/                       landing page (GitHub Pages), privacy policy, font and asset licences
```

## Bundled assets and licences

- Bungee and Lexend fonts, SIL Open Font License 1.1: `docs/OFL-Bungee.txt`, `docs/OFL-Lexend.txt`.
- Sprites from the Kenney 1-Bit Pack and sounds from Kenney Impact Sounds and Interface Sounds, all CC0.
  Sources, file mapping and licence texts: `docs/licenses/`. The same texts ship in
  `app/src/main/assets/licenses/` and show in Settings, Licences.
- The launcher icon, feature graphic, spike plate, deadfall, walls and route thread are drawn in code.
- The 15 level files and the trap, enemy, combo, region and daily tables under `app/src/main/assets/` are
  original to Snarewall and carry the same copyright as the code.

## Licence

Copyright SocialSure Private Limited.
