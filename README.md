# Regional Power

بازی پاک‌سازی ۱۰۰ شهر ایران — تک‌نفره + آمادهٔ توسعه چندنفره

## ساخت APK (پیشنهادی)

### Android Studio
```bash
git clone https://github.com/mrfokolaii-jpg/regional-power.git
```
1. Android Studio → **Open** → پوشه `android`
2. منتظر Gradle Sync
3. **Build → Build APK(s)**
4. نصب روی گوشی

جزئیات: [android/README.md](android/README.md)

### Godot 4
پوشه `godot/` را Import کن → Project → Export → Android

## ساختار مخزن

| مسیر | توضیح |
|------|--------|
| `android/` | پروژه کامل Android (Java + Canvas) |
| `android/.../MainActivity.java` | کل منطق بازی موبایل |
| `godot/` | پروژه Godot 4 |
| `godot/data/stages_part1..4.json` | ۱۰۰ شهر ایران |
| `godot/scripts/` | بازیکن، دشمن، مرحله، UI |
| `godot/scenes/` | صحنه‌های منو و بازی |

## سناریو
دشمن پیاده وارد شهرهای ایران شده؛ بازیکن (انفرادی یا با کمک روسیه / چین / کره شمالی در حالت تیمی) مناطق را پاک‌سازی می‌کند.

## کنترل موبایل
- چپ: جوستیک حرکت
- راست: شلیک، مشت، اسلاید، ری‌لود، ویژه

Package: `com.mrfokolaii.regionalpower`  
توسعه‌دهنده: Mohammad Fokolaii
