# Velocidad Libre

**English** · [Español](README.es.md)

An Android app that tells you your speed out loud while you ride. Built for motorcycles: phone in your pocket, earphones on, and every so often it tells you how you're doing. No ads, no accounts, no internet.

The app and its voice are in Spanish.

## What it does

- **Every 5 or 15 minutes** it says your current speed and the average for that stretch.
- **Every 10 km/h:** when you move into a new ten ("noventa", "cien", "ciento diez"), it says it. One button turns this off.
- **0 to 100 km/h:** tap the button, stop, accelerate, and it tells you how many seconds it took to reach 100.
- **Ride history:** each ride is saved with distance, duration, average and top speed, plus its best 0 to 100. Tap a ride to see its announcements; press and hold to copy or delete it.
- It turns your music down while it talks, and it works with the screen off.
- The notification has **Decir ahora** (say it now) and **Parar** (stop) buttons, so you don't need to open the app.

## How to install

You need Android 8 or newer.

1. On your phone, open the [latest release](https://github.com/gauchitodev/velocidad-libre/releases/latest) and download **velocidad-libre.apk**.
2. Open the file, from the download notification or from your Downloads folder.
3. The first time, Android will say it can't install apps from that source. Tap **Settings**, turn on **Allow from this source**, go back and tap **Install**.
4. If Play Protect says it doesn't recognize the app, tap **More details** and then **Install anyway**. This happens with any app that doesn't come from the Play Store.
5. Open **Velocidad Libre**, tap **Empezar** (start) and allow location ("While using the app") and notifications.
6. When it asks about the battery, tap **Dale** (OK) and then **Allow**. Without this, many phones (Xiaomi, Samsung, Motorola, Huawei) kill the app when the screen is off.

If the voice speaks English, go to **Settings › System › Languages › Text-to-speech output** and choose Spanish.

## Privacy

The app has no internet permission. Your rides are stored only on your phone.

## Building

It's plain Java, with no Gradle and no external libraries. `compilar.sh` builds it with aapt2, javac, d8 and apksigner. Edit the paths at the top of the script to point to wherever you have those tools.

## License

[MIT](LICENSE)
