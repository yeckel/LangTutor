# LangTutor

An Android app for practicing a foreign language (Spanish by default) by
conversing with an AI tutor about **real webpages**. You give it a link, it
reads the article, and then chats with you about it in your target language —
asking questions, helping you form answers, and correcting your mistakes.

The guiding principle is **comprehensible input**: the tutor concentrates its
word choice around the vocabulary you already know (organized in levels,
similar to Duolingo course progression). New words appear sparingly, and only
when their meaning can be understood from context — otherwise they are glossed
inline. Powered by an **Ollama** server that you point the app at (your own
machine, a home server, or a hosted endpoint; an API key can be configured for
endpoints behind an authenticated proxy).

## Status / what exists today

- **Start screen** — paste a webpage link, preview the extracted article, start
  the conversation. "Paste text" fallback for pages that block scrapers or sit
  behind a paywall.
- **Chat screen** — streaming conversation with the tutor. Replies in the
  practice language, always ends with a question, prefixes a *"Corrección"*-style
  block (wrong → right + short explanation) when your answer contains mistakes.
- **Vocabulary levels (Step 1)** — tiered known-word lists (A0–B1, plus Off)
  bundled per language; the tutor sticks mostly to the list, introduces at most
  1–2 new words per message, glosses them in English (e.g. `el abrigo (coat)`),
  and can be nudged live in the chat with **Too hard / Just right / Too easy**.
- **Known-words tracking & resumable lessons (Step 2)** — every chat turn is
  recorded; words the tutor introduces get tracked, words you write yourself
  count as actively known; the home screen shows *Continue last conversation*
  so an app restart drops you back into the lesson mid-conversation.
- **Placement quiz (Step 3)** — *Find my level*: a short 8-question chat whose
  final model verdict (JSON) sets your vocabulary level and records the words
  you used correctly as known.
- **Words list & flash cards** — *My words*: view every tracked word (status,
  counts, cached meaning), tap to toggle known/learning, add or delete words,
  and train them: written meaning checks and *Say it* pronunciation practice
  (speech recognition in the practice language). Correct answers strengthen
  the word automatically.
- **Voice conversation (chat)** — tap the 🎙 button next to Send for a
  ChatGPT-style spoken loop: the mic listens (live transcript appears in the
  input), your speech is sent automatically, the reply streams + is read aloud
  progressively, and the mic re-opens shortly after the reply finishes. The mic
  never opens while the app is still speaking (echo protection), and the loop
  pauses on errors. ⏸/▶ pauses or resumes the mic loop without leaving voice
  mode. Enabling voice mode also switches read-aloud on and persists it;
  disable the mic to go back to typing only (mixing both is fine).
- **Speak-everything switches** — Settings: *Speak words automatically* (each
  card is read in the practice language) and *Speak meanings automatically*
  (meanings are read in your help language when revealed). Both persist;
  answers are only checked, never auto-spoken (so *Say it* practice stays
  meaningful).
- **Settings** — Ollama server URL, optional API key, practice language, help
  language, vocabulary level, model selection (fetched live from the server's
  model list; fetch tests the values currently in the fields, before saving),
  the read-aloud / speak-automation switches, and the **app language** picker.
- **App language (UI)** — choose the interface language itself in Settings:
  English, Čeština, Español, Deutsch, Polski, Slovenčina, or *system default*.
  The choice persists and overrides the phone language (AppCompat per-app
  locales), with all screens translated.

## Project layout

```
app/src/main/java/com/yeckel/tutor/
├── MainActivity.kt, TutorApp.kt, AppContainer.kt   # shell + dependency container
├── data/
│   ├── SettingsRepository.kt                       # DataStore-backed config
│   ├── ProgressRepository.kt                       # known words + resumable lesson
│   ├── ProgressModels.kt                           # ProgressWord / LessonProgress
│   ├── Tokenize.kt / Difficulty.kt / AppDataStore.kt
│   ├── OllamaClient.kt                             # /api/tags + streamed /api/chat
│   ├── OllamaModels.kt                             # JSON DTOs + StreamEvent
│   ├── ArticleFetcher.kt                           # webpage download + Jsoup extraction
│   ├── Article.kt
│   └── VocabRepository.kt                          # tiered known-word lists from assets
└── ui/
    ├── AppNavHost.kt                               # nav graph: start → chat / words / flashcards / placement / settings
    ├── start/  chat/  settings/  words/  flashcards/  placement/   # Screen + ViewModel pairs (chat has TutorPrompt.kt)
    └── theme/

app/src/main/assets/vocab/<lang>/<LEVEL>.json       # generated word lists (A0–B1)
tools/build_vocab.py                                # regenerates the lists (see docstring)
```

