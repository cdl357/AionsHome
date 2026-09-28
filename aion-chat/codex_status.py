"""GPT/Codex 订阅连接状态（只读）。

与聊天调用链刻意分离：
- 不使用 ai_providers._build_codex_chat_environment()（那会把 auth.json 复制到聊天 home）；
- 对 auth.json 只做存在性检查（元数据），绝不读取、复制或输出其内容；
- probe 仅做一次 `app-server --stdio` 的 initialize 握手，不发起任何对话 turn，
  因此握手成功不代表订阅可用，真实订阅验证需在登录后由聊天链路完成。

状态取值（status）：
- cli_missing              未找到 node 或 codex.js（Codex CLI 未安装）
- not_logged_in            CLI 已安装但登录态文件不存在（未登录）
- logged_in                登录态文件存在（仅存在性判断）
- app_server_start_failed  app-server 进程握手失败（仅在 probe=1 时判定）
- available                已登录且 app-server 握手成功（仅在 probe=1 时判定；
                             不代表已验证真实对话）
"""

from __future__ import annotations

import asyncio
import contextlib
import os
import shutil
import time
from pathlib import Path

from codex_app_server import _read_json_line, _write_json
from generation_control import terminate_process_tree


_PROBE_TIMEOUT_SECONDS = 12.0
_PROBE_CACHE_TTL_SECONDS = 30.0

_probe_cache: dict = {"checked_at": 0.0, "ok": False, "error": ""}
_probe_lock = asyncio.Lock()


def _codex_discovery() -> dict:
    """状态每次重新发现 CLI（CLI 可能在服务启动后才安装）；
    聊天调用链仍使用 ai_providers 导入时的缓存值，重启后生效。"""
    import ai_providers

    script = None
    try:
        script = ai_providers._find_codex_script()
    except Exception:
        script = ai_providers._CODEX_SCRIPT
    return {
        "node_found": shutil.which("node") is not None,
        "script_found": bool(script),
        "codex_home_env": bool(os.environ.get("CODEX_HOME")),
        "auth_present": (Path(ai_providers._CODEX_HOME) / "auth.json").is_file(),
    }


async def _probe_app_server_handshake() -> tuple[bool, str]:
    """启动一次 app-server 并等待 initialize 应答；不做任何对话。"""
    import ai_providers

    script = ai_providers._find_codex_script() or ai_providers._CODEX_SCRIPT
    node = shutil.which("node") or "node"
    command = [node, script, "app-server", "--stdio"]
    env = {**os.environ, "NO_COLOR": "1"}

    process = await asyncio.create_subprocess_exec(
        *command,
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
        env=env,
        limit=1024 * 1024,
    )
    stderr_task = asyncio.create_task(process.stderr.read(16 * 1024))
    deadline = asyncio.get_running_loop().time() + _PROBE_TIMEOUT_SECONDS
    ok = False
    error = ""
    try:
        request = {
            "method": "initialize",
            "id": 1,
            "params": {"clientInfo": {"name": "aionshome_status", "title": "AionsHome Status", "version": "1.0.0"}},
        }
        await asyncio.wait_for(
            _write_json(process.stdin, request),
            timeout=max(0.5, deadline - asyncio.get_running_loop().time()),
        )
        while True:
            remaining = deadline - asyncio.get_running_loop().time()
            if remaining <= 0:
                error = "initialize 握手超时"
                break
            try:
                message = await asyncio.wait_for(_read_json_line(process.stdout), timeout=remaining)
            except (EOFError, asyncio.TimeoutError) as exc:
                error = error or ("app-server 输出流已关闭" if isinstance(exc, EOFError) else "initialize 握手超时")
                break
            if message.get("id") != 1:
                continue
            if message.get("error"):
                err = message.get("error")
                error = err.get("message") if isinstance(err, dict) else str(err)
                break
            ok = isinstance(message.get("result"), dict)
            if not ok:
                error = "initialize 返回缺少 result"
            break
    except (FileNotFoundError, OSError) as exc:
        error = f"无法启动 app-server 进程: {exc}"
    except asyncio.TimeoutError:
        error = error or "initialize 握手超时"
    finally:
        if process.returncode is None:
            await terminate_process_tree(process)
        else:
            with contextlib.suppress(Exception):
                await process.wait()
        if not ok and not error and stderr_task.done():
            with contextlib.suppress(Exception):
                tail = (stderr_task.result() or b"").decode("utf-8", errors="replace").strip()
                error = tail[:300]
        stderr_task.cancel()
        with contextlib.suppress(asyncio.CancelledError, Exception):
            await stderr_task
    return ok, error


async def _probe_cached() -> tuple[bool, str]:
    async with _probe_lock:
        now = time.time()
        if now - _probe_cache["checked_at"] < _PROBE_CACHE_TTL_SECONDS:
            return _probe_cache["ok"], _probe_cache["error"]
        ok, error = await _probe_app_server_handshake()
        _probe_cache.update({"checked_at": now, "ok": ok, "error": error})
        return ok, error


async def get_codex_status(*, probe: bool = False) -> dict:
    discovery = _codex_discovery()
    detail = dict(discovery)
    detail.pop("codex_home_env", None)
    detail["codex_home_overridden"] = discovery["codex_home_env"]
    detail["subscription_verified"] = False  # 本阶段不做真实对话验证，永远如实标注

    installed = discovery["node_found"] and discovery["script_found"]
    status = ""
    status_text = ""

    if not installed:
        missing = []
        if not discovery["node_found"]:
            missing.append("Node.js")
        if not discovery["script_found"]:
            missing.append("Codex CLI(codex.js)")
        status = "cli_missing"
        status_text = f"Codex CLI 未安装（缺少 {' 与 '.join(missing)}）"
    else:
        if probe:
            try:
                handshake_ok, handshake_error = await _probe_cached()
            except Exception as exc:  # 探测自身异常一律按启动失败处理，不让状态接口 500
                handshake_ok, handshake_error = False, f"探测异常: {exc}"
            detail["app_server_handshake"] = "ok" if handshake_ok else "failed"
            if handshake_error:
                detail["app_server_error"] = handshake_error
            if not handshake_ok:
                status = "app_server_start_failed"
                status_text = "Codex app-server 启动失败"
        else:
            detail["app_server_handshake"] = "skipped"

        if not status:
            if discovery["auth_present"]:
                status = "logged_in" if not probe else "available"
                status_text = (
                    "Codex 登录态存在，app-server 可启动（订阅真实对话尚未验证）"
                    if probe
                    else "Codex 登录态存在"
                )
            else:
                status = "not_logged_in"
                status_text = "Codex CLI 已安装，但未登录（无登录态）"

    return {
        "route": "codex_subscription",
        "kind": "subscription",
        "status": status,
        "status_text": status_text,
        "detail": detail,
        "checked_at": time.time(),
    }
