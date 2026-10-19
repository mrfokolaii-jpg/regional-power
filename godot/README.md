# Regional Power (Godot 4) — بدون Unity

پاک‌سازی ۱۰۰ شهر ایران از نیروی پیاده دشمن.  
تک‌نفره یا تیمی (ایران + روسیه + چین + کره شمالی).

## ساخت APK (بدون Unity)

1. دانلود رایگان [Godot 4.3+](https://godotengine.org/download) (استاندارد، نه .NET)
2. Import پوشه `regional-power-godot`
3. Project → Export → Android
4. یک‌بار Android Build Template را از خود Godot نصب کن
5. Export → فایل `RegionalPower.apk`

نیاز به Unity نیست.

## محتوا
- ۱۰۰ مرحله شهرهای ایران (`data/stages_100.json`)
- AI دشمن: Grunt / Rusher / Shooter
- ذخیره پیشرفت، XP
- منوی انتخاب مرحله + قفل مراحل

## کنترل (دسکتاپ تست)
- WASD / فلش: حرکت
- Space: پرش
- کلیک چپ: شلیک

توسعه‌دهنده: Mohammad Fokolaii · mrfokolaii@gmail.com  
Package: com.mrfokolaii.regionalpower
