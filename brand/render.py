"""Render brand SVGs to PNGs with headless Chromium (Playwright)."""
import sys, os, base64
from playwright.sync_api import sync_playwright
SRC = '/home/claude/brand/src'

def html_for(layers, size, mask=None, extra=''):
    imgs = ''.join(f'<img src="data:image/svg+xml;base64,{base64.b64encode(open(os.path.join(SRC, l), "rb").read()).decode()}" style="position:absolute;inset:0;width:{size}px;height:{size}px">' for l in layers)
    clip = {'circle': 'border-radius:50%;', 'squircle': f'border-radius:{int(size*0.23)}px;', None: ''}[mask]
    return f'<html><body style="margin:0;background:transparent"><div id="c" style="position:relative;width:{size}px;height:{size}px;overflow:hidden;{clip}">{imgs}{extra}</div></body></html>'

def shot(page, html, out, w, h):
    page.set_viewport_size({'width': w, 'height': h}); page.set_content(html); page.wait_for_timeout(150)
    page.locator('#c').screenshot(path=out, omit_background=True)

if __name__ == '__main__':
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    with sync_playwright() as p:
        b = p.chromium.launch(); page = b.new_page()
        shot(page, html_for(['icon_bg.svg', 'icon_fg.svg'], 1024, 'squircle'), f'{out}/preview_icon_1024.png', 1024, 1024)
        shot(page, html_for(['icon_bg.svg', 'icon_fg.svg'], 512), f'{out}/play_store_icon_512.png', 512, 512)
        shot(page, html_for(['icon_bg.svg', 'icon_fg.svg'], 192, 'circle'), f'{out}/preview_round_192.png', 192, 192)
        shot(page, html_for(['icon_bg.svg', 'icon_fg.svg'], 96, 'squircle'), f'{out}/preview_small_96.png', 96, 96)
        # Android densities: legacy 48dp, adaptive layers 108dp
        dens = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
        for d, s in dens.items():
            r = f'{out}/res/mipmap-{d}'; os.makedirs(r, exist_ok=True)
            L = int(48 * s); A = int(108 * s)
            shot(page, html_for(['icon_bg.svg', 'icon_fg.svg'], L, 'squircle'), f'{r}/ic_launcher.png', L, L)
            shot(page, html_for(['icon_bg.svg', 'icon_fg.svg'], L, 'circle'), f'{r}/ic_launcher_round.png', L, L)
            shot(page, html_for(['icon_fg.svg'], A), f'{r}/ic_launcher_dr_fg.png', A, A)
            shot(page, html_for(['icon_bg.svg'], A), f'{r}/ic_launcher_dr_bg.png', A, A)
        b.close()
    print('rendered')
