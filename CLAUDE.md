# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Session handoffs

`handoff/LATEST.md` holds the previous session's state and the ordered to-do list (max 20 lines). Skills: say "session handoff" to write one, "session resume" to load it (`.claude/skills/`).

## State of the repo

Git repo on `main` (local; the user adds the remote and pushes; never commit `keystore.properties` or `*.jks`). Goal: an Android clone of the **MyMoney** expense tracker (`com.raha.app.mymoney.free` v6.6-free, vc 57, minSdk 23, targetSdk 37) plus two add-ons. Everything below under "Reference app" was recovered from the installed APK on the author's phone, not from source.

## Add-on features (the point of the clone)

1. **Payment detection** (changed 2026-10-22: the forced entry screen was removed, nothing opens by itself): when a configured payment app (user-editable package list; seed with `com.phonepe.app`, `com.google.android.apps.nbu.paisa.user`, `in.amazon.mShop.android.shopping`, `net.one97.paytm`, `in.org.npci.upiapp`) shows a payment-success screen, an AccessibilityService posts an "Add to Pocket?" notification with Add / No. Add opens a pre-filled `InputActivity`.
2. **Screenshot to entry**: a trigger (floating button / quick-settings tile) captures the payment app screen (AccessibilityService `takeScreenshot`, API 30+, or MediaProjection), OCRs amount/payee on-device, and opens the entry screen pre-filled and editable.

## Reference app (reverse-engineered)

