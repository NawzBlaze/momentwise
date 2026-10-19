/**
 * Momentwise — Android UI Prototype Application Script
 * Provides screen routing, interactive state management, simulated device metrics,
 * app guarding toggles, intervention workflows, and dual-theme engine.
 */

(function () {
  'use strict';

  // --- Constants & Storage Keys ---
  const THEME_STORAGE_KEY = 'momentwise-prototype-theme';
  const DATA_STORAGE_KEY = 'momentwise-prototype-data';

  // --- Initial Default Data ---
  const DEFAULT_DATA = {
    metrics: {
      pauses: 7,
      minutesReclaimed: 42,
      intentionalRate: 71,
      streakDays: 6
    },
    guardedApps: [
      { id: 'instagram', name: 'Instagram', meta: 'Social • 45m daily avg', avatar: 'instagram', icon: '📸', guarded: true },
      { id: 'tiktok', name: 'TikTok', meta: 'Entertainment • 1h 10m daily avg', avatar: 'tiktok', icon: '🎵', guarded: true },
      { id: 'x', name: 'X / Twitter', meta: 'News & Social • 35m daily avg', avatar: 'x', icon: '𝕏', guarded: true },
      { id: 'youtube', name: 'YouTube', meta: 'Video & Media • 50m daily avg', avatar: 'youtube', icon: '▶️', guarded: false },
      { id: 'reddit', name: 'Reddit', meta: 'Discussion • 25m daily avg', avatar: 'reddit', icon: '💬', guarded: false },
      { id: 'threads', name: 'Threads', meta: 'Social • 15m daily avg', avatar: 'threads', icon: '🧵', guarded: false },
      { id: 'facebook', name: 'Facebook', meta: 'Social • 20m daily avg', avatar: 'facebook', icon: '👥', guarded: false }
    ],
    settings: {
      pauseDuration: '5',
      breathingGuide: true,
      haptics: true,
      reminderInterval: '10',
      allowExtensions: true
    }
  };

  // --- State ---
  let appState = loadState();
  let currentSlide = 1;
  let toastTimer = null;

  function loadState() {
    try {
      const raw = localStorage.getItem(DATA_STORAGE_KEY);
      if (raw) {
        return JSON.parse(raw);
      }
    } catch (e) {
      console.warn('Failed to load prototype state from storage:', e);
    }
    return JSON.parse(JSON.stringify(DEFAULT_DATA));
  }

  function saveState() {
    try {
      localStorage.setItem(DATA_STORAGE_KEY, JSON.stringify(appState));
    } catch (e) {
      console.warn('Failed to save prototype state:', e);
    }
  }

  // --- DOM Elements Cache ---
  const screens = {
    welcome: document.getElementById('screenWelcome'),
    home: document.getElementById('screenHome'),
    apps: document.getElementById('screenApps'),
    intervention: document.getElementById('screenIntervention'),
    reminder: document.getElementById('screenReminder'),
    settings: document.getElementById('screenSettings')
  };

  const statusClock = document.getElementById('statusClock');
  const appToast = document.getElementById('appToast');
  const toastMessage = document.getElementById('toastMessage');

  // --- Toast Notifications ---
  function showToast(msg) {
    if (!appToast || !toastMessage) return;
    toastMessage.textContent = msg;
    appToast.classList.add('visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      appToast.classList.remove('visible');
    }, 2800);
  }

  // --- Real-time Clock ---
  function updateClock() {
    if (!statusClock) return;
    const now = new Date();
    let hours = now.getHours();
    let minutes = now.getMinutes();
    const formatted = `${hours}:${minutes < 10 ? '0' : ''}${minutes}`;
    statusClock.textContent = formatted;
  }
  setInterval(updateClock, 30000);
  updateClock();

  // --- Screen Navigation Router ---
  function navigateTo(targetScreenId, updateHash) {
    if (!screens[targetScreenId]) return;

    if (updateHash !== false && window.history && window.history.replaceState) {
      window.history.replaceState(null, '', '#' + targetScreenId);
    }

    // Transition screens
    Object.keys(screens).forEach((key) => {
      const screenEl = screens[key];
      if (screenEl) {
        if (key === targetScreenId) {
          screenEl.classList.add('active');
          screenEl.scrollTop = 0;
        } else {
          screenEl.classList.remove('active');
        }
      }
    });

    // Update banner chips
    document.querySelectorAll('.screen-chip').forEach((chip) => {
      chip.classList.toggle('active', chip.getAttribute('data-screen-target') === targetScreenId);
    });

    // Update bottom nav items
    document.querySelectorAll('.nav-item').forEach((item) => {
      item.classList.toggle('active', item.getAttribute('data-nav-target') === targetScreenId);
    });

    // Specialized screen resets
    if (targetScreenId === 'intervention') {
      resetInterventionScreen();
    }
  }

  // Expose for external control / test harness
  window.navigateTo = navigateTo;

  // --- Onboarding Flow ---
  function setupOnboarding() {
    const slides = document.querySelectorAll('.onboarding-slide');
    const dots = document.querySelectorAll('.dot');
    const btnNext = document.getElementById('btnNextOnboarding');
    const btnSkip = document.getElementById('btnSkipOnboarding');
    const nextText = document.getElementById('nextOnboardingText');

    function goToSlide(n) {
      currentSlide = n;
      slides.forEach((s) => s.classList.remove('active'));
      dots.forEach((d) => d.classList.remove('active'));

      const activeSlide = document.querySelector(`.onboarding-slide[data-slide="${n}"]`);
      const activeDot = document.querySelector(`.dot[data-slide-target="${n}"]`);

      if (activeSlide) activeSlide.classList.add('active');
      if (activeDot) activeDot.classList.add('active');

      if (n === slides.length) {
        if (nextText) nextText.textContent = 'Get Started';
      } else {
        if (nextText) nextText.textContent = 'Continue';
      }
    }

    if (btnNext) {
      btnNext.addEventListener('click', () => {
        if (currentSlide < slides.length) {
          goToSlide(currentSlide + 1);
        } else {
          navigateTo('home');
          showToast('Welcome to Momentwise!');
        }
      });
    }

    if (btnSkip) {
      btnSkip.addEventListener('click', () => {
        navigateTo('home');
      });
    }

    dots.forEach((dot) => {
      dot.addEventListener('click', (e) => {
        const target = parseInt(e.currentTarget.getAttribute('data-slide-target'), 10);
        if (target) goToSlide(target);
      });
    });
  }

  // --- Guarded Apps Renderer ---
  function renderGuardedApps() {
    const listContainer = document.getElementById('appsListContainer');
    const chipsContainer = document.getElementById('guardedChipsContainer');
    const countBadge = document.getElementById('appsGuardedCount');
    const summaryText = document.getElementById('guardedCountSummary');

    const guardedOnly = appState.guardedApps.filter((a) => a.guarded);
    const count = guardedOnly.length;

    if (countBadge) countBadge.textContent = count;
    if (summaryText) summaryText.textContent = `${count} app${count === 1 ? '' : 's'} will pause before opening`;

    // Render chips on Home Dashboard
    if (chipsContainer) {
      chipsContainer.innerHTML = '';
      if (guardedOnly.length === 0) {
        chipsContainer.innerHTML = '<span class="section-sub">No apps currently guarded. Tap Manage to add apps.</span>';
      } else {
        guardedOnly.forEach((app) => {
          const pill = document.createElement('div');
          pill.className = 'app-pill';
          pill.innerHTML = `<span class="app-pill-dot"></span><span>${app.name}</span>`;
          chipsContainer.appendChild(pill);
        });
      }
    }

    // Render full list in Apps screen
    if (listContainer) {
      listContainer.innerHTML = '';
      appState.guardedApps.forEach((app) => {
        const row = document.createElement('div');
        row.className = 'app-row';
        row.innerHTML = `
          <div class="app-row-info">
            <div class="app-avatar ${app.avatar}">
              <span>${app.icon}</span>
            </div>
            <div class="app-text-block">
              <span class="app-name-title">${app.name}</span>
              <span class="app-meta-sub">${app.meta}</span>
            </div>
          </div>
          <label class="switch-control" aria-label="Toggle pause for ${app.name}">
            <input type="checkbox" data-app-id="${app.id}" ${app.guarded ? 'checked' : ''}>
            <span class="switch-slider"></span>
          </label>
        `;
        listContainer.appendChild(row);
      });

      // Bind switches
      listContainer.querySelectorAll('input[type="checkbox"]').forEach((checkbox) => {
        checkbox.addEventListener('change', (e) => {
          const appId = e.target.getAttribute('data-app-id');
          const isChecked = e.target.checked;
          const targetApp = appState.guardedApps.find((a) => a.id === appId);
          if (targetApp) {
            targetApp.guarded = isChecked;
            saveState();
            renderGuardedApps();
            showToast(`${targetApp.name} is now ${isChecked ? 'guarded' : 'unguarded'}`);
          }
        });
      });
    }
  }

  // --- Intervention Experience ---
  const interveneChoices = document.getElementById('interveneChoices');
  const subviewPurpose = document.getElementById('subviewPurpose');
  const subviewChecking = document.getElementById('subviewChecking');
  const subviewSuccess = document.getElementById('subviewSuccess');
  const breathingRing = document.getElementById('breathingRing');
  const breathingLabel = document.getElementById('breathingLabel');

  function resetInterventionScreen() {
    if (interveneChoices) interveneChoices.hidden = false;
    if (subviewPurpose) subviewPurpose.hidden = true;
    if (subviewChecking) subviewChecking.hidden = true;
    if (subviewSuccess) subviewSuccess.hidden = true;

    if (breathingLabel) breathingLabel.textContent = 'Take a slow breath';
    if (breathingRing) {
      breathingRing.style.display = 'flex';
      breathingRing.style.animation = 'breathePulse 6s ease-in-out infinite';
    }
  }

  function setupIntervention() {
    const btnPurpose = document.getElementById('btnChoicePurpose');
    const btnChecking = document.getElementById('btnChoiceChecking');
    const btnExit = document.getElementById('btnExitIntervention');
    const btnBack1 = document.getElementById('btnBackToInterventionChoices');
    const btnBack2 = document.getElementById('btnBackToInterventionChoices2');

    const btnStartPurpose = document.getElementById('btnStartPurposeSession');
    const btnStepAway = document.getElementById('btnStepAway');
    const btnContinueReminder = document.getElementById('btnContinueWithReminder');
    const btnCelebrateReturn = document.getElementById('btnCelebrateReturn');

    // Choice 1: Purpose
    if (btnPurpose) {
      btnPurpose.addEventListener('click', () => {
        interveneChoices.hidden = true;
        subviewPurpose.hidden = false;
        if (breathingLabel) breathingLabel.textContent = 'Clarify your purpose';
      });
    }

    // Choice 2: Just checking
    if (btnChecking) {
      btnChecking.addEventListener('click', () => {
        interveneChoices.hidden = true;
        subviewChecking.hidden = false;
        if (breathingLabel) breathingLabel.textContent = 'Pause & reflect';
      });
    }

    // Back to choices
    if (btnBack1) btnBack1.addEventListener('click', resetInterventionScreen);
    if (btnBack2) btnBack2.addEventListener('click', resetInterventionScreen);
    if (btnExit) btnExit.addEventListener('click', () => navigateTo('home'));

    // Purpose Session Started
    if (btnStartPurpose) {
      btnStartPurpose.addEventListener('click', () => {
        appState.metrics.pauses += 1;
        saveState();
        updateDashboardMetrics();
        showToast('Intentional session started. Nudge set for 5m.');
        navigateTo('reminder');
      });
    }

    // Stepped Away (Celebration!)
    if (btnStepAway) {
      btnStepAway.addEventListener('click', () => {
        appState.metrics.pauses += 1;
        appState.metrics.minutesReclaimed += 10;
        saveState();
        updateDashboardMetrics();

        subviewChecking.hidden = true;
        subviewSuccess.hidden = false;
        if (breathingLabel) breathingLabel.textContent = 'Presence restored';
      });
    }

    // Continue Anyway with reminder
    if (btnContinueReminder) {
      btnContinueReminder.addEventListener('click', () => {
        showToast('Opening Instagram. Reminder set for 5 min.');
        navigateTo('reminder');
      });
    }

    // Celebrate return
    if (btnCelebrateReturn) {
      btnCelebrateReturn.addEventListener('click', () => {
        navigateTo('home');
        showToast('+1 Mindful pause logged!');
      });
    }

    // Intent Chips
    document.querySelectorAll('.intent-chip').forEach((chip) => {
      chip.addEventListener('click', (e) => {
        document.querySelectorAll('.intent-chip').forEach((c) => c.classList.remove('active'));
        e.currentTarget.classList.add('active');
      });
    });

    // Time Limit Chips
    document.querySelectorAll('.time-chip').forEach((chip) => {
      chip.addEventListener('click', (e) => {
        document.querySelectorAll('.time-chip').forEach((c) => c.classList.remove('active'));
        e.currentTarget.classList.add('active');
      });
    });
  }

  // --- In-App Reminder Setup ---
  function setupReminder() {
    const btnExit = document.getElementById('btnReminderExit');
    const btnSnooze = document.getElementById('btnReminderSnooze');
    const btnStop = document.getElementById('btnReminderStop');

    if (btnExit) {
      btnExit.addEventListener('click', () => {
        appState.metrics.minutesReclaimed += 15;
        saveState();
        updateDashboardMetrics();
        showToast('Great choice! 15 minutes reclaimed.');
        navigateTo('home');
      });
    }

    if (btnSnooze) {
      btnSnooze.addEventListener('click', () => {
        showToast('Reminder snoozed for 5 minutes.');
        navigateTo('home');
      });
    }

    if (btnStop) {
      btnStop.addEventListener('click', () => {
        showToast('Reminders paused for Instagram for today.');
        navigateTo('home');
      });
    }
  }

  // --- Dashboard Metrics Updater ---
  function updateDashboardMetrics() {
    const elPauses = document.getElementById('metricPauses');
    const elReclaimed = document.getElementById('metricReclaimed');
    const elRate = document.getElementById('metricRate');

    if (elPauses) elPauses.textContent = appState.metrics.pauses;
    if (elReclaimed) elReclaimed.textContent = `${appState.metrics.minutesReclaimed}m`;
    if (elRate) elRate.textContent = `${appState.metrics.intentionalRate}%`;
  }

  // --- Settings Setup ---
  function setupSettings() {
    // Segmented controls for pause duration
    const pauseDurBtns = document.querySelectorAll('#pauseDurationSelector .segment-btn');
    pauseDurBtns.forEach((btn) => {
      btn.addEventListener('click', (e) => {
        pauseDurBtns.forEach((b) => b.classList.remove('active'));
        e.currentTarget.classList.add('active');
        appState.settings.pauseDuration = e.currentTarget.getAttribute('data-duration');
        saveState();
        showToast(`Pause duration set to ${appState.settings.pauseDuration} seconds`);
      });
    });

    // Segmented controls for reminder intervals
    const reminderBtns = document.querySelectorAll('#reminderIntervalSelector .segment-btn');
    reminderBtns.forEach((btn) => {
      btn.addEventListener('click', (e) => {
        reminderBtns.forEach((b) => b.classList.remove('active'));
        e.currentTarget.classList.add('active');
        appState.settings.reminderInterval = e.currentTarget.getAttribute('data-interval');
        saveState();
        showToast(`Reminder interval set to ${appState.settings.reminderInterval} min`);
      });
    });

    // Toggles
    const toggleBreathing = document.getElementById('toggleBreathingGuide');
    if (toggleBreathing) {
      toggleBreathing.checked = appState.settings.breathingGuide;
      toggleBreathing.addEventListener('change', (e) => {
        appState.settings.breathingGuide = e.target.checked;
        saveState();
        showToast(`Breathing guide ${e.target.checked ? 'enabled' : 'disabled'}`);
      });
    }

    const toggleHaptics = document.getElementById('toggleHaptics');
    if (toggleHaptics) {
      toggleHaptics.checked = appState.settings.haptics;
      toggleHaptics.addEventListener('change', (e) => {
        appState.settings.haptics = e.target.checked;
        saveState();
        showToast(`Haptic pulse ${e.target.checked ? 'enabled' : 'disabled'}`);
      });
    }

    const toggleExtensions = document.getElementById('toggleExtensions');
    if (toggleExtensions) {
      toggleExtensions.checked = appState.settings.allowExtensions;
      toggleExtensions.addEventListener('change', (e) => {
        appState.settings.allowExtensions = e.target.checked;
        saveState();
        showToast(`Session extension ${e.target.checked ? 'allowed' : 'disabled'}`);
      });
    }

    // Reset Demo Button
    const btnReset = document.getElementById('btnResetDemo');
    if (btnReset) {
      btnReset.addEventListener('click', () => {
        appState = JSON.parse(JSON.stringify(DEFAULT_DATA));
        saveState();
        renderGuardedApps();
        updateDashboardMetrics();
        showToast('Prototype data restored to default!');
      });
    }
  }

  // --- Theme Controller (Dual-Theme & System Preference) ---
  function setupTheme() {
    const themeButtons = document.querySelectorAll('.theme-btn');
    const settingThemeButtons = document.querySelectorAll('.segment-btn[data-theme-choice]');

    function getSavedThemePreference() {
      try {
        const params = new URLSearchParams(window.location.search);
        const queryTheme = params.get('theme');
        if (queryTheme && ['system', 'light', 'dark'].includes(queryTheme)) {
          return queryTheme;
        }
        return localStorage.getItem(THEME_STORAGE_KEY) || 'system';
      } catch (e) {
        return 'system';
      }
    }

    function applyTheme(themeChoice) {
      let resolvedTheme = themeChoice;
      if (themeChoice === 'system') {
        const isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
        resolvedTheme = isDark ? 'dark' : 'light';
      }

      document.documentElement.setAttribute('data-theme', resolvedTheme);

      // Save preference
      try {
        localStorage.setItem(THEME_STORAGE_KEY, themeChoice);
      } catch (e) {
        /* storage unavailable */
      }

      // Update UI buttons in banner
      themeButtons.forEach((btn) => {
        btn.classList.toggle('active', btn.getAttribute('data-theme-val') === themeChoice);
      });

      // Update UI buttons in settings
      settingThemeButtons.forEach((btn) => {
        btn.classList.toggle('active', btn.getAttribute('data-theme-choice') === themeChoice);
      });
    }

    // Expose for external control / test harness
    window.applyTheme = applyTheme;

    // Initialize with current preference
    const initialPreference = getSavedThemePreference();
    applyTheme(initialPreference);

    // Banner buttons
    themeButtons.forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const val = e.currentTarget.getAttribute('data-theme-val');
        applyTheme(val);
        showToast(`Theme: ${val.charAt(0).toUpperCase() + val.slice(1)}`);
      });
    });

    // Settings segment buttons
    settingThemeButtons.forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const val = e.currentTarget.getAttribute('data-theme-choice');
        applyTheme(val);
        showToast(`Theme: ${val.charAt(0).toUpperCase() + val.slice(1)}`);
      });
    });

    // Listen for OS scheme changes
    if (window.matchMedia) {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        if (getSavedThemePreference() === 'system') {
          applyTheme('system');
        }
      });
    }
  }

  // --- Global Event Bindings ---
  function setupGlobalBindings() {
    // Quick screen chips in desktop banner
    document.querySelectorAll('.screen-chip').forEach((chip) => {
      chip.addEventListener('click', (e) => {
        const target = e.currentTarget.getAttribute('data-screen-target');
        if (target) navigateTo(target);
      });
    });

    // Bottom Navigation Bar items
    document.querySelectorAll('.nav-item').forEach((item) => {
      item.addEventListener('click', (e) => {
        const target = e.currentTarget.getAttribute('data-nav-target');
        if (target) navigateTo(target);
      });
    });

    // Back buttons
    const btnBackApps = document.getElementById('btnBackFromApps');
    if (btnBackApps) btnBackApps.addEventListener('click', () => navigateTo('home'));

    const btnBackSettings = document.getElementById('btnBackFromSettings');
    if (btnBackSettings) btnBackSettings.addEventListener('click', () => navigateTo('home'));

    const btnHomeSettings = document.getElementById('btnHomeSettings');
    if (btnHomeSettings) btnHomeSettings.addEventListener('click', () => navigateTo('settings'));

    // Manage apps button on home
    const btnManageApps = document.getElementById('btnManageApps');
    if (btnManageApps) btnManageApps.addEventListener('click', () => navigateTo('apps'));

    // Save apps button in apps screen
    const btnSaveApps = document.getElementById('btnSaveApps');
    if (btnSaveApps) btnSaveApps.addEventListener('click', () => {
      navigateTo('home');
      showToast('Guarded apps saved');
    });

    // Test drives on home
    const btnTestIntervention = document.getElementById('btnTestIntervention');
    if (btnTestIntervention) btnTestIntervention.addEventListener('click', () => navigateTo('intervention'));

    const btnTestReminder = document.getElementById('btnTestReminder');
    if (btnTestReminder) btnTestReminder.addEventListener('click', () => navigateTo('reminder'));

    const btnTestFromApps = document.getElementById('btnTestInterventionFromApps');
    if (btnTestFromApps) btnTestFromApps.addEventListener('click', () => navigateTo('intervention'));

    // Hash change routing
    window.addEventListener('hashchange', () => {
      const hash = (window.location.hash || '').replace('#', '');
      if (screens[hash]) {
        navigateTo(hash, false);
      }
    });
  }

  // --- Bootstrap on DOM Ready ---
  document.addEventListener('DOMContentLoaded', () => {
    setupTheme();
    setupOnboarding();
    renderGuardedApps();
    updateDashboardMetrics();
    setupIntervention();
    setupReminder();
    setupSettings();
    setupGlobalBindings();

    // Check URL hash for initial screen, fallback to home
    const initialHash = (window.location.hash || '').replace('#', '');
    if (screens[initialHash]) {
      navigateTo(initialHash, false);
    } else {
      navigateTo('home', false);
    }
  });
})();
