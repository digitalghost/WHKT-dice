#!/usr/bin/env python3
"""Exercise home navigation on an emulator; restore its battle preferences afterward.

Usage: python3 tools/check_home_states.py emulator-5554
The app must already be installed. Never runs against a physical device.
"""
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SERIAL = sys.argv[1] if len(sys.argv) == 2 else ""
if not re.fullmatch(r"emulator-\d+", SERIAL):
    raise SystemExit("Pass an explicit emulator serial; physical devices are not supported")
ADB = str(Path(__file__).resolve().parents[1] / "android-sdk/platform-tools/adb")
APP = "com.example.helloworld"
PREF = "shared_prefs/battle_records.xml"


def adb(*args, data=None):
    return subprocess.run([ADB, "-s", SERIAL, *args], input=data,
                          capture_output=True, check=True).stdout


def stop():
    adb("shell", "am", "force-stop", APP)


def home():
    adb("shell", "am", "start", "-f", "0x14000000", "-n", APP + "/.BattleHubActivity")


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/whkt-home-check.xml")
    return list(ET.fromstring(adb("shell", "cat", "/sdcard/whkt-home-check.xml")).iter("node"))


def normalize(value):
    return " ".join(value.split())


def click(label, tree):
    node = next(n for n in tree if normalize(n.get("text", "")) == normalize(label))
    assert node.get("enabled") == "true", label
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    assert x2 > x1 and y2 > y1, label
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.25)


def write_pref(data):
    adb("shell", f"run-as {APP} sh -c 'cat > {PREF}'", data=data)


def fixture(state):
    root = ET.Element("map")
    records = []
    if state != "empty":
        records = [{"battleId": "home-navigation-check", "name": "首页入口回归检查",
                    "keyOp": "占领", "completed": state == "completed",
                    "sideA": {"id": "A", "teamId": "IMP-AOD", "playerName": "玩家 A"},
                    "sideB": {"id": "B", "teamId": "CHAOS-PM", "playerName": "玩家 B"}}]
    ET.SubElement(root, "string", name="battles_v1").text = json.dumps(records, ensure_ascii=False)
    if state == "active":
        ET.SubElement(root, "string", name="active_battle_id").text = records[0]["battleId"]
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


stop()
exists = subprocess.run([ADB, "-s", SERIAL, "shell", "run-as", APP, "test", "-f", PREF]).returncode == 0
original = adb("shell", "run-as", APP, "cat", PREF) if exists else None
# Also retain a recovery copy inside the emulator in case this process is interrupted.
adb("shell", "run-as", APP, "mkdir", "-p", "cache", "shared_prefs")
if exists:
    adb("shell", "run-as", APP, "cp", PREF, "cache/home-check-backup.xml")
try:
    for state in ("empty", "completed", "active"):
        stop()
        write_pref(fixture(state))
        home()
        tree = nodes()
        texts = {normalize(n.get("text", "")) for n in tree}
        assert {"＋ 新建对局", "小队与武器", "自由投掷"} <= texts, (state, texts)
        assert ("继续对局 →" in texts) == (state == "active"), state
        if state == "empty":
            Path(f"/tmp/whkt-home-empty-{SERIAL}.png").write_bytes(adb("exec-out", "screencap", "-p"))
        click("＋ 新建对局", tree)
        assert any(n.get("text") == "关键任务 · 必选" for n in nodes()), state
        home()
        if state == "empty":
            for label, activity in (("小队与武器", "RosterListActivity"), ("自由投掷", "MainActivity")):
                click(label, nodes())
                resumed = adb("shell", "dumpsys", "activity", "activities").decode()
                assert any(activity in line for line in resumed.splitlines() if "topResumedActivity=" in line)
                home()
        print(f"{SERIAL}: {state} home navigation passed", flush=True)
finally:
    stop()
    if original is not None:
        write_pref(original)
        assert adb("shell", "run-as", APP, "cat", PREF) == original
    else:
        adb("shell", "run-as", APP, "rm", "-f", PREF)
    home()
    print(f"{SERIAL}: original battle data restored", flush=True)
