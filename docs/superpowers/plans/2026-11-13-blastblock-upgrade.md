# Blast Block upgrade

1. Make new classic games wait for the save check. Confirm replacement when an active save exists; keep an unreadable save protected and leave Continue unchanged.
2. Show a one-move practice board on the first new classic game. Accept only the highlighted cell, show the real line-clear animation, and let the player start or skip without saving practice data.
3. Play original short sounds for placement, a clear, or a combo. Add a persistent menu toggle and release the audio player with the screen.
4. Translate all visible strings to English and declare English and Turkish for Android's per-app language picker.
5. Add a daily mode with a UTC date, a reproducible opening board and full piece sequence, and a separate per-day personal best. Each attempt starts from the opening; the classic save and top ten are untouched.

Test the date boundary, repeatability, playable opening, practice clear, menu decisions and daily save isolation. Then build offline, run lint and the unit suite, and exercise the flows on the local Android emulator.
