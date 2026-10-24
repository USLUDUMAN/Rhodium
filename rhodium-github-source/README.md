# Rhodium

Rhodium is a client-side Fabric mod for Minecraft 26.3. It adjusts visual quality settings gradually to help keep the game near a chosen FPS target. It works in singleplayer and when joining multiplayer servers. The server does not need Rhodium installed.

## Features

- Automatic and manual modes.
- Gradual FPS-based adjustment of render distance, simulation distance, entity distance, and particle quality.
- When performance needs help, quality is reduced in this order: particles, entity distance, simulation distance, then render distance.
- When there is stable FPS headroom, quality is restored gradually: render distance, simulation distance, entity distance, particles, then the remaining render distance. Multiplayer skips the simulation-distance increase stage and respects limits imposed by the server.
- A configurable target FPS, upgrade margin, render/simulation caps, and individual controls for managed settings. Default caps: 16 render chunks and 12 simulation chunks.
- Saves changed game options so quality adjustments persist across restarts.
- A compact F3 status panel showing the current mode, FPS, next adjustment, and quality levels.
- Turkish, English, and Spanish translations; the interface follows the game's selected language.
- Mod Menu integration. Sodium and Iris compatibility is considered.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API
- Java 25 or newer
- Mod Menu is optional and provides the in-game configuration screen.

Rhodium is client-side. Install it on the player's client; it is not required on dedicated servers.

## Build from source

Install Java 25 or newer, then run the Gradle wrapper from the project root.

### Windows

```powershell
.\gradlew.bat build
```

### Linux / macOS

```sh
./gradlew build
```

The built mod JAR is written to `build/libs/`.

## Türkçe

Rhodium, Minecraft 26.3 Fabric için geliştirilmiş istemci taraflı bir moddur. Oyunu seçilen FPS hedefine yakın tutmaya çalışırken render mesafesini, simülasyon mesafesini, varlık mesafesini ve parçacık kalitesini kademeli olarak ayarlar. Tek oyunculuda ve çok oyunculu sunuculara bağlanan istemcide çalışır; sunucuya kurulması gerekmez.

FPS yetersiz kaldığında kaliteyi parçacıklar → varlık mesafesi → simülasyon mesafesi → render mesafesi sırasıyla düşürür. FPS yeterince uzun süre kararlı biçimde hedefin üzerinde kaldığında kaliteyi kademeli olarak geri yükseltir. Çok oyunculuda simülasyon mesafesini yükseltme aşamasını atlar ve sunucunun sınırlarına uyar.

Mod; otomatik ve manuel mod, hedef FPS, yükseltme marjı, render/simülasyon tavanları ve yönetilecek ayarlar için kontroller sunar. Varsayılan tavanlar render için 16, simülasyon için 12 chunk'tır. Oyun seçeneklerine yaptığı değişiklikleri kaydeder. F3 paneli anlık durumu ve sıradaki ayarı gösterir. Mod Menu yapılandırma ekranı isteğe bağlıdır. Arayüz Türkçe, İngilizce ve İspanyolca çeviriler içerir.
