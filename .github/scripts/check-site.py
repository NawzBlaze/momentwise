#!/usr/bin/env python3
"""Static checks for the public Momentwise page.

This does not render a browser. Pixel screenshots and overflow checks run in
.github/workflows/site-visual.yml when a browser is available.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
html = (ROOT / "index.html").read_text(encoding="utf-8")
css = (ROOT / "styles.css").read_text(encoding="utf-8")
script = (ROOT / "script.js").read_text(encoding="utf-8")
failures: list[str] = []

VERIFICATION = (
    '<meta name="google-site-verification" '
    'content="01omZfSAZVcvwrjPL0O4zI9x9tx9-AXHt6KhZnlq8tw" />'
)


def require(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


required = [
    "INTRODUCING MOMENTWISE",
    "Pause before you scroll.",
    "Personal prompts and gentle reminders help you break automatic scrolling habits and choose how you spend your time.",
    "Make room for what matters.",
    "Make your next tap intentional.",
    "Download for Android",
    "View source code",
    "A signed download is not available yet.",
    "A pause, not a lock",
    "not on the exact second",
    "does not force-stop the other app",
    "not on Google Play",
]
for text in required:
    require(text in html, f"missing copy: {text}")

require('lang="en"' in html, "missing lang")
require('name="viewport"' in html, "missing viewport")
require('rel="canonical" href="https://nawzblaze.github.io/momentwise/"' in html, "canonical changed")
require("og:title" in html and "og:description" in html and "og:image" in html, "missing Open Graph")
require("twitter:card" in html, "missing Twitter card")
require('name="robots" content="index, follow"' in html, "robots meta changed")
require("application/ld+json" in html, "missing structured data")
require('href="#main"' in html, "missing skip link")
require("<main" in html and "<h1" in html, "missing main or h1")
require(html.count("<h1") == 1, "expected one h1")
require('id="download-button"' in html and "disabled" in html and 'aria-disabled="true"' in html, "download must stay disabled in HTML")
require("prototype" not in html.lower(), "prototype must not be linked or named")
require("com.nawzblaze" not in html, "package id must stay off the homepage")
require("keystore" not in html.lower(), "keystore must stay off the homepage")
require("versionCode" not in html, "version notes must stay off the homepage")
require("Google Play" in html and "not on Google Play" in html, "Play status must stay honest")
require("testimonial" not in html.lower() and "★★" not in html, "no testimonials or ratings")
require("users" not in html.lower(), "no user-count claim")
require('name="keywords"' not in html, "no meta keywords")
require("actions/artifacts" not in html, "no Actions artifact link")
require("releases/download" not in html, "homepage must not hard-code a release URL")
require("app-debug" not in html and "debug.apk" not in html.lower(), "no debug APK link")
require("pause-card" not in html and "pause-pair" not in html, "fake pause cards must stay gone")
require("Not a photograph" not in html, "fake phone caption must stay gone")
require('src="./assets/logo.png"' in html, "hero must use assets/logo.png")
require("phone frame" not in html.lower() and "mockup" not in html.lower(), "no phone mockup")

head, _, body = html.partition("</head>")
require(head.count(VERIFICATION) == 1 and html.count(VERIFICATION) == 1, "verification tag must appear once, exactly, in head")
require("google-site-verification" not in body, "verification tag must not be repeated in the body")
require(VERIFICATION not in script and VERIFICATION not in css, "verification tag belongs only in index.html head")

for match in re.finditer(r"<img\b[^>]*>", html):
    tag = match.group(0)
    require("alt=" in tag, f"image missing alt: {tag}")

for match in re.finditer(r'href="([^"]+)"', html):
    href = match.group(1)
    if href.startswith("#") or href.startswith("./") or href.startswith("https://"):
        continue
    failures.append(f"unexpected link: {href}")

require("@media (max-width: 390px)" in css, "missing 390px layout rule")
require("@media (max-width: 800px)" in css, "missing tablet collapse")
require("prefers-reduced-motion" in css, "missing reduced motion")
require(":focus-visible" in css, "missing focus style")
require("nav-toggle" in html and "aria-expanded" in html, "missing mobile nav")
require("data-theme" in script and "prefers-color-scheme" in script, "theme script incomplete")
require("momentwise-theme" in script and "morrow-theme" in script, "theme persistence keys changed")
require("data-theme-value=\"system\"" in html, "auto theme must keep the system value")
require("overflow-x: hidden" not in css and "overflow-x:hidden" not in css, "do not hide horizontal overflow")

for match in re.finditer(r"(?<!@media \()min-width:\s*(\d+)px", css):
    width = int(match.group(1))
    require(width < 320, f"css min-width {width}px may overflow a 320px screen")

json_ld = re.search(r'<script type="application/ld\\+json">\\s*(.*?)\\s*</script>', html, re.S)
require(json_ld is not None, "JSON-LD block missing")
if json_ld:
    try:
        data = json.loads(json_ld.group(1))
    except json.JSONDecodeError as exc:
        failures.append(f"JSON-LD is not valid JSON: {exc}")
    else:
        blob = json.dumps(data)
        require("aggregateRating" not in blob and "review" not in blob, "JSON-LD must not invent ratings")
        require("downloadUrl" not in blob, "JSON-LD must not invent a download")
        graph = data.get("@graph", []) if isinstance(data, dict) else []
        website = next((node for node in graph if isinstance(node, dict) and node.get("@type") == "WebSite"), None)
        app = next((node for node in graph if isinstance(node, dict) and node.get("@type") == "SoftwareApplication"), None)
        require(website is not None, "JSON-LD must include WebSite")
        if website:
            require(website.get("name") == "Momentwise", "WebSite name must be Momentwise")
            require(website.get("url") == "https://nawzblaze.github.io/momentwise/", "WebSite URL must match canonical")
        require(app is not None and app.get("name") == "Momentwise", "SoftwareApplication node must be preserved")

download = json.loads((ROOT / "download.json").read_text(encoding="utf-8"))
expected_apk = "https://github.com/NawzBlaze/momentwise/releases/download/v1.0.0/momentwise-1.0.0-signed.apk"
expected_checksum = "https://github.com/NawzBlaze/momentwise/releases/download/v1.0.0/momentwise-1.0.0-signed.apk.sha256"
expected_report = "https://github.com/NawzBlaze/momentwise/releases/download/v1.0.0/signing-verification.txt"
expected_release = "https://github.com/NawzBlaze/momentwise/releases/tag/v1.0.0"
require(download.get("available") is True, "published signed download must be enabled")
require(download.get("apkUrl") == expected_apk, "download must point at the published signed APK")
require(download.get("checksumUrl") == expected_checksum, "download checksum URL must match the published asset")
require(download.get("verificationReportUrl") == expected_report, "verification report URL must match the published asset")
require(download.get("releaseUrl") == expected_release, "release URL must match the published release")
require(download.get("packageId") == "com.nawzblaze.momentwise", "unexpected package ID in download metadata")
require(download.get("versionName") == "1.0.0" and download.get("versionCode") == 12, "download version metadata must match the published release")
require(isinstance(download.get("sha256"), str) and re.fullmatch(r"[a-f0-9]{64}", download["sha256"]), "download must contain a valid SHA-256")
require(download.get("sha256") == "3a2b5829069b28787646fa6005389dd32c2e7142a1cdeb39b550db03f0c7396", "download checksum must match the published release asset digest")
url_fields = " ".join(str(download.get(key) or "") for key in ("apkUrl", "checksumUrl", "verificationReportUrl", "releaseUrl", "notes"))
require("debug" not in url_fields.lower() and "unsigned" not in url_fields.lower(), "download.json must not name a debug APK")
require("actions/artifacts" not in url_fields, "download.json must not point at an Actions artifact")
require("releases/download/" in script, "script must know the release prefix")
require("config.available !== true" in script, "script must fail closed unless available is true")
require("debug" in script and "unsigned" in script, "script must reject debug and unsigned names")
require("download.json" in script, "script must read download.json")

robots = (ROOT / "robots.txt").read_text(encoding="utf-8")
require("User-agent: *" in robots and "Allow: /" in robots, "robots.txt must allow the site")
require("Sitemap: https://nawzblaze.github.io/momentwise/sitemap.xml" in robots, "robots sitemap URL")
require("prototype" not in robots.lower(), "robots must not advertise the prototype")
require("Disallow: /" not in robots, "robots must not block the site")

sitemap = ET.parse(ROOT / "sitemap.xml")
urls = [node.text for node in sitemap.getroot().iter() if node.tag.endswith("loc")]
require(urls == ["https://nawzblaze.github.io/momentwise/"], f"sitemap URLs changed: {urls}")
require("prototype" not in (ROOT / "sitemap.xml").read_text(encoding="utf-8"), "prototype must stay out of the sitemap")

pages = (ROOT / ".github/workflows/pages.yml").read_text(encoding="utf-8")
require("--exclude assets/logo.png" not in pages, "pages.yml must publish the hero logo")
require("--exclude prototype" in pages, "pages.yml must keep the prototype out of the artifact")
require("--exclude android" in pages, "pages.yml must keep the Android project out of the site")

publish = (ROOT / ".github/workflows/publish-android-release.yml").read_text(encoding="utf-8")
require("workflow_dispatch:" in publish, "publish workflow must be manual")
require("pull_request" not in publish, "publish workflow must not run on pull requests")
require("publish-after-physical-approval" in publish, "publish workflow needs the approval phrase")
require("refs/heads/main" in publish, "publish workflow must require main")
require("contents: write" in publish, "release creation needs contents write")
require(publish.count("contents: write") == 1, "contents write must stay on the release job only")

release = (ROOT / ".github/workflows/android-release.yml").read_text(encoding="utf-8")
require("gh release create" not in release, "artifact workflow must not create a Release")
require("pull_request" not in release, "signing workflow must not run on pull requests")

for path in (ROOT / ".github/workflows").glob("*.yml"):
    if path.name in {"android-release.yml", "publish-android-release.yml"}:
        continue
    text = path.read_text(encoding="utf-8")
    require("MOMENTWISE_KEYSTORE" not in text and "MOMENTWISE_KEY_" not in text, f"{path.name} must not read signing secrets")

widths = [320, 360, 390, 430, 768, 1024, 1280, 1440]
require(all(isinstance(width, int) for width in widths), "width list")

if failures:
    print("site check failed:")
    for item in failures:
        if item:
            print(f"- {item}")
    sys.exit(1)
print("site check passed")
print("widths noted for the browser job: " + ", ".join(str(width) for width in widths))
