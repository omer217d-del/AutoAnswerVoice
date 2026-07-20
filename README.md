# AutoAnswer Voice

Gelen aramaları otomatik yanıtlayan, ses mesajı çalan ve sonra kapatan Android uygulaması.  
Kişisel kullanım için; minSdk **31** (Android 12), targetSdk **34** (Android 14).

## Kurulum

1. Zip'i aç → Android Studio'da **"Open"** ile aç
2. `app/src/main/assets/message.mp3` dosyasını kendininkiyle değiştir  
   (Proje içinde 2 saniyelik test tonu var — doğrudan derlenip çalışır)
3. `Run` butonuna bas veya: `./gradlew assembleDebug`

## İlk Kullanım

1. **Telefon Durumu** iznini ver (açılışta otomatik sorulur)
2. **"Erişilebilirliği Aç"** → Listeden *AutoAnswer Voice*'u bul ve aç
3. Android 13+ için bildirim iznini de ver
4. Ana ekrandaki **KAPALI** butonuna bas → Servis başlar

## Çalışma Akışı

```
Telefon RINGING
  → Accessibility: tüm pencerelerde "Yanıtla" butonu aranır → tıklanır
  → Bulunamazsa: AudioManager HEADSETHOOK eventi gönderilir
  → 1.5 sn bekle → OFFHOOK

Telefon OFFHOOK (wasRinging=true ise)
  → message.mp3 çalınır
  → Ses bitince → Accessibility: "Bitir" butonu aranır → tıklanır

Telefon IDLE → sıfırla
```

## Dosya Yapısı

```
app/src/main/
├── java/com/autoanswervoice/
│   ├── MainActivity.kt                   — Tek ekran (aç/kapat + durum)
│   ├── CallService.kt                    — Foreground servis, TelephonyCallback, MediaPlayer
│   ├── AutoAnswerAccessibilityService.kt — Yanıtla/Bitir butonlarına basar
│   └── BootReceiver.kt                   — Yeniden başlatmada servisi açar
├── assets/
│   └── message.mp3                       — Otomatik çalınan ses (değiştir!)
└── res/xml/
    └── accessibility_service_config.xml  — Tüm OEM dialer uygulamalarını kapsar
```

## GitHub Actions

Her `git push`ta otomatik debug APK üretilir.  
**Actions → Son çalışma → Artifacts → AutoAnswerVoice-debug**

## İzinler

| İzin | Amaç |
|------|-------|
| `READ_PHONE_STATE` | Arama durumunu algıla (RINGING/OFFHOOK/IDLE) |
| `FOREGROUND_SERVICE` | Arka planda çalış |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ medya türü |
| `RECEIVE_BOOT_COMPLETED` | Yeniden başlatmada servisi aç |
| `WAKE_LOCK` | Ekran kapalıyken aktif kal |
| `POST_NOTIFICATIONS` | Android 13+ bildirim |
| `MODIFY_AUDIO_SETTINGS` | Headsethook eventi gönder |

## OEM Uyumluluğu

`AccessibilityService` tüm pencereleri tarar (`flagRetrieveInteractiveWindows`).  
Bulunan butonlar içerik açıklaması veya view ID'sine göre eşleştirilir.  
Pixel, Samsung, Xiaomi ve AOSP tabanlı cihazlarda çalışır.  
Buton bulunamazsa `KEYCODE_HEADSETHOOK` eventi yedek olarak kullanılır.