- Single `base.apk` (no splits), one `classes.dex`, AndroidX + DataStore + Preferences, no native libs. Permissions: only `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.
- Screens (package `com.raha.app.mymoney.ui.activity`): Splash (launcher), Main (bottom tabs: Records, Analysis, Budgets, Accounts, Categories), Input (add/edit record), Search, Export, BackupRestore, Pass/PassPreference (app lock), Preference, Reset, Migration, Intro, Help, ActionWidgetConfig. Only Splash is exported; `adb shell am start` on the others fails with SecurityException — launch via the launcher icon or `monkey`.
- Storage: SQLite file `MyMoney.db` (plus `MyMoneyWidgetConfig.db`). Schema (from dex strings):
  - `table_record(rec_id long pk, rec_time long, rec_type int, rec_amount text, rec_note text, rec_acc_id long, rec_cat_id long, rec_from_id long, rec_to_id long)` — `rec_from_id`/`rec_to_id` are for transfers; amounts are stored as **text**.
  - `table_account(acc_id, acc_name, acc_amount text, acc_initial text, acc_icon int)`
  - `table_category(cat_id, cat_name, cat_type int, cat_icon int)`
  - `table_budget(bug_id, bug_cat_id, bug_limit text, bug_time long)`
  - `table_widget(wgt_id, wgt_total, wgt_smart, wgt_dark, wgt_pass)`
- Not debuggable, so `run-as` and `/data/data` are unreachable without root; the live DB can only be inspected via the app's own Backup/Export.
- Currency is INR (₹); dark, yellow-on-grey theme.

## Working against the phone

- Device: OnePlus CPH2707, wireless debugging at `192.168.0.218:33123`. The port changes whenever Wireless debugging is toggled; reconnect with `adb connect <ip>:<port>` (pair first with `adb pair` if needed).
- Pull the reference APK: `adb shell pm path com.raha.app.mymoney.free` then `adb pull <path>`. Scratch copy lives in the Claude scratchpad, not in this repo.
- jadx 1.5.6 is installed at `~/tools/jadx/bin/jadx` (no sudo/apt needed). Decompile with `~/tools/jadx/bin/jadx -d <out> base.apk`; 22 of 3465 classes fail, none in app code. App code is only ~48 files (~7k lines excluding `R.java`); the rest is obfuscated libs under `defpackage/`. The REA `inspect_android_package` route still needs `REA_JADX_MCP_JAR` (separate jadx-headless-mcp jar) and is unconfigured; use the CLI above instead.
- Model: `model/Record` (types 1=income, 2=expense, 3=transfer; `BigDecimal amount`, `note`, `Account`/`Category`, `transferFrom/To`; `id` = creation time in ms). Parcelable.
- `InputActivity` actions: `<pkg>.ADD_RECORD`, `ADD_RECORD_START`, `ADD_RECORD_EXIT`, `ADD_RECORD_PREFILLED` (takes a `Record` in extra `ex_rec`), `UPDATE_RECORD`. It `finish()`es unless the intent carries boolean extra `key_internal_call=true`. The clone's "prefilled entry from screenshot" maps directly onto `ADD_RECORD_PREFILLED` + `ex_rec`.
- Google Pay is a system-installed app (`com.google.android.apps.nbu.paisa.user`), so `pm list packages -3` misses it.

## Project layout

Single module `:app`, package `app.pocket`, Kotlin, XML layouts + ViewBinding, Material 3 / AppCompat / RecyclerView / Fragment, no DI, no Room. minSdk 33, target/compile 36. Goal is a faithful clone of MyMoney's screens and behaviour written as original code (nothing copied from the decompiled source; icons are open Material icons, fonts are the same OFL fonts: Lexend Deca, Lato, Pacifico).
- `data/` — `Models.kt`, `Store.kt` (the only SQLite access; MyMoney's exact table/column names; account balances are derived, never stored; seeds default accounts/categories; DB v2 seeds on upgrade from the v1 skeleton), `Period.kt` (day/week/month/3mo/6mo/year window math), `Prefs.kt`, `Fmt.kt`, `Icons.kt` (MyMoney's icon-id scheme 1-19 / 101-109 / 121-175 / 201-232 / 301-328 -> `ic_i<id>` drawables).
- `ui/` — `MainActivity` (drawer + toolbar + period header + bottom nav + one shared FAB that delegates to the current fragment via `FabHost`), `MainViewModel` (period window + header totals shared with fragments), `RecordsFragment` (day-grouped list; also holds `showRecordMenu`), `InputActivity` (type toggle, account/category sheets, `Calc` keypad, date/time pickers), `Editors` (account/category create-edit-delete dialogs and the chooser bottom sheets), `ListFragments` (Accounts, Categories, placeholder), `SearchActivity`, `Calc` (expression buffer, unit-tested).
- `PaymentWatcher.kt` (package root) — AccessibilityService + `DismissReceiver`. Opening a watched app posts a silent "Scanning for a transaction success record" notification (id 3, has "Stop scanning" = mute until the app is left). A screen counts as a payment when the text has a success phrase and at most one distinct ₹ amount (history lists have several); it does NOT need the UPI-PIN "arm" step, and old records' detail pages trigger too (same in every app, on purpose). Each payment posts its own notification (unique id, they stack) with amount, payee and a suggested category; a 30 s same-app-same-amount guard stops redraw duplicates. Payee = text after "Paid to / Paying / Sent to", avatar initials dropped. `data/Suggest.kt` picks the category: first what the user last chose for that exact payee (`table_payee`, DB v4, learned when an entry opened from the payment notification is saved, key = lowercase letters/digits of the payee, newest choice overwrites), then payees spelled almost the same (`Suggest.similar`: 3-letter-chunk Dice score >= 0.5 against past expense notes and learned payees, neighbours vote), then past expenses whose note shares a word with the payee, then from a built-in brand/word list (`rules`, first match wins, resolved by name against the user's expense categories); unit-tested, but the list was written by hand and tested on made-up names only, so check it against real payees. Needs POST_NOTIFICATIONS (MainActivity asks; adb cannot grant it). Verified on PhonePe, Amazon Pay and others on the phone. Debug a missed app by adding a `Log.d` of `text` in `onAccessibilityEvent`, or `adb shell uiautomator dump`.
- AI chat (`ui/ChatActivity`, `ui/AiSettingsActivity`, `ui/Md.kt`, `data/Ai.kt`): side menu "Ask Pocket (AI)". The user sets base URL, API key and model for any OpenAI-compatible provider (stored in prefs file `pocket_ai`, excluded from cloud backup by `res/xml/backup_rules.xml` / `data_extraction_rules.xml`; cleartext HTTP is allowed via `network_security.xml` for local servers). `Ai.stream` posts `/chat/completions` with `stream=true`, parses SSE (falls back to a plain JSON reply), and prepends a text summary of the user's data (accounts, 12 monthly category totals, latest 80 records) as the system prompt, so no tool calling. Chats live in `table_chat` / `table_chat_msg` (DB v3, Pocket-only, cleared by Delete & Reset, NOT in the .mbak backup). UI copies the Claude Android app (screenshotted from the phone): its own `chat_*` colours (near-black / warm cream, follows system night mode, ignores Pocket palettes), serif replies in Newsreader (`res/font/newsreader.ttf`, OFL; needs `app:fontFamily` too because the theme sets one), user bubbles, composer card with + / model chip / round send button, full-height drawer of past chats (search, rename, delete, "New chat" pill), reply actions (copy, share, retry), streaming with stop, minimal markdown, and a locally drawn "spending trend" card (role `chart`, no model call). Tested with a real DeepSeek key. Verified on the phone against a mock server (`adb reverse tcp:8099 tcp:8099`, base `http://127.0.0.1:8099/v1`); not yet against a real provider.
- Resource gotcha: `layout_width/height/weight` set via a `style` are ignored by LayoutInflater (crash: "You must supply a layout_width"); always put them on the view.
- Icons are generated, not hand-edited: `python3 tools/gen_icons.py` (downloads `@material-design-icons/svg`, rewrites `res/drawable/ic_*.xml`).
- Palette: default MyMoney palette only, light + dark via `values/` and `values-night/` colors (`pk_*`), exposed as theme attrs `pkNegative`, `pkPositive`, `pkBar`, etc. The other five palettes (classy, darker, nature, rosy, sky) are not ported.