## Building

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug or
   newer works with the pinned AGP 8.7). Android Studio provisions the Android
   SDK and configures `local.properties` automatically.
2. Open the project folder, let Gradle sync, run the `app` configuration on an
   emulator or device.
3. Command-line builds also work via `./gradlew assembleDebug`; the Gradle
   wrapper (8.9) needs a JDK 17–22. This machine's `~/.gradle/gradle.properties`
   already points the Gradle daemon at `openjdk@17` (system JDK 27 and Android
   Studio's bundled JBR 25 are both too new for Gradle 8.9).

## Pointing the app at Ollama

- **Emulator** — Ollama on your own machine is reachable at the default
  `http://10.0.2.2:11434` (emulator alias for the host).
- **Physical phone on the same Wi-Fi** — start Ollama with
  `OLLAMA_HOST=0.0.0.0 ollama serve`, or (if you use the Ollama desktop app)
  enable its *Expose Ollama to the network* setting; then use
  `http://<your-computer-ip>:11434` (Linux: `ip addr`, macOS: `ipconfig getifaddr en0`).
- **Hosted / Ollama cloud** — the app also works against **ollama.com directly**:
  set the *Ollama server URL* to `https://ollama.com` and paste your **API key**
  into the *API key* field (sent as `Authorization: Bearer …`). The model list
  then shows the available cloud models (gemma4, glm, kimi, gpt-oss, …).
  Works from any network - no local server or port forwarding needed.
- **Model** — tap *Fetch available models* and pick one. Language-tutoring
  quality varies by model; gemma / qwen / llama families in 7–8B+ sizes handle
  Spanish well.

Plain `http://` traffic is allowed in the app manifest (`usesCleartextTraffic`).
Remove that flag once you serve over HTTPS.

## Roadmap — the steps we want to implement

Ordered from next-immediately to later. Each step is concrete enough to become
one or two work sessions.

### Step 1 — ✅ Vocabulary levels & comprehensible input (shipped)

The conversation stays inside (and slowly grows) the learner's known
vocabulary. What was built:

- **Tiered word lists as app assets** (`app/src/main/assets/vocab/<lang>/A0|A1|A2|B1.json`),
  generated by **`tools/build_vocab.py`** from open OpenSubtitles frequency
  lists (hermitdave/FrequencyWords). Cumulative tiers: A0 = 300, A1 = 900,
  A2 = 2000, B1 = 4000 most frequent words; proper nouns and junk filtered.
  Languages covered today: `es, en, de, fr, it`.
  *(Still open: overlaying community-extracted Duolingo course decks so the
  order matches Duolingo progression even more closely.)*
- **Level picker in Settings** ("Vocabulary level" chips), persisted with the
  rest of the configuration. Default A1.
- **Tutor prompt rules (i+1 / comprehensible input):**
  - the large majority of every reply consists of words from the active list;
  - at most 1–2 **new** words per message;
  - new words that are not inferable from context get an inline gloss in the
    learner's help language, e.g. `el abrigo (coat)`;
  - the tutor prefers rephrasing with known words over difficult vocabulary.
- **Live comprehension control on the chat screen:** **Too hard / Just right /
  Too easy** chips; the current selection is folded into the system prompt for
  the next answer.
- Long word lists + long articles fit the context: local (non-cloud) models
  are requested with `num_ctx = 8192`.

### Step 2 — ✅ Known-words tracking + resumable lessons (shipped)

What was built:

- **Persistent progress store** (`ProgressRepository`, DataStore-backed JSON —
  deliberately not Room yet: the data is small, and this avoids an annotation
  processor; migrating to Room is a mechanical follow-up if the lists grow).
- **Word tracking per language**, on top of the bundled level lists:
  - every tutor reply is tokenized; words *outside* the active list are
    recorded with an "introduced by tutor" counter;
  - every learner reply strengthens words it contains (in the list or already
    tracked) with a "used by learner" counter — one use marks a word **known**;
  - Settings shows the live *Tracked words from your chats* count.
- **Resumable lesson**: article, language, level, difficulty nudge and the
  whole conversation are stored as the active lesson; the start screen shows a
  *Continue* card (`N messages · level · language`) and the chat resumes the
  conversation exactly where it left off — no new greeting.
- **System prompt refresh per message**: besides the level list it now ships
  "learner already knows these words" (up to 150) and "currently being taught"
  (up to 15) so the tutor deliberately reuses recently introduced vocabulary
  (spaced-repetition lite).

### Step 3 — ✅ Placement & level entry (shipped)

- **Placement chat**: a single model call generates 8 graded questions
  (A0→B1 ladder), the learner answers, and a second structured call
  (`format: "json"` + fence-stripping) produces
  `{level, knownWords[], reason}`.
- **Apply** clamps the verdict to the supported tiers, saves it as the
  vocabulary level, and merges the demonstrated words into the tracker
  (status: known).
- Helper call channel: `OllamaClient.chatOnce(..., expectJson)` for all
  structured side-flows (placement + word meanings).

### Step 3b — ✅ Words list, editing & flash-card training (shipped)

- **My words screen**: tracked words per practice language with status
  (✓ known / • learning), per-word stats and cached meanings; tap toggles
  known/learning, ✕ deletes, "+ Add a word" for manual entries.
- **Flash-card training**: shuffled deck of tracked words; meanings are
  translated once by the configured model and cached in the progress store;
  per card the learner can
  - **write** the English meaning (fuzzy match, accent/article tolerant),
  - **say it** aloud (Android speech recognition localized to the practice
    language; fuzzy match on the transcript),
  - or **reveal**; correct answers increment the word's strength counters
    (`usedByLearner`, `trainCorrect`), misses count as `trainMissed` —
    the prompt's known-word set updates automatically on the next chat.
- Requires the `RECORD_AUDIO` permission (requested in context on first use).

### Step 3c — ✅ Level progression: tier mastery & unlocks (shipped)

- The *My words* screen shows a **Level progress** section: per-tier mastery
  rows (mastered vs practiced, with bars; the current tier and completed tiers
  marked).
- Completing a tier (enough practiced words with ≥80% mastered) shows an
  **Unlock next tier** card; unlocking raises the vocabulary level so the tutor
  may use richer vocabulary — manual gating instead of a silent jump.

### Step 4 — ✅ Voice practice & spoken conversation (shipped)

- Android TTS reads tutor replies aloud **progressively** — sentence by
  sentence as the model streams them, language matched to the practice
  language; chat top-bar toggle and the Settings switches persist.
- Speech recognition (Android `SpeechRecognizer`) for the flash-card *Say it*
  practice: auto-listen after TTS, retries with a budget, pause/resume, and a
  *Listening* indicator. "I don't know" (typed or spoken — recognized in
  several languages) counts as a miss and reveals the meanings.
- **Voice conversation** in chat (see the feature list above): spoken turns
  auto-send, replies are read aloud, and the mic re-opens only when the reply
  is completely spoken (echo protection).
- Answer checking is LLM-judged and tolerant (synonyms, dropped articles,
  pronoun-prefixed forms); only exact form matches skip the model call.
- Optional: pronunciation feedback via phonetic transcription of what it heard
  vs. expected — still open.

### Step 5 — Chat UX refinements

- ✅ **Flash-card deck filter** — on the cards screen chips pick *All* or the
  current vocabulary tier; the deck rebuilds (counters restart).
- ✅ **Explanation card** — in chat, a glossed pattern like `el abrigo (coat)`
  is underlined; tapping it opens a mini card with the meaning and an
  *Add to my words* button (status: learning).
- ✅ **Retry/regenerate** — shown next to errors; re-asks the tutor after
  dropping the failed (possibly partial) attempt.
- ✅ **Pause for the spoken loop** — ⏸/▶ next to the 🎙 pauses the mic loop
  without leaving voice mode.
- Corrections as distinct rendering (wrong → right → reason), not inline text. *(partially shipped: flash cards render per-word state)*
- Conversation archive (the *last* lesson already resumes; a full history view
  is future work).
- Optional pronunciation-feedback pass (Step 4 leftover).

### Step 6 — Robustness & quality

- Pre-summarization step for very long articles (currently truncated
  client-side at ~12k chars).
- Upgrading the dependency set (AGP/Kotlin/Compose BOM pinned to a known-good
  late-2024 combination; Android Studio offers one-tap upgrades).
- HTTPS-only once the server setup allows it (drop `usesCleartextTraffic`).
- Optional: OpenAI-compatible endpoint support (Ollama also exposes `/v1`) so
  any compatible backend works, not just Ollama.

## Debug helper (development)

Debug builds register a one-command configuration preset for device testing:

```
adb shell am broadcast -a com.yeckel.tutor.DEBUG_CONFIG \
  --es url "https://ollama.com" --es key "YOUR_API_KEY" \
  --es model "gemma4:31b" --ez read_aloud true --es app_lang "cs"
```

Omitted extras keep their current values; `app_lang` accepts `system`, `en`,
`cs`, `es`, `de`, `pl`, `sk` and applies the per-app locale immediately. A
second broadcast, `com.yeckel.tutor.DEBUG_SEED_WORDS`, seeds the word tracker
for quick testing. Not compiled into release builds.

## Roadmap ideas (parking lot)

- Import Anki decks / community Duolingo decks directly (`*.apkg` parsing).
- Multiple articles per conversation ("catch up on two stories").
- A word-practice mini-game mode using the known-words database.
- Per-model quality profiles (recommend models good at the chosen language).
