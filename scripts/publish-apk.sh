#!/usr/bin/env bash
# 发版三件套：VPS 直链 + GitHub Release 资产 + releases/ 目录推送
# 用法: bash scripts/publish-apk.sh <apk路径> <版本号，如 0.3.84>
# 产物:
#   http://134.175.7.196:8783/apk/AionsHome-<版本>.apk   （18443 同路径）
#   https://github.com/cdl357/AionsHome/releases/download/v<版本>/AionsHome-<版本>.apk
set -euo pipefail
APK="${1:?用法: publish-apk.sh <apk路径> <版本号>}"
VER="${2:?用法: publish-apk.sh <apk路径> <版本号>}"
HOST=ubuntu@134.175.7.196
REPO=cdl357/AionsHome

# ── 1. VPS 直链 ──
LOCAL_SHA=$(sha256sum "$APK" | cut -d' ' -f1)
scp -o BatchMode=yes "$APK" "$HOST:/tmp/AionsHome-$VER.apk"
REMOTE_SHA=$(ssh -o BatchMode=yes "$HOST" "
  sudo mkdir -p /opt/apk-public &&
  sudo mv /tmp/AionsHome-$VER.apk /opt/apk-public/ &&
  sudo chmod 644 /opt/apk-public/AionsHome-$VER.apk &&
  sha256sum /opt/apk-public/AionsHome-$VER.apk | cut -d' ' -f1
")
[ "$LOCAL_SHA" = "$REMOTE_SHA" ] || { echo "SHA 不一致，中止"; exit 1; }
echo "VPS OK: http://134.175.7.196:8783/apk/AionsHome-$VER.apk"

# ── 2. GitHub Release（令牌走 git credential，不落盘不回显） ──
TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill 2>/dev/null | sed -n 's/^password=//p')
REL_JSON=$(python - "$VER" "$TOKEN" <<'PYEOF'
import json, sys, urllib.request
ver, tok = sys.argv[1], sys.argv[2]
H = {'Authorization': 'Bearer ' + tok, 'Accept': 'application/vnd.github+json', 'User-Agent': 'aionshome-publisher'}
body = json.dumps({
    "tag_name": "v" + ver, "target_commitish": "mobile-companion",
    "name": ver + " · AionsHome",
    "body": "**安装：** 允许未知来源。从旧版本覆盖安装，数据保留（同一签名）。详见仓库 mobile-companion 分支提交说明。",
    "draft": False, "prerelease": False}).encode()
req = urllib.request.Request(f"https://api.github.com/repos/{'cdl357/AionsHome'}/releases",
    data=body, headers={**H, 'Content-Type': 'application/json'}, method='POST')
try:
    rel = json.loads(urllib.request.urlopen(req, timeout=30).read())
except urllib.error.HTTPError as e:
    if e.code == 422:  # tag 已有 release，取它的 id
        req2 = urllib.request.Request(f"https://api.github.com/repos/cdl357/AionsHome/releases/tags/v{ver}",
            headers=H)
        rel = json.loads(urllib.request.urlopen(req2, timeout=30).read())
    else:
        raise
print(rel['id'])
PYEOF
)
ASSET=$(basename "$APK" | sed "s/^/AionsHome-$VER/;s/AionsHome-$VERapp-release/AionsHome-$VER/")
ASSET="AionsHome-$VER.apk"
python - "$VER" "$TOKEN" "$REL_JSON" "$APK" "$ASSET" <<'PYEOF'
import json, sys, urllib.request
ver, tok, rel, apk, asset = sys.argv[1:6]
H = {'Authorization': 'Bearer ' + tok, 'Accept': 'application/vnd.github+json', 'User-Agent': 'aionshome-publisher'}
url = f"https://uploads.github.com/repos/cdl357/AionsHome/releases/{rel}/assets?name={asset}"
data = open(apk, 'rb').read()
try:
    a = json.loads(urllib.request.urlopen(urllib.request.Request(url, data=data,
        headers={**H, 'Content-Type': 'application/octet-stream'}, method='POST'), timeout=600).read())
    print("GitHub Release OK:", a['browser_download_url'])
except urllib.error.HTTPError as e:
    if e.code == 422:
        print("资产已存在（重复发布，跳过）")
    else:
        raise
PYEOF

# ── 3. releases/ 目录进仓库（供仓库文件区下载） ──
REPO_ROOT=$(git rev-parse --show-toplevel)
mkdir -p "$REPO_ROOT/releases"
cp "$APK" "$REPO_ROOT/releases/AionsHome-$VER.apk"
(cd "$REPO_ROOT" && git add "releases/AionsHome-$VER.apk" &&
  git commit -m "发版：AionsHome-$VER.apk" --quiet &&
  git push cdl mobile-companion --quiet &&
  echo "GitHub 仓库文件 OK: releases/AionsHome-$VER.apk") || echo "仓库推送跳过/失败（资产已在 Release 里，不影响下载）"
echo "本地 SHA256: $LOCAL_SHA"
