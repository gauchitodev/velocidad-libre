# Velocidad Libre

**English** · [Español](README.es.md)

An Android app that tells you your speed out loud while you move, whatever you're moving on. Phone in your pocket or on the mount, and every so often it tells you how you're doing, without you having to look at the screen. No ads, no accounts, no internet.

**Languages:** Español, English, Português, Français, Italiano and Deutsch. You choose one the first time you open the app, and you can change it anytime with the button at the top right. The screen, the notification and the voice all switch.

## What it does

- **Every 5 or 15 minutes** it says your current speed and the average for that stretch.
- **Every 10 km/h:** when you move into a new ten ("ninety", "one hundred", "one hundred ten"), it says it. One button turns this off.
- **0 to 100 km/h:** tap the button, stop, accelerate, and it tells you how many seconds it took to reach 100.
- **Trip history:** each trip is saved with distance, duration, average and top speed, plus its best 0 to 100. Tap a trip to see its announcements; press and hold to copy or delete it.
- It turns your music down while it talks, and it works with the screen off.
- The notification has **Say it now** and **Stop** buttons, so you don't need to open the app.

## How to install

You need Android 8 or newer.

1. On your phone, open the [latest release](https://github.com/gauchitodev/velocidad-libre/releases/latest) and download **velocidad-libre.apk**.
2. Open the file, from the download notification or from your Downloads folder.
3. The first time, Android will say it can't install apps from that source. Tap **Settings**, turn on **Allow from this source**, go back and tap **Install**.
4. If Play Protect says it doesn't recognize the app, tap **More details** and then **Install anyway**. This happens with any app that doesn't come from the Play Store.
5. Open **Velocidad Libre**, choose your language, tap **Start** and allow location ("While using the app") and notifications.
6. When it asks about the battery, tap **OK** and then **Allow**. Without this, many phones (Xiaomi, Samsung, Motorola, Huawei) kill the app when the screen is off.

The voice comes from your phone's text-to-speech. If your phone doesn't have the voice for the language you chose, the app shows a red warning: tap it to install the voice.

## Privacy

The app has no internet permission. Your trips are stored only on your phone.

## Building

It's plain Java, with no Gradle and no external libraries. `compilar.sh` builds it with aapt2, javac, d8 and apksigner. Edit the paths at the top of the script to point to wherever you have those tools.

## Adding a language

1. Copy `res/values/strings.xml` (Spanish) to `res/values-XX/strings.xml`, where `XX` is the language code, and translate the texts. Leave the `%1$s` and `%1$d` parts as they are.
2. Add the language to the three lists in `src/dev/gauchito/velocidad/Idioma.java`: its code, its name in its own language, and the text-to-speech voice (for example `nl-NL`).
3. Open a pull request.

## License

[MIT](LICENSE)
