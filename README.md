# Momentwise

> **Make room for what matters.**

Momentwise is an intentional phone-use companion being designed to help people interrupt mindless scrolling, reconsider why they opened distracting apps, and spend their time more deliberately.

---

## Current Status: native app coded, signed APK not published

The public site describes the Android app. It does not offer an APK download, because no signed release file has been published. A browser design reference remains at `/prototype/` until that file exists and the download URL has been checked. It is not the product.

The native project in [`android/`](android/) lists launchable apps, explains Usage Access, display-over-other-apps, and notifications before a rule is enabled, and can run a foreground monitor plus a pause. Settings can pause monitoring without deleting rules. Quit returns to the home screen. It does not force-stop other apps. That pause path has not been proven on an API 34 or API 35 device here. Constraints are in [`ANDROID_IMPLEMENTATION_PLAN.md`](ANDROID_IMPLEMENTATION_PLAN.md).

Proposed application ID: `com.nawzblaze.momentwise`. The app is not published under that ID, and it is not on Google Play.

- **Public Landing Page:** [https://nawzblaze.github.io/momentwise/](https://nawzblaze.github.io/momentwise/)
- **Interactive Mobile UI Prototype:** [https://nawzblaze.github.io/momentwise/prototype/](https://nawzblaze.github.io/momentwise/prototype/)
- **Repository:** [https://github.com/NawzBlaze/momentwise](https://github.com/NawzBlaze/momentwise)

---

## Brand Identity & Design System

- **Product:** Momentwise
- **Tagline:** Make room for what matters.
- **Secondary Line:** Make your next tap intentional.
- **Logo Symbol:** An M-shaped silhouette cradling a rising sun — layered forest-green flows with a warm golden sunrise, evoking morning clarity and conscious choice.
- **Color Palette:**
  - **Light Mode:** Warm cream (`#FAF7F2`), deep forest green (`#14291E`), and restrained sunrise gold (`#C28B38`).
  - **Dark Mode:** Soft charcoal (`#0F1412`), comfortable off-white (`#EDF2EE`), muted green and sunrise gold accents (`#E2AC54`).
- **Personality:** Minimal, premium, calm, thoughtful, and human.
- **Adaptive Theme:** Supports automatic system theme detection (`prefers-color-scheme`) alongside an accessible manual theme switcher (System, Light, Dark) persisted in `localStorage` (`momentwise-theme`) with zero-flash rendering and seamless migration from the previous `morrow-theme` key.

---

## Product Concept

Most screen time does not begin with a deliberate choice. It starts in an idle second: opening an algorithmic feed out of muscle memory, losing track of time, and looking up forty minutes later.

Traditional screen blockers often treat screen time as a failure of willpower, relying on abrasive lockouts that invite bypasses or resentment. Momentwise is being shaped around a calmer thesis: you do not need an authority figure in your pocket—you need a split second of space to remember what you actually care about.

### Guiding Principles

1. **Intentional, not judgmental**
   Technology should never shame you. Momentwise will not use punitive language, anxiety-inducing streak counters, or arbitrary punishments. It exists to foster self-awareness, not guilt.

2. **Helpful, not controlling**
   You always stay in command of your device. If you genuinely want to open an app, you can. Momentwise offers a quiet buffer to confirm your intent, never an adversarial barrier.

3. **Private by design**
   Your habits and app choices belong strictly to you. Momentwise will be architected around on-device processing with zero telemetry brokers, no advertising SDKs, and no remote surveillance.

4. **Simple enough to stay out of the way**
   A tool meant to reduce screen time should not become another app you spend hours managing. Configuration should be minimal, lightweight, and calm.

---

## Repository Structure

The current repository is a clean, build-free static website hosted directly via GitHub Pages:

```text
momentwise/
├── .github/
│   └── workflows/
│       ├── pages.yml           # Automated GitHub Actions deployment to Pages
│       └── android.yml         # Android unit tests, debug APK, and lint
├── assets/
│   └── logo.png                # Approved icon: forest-green M and golden sunrise
├── prototype/                  # Interactive Mobile UI Prototype (Concept Preview)
│   ├── assets/                 # Brand assets copy for prototype preview
│   ├── app.js                  # Prototype navigation router & state machine
│   ├── index.html              # Multi-screen Android mobile prototype shell
│   └── styles.css              # Adaptive Android design system stylesheet
├── android/                    # Native Android foundation (Compose). See android/README.md
├── ANDROID_IMPLEMENTATION_PLAN.md
├── .gitignore                  # Standard ignore rules
├── index.html                  # Accessible, semantic static landing page with theme selector
├── README.md                   # Project documentation & brand system
├── robots.txt                  # Search engine crawl directives
├── script.js                   # Vanilla JS for theme management and accessibility
├── sitemap.xml                 # XML sitemap
└── styles.css                  # Adaptive design tokens & stylesheet (WCAG AAA contrast)
```

---

## Interactive Android UI Prototype

A browser-runnable, high-fidelity clickable prototype of the proposed Momentwise mobile application is available at [`prototype/`](prototype/):

- **Live Prototype URL:** [https://nawzblaze.github.io/momentwise/prototype/](https://nawzblaze.github.io/momentwise/prototype/)
- **Screens Implemented:**
  1. **Welcome / Onboarding:** Brand introduction, non-punitive value proposition, 3-step mindful concept walkthrough.
  2. **Home Dashboard:** Sample intentionality metrics (pauses, reclaimed time, intentional sessions), guarded apps summary, and interactive test drives.
  3. **Guarded Apps:** Interactive selection of apps to guard with persistent toggle switches and educational permission explainer.
  4. **Intervention ("What brought you here?"):** The central Momentwise experience featuring a breathing circle halo, "I have a purpose" intention setting, and "Just checking" reflective exit.
  5. **In-App Reminder:** Simulated mid-session check-in overlay with options to exit, extend (+5 min), or pause reminders for today.
  6. **Settings:** Preferences for mindful pause duration (3s/5s/8s), breathing guide, haptic pulse, reminder interval, and theme engine.
- **Adaptive Appearance:** Supports System (auto-detects OS color scheme), Light (warm cream and forest green), and Dark (soft charcoal and comfortable off-white) modes.
- **Responsive Display:** On desktop displays, presents inside a simulated Android device mockup with quick-navigation controls; on mobile devices (≤560px), seamlessly fills the viewport natively.

> **Important Disclosure:** This prototype uses browser-local storage and simulated sample metrics. No Android background services, accessibility APIs, device permissions, or real app monitoring are active.

---

## Brand Assets

The approved mark is [`assets/logo.png`](assets/logo.png): the supplied cream icon, a flowing forest-green M with a golden sunrise. The site, prototype, and Android app use that file. It is not a redraw.

---

## Local Development & Preview

Because Momentwise's landing page is completely static and build-free, it requires zero external dependencies, package managers, or compilers.

You can preview it locally using any static file server:

```bash
# Using Python 3 built-in server:
python -m http.server 8000

# Or using Node npx:
npx serve .
```

Open your browser at `http://localhost:8000` to inspect the page.

> **Note:** The GitHub Pages deployment serves the site from the `/momentwise/` subpath. Asset paths in `index.html` use `./` relative references which work correctly in both local preview and on GitHub Pages.

---

## Deployment

Deployments are automated through GitHub Actions ([`.github/workflows/pages.yml`](.github/workflows/pages.yml)). Any push to the `main` branch automatically triggers GitHub Pages deployment using official GitHub Pages actions (`actions/deploy-pages`).

**Live URL:** [https://nawzblaze.github.io/momentwise/](https://nawzblaze.github.io/momentwise/)

> **Note on the previous URL:** The repository was renamed from `morrow` to `momentwise`. The previous GitHub Pages URL (`https://nawzblaze.github.io/morrow/`) is not guaranteed to redirect automatically. Use the new URL above.
