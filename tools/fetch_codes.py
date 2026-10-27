#!/usr/bin/env python3
"""拉取并打包内置红外码库。

数据源（均可自由分发）：
- Flipper-IRDB (Lucaslhm/Flipper-IRDB)  许可 CC0-1.0，.ir 文本格式
- SmartIR (smartHomeHub/SmartIR)        许可 MIT，codes/climate 为 Broadlink base64 全状态矩阵

产物（app/src/main/assets/codes/）：
- index.json        码库索引（含来源、许可、仓库 commit）
- <entry_id>.gz     gzip 压缩的原始 .ir / climate JSON，App 端解析

用法：python3 tools/fetch_codes.py
"""
import gzip
import hashlib
import json
import os
import sys
import time
import urllib.request

FLIPPER_REPO = "Lucaslhm/Flipper-IRDB"
FLIPPER_REF = "main"
SMARTIR_REPO = "smartHomeHub/SmartIR"
SMARTIR_REF = "master"

ROOT = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(ROOT, "..", "app", "src", "main", "assets", "codes")
CACHE = os.path.join(ROOT, ".cache")

UA = {"User-Agent": "universalir-fetch/1.0"}

# category -> (deviceType, 品牌精确/大小写不敏感匹配，None=全部, 每品牌文件上限, 分类总上限)
FLIPPER_SELECTION = {
    "TVs": ("tv",
            ["Xiaomi", "TCL", "Hisense", "Skyworth", "Changhong", "Konka", "Coocaa",
             "Haier", "LeEco", "Sharp", "Sony", "Samsung", "LG", "Philips",
             "Panasonic", "Toshiba", "Sanyo", "Tosumo", "PPTV", "Leff"],
            12, 160),
    "LED_Lighting": ("light", None, 60, 260),
    "Fans": ("fan", None, 30, 120),
    "Projectors": ("projector",
                   ["XGIMI", "JMGO", "Xiaomi", "Anker", "Nebula", "Epson", "BenQ",
                    "ViewSonic", "Wanbo", "Dangbei", "Fengmi", "Optoma"],
                   10, 60),
    "ACs": ("ac",
            ["GREE", "Midea", "Haier", "Hisense", "AUX", "Chigo", "TCL", "Xiaomi",
             "Toshiba", "Daikin", "Panasonic", "Mitsubishi", "Fujitsu", "Gree"],
            10, 60),
    "Streaming_Devices": ("box",
                          ["Xiaomi", "Amazon", "Google", "Apple", "Roku", "Nvidia", "Skyworth"],
                          10, 40),
    "Audio_and_Video_Receivers": ("audio",
                                  ["Sony", "Yamaha", "Denon", "Marantz", "Onkyo",
                                   "Pioneer", "JBL", "Bose", "Xiaomi", "Edifier"],
                                  8, 40),
}

# SmartIR climate 码表按 manufacturer 精选（小写包含匹配），每品牌最多 2 个文件
SMARTIR_CLIMATE_BRANDS = [
    "gree", "midea", "haier", "hisense", "aux", "chigo", "tcl", "xiaomi",
    "toshiba", "daikin", "mitsubishi", "fujitsu", "panasonic", "samsung",
    "lg", "sharp", "hitachi", "bosch", "carrier", "ballu",
]
SMARTIR_CLIMATE_PER_BRAND = 2


def http_get(url: str) -> bytes:
    key = hashlib.sha1(url.encode()).hexdigest()
    cache_path = os.path.join(CACHE, key)
    if os.path.exists(cache_path):
        with open(cache_path, "rb") as f:
            return f.read()
    req = urllib.request.Request(url, headers=UA)
    last_err = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = resp.read()
            os.makedirs(CACHE, exist_ok=True)
            with open(cache_path, "wb") as f:
                f.write(data)
            return data
        except Exception as e:  # 网络抖动重试
            last_err = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"下载失败 {url}: {last_err}")


def gh_json(url: str):
    return json.loads(http_get(url).decode("utf-8"))


def resolve_sha(repo: str, ref: str) -> str:
    return gh_json(f"https://api.github.com/repos/{repo}/commits/{ref}")["sha"]


def repo_tree(repo: str, sha: str) -> list:
    data = gh_json(f"https://api.github.com/repos/{repo}/git/trees/{sha}?recursive=1")
    if data.get("truncated"):
        print("警告：tree 被截断，码库可能不全", file=sys.stderr)
    return [e for e in data["tree"] if e["type"] == "blob"]


def raw_url(repo: str, sha: str, path: str) -> str:
    from urllib.parse import quote
    return f"https://raw.githubusercontent.com/{repo}/{sha}/{quote(path)}"


