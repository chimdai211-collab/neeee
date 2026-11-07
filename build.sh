#!/bin/bash
echo "===================================="
echo "  MicEQ Pro - Auto Build APK"
echo "===================================="
cd "$(dirname "$0")"
if [ -f "./gradlew" ]; then
  chmod +x gradlew
  ./gradlew assembleDebug
  if [ $? -eq 0 ]; then
    echo ""
    echo "✅ APK created:"
    find . -name "*.apk" -not -path "*/intermediates/*" 2>/dev/null
  fi
else
  echo "No gradlew found. Open in Android Studio and build."
fi
