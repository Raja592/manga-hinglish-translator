# Manga Hinglish Translator — Android App

A native Android app that captures your manga screen, OCRs the English text with ML Kit, and translates it into natural Indian Hinglish using Google Gemini AI.

## Features

- **Floating Button** — draggable overlay that works on top of any app (Tachiyomi, MangaDex, etc.)
- **Screen Capture** — uses Android MediaProjection API to take a screenshot on demand
- **ML Kit OCR** — on-device text recognition, no internet needed for OCR
- **Gemini AI Translation** — natural Hinglish that sounds like real Indian conversation
- **Dark Mode UI** — deep purple dark theme throughout
- **Copy Buttons** — copy original, Hinglish, or both

---

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 26+ (minSdk 26)
- A device or emulator running Android 8.0+
- A **free Gemini API key** from [aistudio.google.com](https://aistudio.google.com/app/apikey)

---

## Build Instructions

### Step 1 — Open in Android Studio

1. Launch **Android Studio**
2. Click **File → Open**
3. Select this folder (`MangaHinglishTranslator`)
4. Wait for Gradle sync to complete (first sync downloads ~200 MB of dependencies)

### Step 2 — Build the APK

**Option A — Debug APK (recommended for testing):**
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

**Option B — Release APK:**
```
Build → Generate Signed Bundle / APK → APK
```
You will need to create a keystore. Follow Android Studio's wizard.

**Option C — Command line:**
```bash
# Debug
./gradlew assembleDebug

# Release (needs signing config)
./gradlew assembleRelease
```

### Step 3 — Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or drag the APK file onto your device and open it from a file manager.

---

## First-Run Setup (on device)

1. **Open the app** — "Manga Hinglish Translator"
2. **Grant Overlay Permission** — tap "Grant" → enable "Display over other apps"
3. **Grant Notification Permission** (Android 13+) — required for the foreground service
4. **Enter Gemini API Key** — paste your key from aistudio.google.com, tap "Save"
5. **Tap "Start Floating Button"** — the app minimizes, a purple button appears on screen
6. **Open your manga app** (Tachiyomi, etc.), navigate to a page
7. **Tap the purple floating button** — first time, you'll get a system dialog asking for screen capture permission. Allow it.
8. The OCR + translation happens automatically. Results appear as an overlay.

---

## Project Structure

```
app/src/main/
├── AndroidManifest.xml           — Permissions and service declarations
├── java/com/manga/hinglish/translator/
│   ├── MainActivity.kt           — Setup/permission screen
│   ├── FloatingOverlayService.kt — Foreground service: floating button + screen capture
│   ├── ScreenCapturePermissionActivity.kt — Handles MediaProjection permission dialog
│   ├── OcrProcessor.kt           — ML Kit text recognition
│   ├── GeminiTranslator.kt       — Gemini 1.5 Flash REST API call
│   ├── TranslationResultActivity.kt — Overlay showing original + Hinglish
│   └── GeminiApiKeyStore.kt      — SharedPreferences key storage
└── res/
    ├── layout/
    │   ├── activity_main.xml
    │   ├── floating_button.xml
    │   └── activity_translation_result.xml
    ├── drawable/                 — Vector icons, floating button background
    └── values/                   — Colors, strings, themes (dark mode)
```

---

## Key Permissions Explained

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Required for the floating button over other apps |
| `FOREGROUND_SERVICE` | Keeps the service alive while you use other apps |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Android 14+ requirement for screen capture in foreground services |
| `INTERNET` | Gemini API call |
| `POST_NOTIFICATIONS` | Android 13+ notification for the foreground service |

---

## Gemini Translation Prompt

The system prompt instructs Gemini to:
- **Never use Devanagari script** — only Roman letters
- **Keep character names** exactly as-is (Naruto, Luffy, etc.)
- **Preserve manga energy** — shouting, dramatic pauses, exclamations
- **Sound like real Indian conversation** — uses yaar, bhai, arre, kya, nahi, etc.
- **Match tone** — angry lines stay angry, funny lines stay funny

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Floating button doesn't appear | Check "Display over other apps" is on for this app in Settings |
| "No text found" | Make sure the manga page is fully loaded and visible. Some pages have anti-OCR encoding. |
| Gemini API error | Check your API key is correct. Free tier has rate limits — wait a few seconds and retry. |
| Screen capture shows wrong screen | Make sure to tap the button AFTER navigating to the manga page |
| App crashes on Android 14 | Make sure you're using this project as-is — it has the `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission declared |

---

## Dependencies

| Library | Version | Use |
|---|---|---|
| ML Kit Text Recognition | 16.0.0 | On-device OCR |
| Google Generative AI SDK | 0.3.0 | Gemini API |
| OkHttp | 4.12.0 | HTTP networking |
| Kotlin Coroutines | 1.7.3 | Async processing |
| Material Components | 1.11.0 | Dark theme UI |

---

## License

MIT — free to use, modify, and distribute. 
