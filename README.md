# HAL – Minimal Launcher

HAL is a calm Android home screen with text only. It helps you use your phone less.

HAL is free. It has no ads, no subscription and no in-app purchases. HAL collects no data and has no internet permission.

## Features

- Text-only home screen. No icons. You choose the apps that show.
- Three styles: Paper, Mono and Quiet. Each style has a light and a dark theme.
- Two tiles on the home screen: pickups and screen time today.
- Focus: lock distracting apps at the times you choose.
- Always allowed: one list of apps that Focus and the daily limit never lock.
- Daily limit: HAL suggests a limit from your average of the last 7 days.
- A pause before apps that are not on the home screen. The pause grows from 5 to 60 seconds each day.
- Usage reminders, quiet apps, hidden apps, app rename and an A–Z bar.
- Usage screen: time per app today and this week.
- 11 languages.

HAL locks apps only inside HAL. Notifications, recent apps and other launchers can still open a locked app. HAL does not use an Accessibility service.

## Build

Requirements:

- JDK 17 or later to run Gradle. Gradle downloads the JDK 11 toolchain for the Kotlin compiler.
- Android SDK with platform 36. Set `ANDROID_HOME`, or put `sdk.dir` in `local.properties`.

Steps:

1. Clone the repository.
2. Run `./gradlew assembleDebug` to build a debug APK.
3. Run `./gradlew test` to run the unit tests.
4. Run `./gradlew installDebug` to install the app on a connected device.

The repository has no signing configuration. A release build (`./gradlew bundleRelease`) is unsigned. Sign it with your own key.

## License

HAL is licensed under the GNU General Public License v3.0 or later. See [LICENSE](LICENSE).

Copyright (C) 2025-2026 dab-code

The bundled fonts Literata and Chivo Mono use the SIL Open Font License 1.1. See `app/src/main/assets/licenses/`.
