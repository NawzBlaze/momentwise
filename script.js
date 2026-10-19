/**
 * Morrow — Static Landing Page Script
 * Brand Alignment & Adaptive Theme Controller
 */

(() => {
  const STORAGE_KEY = 'morrow-theme';
  const THEME_COLOR_LIGHT = '#FAF7F2';
  const THEME_COLOR_DARK = '#0F1412';

  // System media query for dark mode detection
  const systemDarkQuery = window.matchMedia('(prefers-color-scheme: dark)');

  /**
   * Determine the effective active theme based on preference and OS state
   * @param {string} preference - 'system' | 'light' | 'dark'
   * @returns {'light' | 'dark'}
   */
  function resolveTheme(preference) {
    if (preference === 'light') return 'light';
    if (preference === 'dark') return 'dark';
    return systemDarkQuery.matches ? 'dark' : 'light';
  }

  /**
   * Apply theme attributes, meta theme-color, and sync radio button states
   * @param {string} preference - 'system' | 'light' | 'dark'
   */
  function applyTheme(preference) {
    const activeTheme = resolveTheme(preference);

    // Apply attributes to <html>
    document.documentElement.setAttribute('data-theme', activeTheme);
    document.documentElement.setAttribute('data-theme-preference', preference);

    // Update <meta name="theme-color">
    const metaThemeColor = document.getElementById('meta-theme-color');
    if (metaThemeColor) {
      metaThemeColor.setAttribute('content', activeTheme === 'dark' ? THEME_COLOR_DARK : THEME_COLOR_LIGHT);
    }

    // Sync theme selector radio button states
    const themeButtons = document.querySelectorAll('.theme-option');
    themeButtons.forEach((btn) => {
      const isSelected = btn.getAttribute('data-theme-value') === preference;
      btn.setAttribute('aria-checked', isSelected ? 'true' : 'false');
      btn.tabIndex = isSelected ? 0 : -1;
    });
  }

  /**
   * User selection handler: saves to localStorage and applies theme
   * @param {string} preference - 'system' | 'light' | 'dark'
   */
  function setTheme(preference) {
    try {
      localStorage.setItem(STORAGE_KEY, preference);
    } catch (e) {
      // In private browsing or restricted environments localStorage might throw
    }
    applyTheme(preference);
  }

  /**
   * Initialize theme system and event listeners
   */
  function initTheme() {
    let savedPreference = 'system';
    try {
      savedPreference = localStorage.getItem(STORAGE_KEY) || 'system';
    } catch (e) {}

    // Apply on load
    applyTheme(savedPreference);

    // Listen for OS theme changes (handles 'system' mode dynamically)
    systemDarkQuery.addEventListener('change', () => {
      let currentPreference = 'system';
      try {
        currentPreference = localStorage.getItem(STORAGE_KEY) || 'system';
      } catch (e) {}

      if (currentPreference === 'system') {
        applyTheme('system');
      }
    });

    // Theme selector interaction & keyboard accessibility
    const themeSelector = document.querySelector('.theme-selector');
    if (themeSelector) {
      const buttons = Array.from(themeSelector.querySelectorAll('.theme-option'));

      buttons.forEach((btn, index) => {
        // Click listener
        btn.addEventListener('click', () => {
          const val = btn.getAttribute('data-theme-value');
          if (val) {
            setTheme(val);
            btn.focus();
          }
        });

        // WAI-ARIA Radio Group keyboard navigation
        btn.addEventListener('keydown', (e) => {
          let nextIndex = null;
          if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
            nextIndex = (index + 1) % buttons.length;
          } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
            nextIndex = (index - 1 + buttons.length) % buttons.length;
          }

          if (nextIndex !== null) {
            e.preventDefault();
            const nextButton = buttons[nextIndex];
            const nextVal = nextButton.getAttribute('data-theme-value');
            if (nextVal) {
              setTheme(nextVal);
              nextButton.focus();
            }
          }
        });
      });
    }
  }

  // Document ready lifecycle
  document.addEventListener('DOMContentLoaded', () => {
    initTheme();

    // Update the copyright year automatically
    const yearElement = document.getElementById('current-year');
    if (yearElement) {
      yearElement.textContent = new Date().getFullYear().toString();
    }

    // Ensure external links have appropriate security attributes
    const externalLinks = document.querySelectorAll('a[target="_blank"]');
    externalLinks.forEach((link) => {
      const rel = link.getAttribute('rel') || '';
      if (!rel.includes('noopener')) {
        link.setAttribute('rel', `${rel} noopener noreferrer`.trim());
      }
    });
  });
})();
