#!/usr/bin/env bash
# AionsHome Ubuntu 启动脚本（后续可直接转 systemd，见 scripts/aionshome.service.template）
#
# 用法：
#   ./scripts/start-aionshome.sh                 # 默认端口 8080
#   AIONSPORT=18443 ./scripts/start-aionshome.sh # 自定义端口（避开已占用端口）
#
# 说明：
# - 端口优先读环境变量 AIONSPORT（main.py 支持），默认 8080 与原行为一致
# - 首次运行会自动创建 .venv 并安装 aion-chat/requirements.txt
#   （pywin32 已加 Windows 平台标记，Linux 安装会自动跳过）
# - GPT/Codex 订阅需要 Node.js + Connor-Codex 内安装 @openai/codex + 服务器上完成
#   Codex 登录；本脚本不做任何登录操作
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AION_DIR="$REPO_ROOT/aion-chat"
VENV_DIR="$REPO_ROOT/.venv"
PORT="${AIONSPORT:-8080}"

if ! command -v python3 >/dev/null 2>&1; then
    echo "[AionsHome] 未找到 python3，请先安装 Python 3.10+" >&2
    exit 1
fi

if [ ! -d "$VENV_DIR" ]; then
    echo "[AionsHome] 创建虚拟环境 $VENV_DIR"
    python3 -m venv "$VENV_DIR"
fi

# shellcheck disable=SC1091
source "$VENV_DIR/bin/activate"

if ! python -c "import fastapi" >/dev/null 2>&1; then
    echo "[AionsHome] 安装依赖（首次较慢）"
    pip install --upgrade pip
    # pyncm 已从 PyPI 下架，requirements 中单独处理：先装其余依赖
    grep -v "^pyncm$" "$AION_DIR/requirements.txt" | grep -v "^#" | grep -v "^$" > /tmp/aionshome-reqs.txt
    pip install -r /tmp/aionshome-reqs.txt
fi

# pyncm（网易云音乐 API）从仓库 vendor 安装，纯 Python wheel 跨平台可用
if ! python -c "import pyncm" >/dev/null 2>&1; then
    pip install "$REPO_ROOT/vendor/pyncm-1.8.1-py3-none-any.whl"
fi

echo "[AionsHome] 启动于 0.0.0.0:$PORT"
cd "$AION_DIR"
exec python -u main.py
# 提示：main.py 读取 AIONSPORT 环境变量决定端口；如需 uvicorn 多 worker，
# 可改用：exec python -m uvicorn main:app --host 0.0.0.0 --port "$PORT"
