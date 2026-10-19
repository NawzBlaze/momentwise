# Morrow

> **Make room for what matters.**

Morrow is an intentional phone-use companion being designed to help people interrupt mindless scrolling, reconsider why they opened distracting apps, and spend their time more deliberately.

---

## Current Status: Pre-development

This repository contains the **brand foundation and adaptive static public landing page**. 

The actual Morrow application has **not** been started or built yet. No mobile code, background accessibility services, app-monitoring logic, databases, or client-side application logic are present in this milestone.

- **Public Landing Page:** [https://nawzblaze.github.io/morrow/](https://nawzblaze.github.io/morrow/)
- **Repository:** [https://github.com/NawzBlaze/morrow](https://github.com/NawzBlaze/morrow)

---

## Brand Identity & Design System

- **Product:** Morrow
- **Tagline:** Make room for what matters.
- **Logo Symbol:** An M-shaped silhouette cradling a rising sun, evoking morning clarity and dawn (*morrow*).
- **Color Palette:**
  - **Light Mode:** Warm cream (`#FAF7F2`), deep forest green (`#14291E`), and restrained sunrise gold (`#C28B38`).
  - **Dark Mode:** Soft charcoal (`#0F1412`), comfortable off-white (`#EDF2EE`), muted green and sunrise gold accents (`#E2AC54`).
- **Personality:** Minimal, premium, calm, thoughtful, and human.
- **Adaptive Theme:** Supports automatic system theme detection (`prefers-color-scheme`) alongside an accessible manual theme switcher (System, Light, Dark) persisted in `localStorage` with zero-flash rendering.

---

## Product Concept

Most screen time does not begin with a deliberate choice. It starts in an idle second: opening an algorithmic feed out of muscle memory, losing track of time, and looking up forty minutes later.

Traditional screen blockers often treat screen time as a failure of willpower, relying on abrasive lockouts that invite bypasses or resentment. Morrow is being shaped around a calmer thesis: you do not need an authority figure in your pocket—you need a split second of space to remember what you actually care about.

### Guiding Principles

1. **Intentional, not judgmental**  
   Technology should never shame you. Morrow will not use punitive language, anxiety-inducing streak counters, or arbitrary punishments. It exists to foster self-awareness, not guilt.

2. **Helpful, not controlling**  
   You always stay in command of your device. If you genuinely want to open an app, you can. Morrow offers a quiet buffer to confirm your intent, never an adversarial barrier.

3. **Private by design**  
   Your habits and app choices belong strictly to you. Morrow will be architected around on-device processing with zero telemetry brokers, no advertising SDKs, and no remote surveillance.

4. **Simple enough to stay out of the way**  
   A tool meant to reduce screen time should not become another app you spend hours managing. Configuration should be minimal, lightweight, and calm.

---

## Repository Structure

The current repository is a clean, build-free static website hosted directly via GitHub Pages:

```text
morrow/
├── .github/
│   └── workflows/
│       └── pages.yml      # Automated GitHub Actions deployment to Pages
├── assets/
│   └── favicon.svg        # Scalable SVG brand mark (M + rising sun)
├── .gitignore             # Standard ignore rules
├── index.html             # Accessible, semantic static landing page with theme selector
├── README.md              # Project documentation & brand system
├── robots.txt             # Search engine crawl directives
├── script.js              # Vanilla JS for theme management and accessibility
├── sitemap.xml            # XML sitemap
└── styles.css             # Adaptive design tokens & stylesheet (WCAG AAA contrast)
```

---

## Local Development & Preview

Because Morrow's landing page is completely static and build-free, it requires zero external dependencies, package managers, or compilers.

You can preview it locally using any static file server:

```bash
# Using Python 3 built-in server:
python -m http.server 8000

# Or using Node npx:
npx serve .
```

Open your browser at `http://localhost:8000` to inspect the page.

---

## Deployment

Deployments are automated through GitHub Actions ([`.github/workflows/pages.yml`](.github/workflows/pages.yml)). Any push to the `main` branch automatically triggers GitHub Pages deployment using official GitHub Pages actions (`actions/deploy-pages`).
