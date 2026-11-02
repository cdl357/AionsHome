#!/usr/bin/env bash
# 发布 APK 到 VPS 下载目录（手机随时可下）：
#   bash scripts/publish-apk.sh AionApp/app/build/outputs/apk/release/app-release.apk 0.3.83
# 产物 URL：
#   http://134.175.7.196:8783/apk/AionsHome-<版本>.apk   （18443 端口同样可用）
set -euo pipefail
APK="${1:?用法: publish-apk.sh <apk路径> <版本号>}"
VER="${2:?用法: publish-apk.sh <apk路径> <版本号>}"
HOST=ubuntu@134.175.7.196
LOCAL_SHA=$(sha256sum "$APK" | cut -d' ' -f1)
scp -o BatchMode=yes "$APK" "$HOST:/tmp/AionsHome-$VER.apk"
ssh -o BatchMode=yes "$HOST" "
  sudo mkdir -p /opt/apk-public &&
  sudo mv /tmp/AionsHome-$VER.apk /opt/apk-public/ &&
  sudo chmod 644 /opt/apk-public/AionsHome-$VER.apk &&
  sha256sum /opt/apk-public/AionsHome-$VER.apk | cut -d' ' -f1
"
echo "已发布: http://134.175.7.196:8783/apk/AionsHome-$VER.apk"
echo "本地 SHA256: $LOCAL_SHA（与远端输出比对应一致）"
