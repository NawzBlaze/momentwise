import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const root = process.cwd();
const port = 4173;
const outDir = path.join(root, 'site-screenshots');
const verification = '01omZfSAZVcvwrjPL0O4zI9x9tx9-AXHt6KhZnlq8tw';
const types = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.xml': 'application/xml',
  '.txt': 'text/plain; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

function startServer() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url || '/', `http://127.0.0.1:${port}`);
      let pathname = decodeURIComponent(url.pathname);
      if (pathname.startsWith('/momentwise/')) pathname = pathname.slice('/momentwise'.length) || '/';
      if (pathname === '/' || pathname === '') pathname = '/index.html';
      const file = path.resolve(root, '.' + pathname);
      if (!file.startsWith(root)) {
        res.writeHead(403);
        res.end('forbidden');
        return;
      }
      fs.readFile(file, (err, data) => {
        if (err) {
          res.writeHead(404);
          res.end('not found');
          return;
        }
        res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream' });
        res.end(data);
      });
    });
    server.listen(port, '127.0.0.1', () => resolve(server));
  });
}

const failures = [];
function fail(message) {
  failures.push(message);
  console.error('FAIL ' + message);
}

const playwrightEntry = process.env.PLAYWRIGHT_MODULE;
if (!playwrightEntry) {
  console.error('PLAYWRIGHT_MODULE is not set');
  process.exit(1);
}
fs.mkdirSync(outDir, { recursive: true });
let chromium;
try {
  const require = createRequire(import.meta.url);
  ({ chromium } = require(playwrightEntry));
} catch (error) {
  console.error('require(playwright) failed:', error);
  const imported = await import(pathToFileURL(playwrightEntry).href);
  chromium = imported.chromium || imported.default?.chromium;
}
if (!chromium) {
  console.error('Playwright did not export chromium from ' + playwrightEntry);
  process.exit(1);
}
console.log('chromium ' + chromium.executablePath());
fs.writeFileSync(path.join(outDir, 'chrome-path.txt'), chromium.executablePath());

const server = await startServer();
const browser = await chromium.launch({ args: ['--no-sandbox'] });
const page = await browser.newPage();
const missing = [];
page.on('response', (res) => {
  const url = res.url();
  if (res.status() >= 400 && (url.includes('/momentwise/') || url.includes('127.0.0.1'))) missing.push(res.status() + ' ' + url);
});

const base = `http://127.0.0.1:${port}/momentwise/`;
const downloadReady = page.waitForResponse((res) => res.url().includes('download.json') && res.ok());
await page.goto(base, { waitUntil: 'networkidle' });
await downloadReady;

const tagCount = await page.locator('head meta[name="google-site-verification"]').count();
const tagValue = tagCount === 1
  ? await page.locator('head meta[name="google-site-verification"]').getAttribute('content')
  : '';
if (tagCount !== 1 || tagValue !== verification) fail(`verification tag in head: count=${tagCount} value=${tagValue}`);
if (await page.locator('body meta[name="google-site-verification"]').count()) fail('verification tag duplicated in body');
if (await page.locator('.pause-card, .pause-pair').count()) fail('fake pause cards are still rendered');
const headerDownload = page.locator('.header-download');
if (await headerDownload.count() !== 1 || await headerDownload.getAttribute('href') !== '#download-button') fail('desktop header download CTA missing or incorrectly linked');

