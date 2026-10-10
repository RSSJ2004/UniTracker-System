import 'dotenv/config';
import readline from 'node:readline/promises';
import { chromium } from 'playwright-core';

const BASE = (process.env.CAMPUS_BASE_URL ?? 'https://campusvirtual.intec.edu.do').replace(/\/+$/, '');
const OUT = process.env.STORAGE_STATE ?? 'storageState.json';

(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.LOCAL_CHROME_PATH || undefined,
    channel: process.env.LOCAL_CHROME_PATH ? undefined : (process.env.LOCAL_BROWSER_CHANNEL ?? 'msedge'),
    headless: false,
  });
  const context = await browser.newContext({ locale: 'es-DO', timezoneId: 'America/Santo_Domingo' });
  const page = await context.newPage();
  await page.goto(`${BASE}/login/index.php`);

  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  for (;;) {
    await rl.question('\nInicia sesión, espera a ver tu tablero y presiona ENTER aquí... ');
    await page.goto(`${BASE}/my/`, { waitUntil: 'domcontentloaded' });
    if (!page.url().includes('/login')) break;
    console.log('Moodle sigue mostrando el login: la sesión NO está iniciada. Inicia sesión en la ventana y repite.');
  }
  rl.close();

  await context.storageState({ path: OUT });
  console.log(`Sesión VERIFICADA y guardada en ${OUT} (${page.url()})`);
  await browser.close();
})().catch((e) => {
  console.error(e);
  process.exit(1);
});