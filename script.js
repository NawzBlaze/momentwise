(() => {
  const STORAGE_KEY = 'momentwise-theme';
  const LEGACY_STORAGE_KEY = 'morrow-theme';
  const THEME_COLOR_LIGHT = '#FAF7F2';
  const THEME_COLOR_DARK = '#0F1412';
  const RELEASE_PREFIX = '/NawzBlaze/momentwise/releases/download/';
  const RELEASE_PAGE_PREFIX = '/NawzBlaze/momentwise/releases/';
  const systemDarkQuery = window.matchMedia('(prefers-color-scheme: dark)');

  function getSavedThemePreference() {
    try {
      const current = localStorage.getItem(STORAGE_KEY);
      if (current) return current;
      const legacy = localStorage.getItem(LEGACY_STORAGE_KEY);
      if (legacy) {
        localStorage.setItem(STORAGE_KEY, legacy);
        return legacy;
      }
    } catch (e) {}
    return 'system';
  }

  function resolveTheme(preference) {
    if (preference === 'light') return 'light';
    if (preference === 'dark') return 'dark';
    return systemDarkQuery.matches ? 'dark' : 'light';
  }

  function applyTheme(preference) {
    const activeTheme = resolveTheme(preference);
    document.documentElement.setAttribute('data-theme', activeTheme);
    document.documentElement.setAttribute('data-theme-preference', preference);
    const metaThemeColor = document.getElementById('meta-theme-color');
    if (metaThemeColor) {
      metaThemeColor.setAttribute('content', activeTheme === 'dark' ? THEME_COLOR_DARK : THEME_COLOR_LIGHT);
    }
    document.querySelectorAll('.theme-option').forEach((btn) => {
      const isSelected = btn.getAttribute('data-theme-value') === preference;
      btn.setAttribute('aria-checked', isSelected ? 'true' : 'false');
      btn.tabIndex = isSelected ? 0 : -1;
    });
  }

  function setTheme(preference) {
    try {
      localStorage.setItem(STORAGE_KEY, preference);
    } catch (e) {}
    applyTheme(preference);
  }

  applyTheme(getSavedThemePreference());

  document.querySelectorAll('.theme-option').forEach((btn) => {
    btn.addEventListener('click', () => setTheme(btn.getAttribute('data-theme-value')));
  });

  const themeGroup = document.querySelector('.theme-selector');
  if (themeGroup) {
    themeGroup.addEventListener('keydown', (event) => {
      const keys = ['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp', 'Home', 'End'];
      if (!keys.includes(event.key)) return;
      const options = [...themeGroup.querySelectorAll('.theme-option')];
      if (!options.length) return;
      const current = options.findIndex((btn) => btn.getAttribute('aria-checked') === 'true');
      let next = current < 0 ? 0 : current;
      if (event.key === 'Home') next = 0;
      else if (event.key === 'End') next = options.length - 1;
      else if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = (Math.max(current, 0) + 1) % options.length;
      else next = (Math.max(current, 0) - 1 + options.length) % options.length;
      event.preventDefault();
      setTheme(options[next].getAttribute('data-theme-value'));
      options[next].focus();
    });
  }

  const onSystemChange = () => {
    if (getSavedThemePreference() === 'system') applyTheme('system');
  };
  if (systemDarkQuery.addEventListener) {
    systemDarkQuery.addEventListener('change', onSystemChange);
  } else if (systemDarkQuery.addListener) {
    systemDarkQuery.addListener(onSystemChange);
  }

  const toggle = document.querySelector('.nav-toggle');
  const nav = document.getElementById('site-nav');
  if (toggle && nav) {
    toggle.addEventListener('click', () => {
      const open = nav.classList.toggle('is-open');
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    nav.querySelectorAll('a').forEach((link) => {
      link.addEventListener('click', () => {
        nav.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
      });
    });
    document.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && nav.classList.contains('is-open')) {
        nav.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
        toggle.focus();
      }
    });
  }

  function githubReleaseUrl(value, suffix) {
    let url;
    try { url = new URL(value); } catch (e) { return false; }
    if (url.protocol !== 'https:' || url.hostname !== 'github.com') return false;
    if (url.search || url.hash || url.username || url.password) return false;
    if (!url.pathname.startsWith(RELEASE_PREFIX)) return false;
    const rest = url.pathname.slice(RELEASE_PREFIX.length);
    if (rest.includes('..') || rest.includes('\\')) return false;
    const parts = rest.split('/');
    if (parts.length !== 2 || !parts[0] || !parts[1]) return false;
    if (/debug|unsigned|artifact/i.test(parts[0] + '/' + parts[1])) return false;
    return suffix.test(parts[1]);
  }

  function isDirectReleaseApk(value) {
    return githubReleaseUrl(value, /\.apk$/i);
  }

  async function configureDownload() {
    const button = document.getElementById('download-button');
    if (!button || button.tagName !== 'BUTTON') return;
    let config;
    try {
      const response = await fetch('./download.json', { cache: 'no-store' });
      if (!response.ok) return;
      config = await response.json();
    } catch (e) {
      return;
    }
    if (!config || config.available !== true || !isDirectReleaseApk(config.apkUrl)) return;
    const link = document.createElement('a');
    link.className = 'button button-primary';
    link.id = 'download-button';
    link.href = config.apkUrl;
    link.rel = 'noopener noreferrer';
    link.textContent = 'Download for Android';
    button.replaceWith(link);
    const footerNote = document.getElementById('footer-download-note');
    if (footerNote) footerNote.textContent = 'Not on Google Play. The button downloads the published signed release.';
  }

  configureDownload();
})();
