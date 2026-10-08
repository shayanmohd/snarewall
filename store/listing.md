# Google Play store listing

**Package:** `com.mohdshayan.snarewall`
**Developer account:** SocialSure Private Limited
**Pricing:** Free, with one in-app product full_unlock (one-time, non-consumable, purchase option full-unlock-buy); no advertising

<!-- The sections up to Assets are the phase 2 listing, published with 1.1.0 when the app turns free
     (PILOT-DECISIONS D5 steps 4 and 5). The phase 1 edit to the live paid listing and the 1.1.1 release
     notes are in their own sections at the end. Prices are set by hand in the Play Console (D6) and are
     never written in this listing or in the app: Google Play shows the price. -->

## App name
<!-- at most 30 characters. verify.sh counts everything in this section except comments. -->
Snarewall: Maze Tower Defense

## Short description
<!-- at most 80 characters, one line, no trailing period. Never "free", "no ads", "one-time unlock",
     "no subscription", a price or "try" (PILOT-DECISIONS D7). 74 of 80. -->
Build walls that bend the enemy route and chain traps. Plays fully offline

## Full description
<!-- at most 4000 characters. Plain paragraphs and CAPITALISED headings; Play renders no markdown.
     Say what the app does, what it stores, that it works offline, the free part and the one purchase.
     The promise paragraph stays exactly as written, on its own line (D7). -->
Snarewall is a maze tower defense where your walls decide the enemy path. Every level is open ground with a few fixed rocks, not a set track: drag a wall into place and the route line redraws while your finger is still down, with the change in steps shown at the keep, so you see the new path before the first raider arrives. A wall that would cut the keep off entirely is refused.

Chain your traps. A pusher shoves raiders onto a deadfall. An oil slick next to an ember grate carries its fire. A frost vent sets up the shatter hammer. A snare net holds enemies on a spike plate. Linked traps show a mark between them, so you know a combo is live.

Free to use. One optional purchase unlocks everything, once, for good. No ads, no subscription, no account. Works fully offline.

Free to play: all of Chalk Downs (levels 1 to 5) on all three difficulties, and the daily map every day. The purchase opens the full game: Salt Mine and Fen Causeway, levels 6 to 15.

FREE
- Chalk Downs: 5 handmade levels on Warden, Standard and Iron. Clear a level for bronze, beat its par scores for silver and gold
- 7 of the 10 traps and 2 of the 4 combos, ending with the White Horse warlord
- A daily endless map built from the UTC date, so everyone playing that day gets the same board, traps and waves, with your best score kept for every day. Any trap and any enemy can turn up on it
- 1x, 2x and 3x speed, pause and build at any time, and undo for anything placed before you send the wave
- Your run is saved at the start of every wave, so closing the app never costs you the level
- Export your save file through the share sheet and import it again from any file

FULL GAME, ONE PAYMENT
- Salt Mine and Fen Causeway: 10 more handmade levels on all three difficulties, with four more warlord fights
- Enemies that punish lazy mazes: diggers tunnel under a long detour, jumpers hop a wall one block thick, flyers ignore walls, sappers knock one down, oilskins shrug off fire and frostborn cannot be slowed
- All 10 traps in the campaign, each with one upgrade, and all 4 combos

ONE UNLOCK, NOTHING ELSE
The full game is a single in-app purchase. There is no premium currency, no energy timer, no ads and no subscription, and levels open as you clear them. Buying or restoring the full game goes through Google Play and needs a connection once; after that Snarewall works fully offline. The unlock belongs to the Google account that bought it, so it comes back after a reinstall or on a new phone signed in to that account. If you bought Snarewall when it was a paid app, the full game stays yours; if a new phone shows it locked, write to us with your Google Play order number and we will send a code.

OFFLINE PLAY
Snarewall plays fully offline, with no login. The app has no internet permission, so the app itself never sends your play anywhere. Google Play handles the purchase through the Play Store app.

ROOM ON A TABLET
On a tablet or an unfolded phone the board sits beside the trap tray, so the maze gets the room.

Snarewall is a strategy game with fantasy combat shown as small one-colour sprites. There is no blood.

## Release notes
<!-- at most 500 characters, shown under What's new. 1.1.0 (versionCode 2), published at the switch to
     free (D5 step 5), so it speaks to buyers updating and to new players. -->
Snarewall is now free to start: Chalk Downs (levels 1 to 5) and the daily map are free, and one purchase opens Salt Mine and Fen Causeway, levels 6 to 15. If you bought Snarewall before this change, nothing changes for you: every level stays open, and your progress, medals and daily scores are untouched. Settings has a new Full game section. If levels 6 to 15 ever show locked, write to us from there with your Google Play order number and we will send a code.

## Category
Games, Strategy

## Content rating
IARC long game questionnaire, answered again with 1.1.0. See SHIP-READY.md for the exact answers: fantasy violence against stylised fantasy humanoids and creatures (raiders, sappers, a warlord, plus a bat and a crab-like swarmling), shown as small one-colour 16 pixel sprites, no blood, no gore, no death animation beyond the sprite disappearing. No user interaction, no gambling. Digital purchases: yes, one in-app product. Expected roughly ESRB Everyone 10+ with In-Game Purchases; PEGI 7 or 12 (USK 6 or 12) depending on how IARC scores human-like targets.

## Data safety declaration
<!-- These answers are right only while the built APK has no INTERNET permission and no analytics SDK.
     Google Play's billing system collects payment details directly; the app never sees them, and the
     ownership answer stays on the phone. -->
- Does your app collect or share any of the required user data types? **No**
- Is all of the user data collected by your app encrypted in transit? Not applicable, no data collected
- Do you provide a way for users to request that their data is deleted? Not applicable, no data collected

## Privacy policy URL
https://shayanmohd.github.io/snarewall/privacy-policy.html

## Assets
- App icon: `store/play-icon-512.png` (512x512)
- Feature graphic: `store/play-feature-graphic-1024x500.png` (1024x500)
- Phone screenshots: `store/screenshots/` (6 PNG files, 1080x1920), captions in `store/captions.json`. Upload in the order of its `"shots"` array, 01, 02, 04, 03, 05, 06, not in file name order: the full-game shots 03 and 05 come after the free ones.

## Phase 1 edit to the live paid listing
Made in the Play Console while 1.0.0 is still live and paid, before full_unlock is activated (PILOT-DECISIONS D5 step 2). It removes the in-app purchase claims from the live short and full description and changes nothing else. The exact before and after text is in the comment below, so the unlock checks never read the old claims as live copy.
<!--
Short description
  live: Build walls that bend the enemy route and chain traps. No ads, no IAP, offline
  new:  Build walls that bend the enemy route and chain traps. No ads, offline
  (70 of 80 characters, no trailing period)

Full description, section NOTHING ELSE TO BUY, its first sentence
  live: There are no in-app purchases, no premium currency and no energy timers.
  new:  There is no premium currency and no energy timer.

Nothing else in the live listing changes in phase 1. The phase 2 text above replaces all of it at the switch.
-->

## 1.1.1 release notes
<!-- 1.1.1 (versionCode 3) sets PriorBuyerPolicy.PAID_UNTIL_MS (D5 step 6). At most 500 characters; 263 of 500. -->
This update makes sure everyone who installed Snarewall before it became free to start keeps the full game. If levels 6 to 15 are still locked for you after buying the paid app, write to us from Settings with your Google Play order number and we will send a code.
