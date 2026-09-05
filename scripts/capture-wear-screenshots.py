#!/usr/bin/env python3
"""Capture WearWallet screenshots on an explicit Wear adb serial."""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

PKG = "com.cbstudio.wearwallet"
OUT = Path(__file__).resolve().parents[1] / "docs" / "screenshots"
MIN_BYTES = 18_000
DEVICE = ""


def adb(*args: str) -> None:
    subprocess.run(["adb", "-s", DEVICE, *args], check=False)


def ui_xml() -> str:
    adb("shell", "input", "keyevent", "224")
    time.sleep(0.4)
    adb("shell", "cmd", "statusbar", "collapse")
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return subprocess.run(
        ["adb", "-s", DEVICE, "shell", "cat", "/sdcard/ui.xml"],
        capture_output=True,
        text=True,
    ).stdout


def center_for(xml: str, *labels: str) -> tuple[int, int] | None:
    for label in labels:
        for attr in ("content-desc", "text"):
            for pattern in (
                rf'{attr}="{re.escape(label)}"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
                rf'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*{attr}="{re.escape(label)}"',
            ):
                m = re.search(pattern, xml)
                if m:
                    return (
                        (int(m.group(1)) + int(m.group(3))) // 2,
                        (int(m.group(2)) + int(m.group(4))) // 2,
                    )
    return None


def tap(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.2)


def cap(name: str) -> int:
    adb("shell", "input", "keyevent", "224")
    time.sleep(1.2)
    adb("shell", "screencap", "-p", "/sdcard/cap.png")
    path = OUT / name
    adb("pull", "/sdcard/cap.png", str(path))
    return path.stat().st_size if path.exists() else 0


def save_if_valid(name: str, *must_contain: str) -> int:
    xml = ui_xml()
    size = cap(name)
    joined = xml + Path(OUT / name).read_bytes().decode("latin1", errors="ignore")
    ok = size >= MIN_BYTES and all(token in joined for token in must_contain)
    print(name, size, "OK" if ok else "FAIL", must_contain)
    return size if ok else 0


def wait_for_main(timeout: float = 90.0) -> bool:
    end = time.time() + timeout
    while time.time() < end:
        xml = ui_xml()
        if center_for(xml, "Send", "發送") or "Demo Wallet" in xml:
            return True
        time.sleep(2)
    return False


def ensure_home() -> None:
    for _ in range(4):
        xml = ui_xml()
        if "Sign in" in xml or "Timer" in xml:
            adb("shell", "am", "start", "-n", f"{PKG}/.presentation.MainActivity")
            time.sleep(2)
            continue
        if center_for(xml, "Send", "發送") or (
            "Demo Wallet" in xml and "代幣" not in xml
        ):
            return
        if "返回" in xml:
            pos = center_for(xml, "返回")
            if pos:
                tap(*pos)
                continue
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)
    raise SystemExit("could not reach wallet home")


def scroll_to_actions() -> None:
    adb("shell", "input", "swipe", "227", "320", "227", "100", "300")
    time.sleep(1)


def scroll_to_top() -> None:
    adb("shell", "input", "swipe", "227", "100", "227", "320", "300")
    time.sleep(1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Capture Wear debug screenshots. Serial is required."
    )
    parser.add_argument(
        "--serial",
        default=os.environ.get("ANDROID_SERIAL", ""),
        help="Wear adb serial from `adb devices -l` (or ANDROID_SERIAL).",
    )
    return parser.parse_args()


def main() -> None:
    global DEVICE
    args = parse_args()
    DEVICE = args.serial.strip()
    if not DEVICE:
        print(
            "Pass --serial SERIAL from `adb devices -l`, or set ANDROID_SERIAL.",
            file=sys.stderr,
        )
        raise SystemExit(2)

    OUT.mkdir(parents=True, exist_ok=True)
    adb("shell", "settings", "put", "system", "screen_off_timeout", "600000")
    adb("shell", "svc", "power", "stayon", "true")

    adb("shell", "pm", "clear", PKG)
    time.sleep(1)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.presentation.MainActivity")
    time.sleep(6)
    save_if_valid("01-welcome-onboarding.png", "WearWallet", "創建新錢包")

    tap(227, 392)
    time.sleep(2)
    save_if_valid("02-create-wallet-entry.png", "Demo Wallet")

    if not wait_for_main():
        raise SystemExit("wallet creation failed")

    ensure_home()
    scroll_to_top()
    save_if_valid("04-wallet-home.png", "Demo Wallet", "ETH")

    scroll_to_actions()
    xml = ui_xml()
    pos = center_for(xml, "Receive", "接收") or (373, 366)
    tap(*pos)
    time.sleep(3)
    save_if_valid("05-receive-qr.png", "接收", "QR")

    adb("shell", "input", "keyevent", "4")
    time.sleep(1.5)
    ensure_home()
    scroll_to_actions()
    xml = ui_xml()
    pos = center_for(xml, "Send", "發送") or (80, 366)
    tap(*pos)
    time.sleep(2)
    save_if_valid("06-send-address.png", "發送到", "接收地址")

    adb("shell", "input", "keyevent", "4")
    time.sleep(1)
    ensure_home()
    scroll_to_actions()
    xml = ui_xml()
    pos = center_for(xml, "Send", "發送") or (80, 366)
    tap(*pos)
    time.sleep(2)
    tap(227, 240)
    time.sleep(0.5)
    adb("shell", "input", "text", "0.001")
    time.sleep(1)
    adb("shell", "input", "keyevent", "4")  # hide keyboard
    time.sleep(0.8)
    # stay on address screen if amount step needs address; capture amount UI after manual scroll
    xml = ui_xml()
    pos = center_for(xml, "下一步")
    if pos:
        tap(*pos)
        time.sleep(2)
    save_if_valid("07-send-amount.png", "發送", "ETH")

    adb("shell", "input", "keyevent", "4")
    time.sleep(0.8)
    adb("shell", "input", "keyevent", "4")
    time.sleep(1)
    ensure_home()
    adb("shell", "input", "swipe", "350", "227", "80", "227", "450")
    time.sleep(2)
    save_if_valid("08-settings.png", "設定", "錢包管理")

    xml = ui_xml()
    pos = center_for(xml, "錢包管理") or (227, 200)
    tap(*pos)
    time.sleep(2)
    save_if_valid("09-wallet-management-keystone.png", "Keystone", "錢包")

    adb("shell", "pm", "clear", PKG)
    time.sleep(1)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/.presentation.MainActivity")
    time.sleep(5)
    adb("shell", "input", "swipe", "227", "350", "227", "120", "250")
    time.sleep(1)
    tap(227, 357)
    time.sleep(2)
    save_if_valid("03-import-wallet-entry.png", "導入", "助記詞")


if __name__ == "__main__":
    main()
