# PassVault

[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)

An offline Android password manager in the spirit of KeePass, built with Kotlin and Jetpack Compose.
Your passwords are stored in a single encrypted vault on the device, protected by a master password.
The app has no internet permission and uses only the cryptography built into Android.

## Features

- Encrypted vault protected by a master password
- Entries with title, username, password, URL and notes, plus search
- Password generator (length 8–64, selectable character sets, `SecureRandom`)
- One-tap copy of usernames and passwords; copied secrets are marked sensitive and cleared after 30 s
- Auto-lock after 60 s in the background, plus a manual lock button
- Change master password (re-encrypts the vault with a new salt)
- Encrypted backup export and restore through the system file picker
- Screenshots and screen recording blocked (`FLAG_SECURE`)
- Excluded from cloud backup and device-to-device transfer

## Security design

| | |
|---|---|
| Cipher | AES-256-GCM (authenticated encryption) |
| Key derivation | PBKDF2-HMAC-SHA256, 600,000 iterations, random 16-byte salt |
| Key storage | Never stored; derived from the master password at unlock and wiped from memory on lock |
| Integrity | The file header is authenticated as GCM associated data, so any tampering is detected |
| Writes | Atomic (temporary file + atomic rename), so a crash can't corrupt the vault |

Vault file format (`files/vault.pvlt` in the app's private storage, and the format of exported backups):

```
"PVLT" | version (1 byte) | PBKDF2 iterations (int32, big-endian) | salt (16) | IV (12) | ciphertext + GCM tag (16)
```

The decrypted payload is a JSON document containing the list of entries.

### Limitations

- Not compatible with KeePass `.kdbx` files
- Uses PBKDF2 (built into Android) rather than Argon2
- The master password passes through Compose text fields as a `String`, which can't be reliably wiped from memory
- There is no password recovery: if you forget the master password, the vault cannot be opened

## Requirements

- Android 8.0 (API 26) or newer
- To build: JDK 17+ and the Android SDK (platform 35)

## Building

```sh
./gradlew testDebugUnitTest   # run unit tests
./gradlew assembleDebug       # debug APK -> app/build/outputs/apk/debug/
```

On Windows use `gradlew.bat`. Point Gradle at your Android SDK with the `ANDROID_HOME` environment variable
or a `local.properties` file containing `sdk.dir=...`.

### Signed release build

1. Create a signing key (keep it safe and **never commit it**):
   ```sh
   keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias passvault \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Copy `keystore.properties.example` to `keystore.properties` and fill in your passwords.
3. Build:
   ```sh
   ./gradlew assembleRelease     # -> app/build/outputs/apk/release/app-release.apk
   ```

Without `keystore.properties` the release APK is built unsigned.
Android only installs updates signed with the same key, so back up `release.jks` and its password.

## Installing on a phone

- **Via USB:** enable Developer options and USB debugging, then run `adb install app-release.apk`.
- **Manually:** copy the APK to the phone, open it, and allow installing from that source when prompted.

## Project structure

```
app/src/main/java/dev/passvault/
├── crypto/         VaultCrypto (file format, AES-GCM, PBKDF2), PasswordGenerator
├── data/           Entry model + JSON codec, VaultRepository (file I/O, sessions)
├── ui/             Compose screens: setup/unlock, entry list, edit, settings
├── VaultViewModel  App state, locking, persistence
├── SecureClipboard Sensitive clipboard with auto-clear
└── MainActivity
app/src/test/       Unit tests for the vault format and password generator
```

## License

[MIT](LICENSE)
