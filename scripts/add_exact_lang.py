import io
import json
import os
import sys

LANG_DIR = r"D:\download\工作区\sgu-printer\SGU-Printer\src\main\resources\assets\litematica-printer\lang"

TEXTS = {
    "en_us.json": (
        "Exact placement",
        "Asks a server running this mod to place the exact schematic state. Needed for mushroom stems and vines, "
        "whose states vanilla placement cannot produce. Falls back to vanilla placement when the server does not "
        "support it.",
    ),
    "zh_cn.json": (
        "精确状态放置",
        "让装有本模组服务端的服务器按投影状态落块。蘑菇柄、藤蔓这类方块的状态原版放置无法表达，需要这个开关；"
        "服务端没装本模组或关闭该功能时自动退回原版放置。",
    ),
    "zh_tw.json": (
        "精確狀態放置",
        "讓裝有本模組伺服端的伺服器按投影狀態落塊。蘑菇柄、藤蔓這類方塊的狀態原版放置無法表達，需要這個開關；"
        "伺服端沒裝本模組或關閉該功能時自動退回原版放置。",
    ),
    "lzh.json": (
        "精準置狀",
        "使裝本模之僕者按投影之狀置塊。菇柄、藤之類，原版置之不能達其狀，須此。若僕者未裝或閉之，則復歸原版置法。",
    ),
    "es_es.json": (
        "Colocación exacta",
        "Pide a un servidor con este mod que coloque el estado exacto del esquemático. Necesario para tallos de "
        "champiñón y enredaderas; si el servidor no lo admite, se usa la colocación normal.",
    ),
    "ru_ru.json": (
        "Точная установка",
        "Просит сервер с этим модом установить точное состояние из схемы. Нужно для грибных ножек и лиан; "
        "если сервер это не поддерживает, используется обычная установка.",
    ),
}


def main() -> int:
    for name, (title, desc) in TEXTS.items():
        path = os.path.join(LANG_DIR, name)
        with io.open(path, "r", encoding="utf-8") as handle:
            text = handle.read()
        if '"litematica-printer.config.exactPlacement"' in text:
            print(f"{name}: already present")
            continue
        lines = text.split("\n")
        insert_at = None
        for index, line in enumerate(lines):
            if '"litematica-printer.config.placeUsePacket.desc"' in line:
                insert_at = index + 1
                break
        if insert_at is None:
            print(f"{name}: ANCHOR NOT FOUND", file=sys.stderr)
            return 1
        indent = "  "
        new_lines = [
            f'{indent}"litematica-printer.config.exactPlacement": {json.dumps(title, ensure_ascii=False)},',
            f'{indent}"litematica-printer.config.exactPlacement.desc": {json.dumps(desc, ensure_ascii=False)},',
        ]
        lines[insert_at:insert_at] = new_lines
        text = "\n".join(lines)
        with io.open(path, "w", encoding="utf-8", newline="") as handle:
            handle.write(text)
        entries = json.loads(text)
        assert "litematica-printer.config.exactPlacement" in entries
        assert "litematica-printer.config.exactPlacement.desc" in entries
        print(f"{name}: inserted ({len(entries)} keys)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
