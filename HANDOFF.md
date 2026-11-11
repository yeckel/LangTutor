# LangTutor — session handoff (Mon Nov 9, 2026)

Fresh-start notes, so the next session can pick up mid-stride.
Everything below lives on `origin/main`, clean tree.

## Where the app stands

Whole UI translated in 6 languages (EN/CZ/ES/DE/PL/SK).

Today's additions, in commit order:

1. `625b408` — **App-language switch** in Settings (System/EN/Čeština/Español/
   Deutsch/Polski/Slovenčina), persists and overrides the phone locale
   (AppCompatActivity + `AppLocalesMetadataHolderService`); every screen
   translated (78 keys × 6).
2. `89e3282` — **Voice conversation mode in chat**: 🎙 next to Send → speak →
   auto-send → reply streams on screen + progressive read-aloud → mic
   re-opens automatically (ChatGPT-voice-style loop).
3. `e879dc8` — **Echo fix**: the mic never opens while the app speaks; the
   reopen waits until the TTS queue drains after the reply streamed fully,
   plus a 600 ms settle delay.
4. `7cb614f` — README refresh; tutor-prompt gloss aligned to the help
   language (was "English" in one rule).
5. `7d2a1ec` — **New launcher icon** (two speech bubbles + spark on indigo,
   monochrome layer for themed icons), **flash-card tier filter** (Vše/A1
   chips, deck rebuild + fresh counters), **explanation card** (tap an
   underlined gloss → bottom sheet with meaning + "Add to my words"),
   **Retry** button next to errors (drops the failed attempt, regenerates),
   **pause/resume** (⏸/▶) for the spoken loop.
6. `10f0977` — **Practice-language dropdown in Settings** (es/en/de/fr/it,
   native names; custom stored values kept as extra options), pick auto-saves.
7. **💡 Reformulated sentence**: when the learner's message has mistakes, the
   correction block now ends with a `💡 Puedes decir: "…"`-style line (fixed
   emoji marker; full corrected version of the whole message, practice
   language). Chat renders that line as a distinct primaryContainer card with
   a 🔊 glyph — tap to hear it spoken (TTS, practice-language locale); in
   voice mode the mic closes during playback and reopens after (reuses the
   reply's reopen machinery). The 💡 emoji is stripped from all read-aloud.

From earlier days: ollama.com cloud backend + Bearer key (model
`gemma4:31b`), streaming chat with corrections, vocabulary tiers A0–B1
per language (es/en/de/fr/it), known-words tracking, resumable lessons,
placement quiz, flash cards (write + say-it), tier mastery bars and
unlock cards.

## Verified vs not verified

- ✅ **Reformulation card verified live on the phone** (Tue Nov 11): typed a
  flawed Spanish sentence ("… because tiene muy mal tiempo") → full
  `Corrección:` block + 🔊 `Puedes decir: "…"` card + normal response; with
  voice mode on, tapping the card speaks it (mic closes → "Mluvím"), then the
  mic reopens ("Poslouchám") with no echo and no stray turns. The 💡 emoji is
  never read aloud (stripped in `queueReadAloud`). Old lessons simply render
  plain text (the prompt change only affects new replies).
- ✅ Emulator (Nov 9): new UI verified live — full Czech home + settings,
  voice mode on/off, pause/resume transitions, deck counts with the
  filter chips, retry on a broken-connection error, no crashes.
- ✅ **Spoken voice conversation on the physical phone** (Nov 11): the mic
  listen/reopen loop works end-to-end on real hardware (observed with the
  suggestion-card tap test).
- ⏳ Practice-language dropdown live-verified (Nov 10): field, menu,
  persistence across force-stop.

## Screenshots

`docs/screenshots/` has five README shots (start, chat reformulation, words,
flashcards, settings — API key redacted), taken Tue Nov 11 on the phone,
386×834 PNG. The settings shot MUST stay redacted — the live UI shows a real
API key; recreate it with a blur over the key field if you retake it.

## Environment notes

- **Gradle needs JDK 17**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/
  openjdk.jdk/Contents/Home` — already pinned via `~/.gradle/gradle.properties`
  (`org.gradle.java.home`). JDK 27 and Studio's JBR 25 break Gradle 8.9.
- AGP incremental-dexing trap: after source edits, `assembleDebug` can stay
  "up-to-date" and ship a stale APK — run `./gradlew clean :app:assembleDebug`
  when a deployed change doesn't appear.
- **Never `adb uninstall` the phone app** — it wipes DataStore (tracked
  words + lesson). Seed via `am broadcast -a com.yeckel.tutor.DEBUG_SEED_WORDS
  --es lang es --es seed_words "w1,w2,w3"` — note the extra name is
  **`seed_words`**, not `words`. Only words OUTSIDE the tier lists get
  tracked (common words are skipped by design).
- Emulator app language is currently **Czech** (`--es app_lang cs`); reset
  with `--es app_lang system` for English-UI checks. The extra lives on the
  DEBUG_CONFIG broadcast.
- Emulator DNS is unreliable: direct Wikipedia fetches hang — use the
  paste-text flow for articles; ollama.com requests work.
- Devices: phone `R5CY839VXDV` (Samsung A56, PIN-locked — ask the user to
  unlock before UI automation), emulator `emulator-5554` (AVD
  `Tutor_Emulator`).
- Ollama cloud API key is in both devices' DataStore — never commit it.
- `svc power stayon` was restored to normal on the phone at end of day.

## Roadmap candidates for next time (pick one)

1. Conversation archive — browse past lessons, not just "continue last".
2. Corrections layout — wrong → right → reason styling (the 💡 reformulation
   card is done; the fix list itself is still plain text).
3. Pronunciation feedback — heard vs expected phonetics.
4. Long-article pre-summarization (client-side truncation at ~12k chars).
5. Release APK with proper versioning (`versionName` still `0.1.0`,
   debug-signed).
