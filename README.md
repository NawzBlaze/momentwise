# Momentwise

> **Make room for what matters.**

Momentwise is an intentional phone-use companion being designed to help people interrupt mindless scrolling, reconsider why they opened distracting apps, and spend their time more deliberately.

---

## Current Status: Pre-development

This repository contains the **brand foundation and adaptive static public landing page**.

The actual Momentwise application has **not** been started or built yet. No mobile code, background accessibility services, app-monitoring logic, databases, or client-side application logic are present in this milestone.

- **Public Landing Page:** [https://nawzblaze.github.io/momentwise/](https://nawzblaze.github.io/momentwise/)
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
│       └── pages.yml           # Automated GitHub Actions deployment to Pages
├── assets/
│   ├── favicon.svg             # Favicon-optimized SVG brand mark (16x16 / 32x32 crisp)
│   ├── logo.svg                # Primary full-colour vector mark (transparent background)
│   ├── logo-light-bg.svg       # Light-surface presentation squircle
│   ├── logo-dark-bg.svg        # Dark-surface presentation squircle
│   └── logo-monochrome.svg     # Single-tone monochrome mark (currentColor)
├── .gitignore                  # Standard ignore rules
├── index.html                  # Accessible, semantic static landing page with theme selector
├── README.md                   # Project documentation & brand system
├── robots.txt                  # Search engine crawl directives
├── script.js                   # Vanilla JS for theme management and accessibility
├── sitemap.xml                 # XML sitemap
└── styles.css                  # Adaptive design tokens & stylesheet (WCAG AAA contrast)
```

---

## Brand Assets

The approved brand vector assets are located in [`assets/`](assets/):

1. **`assets/logo.svg`**: Primary full-colour vector mark on transparent background. Features the layered forest-green folds, sunrise disc, atmospheric glow, and illuminated ridge crests.
2. **`assets/logo-light-bg.svg`**: Full-colour mark presented on a warm cream (`#FAF7F2`) squircle for app-icon or light-mode preview cards.
3. **`assets/logo-dark-bg.svg`**: Full-colour mark presented on a soft charcoal (`#0F1412`) squircle for app-icon or dark-mode preview cards.
4. **`assets/logo-monochrome.svg`**: Single-tone silhouette using `currentColor` for monochrome printing, single-ink placements, or high-contrast interfaces.
5. **`assets/favicon.svg`**: Production favicon vector asset tuned for crisp legibility at small sizes (16×16 and 32×32).

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
