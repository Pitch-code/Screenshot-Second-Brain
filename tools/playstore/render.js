/*
 * Renders the Play Store feature screenshots (1080x1920 portrait) from the HTML
 * in this directory — same generate-don't-draw approach as tools/store_graphics.py
 * and tools/producthunt, so the graphics can't drift from the brand.
 *
 * These are promotional caption slides, NOT device captures. Play accepts styled
 * promo screenshots, and captioned feature slides convert far better than raw,
 * uncaptioned screenshots (which is the feedback that prompted them). For the
 * strongest listing, interleave 2-3 real captures from the live app between these.
 *
 * Requires Chromium + playwright-core; not project deps, so it runs on demand.
 *
 *   CHROME=/path/to/chrome PLAYWRIGHT=/path/to/playwright-core \
 *     node tools/playstore/render.js
 *
 * Output: play/screenshots/*.png
 */
const path = require('path');
const fs = require('fs');
const { chromium } = require(process.env.PLAYWRIGHT || 'playwright-core');

const EXE = process.env.CHROME;
const DIR = __dirname;
const OUT = path.resolve(__dirname, '..', '..', 'play', 'screenshots');

const jobs = [
  { html: 'pslide1.html', out: 'play-1-privacy.png' },
  { html: 'pslide2.html', out: 'play-2-search.png' },
  { html: 'pslide3.html', out: 'play-3-actions.png' },
  { html: 'pslide4.html', out: 'play-4-cleanup.png' },
  { html: 'pslide5.html', out: 'play-5-price.png' },
];
const W = 1080, H = 1920;

(async () => {
  if (!EXE) throw new Error('Set CHROME to a Chromium/Chrome executable path.');
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: EXE, args: ['--no-sandbox'] });
  for (const j of jobs) {
    const ctx = await browser.newContext({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
    const page = await ctx.newPage();
    await page.goto('file://' + path.join(DIR, j.html), { waitUntil: 'networkidle' });
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: path.join(OUT, j.out), clip: { x: 0, y: 0, width: W, height: H } });
    await ctx.close();
    console.log('wrote', j.out, W + 'x' + H);
  }
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
