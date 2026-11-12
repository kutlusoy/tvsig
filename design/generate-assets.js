// Generates all raster graphics of the app from design/TVSig_icon.svg.
// Usage: npm install playwright (once, in any folder), then: node design/generate-assets.js
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const res = path.join(root, 'app/src/main/res');
const svg = fs.readFileSync(path.join(__dirname, 'TVSig_icon.svg'), 'utf8')
  .replace(/<\?xml[^>]*\?>/, '')
  .replace(/<!DOCTYPE[^>]*>/, '')
  .replace(/<!--[\s\S]*?-->/g, '');

const LOGO_RATIO = 434.889 / 320.446;
const TEXT_COLOR = '#1A1A1A';
const DEBUG_BACKGROUND = '#FFB300';
const DEBUG_LABEL = '#B71C1C';
const DENSITIES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };

const logo = (width, filter = '') =>
  `<div style="width:${width}px;height:${width / LOGO_RATIO}px;${filter ? `filter:${filter};` : ''}">` +
  svg.replace('<svg ', '<svg style="display:block;width:100%;height:100%" ') + '</div>';

const page = (w, h, body, background = 'transparent') =>
  `<html><body style="margin:0;width:${w}px;height:${h}px;background:${background};overflow:hidden;` +
  `display:flex;align-items:center;justify-content:center;font-family:Inter,sans-serif">${body}</body></html>`;

const wordmark = (size) =>
  `<span style="font-size:${size}px;font-weight:300;letter-spacing:${-size * 0.02}px;color:${TEXT_COLOR};line-height:1">TVSig</span>`;

const lockup = (logoWidth, textSize, gap) =>
  `<div style="display:flex;align-items:center;gap:${gap}px">${logo(logoWidth)}${wordmark(textSize)}</div>`;

(async () => {
  const browser = await chromium.launch();
  const tab = await browser.newPage();

  async function shot(file, w, h, body, background) {
    await tab.setViewportSize({ width: Math.round(w), height: Math.round(h) });
    await tab.setContent(page(Math.round(w), Math.round(h), body, background));
    const target = path.isAbsolute(file) ? file : path.join(root, file);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    await tab.screenshot({ path: target, omitBackground: !background });
    console.log('wrote', path.relative(root, target));
  }

  // Adaptive launcher icon: 108dp canvas, logo inside the safe zone
  await shot(`${res}/drawable-nodpi/ic_launcher_foreground.png`, 432, 432, logo(432 * 0.54));
  await shot(`${res}/drawable-nodpi/ic_launcher_monochrome.png`, 432, 432, logo(432 * 0.54, 'brightness(0)'));

  // Legacy launcher icons (API 24 and 25)
  for (const [name, factor] of Object.entries(DENSITIES)) {
    const s = 48 * factor;
    const tile = (radius, logoShare) =>
      `<div style="width:${s}px;height:${s}px;border-radius:${radius};background:#fff;` +
      `display:flex;align-items:center;justify-content:center">${logo(s * logoShare)}</div>`;
    await shot(`${res}/mipmap-${name}/ic_launcher.png`, s, s, tile('12%', 0.68));
    await shot(`${res}/mipmap-${name}/ic_launcher_round.png`, s, s, tile('50%', 0.58));
  }

  // Store icon, full bleed
  await shot('app/src/main/ic_launcher-playstore.png', 512, 512, logo(512 * 0.62), '#fff');

  // Android TV banner: 320x180dp
  for (const [name, factor] of Object.entries(DENSITIES)) {
    if (name === 'xxxhdpi') continue;
    await shot(`${res}/drawable-${name}/tv_banner.png`, 320 * factor, 180 * factor,
      `<div style="transform:scale(${factor});transform-origin:center">${lockup(104, 54, 18)}</div>`, '#fff');
  }

  // Splash logo
  await shot(`${res}/drawable-nodpi/splash_logo.png`, 960, 960 / LOGO_RATIO, logo(960));

  // Notification icon: white silhouette on transparent
  for (const [name, factor] of Object.entries(DENSITIES)) {
    const s = 24 * factor;
    await shot(`${res}/drawable-${name}/ic_stat_tvsig.png`, s, s, logo(s * 0.92, 'brightness(0) invert(1)'));
  }

  // Repository graphics
  await shot('doc/logo.png', 1024, 1024 / LOGO_RATIO, logo(1024));
  await shot('doc/social-preview.png', 1280, 640,
    `<div style="display:flex;align-items:center;gap:56px">${logo(430)}` +
    `<div>${wordmark(150)}<div style="margin-top:18px;font-size:38px;font-weight:400;color:#021B8D">` +
    `Android Digital Signage</div></div></div>`, '#fff');

  // Debug build: orange background and a DEBUG label, so it is not mixed up with the release app
  const debugRes = path.join(root, 'app/src/debug/res');
  const debugBox = (box) =>
    `<div style="position:relative;width:${box}px;height:${box}px">` +
    `<div style="position:absolute;left:${box * 0.12}px;top:${box * 0.08}px;width:${box * 0.76}px">${logo(box * 0.76)}</div>` +
    `<div style="position:absolute;left:${box * 0.1}px;bottom:${box * 0.06}px;width:${box * 0.8}px;height:${box * 0.24}px;` +
    `border-radius:${box * 0.06}px;background:${DEBUG_LABEL};color:#fff;display:flex;align-items:center;justify-content:center;` +
    `font-weight:800;font-size:${box * 0.16}px;letter-spacing:${box * 0.015}px">DEBUG</div></div>`;

  await shot(`${debugRes}/drawable-nodpi/ic_launcher_foreground.png`, 432, 432, debugBox(300));
  for (const [name, factor] of Object.entries(DENSITIES)) {
    const size = 48 * factor;
    const tile = (radius) =>
      `<div style="width:${size}px;height:${size}px;border-radius:${radius};background:${DEBUG_BACKGROUND};` +
      `display:flex;align-items:center;justify-content:center">${debugBox(size * 0.8)}</div>`;
    await shot(`${debugRes}/mipmap-${name}/ic_launcher.png`, size, size, tile('12%'));
    await shot(`${debugRes}/mipmap-${name}/ic_launcher_round.png`, size, size, tile('50%'));
  }
  for (const [name, factor] of Object.entries(DENSITIES)) {
    if (name === 'xxxhdpi') continue;
    await shot(`${debugRes}/drawable-${name}/tv_banner.png`, 320 * factor, 180 * factor,
      `<div style="transform:scale(${factor});transform-origin:center;display:flex;align-items:center;gap:18px">` +
      `${debugBox(120)}${wordmark(54)}</div>`, DEBUG_BACKGROUND);
  }

  await browser.close();
})();
