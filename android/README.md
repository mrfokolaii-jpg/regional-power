# Regional Power — Android

## ساخت APK با Android Studio

1. [Android Studio](https://developer.android.com/studio) را نصب کن
2. **Open** → پوشه `android` همین مخزن
3. منتظر Gradle Sync بمان
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. فایل APK را نصب کن

```bash
cd android
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Package: `com.mrfokolaii.regionalpower`  
Version: 1.0.1
