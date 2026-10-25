# Link to TV
Send a video link from your phone to your Android TV; it opens in VLC.
Made by Looney: https://looney.is-a.dev

## Build
Push to GitHub: the included workflow builds `app-debug.apk` (Actions > latest run > Artifacts).
Or open the folder in Android Studio and build.

## Use (iPhone, nothing to install)
1. TV: open Link to TV. Install VLC on the TV.
2. iPhone (same Wi-Fi): scan the QR code on the TV, or open the address in Safari.
3. First time only: enter the 6-digit pairing code.
4. Paste a direct video link (.mp4, .m3u8, ...) and tap Send. Recent links and favourites are listed on the page.
5. TV > Paired devices: select a phone to remove it.

## Notes
- Same Wi-Fi required. Links must be direct media URLs, not web pages.
- Updating from an older build: phones must be paired again once.
- Untested scaffold: expect small fixes.
