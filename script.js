(() => {
  const STORAGE_KEY = 'momentwise-theme';
  const LEGACY_STORAGE_KEY = 'morrow-theme';
  const THEME_COLOR_LIGHT = '#FAF7F2';
  const THEME_COLOR_DARK = '#0F1412';
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
  }
})();
