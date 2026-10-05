# Sheliak

A music player for Android that brings your own music together: one library merged from local files and the
media servers you run yourself, played by Sheliak's own player. No streaming-service accounts, no data collection.

Sheliak (β Lyrae) is the second star of the constellation Lyra, and the app is built on the UI contract of
[Lyra](https://github.com/CrsMthw/Lyra): Material 3 Expressive, one adaptive layout for phones, foldables and
tablets, Material You or an accent colour of your choice, and an AMOLED black mode.

## Status

Early development: the project skeleton is in place. There is no release yet.

## Sources, in the order they arrive

1. **Plex**: sign-in, library sync and playback
2. **Local device**: the phone's own music
3. **Jellyfin**
4. **Samba** (SMB shares)

Later on the roadmap: more servers (Emby, Subsonic/OpenSubsonic and others) and Google Cast output, each source
behind the same interface so the merged library never changes shape.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
