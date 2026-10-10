// Prueba local desde VS Code:  npm run dev
import 'dotenv/config';
import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright-core';
import { getConfig } from './config';
import { collectTasks } from './services/scraper.service';

(async () => {
  const cfg = getConfig();
  
  // Buscar primero en la raíz 'storageState.json', luego respaldo en '.auth/state.json'
  const defaultPath = fs.existsSync(path.resolve(process.cwd(), 'storageState.json'))
    ? 'storageState.json'
    : '.auth/state.json';

  const statePath = process.env.STORAGE_STATE ?? defaultPath;
  const resolvedStatePath = path.resolve(process.cwd(), statePath);
  const storageState = fs.existsSync(resolvedStatePath) ? resolvedStatePath : undefined;

  console.error(storageState ? `[local] Usando sesión guardada: ${statePath}` : '[local] Sin sesión guardada: login con usuario/clave');

  const browser = await chromium.launch({
    executablePath: process.env.LOCAL_CHROME_PATH || undefined,
    channel: process.env.LOCAL_CHROME_PATH ? undefined : (process.env.LOCAL_BROWSER_CHANNEL ?? 'msedge'),
    headless: process.env.HEADLESS !== 'false',
  });
  try {
    const tasks = await collectTasks(browser, cfg, { storageState });
    console.log(JSON.stringify({ ok: true, count: tasks.length, tasks }, null, 2));
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});