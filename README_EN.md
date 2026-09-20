# WHKT Dice

<p align="center">
  <strong>An Android tactical dice tray built around the Kill Team attack–defence flow</strong><br>
  Paired combat rolls, swipe navigation on phones, and two trays side by side on tablets.
</p>

<p align="center">
  <a href="README.md">简体中文</a> · <a href="README_EN.md">English</a>
</p>

<p align="center">
  <img alt="Android 7.0+" src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white">
  <img alt="License" src="https://img.shields.io/github/license/digitalghost/WHKT-dice">
  <img alt="Last commit" src="https://img.shields.io/github/last-commit/digitalghost/WHKT-dice">
</p>

WHKT Dice is an offline Android dice roller for tabletop skirmish players. Instead of behaving like a generic random-number utility, it follows the familiar “attack first, defence second” combat sequence. Both results stay visible so players can compare the roll as naturally as they would compare two physical dice trays.

> The app UI is currently available in Simplified Chinese. Contributions for English and other localizations are very welcome.

## Screenshots

### Tablet: two combat trays on one screen

<p align="center">
  <img src="docs/images/tablet-overview.jpg" alt="WHKT Dice dual-tray tablet interface" width="100%">
</p>

Each tray has its own success, critical and failure status bar. Once defence is rolled, both sets of results remain visible for immediate comparison.

### Phone: swipe between preserved trays

<p align="center">
  <img src="docs/images/phone-overview.jpg" alt="WHKT Dice defence tray on a phone" width="320">
</p>

The phone layout shows one large tray at a time. After confirming the attack, swipe horizontally to review the attack and defence trays. Reviewing the previous tray is read-only, so it cannot accidentally change the active combat stage.

## Highlights

- **Paired attack and defence flow** — roll and confirm the attack, then move directly into defence without rebuilding the encounter.
- **Responsive phone and tablet layouts** — swipe between preserved trays on phones; compare both trays side by side on tablets.
- **Readable combat results** — successes, criticals and failures use distinct colors and type emphasis; tablet trays have separate result bars.
- **Cover support** — enable cover during defence to retain one defence die as a normal success.
- **Gesture-first controls** — swipe up to roll, tap dice and swipe up to reroll, or swipe down with two fingers to clear.
- **Tactile rolling experience** — animated dice movement, collisions, settled orientations and a synchronized rolling/clattering sound.
- **Distinct tray identities** — a dark red and brass attack tray contrasts with the cold blue steel defence tray.
- **Roll history** — stores complete attack–defence encounters and emphasizes critical, successful and failed results without tying entries to a skin.
- **Centralized themes** — visual skins live behind one entry point instead of occupying the main play surface.
- **Offline and local-first** — no account or network connection is required; roll history stays on the device.

## Gestures and controls

| Action | Phone | Tablet |
| --- | --- | --- |
| Roll current dice | Swipe up on the active tray | Swipe up on the active-stage tray |
| Select dice | Tap a die | Tap a die |
| Reroll selected dice | Select, then swipe up | Select, then swipe up |
| Clear the current result | Two-finger swipe down | Two-finger swipe down |
| Review the other tray | Swipe horizontally after confirming attack | Both trays are always visible |
| Start another encounter | Tap “再次对抗” | Tap “再次对抗” |

## Requirements

- Android 7.0 (API 24) or newer
- A landscape tablet or portrait Android phone is recommended
- JDK 17 and Android SDK 34 are required when building from source

## Build from source

```bash
git clone https://github.com/digitalghost/WHKT-dice.git
cd WHKT-dice
./gradlew :app:assembleDebug
```

The generated APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected Android device with USB debugging enabled:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests and checks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

The repository includes unit tests for roll parsing and combat-result logic. The interface has also been exercised on phone and tablet emulators and on a physical Android tablet.

## Project layout

```text
app/src/main/java/com/example/helloworld/
├── MainActivity.kt       # Combat flow, responsive UI and history interaction
├── DiceTrayView.kt       # Dice rendering, animation, collisions and gestures
├── RollLogic.kt          # Success, critical, failure and reroll rules
├── RollHistoryStore.kt   # On-device roll history
└── DiceSoundPlayer.kt    # Low-latency dice sound playback

app/src/main/res/
├── layout/               # Phone layout
├── layout-sw600dp/       # Dual-tray tablet layout
└── drawable-nodpi/       # Attack tray, defence tray and visual assets
```

## Contributing

Issues and pull requests are welcome. Good first areas to contribute include:

- English and additional app localizations
- Accessibility and screen-reader improvements
- Support for more screen sizes
- Dice animation, audio and performance improvements
- Usability feedback from real tabletop sessions

Please run the unit tests and Android Lint before submitting code.

## Disclaimer

This is an unofficial, fan-made gaming aid. It is not affiliated with, sponsored by or endorsed by Games Workshop. “Warhammer 40,000,” “Kill Team,” and related names and marks belong to their respective owners. This repository does not include or replace official rules text; always use the latest official rules available to you.

## License

The project code is released under the [Apache License 2.0](LICENSE). Font license files are available in [`docs/licenses`](docs/licenses). Third-party names, trademarks and assets remain the property of their respective owners.

---

If WHKT Dice makes your games smoother, consider starring the repository and sharing your table-tested feedback through Issues.
