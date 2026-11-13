// Renders the layers of the animated splash screen and the preview file design/TVSig_splash.svg.
// Usage: npm install playwright (once, in any folder), then: node design/generate-splash.js
// The glow is blurred once here, the app only moves and fades the finished layers.
// Regions are in design units (a 1920x1080 stage). Keep them in sync with SplashLayers.kt.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const out = path.join(root, 'app/src/main/res/drawable-nodpi');
const logoSvg = fs.readFileSync(path.join(__dirname, 'TVSig_icon.svg'), 'utf8')
  .replace(/<\?xml[^>]*\?>/, '').replace(/<!DOCTYPE[^>]*>/, '').replace(/<!--[\s\S]*?-->/g, '')
  .replace(/<svg [^>]*>/, '<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 434.889 320.446">');

const LOGO = { x: 744, y: 147, w: 432, h: 432 / (434.889 / 320.446) };
const GLOW = { x: LOGO.x - 130, y: LOGO.y - 130, w: LOGO.w + 260, h: LOGO.h + 260, scale: 0.5 };
const TITLE = { x: 740, y: 515, w: 440, h: 165, scale: 2 };
const SUB = { x: 760, y: 655, w: 400, h: 70, scale: 2 };
const SPARK_CENTER = { x: 1162, y: 158 };
const SPARK = { x: SPARK_CENTER.x - 150, y: SPARK_CENTER.y - 150, w: 300, h: 300, scale: 1 };
const DOT = { x: 0, y: 0, w: 64, h: 64, scale: 1 };
const SHARP = { x: 0, y: 0, w: 64, h: 64, scale: 1 };
const STAR = { x: 0, y: 0, w: 128, h: 128, scale: 1 };

const FONT = "Arial, Helvetica, 'Liberation Sans', sans-serif";

const logoAt = (region, filter) =>
  `<div style="position:absolute;left:${(LOGO.x - region.x) * region.scale}px;top:${(LOGO.y - region.y) * region.scale}px;` +
  `width:${LOGO.w * region.scale}px;height:${LOGO.h * region.scale}px;filter:${filter}">` +
  logoSvg.replace('<svg ', '<svg style="display:block;width:100%;height:100%" ') + '</div>';

const textSvg = (region, body, filter = 'none') =>
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${region.w} ${region.h}" ` +
  `style="position:absolute;left:0;top:0;width:${region.w * region.scale}px;height:${region.h * region.scale}px;filter:${filter}">${body}</svg>`;

const titleText = (fill, region = TITLE) =>
  `<text x="${960 - region.x}" y="${627 - region.y}" text-anchor="middle" font-family="${FONT}" font-weight="700" ` +
  `font-size="112" letter-spacing="2" fill="${fill}">TVSig</text>`;
const subText = (fill) =>
  `<text x="${960 - SUB.x}" y="${694 - SUB.y}" text-anchor="middle" font-family="${FONT}" font-size="30" ` +
  `textLength="354" lengthAdjust="spacingAndGlyphs" fill="${fill}">Android Digital Signage</text>`;

const BG = { w: 960, h: 540 };
let seed = 7;
const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
const dots = Array.from({ length: 0 }, () => {
  const x = rnd() * BG.w, y = rnd() * BG.h, r = 1.5 + rnd() * 4.5;
  const near = Math.hypot((x - 480) / 330, (y - 205) / 190) < 1;
  const c = rnd() < 0.55 ? '120,170,255' : '249,138,254';
  return `<div style="position:absolute;left:${x - r}px;top:${y - r}px;width:${2 * r}px;height:${2 * r}px;border-radius:50%;` +
    `background:radial-gradient(circle,rgba(${c},${near ? 0.1 : 0.34}) 0%,rgba(${c},${near ? 0.04 : 0.12}) 55%,rgba(${c},0) 100%)"></div>`;
}).join('');
const background =
  `<div style="position:absolute;inset:0;background:` +
  `radial-gradient(ellipse 60% 70% at 50% 38%,rgba(40,92,210,.55) 0%,rgba(26,56,150,.28) 38%,rgba(10,22,70,0) 75%),` +
  `radial-gradient(ellipse 55% 60% at 8% 105%,rgba(122,52,170,.55) 0%,rgba(80,34,130,.22) 45%,rgba(40,16,80,0) 80%),` +
  `radial-gradient(ellipse 50% 55% at 98% 100%,rgba(20,90,200,.40) 0%,rgba(14,50,130,.16) 50%,rgba(8,24,70,0) 80%),` +
  `linear-gradient(180deg,#040a1f 0%,#071433 48%,#0a1038 100%)"></div>` +
  dots +
  `<div style="position:absolute;inset:0;background:radial-gradient(ellipse 75% 75% at 50% 45%,rgba(0,0,0,0) 55%,rgba(1,3,12,.62) 100%)"></div>` +
  `<svg style="position:absolute;inset:0;width:100%;height:100%;opacity:.07;mix-blend-mode:overlay" xmlns="http://www.w3.org/2000/svg">` +
  `<filter id="n"><feTurbulence type="fractalNoise" baseFrequency="1.4" numOctaves="2" seed="3"/><feColorMatrix type="saturate" values="0"/></filter>` +
  `<rect width="100%" height="100%" filter="url(#n)"/></svg>`;

