"""Toca no elemento da tela cujo texto (ou descrição) contém o argumento. Usa o uiautomator."""
import re
import subprocess
import sys

target = sys.argv[1]
xml = subprocess.run(["adb", "exec-out", "uiautomator", "dump", "/dev/tty"], capture_output=True, text=True).stdout
for node in re.finditer(r"<node [^>]*>", xml):
    attrs = node.group(0)
    text = re.search(r'text="([^"]*)"', attrs).group(1)
    desc = re.search(r'content-desc="([^"]*)"', attrs).group(1)
    if target in text or target in desc:
        x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', attrs).groups())
        subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
        print(f"tocou em '{target}'")
        sys.exit(0)
print(f"não achou '{target}'")
