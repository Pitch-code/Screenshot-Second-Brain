/*
 * Renders the Product Hunt launch assets from the HTML in this directory.
 *
 * Kept in the repo, and generated rather than hand-designed, for the same reason as
 * tools/store_graphics.py: the launch graphics and the brand mark must not drift
 * apart. The logo here is the exact launcher-icon geometry from
 * app/src/main/res/drawable/ic_launcher_foreground.xml, and the palette mirrors
 * BrandColors in core/designsystem.
 *
 * The gallery slides are deliberately branded caption cards, NOT app screenshots.
 * Product Hunt galleries commonly use promo cards, and fabricating fake UI would
 * misrepresent the product. For the strongest listing, interleave 2-3 real screen
 * captures from the live app (the ones the Play listing needs anyway).
 *
 * Requires a Chromium and the `playwright-core` module; neither is a project
 * dependency, so this runs on demand, not in CI — exactly like store_graphics.py.
 *
 * Usage (adjust the two paths for your machine):
 *   CHROME=/path/to/chrome \
 *   PLAYWRIGHT=/path/to/node_modules/playwright-core \
 *   node tools/producthunt/render.js
 *
 * Output: play/producthunt/*.png
 */
const path = require('path');
const fs = require('fs');

const pw = process.env.PLAYWRIGHT || 'playwright-core';
const { chromium } = require(pw);

const EXE = process.env.CHROME; // Chromium/Chrome executable path; required.
const DIR = __dirname;
const OUT = path.resolve(__dirname, '..', '..', 'play', 'producthunt');

const jobs = [
  { html: 'logo.html',   out: 'logo-240.png',           w: 240,  h: 240 },
  { html: 'slide1.html', out: 'gallery-1-find.png',     w: 1270, h: 760 },
  { html: 'slide2.html', out: 'gallery-2-privacy.png',  w: 1270, h: 760 },
  { html: 'slide3.html', out: 'gallery-3-speed.png',    w: 1270, h: 760 },
  { html: 'slide4.html', out: 'gallery-4-actions.png',  w: 1270, h: 760 },
  { html: 'slide5.html', out: 'gallery-5-price.png',    w: 1270, h: 760 },
];

(async () => {
  if (!EXE) throw new Error('Set CHROME to a Chromium/Chrome executable path.');
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: EXE, args: ['--no-sandbox'] });
  for (const j of jobs) {
    // deviceScaleFactor 1 so the PNG is exactly w x h, as the stores expect.
    const ctx = await browser.newContext({
      viewport: { width: j.w, height: j.h },
      deviceScaleFactor: 1,
    });
    const page = await ctx.newPage();
    await page.goto('file://' + path.join(DIR, j.html), { waitUntil: 'networkidle' });
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({
      path: path.join(OUT, j.out),
      clip: { x: 0, y: 0, width: j.w, height: j.h },
    });
    await ctx.close();
    console.log('wrote', j.out, j.w + 'x' + j.h);
  }
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
