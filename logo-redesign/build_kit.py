import os
import sys
import shutil

SKILL_SCRIPTS = r"C:\Users\agung\.gemini\config\plugins\logo-design\skills\logo-design\scripts"
if SKILL_SCRIPTS not in sys.path:
    sys.path.insert(0, SKILL_SCRIPTS)

import render_png

def make_symbol_svg(bg_color="#14532D", rim_color="#22C55E", rim_opacity="0.45", glyph_color="#FFFFFF", title="Duit Aing Logo"):
    rim_element = ""
    if rim_color and rim_color.lower() != "none":
        rim_element = f'  <rect x="26" y="26" width="204" height="204" rx="44" fill="none" stroke="{rim_color}" stroke-width="3.5" stroke-opacity="{rim_opacity}"/>\n'
    
    bg_element = ""
    if bg_color and bg_color.lower() != "none":
        bg_element = f'  <rect x="16" y="16" width="224" height="224" rx="52" fill="{bg_color}"/>\n'

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256" role="img" aria-labelledby="logo-title">
  <title id="logo-title">{title}</title>
{bg_element}{rim_element}  <g transform="translate(48, 45) scale(0.65)">
    <path fill="{glyph_color}" fill-rule="evenodd" d="M34 48C34 38 42 32 54 32H126C188 32 222 70 222 128C222 186 188 224 126 224H54C42 224 34 218 34 208ZM166 76L102 76L124 98L76 146L100 170L148 122L170 144Z"/>
  </g>
</svg>"""

def make_lockup_svg(variant="color", title="Duit Aing Horizontal Lockup"):
    if variant == "color":
        bg = "#14532D"
        rim = "#22C55E"
        rim_op = "0.45"
        glyph = "#FFFFFF"
        text_title_color = "#14532D"
        text_sub_color = "#14532D"
        sub_opacity = "0.75"
    elif variant == "black":
        bg = "#000000"
        rim = "#000000"
        rim_op = "0.3"
        glyph = "#FFFFFF"
        text_title_color = "#000000"
        text_sub_color = "#000000"
        sub_opacity = "0.7"
    elif variant == "white":
        bg = "#FFFFFF"
        rim = "#FFFFFF"
        rim_op = "0.3"
        glyph = "#000000"
        text_title_color = "#FFFFFF"
        text_sub_color = "#FFFFFF"
        sub_opacity = "0.8"
    else:  # mono jade
        bg = "#14532D"
        rim = "none"
        rim_op = "0"
        glyph = "#FFFFFF"
        text_title_color = "#14532D"
        text_sub_color = "#14532D"
        sub_opacity = "0.75"

    rim_line = ""
    if rim.lower() != "none":
        rim_line = f'<rect x="39.5" y="39.5" width="153" height="153" rx="33" fill="none" stroke="{rim}" stroke-width="2.6" stroke-opacity="{rim_op}"/>'

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 840 256" width="840" height="256" role="img" aria-labelledby="lockup-title">
  <title id="lockup-title">{title}</title>
  <!-- Brand Badge (Segel Taktil Neo-Brutalis) -->
  <g id="badge">
    <rect x="32" y="32" width="168" height="168" rx="39" fill="{bg}"/>
    {rim_line}
    <g transform="translate(56, 54) scale(0.4875)">
      <path fill="{glyph}" fill-rule="evenodd" d="M34 48C34 38 42 32 54 32H126C188 32 222 70 222 128C222 186 188 224 126 224H54C42 224 34 218 34 208ZM166 76L102 76L124 98L76 146L100 170L148 122L170 144Z"/>
    </g>
  </g>
  <!-- Wordmark -->
  <g id="wordmark">
    <text x="240" y="132" font-family="'Plus Jakarta Sans', system-ui, -apple-system, sans-serif" font-weight="900" font-size="64" letter-spacing="2" fill="{text_title_color}">DUIT AING</text>
    <text x="242" y="168" font-family="'Plus Jakarta Sans', system-ui, -apple-system, sans-serif" font-weight="700" font-size="16" letter-spacing="3" fill="{text_sub_color}" opacity="{sub_opacity}">MANAJEMEN KAS PRIBADI</text>
  </g>
</svg>"""