const layers = {
  splash_glow_wide: [GLOW,
    logoAt(GLOW, 'blur(24px) saturate(2.2)') + logoAt(GLOW, 'blur(14px) saturate(1.8)')],
  splash_glow_near: [GLOW,
    logoAt(GLOW, 'blur(6px) saturate(1.6) brightness(1.15) opacity(.8)')],
  splash_title: [TITLE,
    textSvg(TITLE, titleText('#2f7bff'), `blur(${26}px)`) +
    textSvg(TITLE, titleText('#2f7bff'), `blur(${14}px)`) +
    textSvg(TITLE, titleText('#8fe3ff'), `blur(${7}px)`) +
    textSvg(TITLE, `<defs><linearGradient id="t" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/>` +
      `<stop offset="1" stop-color="#d4ecff"/></linearGradient></defs>` + titleText('url(#t)'))],
  splash_subtitle: [SUB,
    textSvg(SUB, subText('#3b96ff'), 'blur(18px)') +
    textSvg(SUB, subText('#6cc4ff'), 'blur(8px)') +
    textSvg(SUB, subText('#e6f3ff'))],
  splash_spark: [SPARK,
    `<div style="position:absolute;inset:0;background:radial-gradient(circle,rgba(255,255,255,1) 0%,rgba(215,240,255,.9) 5%,rgba(120,190,255,.45) 16%,rgba(70,130,255,.15) 34%,rgba(70,130,255,0) 55%)"></div>` +
    `<div style="position:absolute;left:${150 - 150.0}px;top:${150 - 8.0}px;width:300px;height:16px;border-radius:8.0px;transform:rotate(0deg);opacity:0.5;filter:blur(6px);background:linear-gradient(90deg,rgba(255,255,255,0),#6cb4ff 50%,rgba(255,255,255,0))"></div>` +
    `<div style="position:absolute;left:${150 - 150.0}px;top:${150 - 2.0}px;width:300px;height:4px;border-radius:2.0px;transform:rotate(0deg);opacity:1;filter:blur(0.6px);background:linear-gradient(90deg,rgba(255,255,255,0),#fff 50%,rgba(255,255,255,0))"></div>` +
    `<div style="position:absolute;left:${150 - 132.0}px;top:${150 - 6.0}px;width:264px;height:12px;border-radius:6.0px;transform:rotate(90deg);opacity:0.45;filter:blur(5px);background:linear-gradient(90deg,rgba(255,255,255,0),#6cb4ff 50%,rgba(255,255,255,0))"></div>` +
    `<div style="position:absolute;left:${150 - 132.0}px;top:${150 - 1.75}px;width:264px;height:3.5px;border-radius:1.75px;transform:rotate(90deg);opacity:1;filter:blur(0.6px);background:linear-gradient(90deg,rgba(255,255,255,0),#fff 50%,rgba(255,255,255,0))"></div>` +
    `<div style="position:absolute;left:${150 - 85.0}px;top:${150 - 4.0}px;width:170px;height:8px;border-radius:4.0px;transform:rotate(45deg);opacity:0.4;filter:blur(4px);background:linear-gradient(90deg,rgba(255,255,255,0),#9fd0ff 50%,rgba(255,255,255,0))"></div>` +
    `<div style="position:absolute;left:${150 - 85.0}px;top:${150 - 1.25}px;width:170px;height:2.5px;border-radius:1.25px;transform:rotate(45deg);opacity:0.8;filter:blur(0.6px);background:linear-gradient(90deg,rgba(255,255,255,0),#fff 50%,rgba(255,255,255,0))"></div>` +
    `<div style="position:absolute;left:${150 - 85.0}px;top:${150 - 4.0}px;width:170px;height:8px;border-radius:4.0px;transform:rotate(-45deg);opacity:0.4;filter:blur(4px);background:linear-gradient(90deg,rgba(255,255,255,0),#9fd0ff 50%,rgba(255,255,255,0))"></div>` +
    `<div style="position:absolute;left:${150 - 85.0}px;top:${150 - 1.25}px;width:170px;height:2.5px;border-radius:1.25px;transform:rotate(-45deg);opacity:0.8;filter:blur(0.6px);background:linear-gradient(90deg,rgba(255,255,255,0),#fff 50%,rgba(255,255,255,0))"></div>`],
  splash_particle: [DOT,
    `<div style="position:absolute;inset:0;background:radial-gradient(circle,rgba(255,255,255,1) 0%,rgba(255,255,255,.55) 30%,rgba(255,255,255,.14) 65%,rgba(255,255,255,0) 100%)"></div>`],
  splash_dot_soft: [SHARP,
    `<div style="position:absolute;inset:0;background:radial-gradient(circle,rgba(255,255,255,.95) 0%,rgba(255,255,255,.5) 30%,rgba(255,255,255,.14) 60%,rgba(255,255,255,0) 92%)"></div>`],
  splash_dot_sharp: [SHARP,
    `<div style="position:absolute;inset:0;background:radial-gradient(circle,rgba(255,255,255,1) 0%,rgba(255,255,255,.95) 46%,rgba(255,255,255,.35) 68%,rgba(255,255,255,0) 82%)"></div>`],
  splash_star: [STAR,
    `<div style="position:absolute;inset:0;background:radial-gradient(circle,rgba(255,255,255,.9) 0%,rgba(255,255,255,.3) 14%,rgba(255,255,255,0) 40%)"></div>` +
    `<div style="position:absolute;left:0;top:61px;width:128px;height:6px;border-radius:3px;filter:blur(.8px);background:linear-gradient(90deg,rgba(255,255,255,0),#fff 50%,rgba(255,255,255,0))"></div>` +
    `<div style="position:absolute;left:61px;top:0;width:6px;height:128px;border-radius:3px;filter:blur(.8px);background:linear-gradient(180deg,rgba(255,255,255,0),#fff 50%,rgba(255,255,255,0))"></div>` +
    `<div style="position:absolute;left:24px;top:62px;width:80px;height:4px;transform:rotate(45deg);opacity:.5;filter:blur(.8px);background:linear-gradient(90deg,rgba(255,255,255,0),#fff 50%,rgba(255,255,255,0))"></div>` +
    `<div style="position:absolute;left:24px;top:62px;width:80px;height:4px;transform:rotate(-45deg);opacity:.5;filter:blur(.8px);background:linear-gradient(90deg,rgba(255,255,255,0),#fff 50%,rgba(255,255,255,0))"></div>`],
};

