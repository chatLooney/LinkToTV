# Link to TV
Send a video link from your phone to your Android TV; it opens in VLC.

## Build
1. Open this folder in Android Studio (it will fetch Gradle) and let it sync.
2. Run the `app` configuration on your Android TV (or `adb connect <tv-ip>` then run).
   An Android phone can also install the same APK to use as a sender (optional).

## Use (iPhone, nothing to install)
1. TV: open "Link to TV". Allow "Display over other apps" when asked. Install VLC on the TV.
2. iPhone (same Wi-Fi): scan the QR code on the TV, or open Safari and type the address shown.
3. First time only: enter the 6-digit pairing code from the TV.
4. Paste a direct video link (.mp4, .m3u8, ...) and tap Send.
   Tip: Safari Share > Add to Home Screen gives you a Link to TV icon.

## Notes
- Phone and TV must be on the same Wi-Fi. Links must be direct media URLs, not web pages.
- The pairing code changes after every pairing and after 5 wrong attempts.
- Untested scaffold: expect small fixes.
