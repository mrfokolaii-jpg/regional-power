#!/bin/bash
cd "$(dirname "$0")"
if [ -f RegionalPower.apk.b64 ]; then
  base64 -d RegionalPower.apk.b64 > RegionalPower.apk 2>/dev/null || base64 -D RegionalPower.apk.b64 > RegionalPower.apk
elif [ -f RegionalPower.apk.b64.part1 ] && [ -f RegionalPower.apk.b64.part2 ]; then
  cat RegionalPower.apk.b64.part1 RegionalPower.apk.b64.part2 | base64 -d > RegionalPower.apk 2>/dev/null || cat RegionalPower.apk.b64.part1 RegionalPower.apk.b64.part2 | base64 -D > RegionalPower.apk
else
  echo "base64 files not found"
  exit 1
fi
echo "OK: RegionalPower.apk"
ls -la RegionalPower.apk