def save_gz(entry_id: str, data: bytes) -> str:
    fname = f"{entry_id}.gz"
    os.makedirs(ASSETS, exist_ok=True)
    with gzip.open(os.path.join(ASSETS, fname), "wb", compresslevel=9) as f:
        f.write(data)
    return fname


def slug(s: str) -> str:
    return "".join(c if c.isalnum() or c in "-_" else "_" for c in s)


def fetch_flipper(entries: list):
    sha = resolve_sha(FLIPPER_REPO, FLIPPER_REF)
    print(f"Flipper-IRDB @ {sha[:10]}")
    blobs = repo_tree(FLIPPER_REPO, sha)
    for category, (dtype, brands, per_brand_cap, cat_cap) in FLIPPER_SELECTION.items():
        prefix = category + "/"
        files = [b for b in blobs if b["path"].startswith(prefix)]
        by_brand = {}
        for b in files:
            rest = b["path"][len(prefix):]
            if "/" not in rest:
                continue
            brand, fname = rest.split("/", 1)
            if not fname.endswith(".ir"):
                continue
            if brands is not None and brand.lower() not in [x.lower() for x in brands]:
                continue
            by_brand.setdefault(brand, []).append((fname, b["path"]))
        total = 0
        for brand in sorted(by_brand):
            if total >= cat_cap:
                break
            picked = sorted(by_brand[brand])
            for fname, path in picked[:per_brand_cap]:
                if total >= cat_cap:
                    break
                data = http_get(raw_url(FLIPPER_REPO, sha, path))
                model = fname[:-3]
                entry_id = f"flipper_{slug(category)}_{slug(brand)}_{slug(model)}"
                gz = save_gz(entry_id, data)
                entries.append({
                    "id": entry_id,
                    "kind": "flipper",
                    "deviceType": dtype,
                    "brand": brand,
                    "model": model,
                    "file": gz,
                    "signalCount": data.count(b"name:"),
                })
                total += 1
        print(f"  {category}: {total} 个文件")


def fetch_smartir(entries: list):
    sha = resolve_sha(SMARTIR_REPO, SMARTIR_REF)
    print(f"SmartIR @ {sha[:10]}")
    blobs = repo_tree(SMARTIR_REPO, sha)
    climate = sorted(b["path"] for b in blobs
                     if b["path"].startswith("codes/climate/") and b["path"].endswith(".json"))
    per_brand = {}
    for path in climate:
        try:
            meta = json.loads(http_get(raw_url(SMARTIR_REPO, sha, path)).decode("utf-8"))
        except Exception as e:
            print(f"  跳过 {path}: {e}", file=sys.stderr)
            continue
        mfr = str(meta.get("manufacturer", "")).strip()
        mfr_key = mfr.lower()
        if not any(tok in mfr_key for tok in SMARTIR_CLIMATE_BRANDS):
            continue
        if per_brand.get(mfr_key, 0) >= SMARTIR_CLIMATE_PER_BRAND:
            continue
        per_brand[mfr_key] = per_brand.get(mfr_key, 0) + 1
        model = os.path.splitext(os.path.basename(path))[0]
        entry_id = f"smartir_ac_{slug(mfr)}_{slug(model)}"
        gz = save_gz(entry_id, json.dumps(meta, ensure_ascii=False).encode("utf-8"))
        entries.append({
            "id": entry_id,
            "kind": "smartir_climate",
            "deviceType": "ac",
            "brand": mfr,
            "model": model,
            "file": gz,
            "minTemperature": int(float(meta.get("minTemperature", 16))),
            "maxTemperature": int(float(meta.get("maxTemperature", 30))),
        })
    print(f"  climate: {len(per_brand)} 个品牌 {sum(per_brand.values())} 个文件")


def main():
    entries: list = []
    fetch_flipper(entries)
    fetch_smartir(entries)
    index = {
        "version": 1,
        "sources": [
            {"name": "Flipper-IRDB", "url": f"https://github.com/{FLIPPER_REPO}", "license": "CC0-1.0"},
            {"name": "SmartIR", "url": f"https://github.com/{SMARTIR_REPO}", "license": "MIT"},
        ],
        "entries": entries,
    }
    os.makedirs(ASSETS, exist_ok=True)
    with open(os.path.join(ASSETS, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=1)
    total = sum(os.path.getsize(os.path.join(ASSETS, f)) for f in os.listdir(ASSETS))
    print(f"完成：{len(entries)} 个码表，assets 总大小 {total / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    main()
