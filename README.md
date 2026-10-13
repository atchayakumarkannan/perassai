# AK AI — Jarvis-style Android assistant (starter build)

Native Kotlin + Jetpack Compose app for Android 8+ (target SDK 35), prepared for testing on a Redmi Note 12 5G / Android 14.

## In this build

- Chat interface with Android speech recognition input and optional spoken replies.
- Optional Gemini API chat using `gemini-2.5-flash` (model availability and free quota can change; API key required for general AI chat).
- Explicit on-device memories: save, list, and clear memories.
- App picker: browse launcher apps installed on the device, add/remove them from AK AI's app library, launch them, and select a default music app.
- Basic commands: open a selected app, open the dialer/contacts/settings, web search, open a website, set an alarm, and open Spotify / YouTube Music where installed.
- Music search hand-off: try a Spotify search URI or YouTube Music search URL. Direct song playback depends on the target app and its supported links/API.
- Local reminders: schedule a reminder in a number of minutes/hours from chat or through the Reminders tab, show a notification, and reschedule saved reminders after reboot.
- Image Studio: select an image, apply offline original / black-and-white / warm / cool looks, and save a copy to `Pictures/AK AI`.
- GitHub Actions workflow to build and upload a debug APK.

## Important limitations

- This is a first implementation of the assistant, not unrestricted or fully autonomous phone control.
- General chat uses a cloud Gemini API. Messages and included memories are sent to Google for responses. Review Google's current terms before use. The API key is stored in app-private preferences for this personal prototype, but an APK is not a secure place for a secret key.
- Free AI quotas and model access can change. Local memories, app launching, reminders, and basic image filters do not require an AI API key.
- The image studio in this build does **not** yet do generative AI image editing. It provides free offline filters; a separate image model/provider needs to be integrated and tested before promising free generative edits.
- Notification delivery depends on granting notification permission and the device's battery/background policies. Reminder times use Android's inexact-friendly alarm API and may not fire at the exact second.
- Android does not let ordinary apps freely control every third-party app. Spotify / YouTube Music playback behavior depends on installed versions and their supported intents, links, or APIs.
- No always-listening wake word is included. This avoids unnecessary microphone use and battery drain in the first build.

## Build APK with GitHub Actions

1. Create a GitHub repository and upload the **contents** of this folder (not the ZIP itself).
2. Open **Actions** and run **Build AK AI APK** (or push to `main` / `master`).
3. Download the `AK-AI-debug-apk` artifact from the completed workflow run.
4. Extract `app-debug.apk` and install it on your phone. Allow installation from the app used to download/open the APK if Android asks.

The project uses Gradle 8.9, Android Gradle Plugin 8.7.3, JDK 17, and Android SDK 35 through GitHub Actions. This workspace did not have the Android SDK/Gradle installed, so the source was not locally compiled or device-tested here.

## Enable AI chat

1. Create an API key in Google AI Studio.
2. Open AK AI → Settings → paste the key → Save.
3. Messages used for AI responses are sent to Google's Gemini API. Never commit your API key to source control.

## Example commands

- `Remember that my project presentation is on Friday`
- `What do you remember?`
- `Open app Spotify` (add Spotify first in Apps)
- `Open YouTube Music`
- `Play music Coldplay`
- `Set Spotify as music app` (Spotify must be in your app library)
- `Remind me to study in 20 minutes`
- `Remind me to call home in 2 hours`
- `Open dialer`
- `Open contacts`
- `Open settings`
- `Search for power system engineering jobs`
- `Open website example.com`
- `Set an alarm for 7:30 AM`
- `Call 9876543210` (opens the dialer; it does not place the call automatically)
