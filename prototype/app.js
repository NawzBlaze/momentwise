/**
 * Momentwise — App Rule Builder & UI Prototype
 * Complete client-side state machine, independent per-app rule builder,
 * responsive navigation router, and dual-theme controller.
 */

(function () {
  'use strict';

  // --- Constants & Storage Keys ---
  const THEME_STORAGE_KEY = 'momentwise-prototype-theme';
  const RULES_STORAGE_KEY = 'momentwise-prototype-rules-v2';

  // --- SVG Vector Icons for Clean, Professional App Presentation ---
  const APP_SVGS = {
    instagram: `<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="20" rx="5" ry="5"></rect><path d="M16 11.37A4 4 0 1 1 12.63 8 4 4 0 0 1 16 11.37z"></path><line x1="17.5" y1="6.5" x2="17.51" y2="6.5"></line></svg>`,
    tiktok: `<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M19.59 6.69a4.83 4.83 0 0 1-3.77-4.25V2h-3.45v13.67a2.89 2.89 0 0 1-5.2 1.74 2.89 2.89 0 0 1 2.31-4.64c.29 0 .58.04.86.12V9.42a6.34 6.34 0 0 0-.86-.06A6.34 6.34 0 0 0 3.1 15.7a6.34 6.34 0 0 0 10.82 4.48c1.37-1.37 1.83-3.08 1.83-5.2V8.75a8.28 8.28 0 0 0 4.84 1.44V6.75a4.85 4.85 0 0 1-1-.06z"/></svg>`,
    x: `<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/></svg>`,
    youtube: `<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z"/></svg>`,
    reddit: `<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="9" cy="11.5" r="1.5"/><circle cx="15" cy="11.5" r="1.5"/><path d="M8.5 15.5c1 1 2.33 1.5 3.5 1.5s2.5-.5 3.5-1.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>`,
    threads: `<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M12.002 2C6.477 2 2 6.477 2 12s4.477 10 10.002 10c3.21 0 6.09-1.517 7.933-3.877l-1.99-1.52A7.472 7.472 0 0 1 12.002 19.5c-4.142 0-7.5-3.358-7.5-7.5s3.358-7.5 7.5-7.5c3.784 0 6.91 2.802 7.433 6.444H12v2.5h9.948A9.99 9.99 0 0 0 12.002 2z"/></svg>`
  };

  // --- Default Rules Generator ---
  function createDefaultRule(id, name, category, enabled, defaultFreq) {
    return {
      id,
      name,
      category,
      enabled: !!enabled,
      configuredEver: !!enabled,
      openingNotice: 'What brought you here?',
      option1: {
        label: 'Quit',
        action: 'exit'
      },
      option2: {
        label: 'Anyways',
        action: 'continue'
      },
      reminderFrequency: defaultFreq || 'never',
      customMinutes: 8,
      reminderNotice: "You're still here?",
      advanced: {
        action1: {
          label: "Okay, I'm quitting",
          action: 'exit'
        },
        action2: {
          label: 'Wait',
          action: 'snooze',
          snoozeMinutes: 10
        },
        action3: {
          label: 'Dismiss reminder',
          action: 'dismiss'
        }
      }
    };
  }

  const DEFAULT_APP_RULES = [
    createDefaultRule('instagram', 'Instagram', 'Social Media', true, '10'),
    createDefaultRule('tiktok', 'TikTok', 'Short Video', true, '15'),
    createDefaultRule('x', 'X / Twitter', 'News & Feeds', false, 'never'),
    createDefaultRule('youtube', 'YouTube', 'Video & Media', false, 'never'),
    createDefaultRule('reddit', 'Reddit', 'Communities', false, 'never'),
    createDefaultRule('threads', 'Threads', 'Social Media', false, 'never')
  ];

  // --- State ---
  let appRules = loadAppRules();
  let currentConfigAppId = 'instagram';
  let activePreviewAppId = 'instagram';
  let toastTimer = null;

  function loadAppRules() {
    try {
      const raw = localStorage.getItem(RULES_STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed) && parsed.length > 0) {
          // Migration: upgrade legacy default "You Are Distracted" to approved "What brought you here?"
          // Only touches apps where the notice equals the known legacy default, preserving genuine user-entered copy.
          let migrated = false;
          parsed.forEach((r) => {
            if (r.openingNotice && r.openingNotice.trim().toLowerCase() === 'you are distracted') {
              r.openingNotice = 'What brought you here?';
              migrated = true;
            }
          });
          if (migrated) {
            try {
              localStorage.setItem(RULES_STORAGE_KEY, JSON.stringify(parsed));
            } catch (ignore) {}
          }
          return parsed;
        }
      }
    } catch (e) {
      console.warn('Failed to load rules from localStorage:', e);
    }
    return JSON.parse(JSON.stringify(DEFAULT_APP_RULES));
  }

  function saveAppRules() {
    try {
      localStorage.setItem(RULES_STORAGE_KEY, JSON.stringify(appRules));
    } catch (e) {
      console.warn('Failed to save rules to localStorage:', e);
    }
  }

  function getRule(id) {
    return appRules.find((r) => r.id === id) || appRules[0];
  }

  // --- Screens Map ---
  const screens = {
    welcome: document.getElementById('screenWelcome'),
    home: document.getElementById('screenHome'),
    apps: document.getElementById('screenApps'),
    configure: document.getElementById('screenConfigure'),
    intervention: document.getElementById('screenIntervention'),
    reminder: document.getElementById('screenReminder'),
    settings: document.getElementById('screenSettings')
  };

  const appToast = document.getElementById('appToast');
  const toastMessage = document.getElementById('toastMessage');
  const statusClock = document.getElementById('statusClock');

  // --- Toast Notification ---
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
    statusClock.textContent = `${hours}:${minutes < 10 ? '0' : ''}${minutes}`;
  }
  setInterval(updateClock, 30000);
  updateClock();

  // --- Screen Navigation Router ---
  function navigateTo(targetScreenId, updateHash) {
    if (!screens[targetScreenId]) return;

    if (updateHash !== false && window.history && window.history.replaceState) {
      window.history.replaceState(null, '', '#' + targetScreenId);
    }

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

    // Update bottom nav items (only Home, Apps, Settings)
    document.querySelectorAll('.nav-item').forEach((item) => {
      item.classList.toggle('active', item.getAttribute('data-nav-target') === targetScreenId);
    });

    // Dynamic screen hooks
    if (targetScreenId === 'home') {
      renderHomeOverview();
    } else if (targetScreenId === 'apps') {
      renderAppsList();
    } else if (targetScreenId === 'configure') {
      populateConfigureScreen(currentConfigAppId);
    } else if (targetScreenId === 'intervention') {
      populateInterventionPreview(activePreviewAppId);
    } else if (targetScreenId === 'reminder') {
      populateReminderPreview(activePreviewAppId);
    }
  }
  window.navigateTo = navigateTo;

  // --- Home Screen Overview Renderer ---
  function renderHomeOverview() {
    const activeRules = appRules.filter((r) => r.enabled);
    const headingEl = document.getElementById('homeActiveRulesHeading');
    const subEl = document.getElementById('homeActiveRulesSub');
    const chipsContainer = document.getElementById('homeActiveChips');
    const hintOpening = document.getElementById('testOpeningHint');
    const hintReminder = document.getElementById('testReminderHint');

    const count = activeRules.length;
    if (headingEl) {
      headingEl.textContent = count === 0 ? 'No Rules Configured' : `${count} Active Rule${count === 1 ? '' : 's'} (Demo)`;
    }
    if (subEl) {
      subEl.textContent = count === 0
        ? 'Select an app in the Apps tab to configure a mindful pause rule.'
        : 'Simulated opening pauses and check-in reminders configured in this browser demo.';
    }

    if (chipsContainer) {
      chipsContainer.innerHTML = '';
      if (count === 0) {
        chipsContainer.innerHTML = '<span class="empty-hint">Tap Manage Apps below to choose an app to guard.</span>';
      } else {
        activeRules.forEach((app) => {
          const chip = document.createElement('div');
          chip.className = 'app-rule-chip';
          const freqLabel = app.reminderFrequency === 'never' ? 'No reminders' : `${app.reminderFrequency === 'custom' ? app.customMinutes : app.reminderFrequency}m reminders`;
          chip.innerHTML = `
            <span class="chip-status-dot"></span>
            <strong>${app.name}</strong>
            <span class="chip-freq">${freqLabel}</span>
          `;
          chip.addEventListener('click', () => {
            currentConfigAppId = app.id;
            navigateTo('configure');
          });
          chipsContainer.appendChild(chip);
        });
      }
    }

    const firstActive = activeRules[0] || appRules[0];
    if (hintOpening && firstActive) {
      hintOpening.textContent = `Preview "${firstActive.openingNotice}" on ${firstActive.name}`;
    }
    if (hintReminder && firstActive) {
      hintReminder.textContent = firstActive.reminderFrequency === 'never'
        ? `Reminders are off for ${firstActive.name}`
        : `Preview mid-session check-in on ${firstActive.name}`;
    }
  }

  // --- Apps List Renderer ---
  function renderAppsList() {
    const container = document.getElementById('appsListContainer');
    const counterBadge = document.getElementById('appsActiveCounterBadge');
    if (!container) return;

    const activeCount = appRules.filter((r) => r.enabled).length;
    if (counterBadge) {
      counterBadge.textContent = `${activeCount} active`;
    }

    container.innerHTML = '';
    appRules.forEach((app) => {
      const row = document.createElement('div');
      row.className = `app-row-item ${app.enabled ? 'is-enabled' : ''}`;
      row.setAttribute('role', 'listitem');

      let statusText = 'Not guarded';
      if (app.enabled) {
        if (app.reminderFrequency === 'never') {
          statusText = 'Rule active • Opening pause only';
        } else {
          const mins = app.reminderFrequency === 'custom' ? app.customMinutes : app.reminderFrequency;
          statusText = `Rule active • ${mins} min reminders`;
        }
      }

      const svgIcon = APP_SVGS[app.id] || `<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><circle cx="12" cy="12" r="10"/></svg>`;

      row.innerHTML = `
        <div class="app-row-main" data-app-id="${app.id}">
          <div class="app-icon-badge ${app.id}">
            ${svgIcon}
          </div>
          <div class="app-row-text">
            <div class="app-row-title-row">
              <span class="app-row-name">${app.name}</span>
              <span class="app-category-tag">${app.category}</span>
            </div>
            <span class="app-row-status">${statusText}</span>
          </div>
        </div>
        <div class="app-row-actions">
          <button type="button" class="btn-config-app" data-app-id="${app.id}" aria-label="Configure rule for ${app.name}" title="Configure ${app.name}">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          </button>
          <label class="switch-control" aria-label="Toggle ${app.name} rule">
            <input type="checkbox" data-app-id="${app.id}" ${app.enabled ? 'checked' : ''}>
            <span class="switch-slider"></span>
          </label>
        </div>
      `;

      // Clicking main row area opens configuration
      const mainClickArea = row.querySelector('.app-row-main');
      mainClickArea.addEventListener('click', () => {
        currentConfigAppId = app.id;
        navigateTo('configure');
      });

      // Clicking configure icon button opens configuration
      const configBtn = row.querySelector('.btn-config-app');
      configBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        currentConfigAppId = app.id;
        navigateTo('configure');
      });

      // Toggle switch handling
      const checkbox = row.querySelector('input[type="checkbox"]');
      checkbox.addEventListener('change', (e) => {
        const isChecked = e.target.checked;
        app.enabled = isChecked;

        if (isChecked && !app.configuredEver) {
          // First-time enablement: immediately open configuration screen!
          app.configuredEver = true;
          saveAppRules();
          currentConfigAppId = app.id;
          showToast(`${app.name} enabled. Set your opening rule.`);
          navigateTo('configure');
        } else {
          saveAppRules();
          renderAppsList();
          showToast(`${app.name} is now ${isChecked ? 'guarded' : 'inactive'}`);
        }
      });

      container.appendChild(row);
    });
  }

  // --- Configure Screen Logic ---
  const elConfigTitle = document.getElementById('configureScreenTitle');
  const elConfigToggle = document.getElementById('configureAppToggle');
  const elOpeningNotice = document.getElementById('inputOpeningNotice');
  const elOption1Label = document.getElementById('inputOption1Label');
  const elOption2Label = document.getElementById('inputOption2Label');
  const elCustomInterval = document.getElementById('inputCustomInterval');
  const elCustomIntervalBox = document.getElementById('customIntervalBox');
  const elReminderNotice = document.getElementById('inputReminderNotice');
  const elReminderNoticeGroup = document.getElementById('reminderNoticeGroup');
  const elReminderDisabledNote = document.getElementById('reminderDisabledNote');
  const elAdvReminderNotice = document.getElementById('inputAdvancedReminderNotice');
  const elAdvAction1Label = document.getElementById('inputAdvAction1Label');
  const elAdvAction2Label = document.getElementById('inputAdvAction2Label');
  const elAdvAction3Label = document.getElementById('inputAdvAction3Label');
  const elSnoozeDuration = document.getElementById('inputSnoozeDuration');
  const freqChips = document.querySelectorAll('.freq-chip');

  let activeFreqChoice = 'never';

  function populateConfigureScreen(appId) {
    const app = getRule(appId);
    if (!app) return;

    if (elConfigTitle) elConfigTitle.textContent = `Configure ${app.name}`;
    if (elConfigToggle) elConfigToggle.checked = app.enabled;

    // Section A: Opening Notice
    if (elOpeningNotice) elOpeningNotice.value = app.openingNotice || 'What brought you here?';

    // Section B: Response Options
    if (elOption1Label) elOption1Label.value = app.option1.label || 'Quit';
    if (elOption2Label) elOption2Label.value = app.option2.label || 'Anyways';

    // Section C: Reminders
    activeFreqChoice = app.reminderFrequency || 'never';
    freqChips.forEach((chip) => {
      chip.classList.toggle('active', chip.getAttribute('data-freq') === activeFreqChoice);
    });

    if (elCustomInterval) elCustomInterval.value = app.customMinutes || 8;
    if (elReminderNotice) elReminderNotice.value = app.reminderNotice || "You're still here?";
    if (elAdvReminderNotice) elAdvReminderNotice.value = app.reminderNotice || "You're still here?";

    updateReminderFrequencyUI();

    // Section D: Advanced Reminder Options
    if (elAdvAction1Label) elAdvAction1Label.value = app.advanced.action1.label || "Okay, I'm quitting";
    if (elAdvAction2Label) elAdvAction2Label.value = app.advanced.action2.label || 'Wait';
    if (elAdvAction3Label) elAdvAction3Label.value = app.advanced.action3.label || 'Dismiss reminder';
    if (elSnoozeDuration) elSnoozeDuration.value = app.advanced.action2.snoozeMinutes || 10;

    // Reset validations
    clearValidationErrors();
  }

  function updateReminderFrequencyUI() {
    const isNever = activeFreqChoice === 'never';
    const isCustom = activeFreqChoice === 'custom';

    if (elCustomIntervalBox) elCustomIntervalBox.hidden = !isCustom;
    if (elReminderNoticeGroup) elReminderNoticeGroup.hidden = isNever;
    if (elReminderDisabledNote) elReminderDisabledNote.hidden = !isNever;
  }

  function clearValidationErrors() {
    ['valMsgOpeningNotice', 'valMsgOption1', 'valMsgOption2', 'valMsgCustomInterval', 'valMsgReminderNotice'].forEach((id) => {
      const el = document.getElementById(id);
      if (el) el.textContent = '';
    });
  }

  function setupConfigureScreen() {
    // Frequency chip selection
    freqChips.forEach((chip) => {
      chip.addEventListener('click', (e) => {
        freqChips.forEach((c) => c.classList.remove('active'));
        e.currentTarget.classList.add('active');
        activeFreqChoice = e.currentTarget.getAttribute('data-freq');
        updateReminderFrequencyUI();
      });
    });

    // Sync simple reminder notice and advanced reminder notice
    if (elReminderNotice && elAdvReminderNotice) {
      elReminderNotice.addEventListener('input', () => {
        elAdvReminderNotice.value = elReminderNotice.value;
      });
      elAdvReminderNotice.addEventListener('input', () => {
        elReminderNotice.value = elAdvReminderNotice.value;
      });
    }

    // Save Rule Button
    const btnSave = document.getElementById('btnSaveAppRule');
    if (btnSave) {
      btnSave.addEventListener('click', () => {
        saveCurrentConfig(true);
      });
    }

    // Test Rule Button
    const btnTest = document.getElementById('btnTestThisRule');
    if (btnTest) {
      btnTest.addEventListener('click', () => {
        if (saveCurrentConfig(false)) {
          activePreviewAppId = currentConfigAppId;
          navigateTo('intervention');
        }
      });
    }

    // Cancel Button
    const btnCancel = document.getElementById('btnCancelConfigure');
    if (btnCancel) {
      btnCancel.addEventListener('click', () => {
        navigateTo('apps');
      });
    }

    // Back Arrow
    const btnBack = document.getElementById('btnBackFromConfigure');
    if (btnBack) {
      btnBack.addEventListener('click', () => {
        navigateTo('apps');
      });
    }

    // App Enabled Toggle in header
    if (elConfigToggle) {
      elConfigToggle.addEventListener('change', (e) => {
        const app = getRule(currentConfigAppId);
        if (app) {
          app.enabled = e.target.checked;
          app.configuredEver = true;
          saveAppRules();
          showToast(`${app.name} rule ${app.enabled ? 'activated' : 'paused'}`);
        }
      });
    }

    // Reset this app to defaults
    const btnResetApp = document.getElementById('btnResetAppDefaults');
    if (btnResetApp) {
      btnResetApp.addEventListener('click', () => {
        const app = getRule(currentConfigAppId);
        if (app) {
          const fresh = createDefaultRule(app.id, app.name, app.category, app.enabled, 'never');
          Object.assign(app, fresh);
          populateConfigureScreen(app.id);
          showToast(`Defaults restored for ${app.name}`);
        }
      });
    }
  }

  function saveCurrentConfig(redirectBack) {
    clearValidationErrors();
    let hasError = false;

    const openingNoticeVal = (elOpeningNotice ? elOpeningNotice.value : '').trim();
    if (!openingNoticeVal) {
      const valEl = document.getElementById('valMsgOpeningNotice');
      if (valEl) valEl.textContent = 'Please enter an opening notice.';
      hasError = true;
    }

    const opt1Val = (elOption1Label ? elOption1Label.value : '').trim();
    if (!opt1Val) {
      const valEl = document.getElementById('valMsgOption1');
      if (valEl) valEl.textContent = 'Label cannot be empty.';
      hasError = true;
    }

    const opt2Val = (elOption2Label ? elOption2Label.value : '').trim();
    if (!opt2Val) {
      const valEl = document.getElementById('valMsgOption2');
      if (valEl) valEl.textContent = 'Label cannot be empty.';
      hasError = true;
    }

    if (opt1Val && opt2Val && opt1Val.toLowerCase() === opt2Val.toLowerCase()) {
      const valEl = document.getElementById('valMsgOption2');
      if (valEl) valEl.textContent = 'Option labels must be distinct.';
      hasError = true;
    }

    let customMinutesVal = 8;
    if (activeFreqChoice === 'custom') {
      const num = parseInt(elCustomInterval ? elCustomInterval.value : '0', 10);
      if (isNaN(num) || num < 1 || num > 300) {
        const valEl = document.getElementById('valMsgCustomInterval');
        if (valEl) valEl.textContent = 'Please enter a valid interval between 1 and 300 minutes.';
        hasError = true;
      } else {
        customMinutesVal = num;
      }
    }

    const reminderNoticeVal = (elReminderNotice ? elReminderNotice.value : '').trim();
    if (activeFreqChoice !== 'never' && !reminderNoticeVal) {
      const valEl = document.getElementById('valMsgReminderNotice');
      if (valEl) valEl.textContent = 'Please provide a reminder notice message.';
      hasError = true;
    }

    if (hasError) {
      showToast('Please fix the errors above.');
      return false;
    }

    // Persist to current app rule
    const app = getRule(currentConfigAppId);
    if (app) {
      app.configuredEver = true;
      app.enabled = elConfigToggle ? elConfigToggle.checked : true;
      app.openingNotice = openingNoticeVal;
      app.option1.label = opt1Val;
      app.option2.label = opt2Val;
      app.reminderFrequency = activeFreqChoice;
      app.customMinutes = customMinutesVal;
      app.reminderNotice = reminderNoticeVal || "You're still here?";

      // Advanced options
      app.advanced.action1.label = (elAdvAction1Label && elAdvAction1Label.value.trim()) || "Okay, I'm quitting";
      app.advanced.action2.label = (elAdvAction2Label && elAdvAction2Label.value.trim()) || 'Wait';
      app.advanced.action3.label = (elAdvAction3Label && elAdvAction3Label.value.trim()) || 'Dismiss reminder';
      const snoozeNum = parseInt(elSnoozeDuration ? elSnoozeDuration.value : '10', 10);
      app.advanced.action2.snoozeMinutes = isNaN(snoozeNum) || snoozeNum < 1 ? 10 : snoozeNum;

      saveAppRules();
      showToast(`Saved rule for ${app.name}`);

      if (redirectBack) {
        navigateTo('apps');
      }
      return true;
    }
    return false;
  }

  // --- Intervention Preview Screen Populator ---
  function populateInterventionPreview(appId) {
    const app = getRule(appId);
    if (!app) return;

    const elAppName = document.getElementById('previewAppName');
    const elNotice = document.getElementById('previewOpeningNotice');
    const elOpt1 = document.getElementById('previewOption1Label');
    const elOpt2 = document.getElementById('previewOption2Label');
    const elChoices = document.getElementById('previewChoicesBox');
    const elQuitPanel = document.getElementById('previewResultQuit');
    const elContinuePanel = document.getElementById('previewResultContinue');
    const elQuitApp = document.getElementById('quitAppName');
    const elContinueStatus = document.getElementById('continueReminderStatus');

    if (elAppName) elAppName.textContent = app.name;
    if (elNotice) elNotice.textContent = app.openingNotice;
    if (elOpt1) elOpt1.textContent = app.option1.label;
    if (elOpt2) elOpt2.textContent = app.option2.label;
    if (elQuitApp) elQuitApp.textContent = app.name;

    // Reset visibility
    if (elChoices) elChoices.hidden = false;
    if (elQuitPanel) elQuitPanel.hidden = true;
    if (elContinuePanel) elContinuePanel.hidden = true;

    if (elContinueStatus) {
      if (app.reminderFrequency === 'never') {
        elContinueStatus.textContent = `Continuing into ${app.name}. No mid-session reminders are scheduled.`;
      } else {
        const mins = app.reminderFrequency === 'custom' ? app.customMinutes : app.reminderFrequency;
        elContinueStatus.textContent = `Continuing into ${app.name}. Check-in reminder scheduled in ${mins} minutes.`;
      }
    }
  }

  function setupInterventionPreview() {
    const btnQuit = document.getElementById('btnPreviewQuit');
    const btnContinue = document.getElementById('btnPreviewContinue');
    const elChoices = document.getElementById('previewChoicesBox');
    const elQuitPanel = document.getElementById('previewResultQuit');
    const elContinuePanel = document.getElementById('previewResultContinue');

    if (btnQuit) {
      btnQuit.addEventListener('click', () => {
        if (elChoices) elChoices.hidden = true;
        if (elQuitPanel) elQuitPanel.hidden = false;
        showToast('Exit action triggered. Room made for what matters.');
      });
    }

    if (btnContinue) {
      btnContinue.addEventListener('click', () => {
        if (elChoices) elChoices.hidden = true;
        if (elContinuePanel) elContinuePanel.hidden = false;
        showToast('Continue action triggered.');
      });
    }

    const btnReturnQuit = document.getElementById('btnReturnFromQuit');
    if (btnReturnQuit) btnReturnQuit.addEventListener('click', () => navigateTo('apps'));

    const btnReturnCont = document.getElementById('btnReturnFromContinue');
    if (btnReturnCont) btnReturnCont.addEventListener('click', () => navigateTo('home'));

    const btnTriggerReminder = document.getElementById('btnTriggerReminderFromContinue');
    if (btnTriggerReminder) {
      btnTriggerReminder.addEventListener('click', () => {
        navigateTo('reminder');
      });
    }

    const btnExit = document.getElementById('btnExitIntervention');
    if (btnExit) btnExit.addEventListener('click', () => navigateTo('home'));

    const btnEdit = document.getElementById('btnEditFromPreview');
    if (btnEdit) {
      btnEdit.addEventListener('click', () => {
        currentConfigAppId = activePreviewAppId;
        navigateTo('configure');
      });
    }
  }

  // --- Reminder Preview Screen Populator ---
  function populateReminderPreview(appId) {
    const app = getRule(appId);
    if (!app) return;

    const elFeedTitle = document.getElementById('simFeedAppTitle');
    const elDisabledPanel = document.getElementById('panelReminderDisabled');
    const elActivePanel = document.getElementById('panelReminderActive');
    const elDisabledAppName = document.getElementById('disabledReminderAppName');
    const elNotice = document.getElementById('previewReminderNotice');
    const elSub = document.getElementById('previewReminderSub');
    const elAct1 = document.getElementById('labelAdvAction1');
    const elAct2 = document.getElementById('labelAdvAction2');
    const elAct3 = document.getElementById('labelAdvAction3');

    if (elFeedTitle) elFeedTitle.textContent = `${app.name} Feed`;

    if (app.reminderFrequency === 'never') {
      if (elDisabledPanel) elDisabledPanel.hidden = false;
      if (elActivePanel) elActivePanel.hidden = true;
      if (elDisabledAppName) elDisabledAppName.textContent = app.name;
    } else {
      if (elDisabledPanel) elDisabledPanel.hidden = true;
      if (elActivePanel) elActivePanel.hidden = false;
      const mins = app.reminderFrequency === 'custom' ? app.customMinutes : app.reminderFrequency;
      if (elNotice) elNotice.textContent = app.reminderNotice;
      if (elSub) elSub.textContent = `Scheduled check-in after ${mins} minutes in ${app.name}`;
      if (elAct1) elAct1.textContent = app.advanced.action1.label;
      if (elAct2) elAct2.textContent = `${app.advanced.action2.label} (+${app.advanced.action2.snoozeMinutes} min)`;
      if (elAct3) elAct3.textContent = app.advanced.action3.label;
    }
  }

  function setupReminderPreview() {
    const btnAct1 = document.getElementById('btnReminderAction1');
    const btnAct2 = document.getElementById('btnReminderAction2');
    const btnAct3 = document.getElementById('btnReminderAction3');
    const btnEnable = document.getElementById('btnEnableReminderShortcut');

    if (btnAct1) {
      btnAct1.addEventListener('click', () => {
        showToast('App closed. Intentional choice logged.');
        navigateTo('home');
      });
    }

    if (btnAct2) {
      btnAct2.addEventListener('click', () => {
        const app = getRule(activePreviewAppId);
        const mins = app.advanced.action2.snoozeMinutes || 10;
        showToast(`Reminder snoozed for ${mins} minutes.`);
        navigateTo('home');
      });
    }

    if (btnAct3) {
      btnAct3.addEventListener('click', () => {
        const app = getRule(activePreviewAppId);
        showToast(`Reminders paused for ${app.name} for the rest of today.`);
        navigateTo('home');
      });
    }

    if (btnEnable) {
      btnEnable.addEventListener('click', () => {
        currentConfigAppId = activePreviewAppId;
        navigateTo('configure');
      });
    }
  }

  // --- Theme Controller ---
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

      try {
        localStorage.setItem(THEME_STORAGE_KEY, themeChoice);
      } catch (e) {
        /* storage unavailable */
      }

      themeButtons.forEach((btn) => {
        btn.classList.toggle('active', btn.getAttribute('data-theme-val') === themeChoice);
      });

      settingThemeButtons.forEach((btn) => {
        btn.classList.toggle('active', btn.getAttribute('data-theme-choice') === themeChoice);
      });
    }
    window.applyTheme = applyTheme;

    applyTheme(getSavedThemePreference());

    themeButtons.forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const val = e.currentTarget.getAttribute('data-theme-val');
        applyTheme(val);
        showToast(`Theme: ${val.charAt(0).toUpperCase() + val.slice(1)}`);
      });
    });

    settingThemeButtons.forEach((btn) => {
      btn.addEventListener('click', (e) => {
        const val = e.currentTarget.getAttribute('data-theme-choice');
        applyTheme(val);
        showToast(`Theme: ${val.charAt(0).toUpperCase() + val.slice(1)}`);
      });
    });

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

    // Primary Bottom Navigation Bar items
    document.querySelectorAll('.nav-item').forEach((item) => {
      item.addEventListener('click', (e) => {
        const target = e.currentTarget.getAttribute('data-nav-target');
        if (target) navigateTo(target);
      });
    });

    // Home buttons
    const btnHomeSettings = document.getElementById('btnHomeToSettings');
    if (btnHomeSettings) btnHomeSettings.addEventListener('click', () => navigateTo('settings'));

    const btnHomeManage = document.getElementById('btnHomeManageApps');
    if (btnHomeManage) btnHomeManage.addEventListener('click', () => navigateTo('apps'));

    const btnTestOpen = document.getElementById('btnTestOpeningNotice');
    if (btnTestOpen) {
      btnTestOpen.addEventListener('click', () => {
        const active = appRules.find((r) => r.enabled) || appRules[0];
        activePreviewAppId = active.id;
        navigateTo('intervention');
      });
    }

    const btnTestRem = document.getElementById('btnTestReminderNotice');
    if (btnTestRem) {
      btnTestRem.addEventListener('click', () => {
        const active = appRules.find((r) => r.enabled) || appRules[0];
        activePreviewAppId = active.id;
        navigateTo('reminder');
      });
    }

    // Welcome onboarding buttons
    const btnWelcomeGetStarted = document.getElementById('btnWelcomeGetStarted');
    if (btnWelcomeGetStarted) btnWelcomeGetStarted.addEventListener('click', () => navigateTo('apps'));

    const btnWelcomeSkip = document.getElementById('btnWelcomeSkip');
    if (btnWelcomeSkip) btnWelcomeSkip.addEventListener('click', () => navigateTo('home'));

    // Settings Reset All Demo Button
    const btnResetAll = document.getElementById('btnResetAllDemo');
    if (btnResetAll) {
      btnResetAll.addEventListener('click', () => {
        if (confirm('Reset all app rules to factory defaults?')) {
          appRules = JSON.parse(JSON.stringify(DEFAULT_APP_RULES));
          saveAppRules();
          renderHomeOverview();
          renderAppsList();
          showToast('All rules reset to defaults');
        }
      });
    }

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
    setupConfigureScreen();
    setupInterventionPreview();
    setupReminderPreview();
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
