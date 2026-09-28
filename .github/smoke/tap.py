"""Toca no elemento da tela cujo texto (ou descrição) contém o argumento. Usa o uiautomator.

Com "swipe" como segundo argumento, arrasta a partir do elemento para a esquerda (rola uma
fileira de botões que passa da largura da tela).
"""
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
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        if len(sys.argv) > 2 and sys.argv[2] == "swipe":
            subprocess.run(["adb", "shell", "input", "swipe", str(cx), str(cy), str(max(cx - 700, 10)), str(cy), "400"])
            print(f"arrastou a partir de '{target}'")
        else:
            subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)])
            print(f"tocou em '{target}'")
        sys.exit(0)
print(f"não achou '{target}'")
