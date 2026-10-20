# Regional Power — Android

## Build با Android Studio
1. این پوشه را Open کن
2. Gradle Sync
3. Build → Build Bundle(s) / APK(s) → Build APK(s)

خروجی معمولاً:
`app/build/outputs/apk/debug/app-debug.apk`

## خط فرمان
```bash
cd android
./gradlew assembleDebug
```

## مراحل
فایل‌های `app/src/main/assets/stages_part1.json` تا `part4` را بخوان؛ یا از `godot/data/` کپی کن.

Package: com.mrfokolaii.regionalpower  
minSdk 21 · targetSdk 33
