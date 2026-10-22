# Pocket

An Android expense tracker modelled on the MyMoney app, written as original code, with two add-ons: it notices
payments made in UPI apps and offers to add them, and it has an AI chat about your own spending.
Not affiliated with MyMoney. Everything is stored on the phone.

## Features

- **Records** with day, week, month, 3-month, 6-month and yearly views, sorting, totals, edit, duplicate and delete.
  Add and edit through a calculator keypad.
- **Accounts and categories** (create, edit, delete, detail screens), **budgets** per month with carry-over,
  **analysis** (donut and bar charts, per-account), **search**.
- Backup and restore (own `.mbak` JSON file), CSV and PDF export, passcode lock, home-screen widget, twice-daily
  reminder, six colour themes in light and dark.
- **Payment detection.** An accessibility service reads the screen of the payment apps you choose (PhonePe, Google Pay,
  Amazon Pay, Paytm, BHIM by default). When a payment succeeds it posts an "Add to Pocket?" notification with the
  amount, payee and a suggested category. Add opens a pre-filled entry; No dismisses it. Nothing opens by itself.
- **Category suggestions**, all on the device: what you last chose for that payee, payees spelled almost the same,
  past notes that share a word, then a built-in list of Indian brands and everyday words.
- **Ask Pocket (AI).** A chat that answers questions about your spending, in the style of the Claude app: streaming
  replies, chat history, tables, and a locally drawn spending-trend chart. It works with any OpenAI-compatible
  provider you set up (base URL, API key, model).

## Privacy

- Records, chats and settings stay in the app's private storage. Pocket asks for the internet permission only for the
  AI chat.
- The payment watcher reads on-screen text of the apps you list, only to find a success message, amount and payee.
- When you chat, a text summary of your records (account balances, monthly category totals, your latest 80 records)
  is sent to the provider you configured. Do not set a provider you do not trust. The API key is kept out of
  Android's cloud backup but is stored unencrypted in the app's private storage.
- Cleartext `http://` is allowed so a local model server (LM Studio, Ollama) works.

## Build

Needs JDK 21 and the Android SDK (platform 36). JDK 25 does not work with this Android Gradle Plugin.

```
export JAVA_HOME=~/tools/jdk21            # or any JDK 21
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug                   # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest               # calculator and category-suggestion tests
```

### Release build

`./gradlew assembleRelease` makes a shrunk (R8) build at `app/build/outputs/apk/release/app-release.apk`. It is signed
when a `keystore.properties` file exists in the project root:

```
storeFile=/absolute/path/to/pocket-release.jks
storePassword=...
keyAlias=pocket
keyPassword=...
```

Create the key once with `keytool -genkeypair -keystore pocket-release.jks -alias pocket -keyalg RSA -keysize 4096
-validity 10000`. `keystore.properties` and `*.jks` are git-ignored; **back both up somewhere safe**, because without
the key an installed release can never be updated. A release build is signed with a different key from the debug build,
so it cannot be installed over it (uninstall first, after a backup). To try a release build next to the debug app, add
`-PidSuffix=.rel`.

## Layout

Single module `:app`, package `app.pocket`, Kotlin, XML layouts with ViewBinding, Material 3, no DI, no Room.

- `data/` storage (`Store`, SQLite), models, periods, preferences, formatting, category suggestions (`Suggest`), AI
  client (`Ai`).
- `ui/` screens: main tabs, entry (`InputActivity`), chat (`ChatActivity`), settings, backup, export, lock.
- `PaymentWatcher.kt` the accessibility service and its notifications; `Widget.kt`, `Reminder.kt`.
- `tools/` scripts that generate the icon drawables and theme palettes.
- `CLAUDE.md` and `handoff/` notes for working on the project with Claude Code.

## Credits

Fonts (SIL Open Font License): Lexend Deca, Lato, Pacifico, Newsreader. Icons: Material Design icons (Apache 2.0).
