# AutoAnswerVoice

Gelen aramaları otomatik yanıtlayan ve önceden kaydedilmiş bir ses mesajı (recording.mp3) çalan Android uygulaması.

## Yapılan Düzeltmeler (v1.1)

### Sorun 1 — Çağrı yanıtlanmıyor
**Kök neden:** Uygulama yalnızca Accessibility Service üzerinden UI düğmelerine tıklamaya çalışıyordu. Bu yöntem üretici bağımlıdır ve çoğu cihazda başarısız olur.

**Düzeltme:**
- `ANSWER_PHONE_CALLS` izni manifest'e eklendi.
- `CallService` artık **önce** `TelecomManager.acceptRingingCall()` (Android 8+ yerleşik API'si) ile aramayı yanıtlar.
- Bu başarısız olursa Accessibility Service fallback'i devreye girer.
- Accessibility Service'deki anahtar kelime listesi genişletildi (Samsung, Xiaomi, Huawei, OPPO uyumluluğu).

### Sorun 2 — Ortam sesi karşı tarafa gidiyor
**Kök neden:** `MediaPlayer` varsayılan olarak `STREAM_MUSIC` kanalını kullanır. Bu kanalda mikrofon susturulmaz; ortam sesi aramaya karışır.

**Düzeltme:**
- Mesaj çalmaya başlamadan önce `audioManager.isMicrophoneMute = true` ile **mikrofon tamamen susturulur.**
- `MediaPlayer` artık `AudioAttributes.USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH` kullanır (telefon araması ses kanalı).
- `audioManager.mode = AudioManager.MODE_IN_CALL` ile ses motoru arama moduna alınır.
- Mesaj bitince mikrofon tekrar açılır ve arama kapatılır.

> **Not:** GSM/cellular aramalarda sesi karşı tarafa iletmenin Android standart API'si yoktur. `USAGE_VOICE_COMMUNICATION` ile birçok cihazda ses uplink'e karışır, ancak bu davranış üretici HAL implementasyonuna bağlıdır. Mikrofon susturma her cihazda çalışır.

---

## Kullanım

### Gereksinimler
- Android 12+ (API 31)
- Aşağıdaki izinlerin verilmesi:
  - **Telefon durumunu oku** (`READ_PHONE_STATE`)
  - **Aramaları yanıtla** (`ANSWER_PHONE_CALLS`) ← YENİ
  - **Mikrofon** (`RECORD_AUDIO`)
  - **Bildirim göster** (Android 13+)
- Erişilebilirlik servisinin etkinleştirilmesi

### Kurulum adımları
1. Uygulamayı yükle ve aç.
2. İzin istemlerini **Onayla**.
3. **"Erişilebilirlik Servisi Aç"** butonuna bas → *AutoAnswer Voice*'i etkinleştir.
4. **"Ses Kaydı Yap"** butonu ile mesajını kaydet → **"Kaydı Durdur"**.
5. **"KAPALI"** butonuna bas → Servis başlar (**"AÇIK"** olur).
6. Artık gelen aramalar otomatik yanıtlanır ve mesaj çalınır.

### Akış
```
Gelen Arama
    ↓
TelecomManager.acceptRingingCall()  [birincil]
    │  başarısız olursa
    └→ AccessibilityService UI tıklama  [yedek]
         │  başarısız olursa
         └→ HEADSETHOOK tuşu simülasyonu
    ↓
CALL_STATE_OFFHOOK algılandı (1 sn gecikme)
    ↓
Mikrofon sustur  +  Mod = IN_CALL
    ↓
recording.mp3 çal  (varsa)
    yoksa → message.mp3 çal  (assets)
    ↓
Çalma bitti → Mikrofon aç → Aramayı kapat
```

---

## Derleme
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