const downloadConfig = JSON.parse(fs.readFileSync(path.join(root, 'download.json'), 'utf8'));
const button = page.locator('#download-button');
await page.waitForFunction((available) => {
  const el = document.getElementById('download-button');
  return available ? el?.tagName === 'A' : el?.tagName === 'BUTTON';
}, downloadConfig.available === true);
const buttonState = await button.evaluate((el) => ({
  tag: el.tagName,
  disabled: el.disabled === true,
  href: el.href || '',
  text: el.textContent.trim(),
}));
if (downloadConfig.available === true) {
  if (buttonState.tag !== 'A' || buttonState.disabled) fail('published download did not become an active link');
  if (buttonState.href !== downloadConfig.apkUrl) fail('website download href does not match download.json');
  if (!/\/releases\/download\/v\d+\.\d+\.\d+\//.test(buttonState.href) || !buttonState.href.endsWith('.apk')) fail('website download is not a versioned GitHub Release APK');
  if (/debug|unsigned/i.test(buttonState.href) || buttonState.href.includes('/actions/artifacts')) fail('website download points at a debug or CI artifact');
  if (await page.locator('.version-label, .download-block, .download-details, .status-line').count()) fail('technical download metadata block must be absent');
  const visibleCopy = (await page.locator('body').innerText()) || '';
  for (const term of ['Version code', 'SHA-256', 'Checksum file', 'Verification report', 'Release notes']) {
    if (visibleCopy.includes(term)) fail('technical download detail remains visible: ' + term);
  }
} else {
  if (buttonState.tag !== 'BUTTON' || !buttonState.disabled) fail('download button was enabled without a published release');
}
const logo = page.locator('.hero-logo');
const logoInfo = await logo.evaluate((img) => {
  const style = getComputedStyle(img);
  const box = img.getBoundingClientRect();
  return {
    complete: img.complete,
    naturalWidth: img.naturalWidth,
    naturalHeight: img.naturalHeight,
    src: img.currentSrc || img.src,
    objectFit: style.objectFit,
    width: box.width,
    height: box.height,
  };
});
if (!logoInfo.complete || logoInfo.naturalWidth !== 960 || logoInfo.naturalHeight !== 960) fail('hero logo did not load at 960x960');
if (!logoInfo.src.includes('/assets/logo.png')) fail('hero logo src is not assets/logo.png');
if (logoInfo.objectFit === 'cover') fail('hero logo object-fit crops the artwork');
if (logoInfo.height <= 0 || Math.abs(logoInfo.width / logoInfo.height - 1) > 0.03) fail('hero logo is distorted');

const widths = [320, 360, 390, 430, 768, 1024, 1280, 1440];
const shots = new Set([390, 768, 1280, 1440]);
const report = { widths: {}, missing: [] };

async function measure(label) {
  return page.evaluate((name) => {
    const view = document.documentElement.clientWidth;
    const offenders = [];
    for (const el of document.querySelectorAll('body *')) {
      const rect = el.getBoundingClientRect();
      if (rect.width < 1 || rect.height < 1) continue;
      if (rect.right > view + 1 || rect.left < -1) {
        offenders.push(`${el.tagName}.${String(el.className).slice(0, 40)} ${Math.round(rect.left)}:${Math.round(rect.right)}`);
      }
    }
    const targets = [...document.querySelectorAll('button, summary, .site-nav a, .footer-links a, .hero-actions a, .hero-actions button')]
      .filter((el) => el.getBoundingClientRect().width > 0)
      .filter((el) => el.getBoundingClientRect().height < 43)
      .map((el) => `${el.tagName} ${el.textContent.trim().slice(0, 32)} ${Math.round(el.getBoundingClientRect().width)}x${Math.round(el.getBoundingClientRect().height)}`);
    return {
      label: name,
      theme: document.documentElement.getAttribute('data-theme'),
      preference: document.documentElement.getAttribute('data-theme-preference'),
      overflow: document.documentElement.scrollWidth - view,
      offenders: offenders.slice(0, 8),
      smallTargets: targets.slice(0, 8),
    };
  }, label);
}

async function shoot(name, full) {
  const jpeg = name.replace(/\.png$/, '.jpg');
  await page.screenshot({
    path: path.join(outDir, jpeg),
    fullPage: full,
    type: 'jpeg',
    quality: 62,
  });
}

for (const width of widths) {
  const height = width < 700 ? 844 : width < 1100 ? 1024 : 900;
  await page.setViewportSize({ width, height });
  await page.evaluate(() => window.scrollTo(0, 0));
  const light = await measure(`${width}-light`);
  report.widths[width] = { light };
  if (light.overflow > 1 || light.offenders.length) fail(`${width}px light overflow ${light.overflow} ${light.offenders.join(' | ')}`);
  if (light.smallTargets.length) fail(`${width}px light touch targets ${light.smallTargets.join(' | ')}`);
  if (shots.has(width)) {
    await shoot(`${width}-light-fold.png`, false);
    await shoot(`${width}-light-full.png`, true);
  } else {
    await shoot(`${width}-light-full.png`, true);
  }
  await page.locator('[data-theme-value="dark"]').click();
  const dark = await measure(`${width}-dark`);
  report.widths[width].dark = dark;
  if (dark.theme !== 'dark' || dark.preference !== 'dark') fail(`${width}px dark theme did not apply`);
  if (dark.overflow > 1 || dark.offenders.length) fail(`${width}px dark overflow ${dark.overflow} ${dark.offenders.join(' | ')}`);
  if (shots.has(width)) {
    await shoot(`${width}-dark-fold.png`, false);
    await shoot(`${width}-dark-full.png`, true);
  }
  await page.locator('[data-theme-value="light"]').click();
}

await page.setViewportSize({ width: 1280, height: 900 });
await page.evaluate(() => localStorage.removeItem('momentwise-theme'));
await page.emulateMedia({ colorScheme: 'dark' });
await page.reload({ waitUntil: 'networkidle' });
const autoDark = await measure('1280-auto-dark');
report.autoDark = autoDark;
if (autoDark.preference !== 'system' || autoDark.theme !== 'dark') fail(`auto dark failed: ${JSON.stringify(autoDark)}`);
await shoot('1280-auto-dark-fold.png', false);
await page.emulateMedia({ colorScheme: 'light' });
await page.reload({ waitUntil: 'networkidle' });
const autoLight = await measure('1280-auto-light');
report.autoLight = autoLight;
if (autoLight.preference !== 'system' || autoLight.theme !== 'light') fail(`auto light failed: ${JSON.stringify(autoLight)}`);

await page.locator('[data-theme-value="dark"]').click();
await page.reload({ waitUntil: 'networkidle' });
const persisted = await measure('persisted-dark');
if (persisted.preference !== 'dark' || persisted.theme !== 'dark') fail('dark theme did not persist across reload');
await page.locator('[data-theme-value="light"]').click();
await page.reload({ waitUntil: 'networkidle' });
const persistedLight = await measure('persisted-light');
if (persistedLight.preference !== 'light' || persistedLight.theme !== 'light') fail('light theme did not persist across reload');

await page.setViewportSize({ width: 390, height: 844 });
await page.locator('.nav-toggle').click();
const menuOpen = await page.locator('#site-nav').evaluate((el) => getComputedStyle(el).display !== 'none');
if (!menuOpen) fail('mobile menu did not open');
const menuMeasure = await measure('390-menu');
if (menuMeasure.overflow > 1 || menuMeasure.offenders.length) fail(`390 menu overflow ${menuMeasure.offenders.join(' | ')}`);
await page.keyboard.press('Escape');
const menuClosed = await page.locator('.nav-toggle').getAttribute('aria-expanded');
if (menuClosed !== 'false') fail('escape did not close the menu');

await page.locator('#faq summary').first().click();
const faqOpen = await page.locator('#faq details').first().evaluate((el) => el.open);
if (!faqOpen) fail('FAQ did not open');
await shoot('390-faq-open.png', true);

if (missing.length) fail('failed responses: ' + missing.join(' | '));
report.missing = missing;
report.failures = failures.slice();
fs.writeFileSync(path.join(outDir, 'report.json'), JSON.stringify(report, null, 2));

await page.close().catch((error) => console.error('page close', error));
await browser.close().catch((error) => console.error('browser close', error));

const lighthouseBin = process.env.LIGHTHOUSE_BIN;
if (lighthouseBin) {
  const result = spawnSync(lighthouseBin, [
    base,
    `--chrome-path=${chromium.executablePath()}`,
    '--output=json',
    '--output=html',
    `--output-path=${path.join(outDir, 'lighthouse')}`,
    '--only-categories=performance,accessibility,best-practices,seo',
    '--chrome-flags=--headless=new --no-sandbox --disable-gpu',
    '--quiet',
  ], { stdio: 'inherit', timeout: 75000 });
  const scorePath = path.join(outDir, 'lighthouse.report.json');
  if (result.status !== 0 || !fs.existsSync(scorePath)) {
    const note = `lighthouse did not finish (status ${result.status}, signal ${result.signal || 'none'})`;
    console.error(note);
    fs.writeFileSync(path.join(outDir, 'lighthouse-error.txt'), note);
  } else {
    const lh = JSON.parse(fs.readFileSync(scorePath, 'utf8'));
    const scores = Object.fromEntries(Object.entries(lh.categories).map(([key, value]) => [key, Math.round(value.score * 100)]));
    fs.writeFileSync(path.join(outDir, 'lighthouse-scores.json'), JSON.stringify(scores, null, 2));
    console.log('lighthouse scores ' + JSON.stringify(scores));
  }
} else {
  console.log('lighthouse skipped: LIGHTHOUSE_BIN not set');
}

server.close();

if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log('visual check passed');