(async () => {
  const browser = await chromium.launch();
  const tab = await browser.newPage();
  const b64 = {};
  for (const [name, [region, body]] of Object.entries(layers)) {
    const w = Math.round(region.w * region.scale), h = Math.round(region.h * region.scale);
    await tab.setViewportSize({ width: w, height: h });
    await tab.setContent(`<html><body style="margin:0;width:${w}px;height:${h}px;position:relative;overflow:hidden;background:transparent">${body}</body></html>`);
    if (name.startsWith('splash_p') || name === 'splash_star' || name.startsWith('splash_dot')) b64[name] = '';
    const file = path.join(out, `${name}.png`);
    await tab.screenshot({ path: file, omitBackground: true });
    b64[name] = fs.readFileSync(file).toString('base64');
    console.log('wrote', path.relative(root, file), `${w}x${h}`);
  }
  await tab.setViewportSize({ width: BG.w, height: BG.h });
  await tab.setContent(`<html><body style="margin:0;width:${BG.w}px;height:${BG.h}px;position:relative;overflow:hidden">${background}</body></html>`);
  const bgFile = path.join(out, 'splash_background.jpg');
  await tab.screenshot({ path: bgFile, type: 'jpeg', quality: 92 });
  b64.bg = fs.readFileSync(bgFile).toString('base64');
  console.log('wrote', path.relative(root, bgFile), `${(fs.statSync(bgFile).size / 1024).toFixed(0)} KB`);
  await browser.close();

  const image = (name, region, id, extra = '') =>
    `<image id="${id}" href="data:image/png;base64,${b64[name]}" x="${region.x}" y="${region.y}" width="${region.w}" height="${region.h}" ${extra}/>`;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="1920" height="1080" viewBox="0 0 1920 1080" role="img" aria-label="TVSig Android Digital Signage intro">
<title>TVSig splash screen</title>
<defs>
<linearGradient id="back" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#020613"/><stop offset=".55" stop-color="#07142c"/><stop offset="1" stop-color="#02040d"/></linearGradient>
<linearGradient id="shine" x1="0" x2="1"><stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset=".36" stop-color="#fff" stop-opacity="0"/><stop offset=".5" stop-color="#fff" stop-opacity=".7"/><stop offset=".64" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
<clipPath id="shapeClip"><svg x="${LOGO.x}" y="${LOGO.y}" width="${LOGO.w}" height="${LOGO.h}" viewBox="0 0 434.889 320.446"><path d="${logoSvg.match(/<path fill="url\(#SVGID_1_\)" d="([^"]+)"/)[1]}"/></svg></clipPath>
<style>
svg *{transform-box:fill-box}
#glowWide,#glowNear,#logo,#sheen,#title,#sub,#spark{will-change:transform,opacity;animation-duration:5s;animation-fill-mode:both}
#logo,#glowWide,#glowNear{transform-origin:960px 306px;transform-box:view-box;animation-timing-function:cubic-bezier(.3,0,.2,1)}
#logo{animation-name:entrance}
#glowWide{animation-name:entrance,breathe;animation-duration:5s,2.4s;animation-delay:0s,3.1s;animation-iteration-count:1,infinite}
#glowNear{animation-name:entrance,breathe;animation-duration:5s,2.4s;animation-delay:0s,3.1s;animation-iteration-count:1,infinite}
#sheen{animation-name:sweep;animation-timing-function:ease-in-out}
#title{animation-name:titleIn;animation-timing-function:cubic-bezier(.22,.72,.25,1)}
#sub{animation-name:subIn;animation-timing-function:cubic-bezier(.22,.72,.25,1)}
#spark{animation-name:sparkle;transform-origin:${SPARK_CENTER.x}px ${SPARK_CENTER.y}px;transform-box:view-box}
@keyframes entrance{0%,5%{opacity:0;transform:translateY(18px) scale(.9)}23%{opacity:1}50%,100%{opacity:1;transform:none}}
@keyframes breathe{0%,100%{opacity:1}50%{opacity:.8}}
@keyframes sweep{0%,19%{opacity:0;transform:translateX(-550px)}27%{opacity:1}46%{opacity:.75}57%,100%{opacity:0;transform:translateX(570px)}}
@keyframes titleIn{0%,34%{opacity:0;transform:translateY(20px)}57%,100%{opacity:1;transform:translateY(0)}}
@keyframes subIn{0%,45%{opacity:0;transform:translateY(13px)}66%,100%{opacity:1;transform:translateY(0)}}
@keyframes sparkle{0%,36%{opacity:0;transform:scale(.5) rotate(-20deg)}45%{opacity:1;transform:scale(1) rotate(0)}60%,100%{opacity:0;transform:scale(.7) rotate(20deg)}}
@media(prefers-reduced-motion:reduce){#glowWide,#glowNear,#logo,#title,#sub{animation:none;opacity:1;transform:none}#sheen,#spark{animation:none;opacity:0}}
</style>
</defs>
<image href="data:image/jpeg;base64,${b64.bg}" width="1920" height="1080" preserveAspectRatio="xMidYMid slice"/>
${image('splash_glow_wide', GLOW, 'glowWide')}
${image('splash_glow_near', GLOW, 'glowNear')}
<g id="logo">${logoSvg.replace(/<svg /, `<svg x="${LOGO.x}" y="${LOGO.y}" width="${LOGO.w}" height="${LOGO.h}" `)}</g>
<g clip-path="url(#shapeClip)"><rect id="sheen" x="655" y="122" width="570" height="405" fill="url(#shine)" transform="skewX(-14)"/></g>
${image('splash_spark', SPARK, 'spark')}
${image('splash_title', TITLE, 'title')}
${image('splash_subtitle', SUB, 'sub')}
</svg>
`;
  fs.writeFileSync(path.join(__dirname, 'TVSig_splash.svg'), svg);
  console.log('wrote design/TVSig_splash.svg', (svg.length / 1024).toFixed(0) + ' KB');
})();