### Done vs not done (parity with MyMoney 6.6)
Done and verified on the phone: Records (period views, sort, totals, day groups, edit/duplicate/delete), add/edit entry with calculator, Accounts and Categories (CRUD, detail screens), Analysis (expense/income overview donut, expense/income flow bar charts, account analysis), Budgets (per-month limits, copy from past months, carry-over), search, Preferences, Backup & Restore (own JSON .mbak; MyMoney's is encrypted and NOT importable), Delete & Reset, CSV export (share sheet), passcode lock (`Lock` + `PassActivity`; relocks 3 s after the app leaves the foreground), home-screen widget (`Widget.kt`, config screen, one-tap add, smart input, passcode option), first-run intro, Help, Feedback (mailto, no preset recipient), twice-daily reminder (logic written, only the notification itself unverified), launcher icon.
Add-ons: payment watcher + notification (`PaymentWatcher.kt`) works on real payments (2026-10-22), see the layout entry above. UPI apps broadcast nothing, so it reads on-screen text; a missed app needs its success wording added to `success`. IDEA, deliberately NOT built (privacy): ask the user's own chat provider (`Ai`) to pick a category for a payee nobody else could place, sending only the payee name plus the user's category list; build it only as an explicit opt-in. On-device decision models (Laya, d1-omni, tev1:0.8b) and embedding models were benchmarked 2026-10-22 and rejected: 26-28% on obscure names. Planned: screenshot OCR (ML Kit, ~4 MB) and an on-device category model (embedding model vs FunctionGemma, to be benchmarked on ~30 real payee names; needs the user's list). Screenshot-to-entry is deferred to v2 (`canTakeScreenshot` is already declared in `res/xml/accessibility_config.xml`).
Themes and PDF export are now built (below). All seven features from MyMoney's Pro list exist in Pocket.
Themes: Original + Classy/Darker/Nature/Rosy/Sky, each light and dark. `tools/gen_themes.py <decoded-MyMoney-res-dir>` writes `res/values*/themes_palettes.xml` (only colour numbers are read); the theme is applied per activity by `App`'s lifecycle callbacks (`Prefs.themeRes`, stale screens recreate on resume). Widgets keep fixed light/dark colours.
PDF: `ui/PdfReport.kt` (android PdfDocument, A4, summary + category bars + paginated transactions), shared from Export Report. Verified by pulling the file with `adb shell run-as app.pocket cat cache/exports/<file>` and rendering with `pdftoppm`.

Icons: category/account icons are drawn as coloured round badges (`ui/BadgeDrawable`, colours in `data/Icons.kt` `fixed` map + rotating palette). They use open Material glyphs, never MyMoney artwork. UI chrome icons stay plain tinted glyphs.
Header (`MainActivity.updateHeader`) now shows on every tab: period totals (Records/Analysis), Budget/Spent (Budgets), all-time "[ All Accounts ₹x ]" + Expense/Income so far (Accounts/Categories).
Test data: a sample backup (`PocketBackup_sample.mbak`, 20 records, 4 accounts, budgets) was restored on the phone for UI checks; use Delete & Reset to clear it.

Entry screen (`InputActivity`) mirrors MyMoney's layout: Cancel/Save row, INCOME | EXPENSE | TRANSFER selector, Account/Category (From/To) captions + outlined buttons, large notes box, outlined calculator display, operator column on the left of the keypad. Portrait is `layout/activity_input.xml`; landscape (`layout-land/`) is two columns. The activity is no longer `configChanges`-locked: its state (type, amount, ids, time) is saved/restored across rotation. Landscape main (`layout-land/activity_main.xml`) uses an icon-only bottom bar, a scrolling toolbar and a fixed status-bar strip (`content_root` inset padding).
Tapping a record (Records, Search, Account/Category detail) opens `showRecordCard` (`ui/RecordCard.kt`, `dialog_record.xml`): a card coloured by type with close/delete/edit/duplicate, amount, time, account/category chips and note. Only its Edit/Duplicate go to `InputActivity`. The + FAB is shown on Records only; Accounts/Categories use their inline "Add new" button.
Testing rotation over adb: `settings put system accelerometer_rotation 0; settings put system user_rotation 1` (landscape) / `0` (portrait); restore `accelerometer_rotation 1` afterwards.

Gotchas learned: RemoteViews (widgets) only allow a fixed set of view classes (no plain `<View>`; use `ImageView`); layout attributes in `style=` are ignored by LayoutInflater; Settings/other non-exported activities cannot be `am start`ed from adb.

## Build

Toolchain is user-local (no sudo): JDK 21 at `~/tools/jdk21`, Gradle 8.13 wrapper (`./gradlew`). JDK 25 (system default) does not work with AGP 8.13, so always set JAVA_HOME:

```
export JAVA_HOME=~/tools/jdk21
./gradlew assembleDebug                       # APK at app/build/outputs/apk/debug/
adb -s 192.168.0.218:33123 install -r app/build/outputs/apk/debug/app-debug.apk
```

Release: `./gradlew assembleRelease` (R8 on, 3.6 MB vs 17.7 MB debug) signs with the key named in the git-ignored `keystore.properties` (keystore at `~/.android/pocket-release.jks`, created 2026-10-22; the user must back up both). It cannot install over the debug app (different signature; uninstall wipes data, so back up first). `-PidSuffix=.rel` builds a side-by-side copy (`app.pocket.rel`) for testing R8 on the phone; the minified build was checked that way (intro, main, chat), uninstall it afterwards. The phone currently has the debug build. Phone over USB is serial `167dfaca`.

SDK at `~/Android/Sdk` (platform 36, build-tools 36 + 35); `local.properties` holds `sdk.dir`. Verified on the phone: builds, installs, records can be added and listed. Unit tests: `./gradlew testDebugUnitTest` (only `CalcTest`, 10 tests passing; carry-over and backup have no automated tests, they were checked by hand); single test: `./gradlew testDebugUnitTest --tests app.pocket.ui.CalcTest`. The OnePlus "install finished" screen steals focus after `adb install`; press back before `adb shell am start -n app.pocket/.ui.MainActivity`. App crashes show in `adb logcat -d -b crash`.