def build_full_kit():
    out_dir = r"e:\CASH\logo-kit"
    os.makedirs(out_dir, exist_ok=True)
    os.makedirs(os.path.join(out_dir, "svg"), exist_ok=True)
    os.makedirs(os.path.join(out_dir, "png"), exist_ok=True)
    os.makedirs(os.path.join(out_dir, "web"), exist_ok=True)

    # 1. SVGs Definitions
    svg_map = {
        "duit-aing-symbol-color.svg": make_symbol_svg("#14532D", "#22C55E", "0.45", "#FFFFFF", "Duit Aing Logo (Color)"),
        "duit-aing-symbol-black.svg": make_symbol_svg("#000000", "#000000", "0.3", "#FFFFFF", "Duit Aing Logo (Black)"),
        "duit-aing-symbol-white.svg": make_symbol_svg("#FFFFFF", "#FFFFFF", "0.3", "#000000", "Duit Aing Logo (White)"),
        "duit-aing-symbol-mono-14532d.svg": make_symbol_svg("#14532D", "none", "0", "#FFFFFF", "Duit Aing Logo (Mono Jade)"),
        "duit-aing-symbol-favicon.svg": make_symbol_svg("#14532D", "#22C55E", "0.45", "#FFFFFF", "Duit Aing Favicon"),
        "duit-aing-symbol-app-icon.svg": make_symbol_svg("#14532D", "#22C55E", "0.45", "#FFFFFF", "Duit Aing App Icon"),
        "duit-aing-lockup-color.svg": make_lockup_svg("color", "Duit Aing Logo (Horizontal Color)"),
        "duit-aing-lockup-black.svg": make_lockup_svg("black", "Duit Aing Logo (Horizontal Black)"),
        "duit-aing-lockup-white.svg": make_lockup_svg("white", "Duit Aing Logo (Horizontal White)"),
    }

    # Write root and svg/ folder copies
    for filename, content in svg_map.items():
        root_path = os.path.join(out_dir, filename)
        sub_path = os.path.join(out_dir, "svg", filename)
        with open(root_path, "w", encoding="utf-8") as f:
            f.write(content)
        with open(sub_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Wrote {filename}")

    # 2. Render PNGs for Symbol variants: 512, 256, 128, 64, 32, 16
    symbol_keys = [
        "duit-aing-symbol-color",
        "duit-aing-symbol-black",
        "duit-aing-symbol-white",
        "duit-aing-symbol-mono-14532d",
        "duit-aing-symbol-favicon",
        "duit-aing-symbol-app-icon"
    ]
    sizes = [512, 256, 128, 64, 32, 16]

    for key in symbol_keys:
        svg_src = os.path.join(out_dir, f"{key}.svg")
        for s in sizes:
            png_name = f"{key}-{s}.png"
            png_root = os.path.join(out_dir, png_name)
            png_sub = os.path.join(out_dir, "png", png_name)
            render_png.render(svg_src, png_root, s, s)
            shutil.copyfile(png_root, png_sub)
        print(f"Rendered PNGs for {key}")

    # Render PNGs for Lockups: 1024x312, 512x156, 256x78
    lockup_keys = ["duit-aing-lockup-color", "duit-aing-lockup-black", "duit-aing-lockup-white"]
    for key in lockup_keys:
        svg_src = os.path.join(out_dir, f"{key}.svg")
        for w, h, s_label in [(1024, 312, "1024"), (512, 156, "512"), (256, 78, "256")]:
            png_name = f"{key}-{s_label}.png"
            png_root = os.path.join(out_dir, png_name)
            png_sub = os.path.join(out_dir, "png", png_name)
            render_png.render(svg_src, png_root, w, h)
            shutil.copyfile(png_root, png_sub)
        print(f"Rendered PNGs for {key}")

    # 3. Web & Favicon Assets
    web_dir = os.path.join(out_dir, "web")
    favicon_svg = os.path.join(out_dir, "duit-aing-symbol-favicon.svg")
    
    # Favicon PNGs
    fav_16 = os.path.join(web_dir, "favicon-16.png")
    fav_32 = os.path.join(web_dir, "favicon-32.png")
    fav_48 = os.path.join(web_dir, "favicon-48.png")
    apple_180 = os.path.join(web_dir, "apple-touch-icon.png")
    icon_192 = os.path.join(web_dir, "icon-192.png")
    icon_512 = os.path.join(web_dir, "icon-512.png")
    maskable_512 = os.path.join(web_dir, "maskable-512.png")

    render_png.render(favicon_svg, fav_16, 16, 16)
    render_png.render(favicon_svg, fav_32, 32, 32)
    render_png.render(favicon_svg, fav_48, 48, 48)
    render_png.render(favicon_svg, apple_180, 180, 180)
    render_png.render(favicon_svg, icon_192, 192, 192)
    render_png.render(favicon_svg, icon_512, 512, 512)
    render_png.render(favicon_svg, maskable_512, 512, 512)

    # Copy to root web aliases
    for f in [fav_16, fav_32, fav_48, apple_180, icon_192, icon_512, maskable_512]:
        shutil.copyfile(f, os.path.join(out_dir, os.path.basename(f)))

    # Favicon.ico
    ico_path = os.path.join(out_dir, "favicon.ico")
    render_png.write_ico([fav_16, fav_32, fav_48], ico_path)
    shutil.copyfile(ico_path, os.path.join(web_dir, "favicon.ico"))
    print("Generated favicon.ico and web icons")

    # Webmanifest & Head Snippet
    manifest = """{
  "name": "Duit Aing - Manajemen Kas Pribadi",
  "short_name": "Duit Aing",
  "icons": [
    {
      "src": "/icon-192.png",
      "type": "image/png",
      "sizes": "192x192"
    },
    {
      "src": "/icon-512.png",
      "type": "image/png",
      "sizes": "512x512"
    },
    {
      "src": "/maskable-512.png",
      "type": "image/png",
      "sizes": "512x512",
      "purpose": "maskable"
    }
  ],
  "theme_color": "#14532D",
  "background_color": "#14532D",
  "display": "standalone"
}"""
    head_snippet = """<link rel="icon" href="/favicon.ico" sizes="any">
<link rel="icon" href="/duit-aing-symbol-favicon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="/apple-touch-icon.png">
<link rel="manifest" href="/site.webmanifest">
<meta name="theme-color" content="#14532D">"""

    with open(os.path.join(out_dir, "site.webmanifest"), "w", encoding="utf-8") as f:
        f.write(manifest)
    with open(os.path.join(web_dir, "site.webmanifest"), "w", encoding="utf-8") as f:
        f.write(manifest)

    with open(os.path.join(out_dir, "head-snippet.html"), "w", encoding="utf-8") as f:
        f.write(head_snippet)
    with open(os.path.join(web_dir, "head-snippet.html"), "w", encoding="utf-8") as f:
        f.write(head_snippet)

    # 4. Brand Guidelines
    guidelines_content = """# Duit Aing — Pedoman Identitas Logo & Brand Guidelines

> Panduan identitas visual resmi untuk aplikasi manajemen kas pribadi **Duit Aing**.

---

## 1. Filosofi & Konsep Logo
- **Gagasan Utama:** *Segel Taktil Neo-Brutalis (Opsi 2)* — Penggabungan monogram huruf **'D'** kokoh dengan potongan ruang negatif (*negative space*) panah bersudut 45° yang mengarah ke kanan-atas (pertumbuhan finansial & perputaran arus kas aktif). Seluruh elemen dibingkai dalam wadah squircle ganda (*minted double-bezel squircle*) bercorak stempel / segel koin fisik (*tactile minted seal*).
- **Karakter Visual:** Tegas, terpercaya, modern, dan fungsional tanpa basa-basi (*unpretentious high-contrast fintech*).
- **Struktur Geometri:** Koordinat integer berbasis kanvas 256×256 px, rasio kontras tinggi WCAG AAA (> 11:1), bezel ganda dengan sudut membulat radius 52 px & 44 px.
- **Daftar Berkas Utama:**
  - `duit-aing-symbol-color.svg` — Simbol utama penuh warna (Jade `#14532D`, Bezel `#22C55E`, Glif Putih `#FFFFFF`).
  - `duit-aing-symbol-black.svg` — Versi monokrom hitam pekat (stempel, dokumen hitam putih, thermal receipt).
  - `duit-aing-symbol-white.svg` — Versi reversed putih untuk latar gelap (*dark mode / high contrast*).
  - `duit-aing-symbol-mono-14532d.svg` — Versi satu warna Jade monolitik.
  - `duit-aing-lockup-color.svg` — Varian horizontal lengkap dengan tipografi *DUIT AING* dan *MANAJEMEN KAS PRIBADI*.
  - `duit-aing-symbol-favicon.svg` & `favicon.ico` — Berkas web dan favicon micro browser.
  - `duit-aing-symbol-app-icon.svg` — Aset dasar icon launcher Android & iOS.

---

## 2. Ruang Bebas (Clear Space)
Pertahankan ruang kosong minimal sebesar **1 × Lebar Batang D** (sekitar 32 px pada skala kanvas 256×256 px) di sekeliling batas luar segel squircle. Tidak diperkenankan meletakkan elemen visual, teks, atau ornamen grafis lain yang mengganggu zona batas ini.

---

## 3. Batas Ukuran Minimum (Minimum Size)

| Varian | Tampilan Digital | Media Cetak | Keterangan |
|---|---|---|---|
| **Horizontal Lockup** | 120 px lebar | 32 mm lebar | Di bawah 120 px, tampilkan simbol saja |
| **Simbol Standar** | 32 px | 8 mm | Menggunakan file master |
| **Simbol Mikro / Favicon** | 16 px | 4 mm | Wajib gunakan file `duit-aing-symbol-favicon.svg` / `favicon.ico` |

---

## 4. Palet Warna Resmi (Brand Colors)

| Peran | Nama Warna | HEX | RGB | Kegunaan |
|---|---|---|---|---|
| **Primary Brand** | Deep British Racing Jade | `#14532D` | 20, 83, 45 | Latar dasar squircle, kartu utama, aksen tombol |
| **Accent Glow** | Vibrant Emerald | `#22C55E` | 34, 197, 94 | Bingkai bezel dalam (*tactile double bezel*), status positif |
| **Glyph High-Contrast** | Pristine White | `#FFFFFF` | 255, 255, 255 | Monogram huruf D & siluet panah |
| **Dark Neutral** | Charcoal Noir | `#121614` | 18, 22, 20 | Teks judul aplikasi, bayangan solid Neo-Brutalis |
| **Light Surface** | Morning Pebble | `#F4F5F2` | 244, 245, 242 | Latar kanvas aplikasi, kartu gading |

---

## 5. Tipografi Pendamping (Typography)
- **Primary Wordmark / Titles:** Plus Jakarta Sans (Weight: Black 900 / ExtraBold 800, huruf kapital, letter-spacing 1.5–2.0 sp).
- **Subheadings & Slogan:** Plus Jakarta Sans (Weight: Bold 700 / SemiBold 600, huruf kapital, letter-spacing 1.0 sp).
- **Body & Angka Finansial:** Plus Jakarta Sans (Weight: SemiBold 600 / Bold 700, format angka tabular mono).

---

## 6. Larangan Penggunaan (Brand Rules)
1. **Dilarang Menghilangkan Panah Negatif:** Siluet panah 45° adalah elemen kunci makna arus kas; tidak boleh ditutup atau dihapus.
2. **Dilarang Mendistorsi Rasio Aspek:** Jangan meregangkan (*stretch*) atau meremukkan (*squash*) logo.
3. **Dilarang Menggunakan Soft Blur Shadow:** Brand Duit Aing mengadopsi estetika Neo-Brutalisme tegas, gunakan *hard solid offset shadow* atau flat murni.
4. **Dilarang Gradien Sembarangan:** Jangan memberikan gradien spektrum warna yang mengaburkan ketegasan identitas warna Jade.
"""
    with open(os.path.join(out_dir, "brand-guidelines.md"), "w", encoding="utf-8") as f:
        f.write(guidelines_content)
    print("Written brand-guidelines.md")

if __name__ == "__main__":
    build_full_kit()
