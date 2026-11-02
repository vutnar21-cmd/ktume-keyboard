"""
Собирает шрифт клавиатуры Keske Rounded (app/src/main/res/font/keske_rounded.ttf) из Comfortaa.

В Comfortaa строчная «т» нарисована в болгарской форме и похожа на латинскую «m» (путается с «м»).
Скрипт заменяет только её на стандартную русскую форму, собранную из штрихов самого шрифта:
стойка — «ı» (dotlessi), перекладина — два перекрывающихся «-» (hyphen). Остальные буквы, включая
Λ-образную «л», не меняются. По OFL (Reserved Font Name «Comfortaa») изменённый шрифт переименован.

Исходник: Comfortaa[wght].ttf v3.105 из https://github.com/google/fonts/tree/main/ofl/comfortaa
(SHA-256 0fc3f45dc48b614db9c39181502544b37217ecbf8bee2fb35886992bc96c5bd3).

Запуск (нужен fontTools):  python patch_comfortaa_te.py Comfortaa[wght].ttf keske_rounded.ttf
"""
import sys
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables._g_l_y_f import Glyph, GlyphComponent

src, out = sys.argv[1], sys.argv[2]
f = TTFont(src)
glyf, hmtx = f["glyf"], f["hmtx"]
cmap = f.getBestCmap()
target = cmap[0x0442]           # строчная «т»
x_height = f["OS/2"].sxHeight    # 547
LEFT, BAR = 25, 470              # левый край и длина перекладины в единицах шрифта

hy = glyf["hyphen"]; hy.recalcBounds(glyf)
st = glyf["dotlessi"]; st.recalcBounds(glyf)
bar_top_shift = x_height - hy.yMax
stem_w = st.xMax - st.xMin
center = LEFT + BAR / 2

def comp(name, dx, dy, flags=0, y_scale=None):
    c = GlyphComponent(); c.glyphName = name; c.x = int(round(dx)); c.y = int(round(dy)); c.flags = flags
    if y_scale is not None:
        c.transform = [[1.0, 0.0], [0.0, y_scale]]
    return c

g = Glyph(); g.numberOfContours = -1
g.components = [
    comp("dotlessi", center - stem_w / 2 - st.xMin, 0, flags=0x0400, y_scale=0.95),  # стойка чуть ниже перекладины
    comp("hyphen", LEFT - hy.xMin, bar_top_shift),                            # перекладина: левая половина
    comp("hyphen", LEFT + BAR - hy.xMax, bar_top_shift),                      # перекладина: правая половина
]
glyf[target] = g
g.recalcBounds(glyf)
hmtx[target] = (int(LEFT * 2 + BAR), g.xMin)
if "gvar" in f and target in f["gvar"].variations:
    del f["gvar"].variations[target]   # у старой формы были вариации точек; у составной их нет
# OFL: Reserved Font Name «Comfortaa» — изменённая версия получает своё имя, исходный копирайт сохраняется
NEW_FAMILY = "Keske Rounded"
for rec in f["name"].names:
    if rec.nameID in (0, 13, 14):
        continue
    text = rec.toUnicode()
    if "Comfortaa" in text:
        rec.string = text.replace("Comfortaa", NEW_FAMILY.replace(" ", "") if rec.nameID in (6, 25) else NEW_FAMILY)
copyright = f["name"].getName(0, 3, 1, 0x409)
note = " Modified for Keske Tume (Cyrillic te redrawn in the standard Russian form), 2026."
for rec in f["name"].names:
    if rec.nameID == 0 and note not in rec.toUnicode():
        rec.string = rec.toUnicode() + note
if "DSIG" in f:
    del f["DSIG"]
f.save(out)
print("patched", target, "bounds", (g.xMin, g.yMin, g.xMax, g.yMax), "advance", hmtx[target][0])
