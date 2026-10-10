import type { Browser, Page } from 'playwright-core';
import type { AppConfig } from '../config';
import type { Task, TaskPhase } from '../types';
import { classifyEvent, clean, parseSpanishDate, stableId } from '../utils/parser';
import * as fs from 'node:fs';

interface RawEvent {
  id: string | null;
  component: string | null;
  title: string;
  dateText: string;
  course: string;
  href: string | null;
}

export interface CollectOptions {
  storageState?: any;
}

// "Vencimiento de X" -> due | "Se abre X" -> opens | "Se cierra X" -> closes
const PHASE_PREFIXES: Array<[RegExp, TaskPhase]> = [
  [/^vencimiento de\s+/i, 'due'],
  [/^se abre\s+/i, 'opens'],
  [/^se cierra\s+/i, 'closes'],
];

function splitPhase(rawTitle: string): { title: string; phase: TaskPhase | null } {
  let title = clean(rawTitle).replace(/\s+(vence|cierra|closes?|is due)$/i, '');
  for (const [re, phase] of PHASE_PREFIXES) {
    if (re.test(title)) return { title: title.replace(re, ''), phase };
  }
  return { title, phase: null };
}

export async function collectTasks(
  browser: Browser,
  cfg: AppConfig,
  opts: CollectOptions = {},
): Promise<Task[]> {
  // 1. Verificación de la existencia del archivo de sesión
  const statePath = opts.storageState;
  if (typeof statePath === 'string') {
    const exists = fs.existsSync(statePath);
    console.log(`[scraper] Verificando storageState en ruta: "${statePath}" | ¿Existe?: ${exists}`);
    if (!exists) {
      throw new Error(`[scraper-error] ARCHIVO_NO_ENCONTRADO: El archivo storageState.json no existe en la ruta esperada: ${statePath}`);
    }
  }

  const context = await browser.newContext({
    storageState: opts.storageState,
    userAgent:
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    locale: 'es-DO',
    timezoneId: 'America/Santo_Domingo',
    viewport: { width: 1280, height: 900 },
  });
  // Evita "__name is not defined" cuando el código se ejecuta con tsx/esbuild
  await context.addInitScript('window.__name = window.__name || ((fn) => fn);');

  const page = await context.newPage();
  page.setDefaultTimeout(cfg.navTimeoutMs);

  try {
    return await scrapeUpcoming(page, cfg);
  } catch (error) {
    console.error('[scraper] Error crítico en collectTasks:', error);
    throw error;
  } finally {
    await context.close();
  }
}

async function scrapeUpcoming(page: Page, cfg: AppConfig): Promise<Task[]> {
  const targetUrl = `${cfg.baseUrl}${cfg.upcomingPath}`;
  console.log(`[scraper] Navegando a la URL objetivo: ${targetUrl}`);

  await page.goto(targetUrl, { waitUntil: 'domcontentloaded', timeout: cfg.navTimeoutMs });

  const currentUrl = page.url();
  const pageTitle = await page.title().catch(() => 'Desconocido');
  console.log(`[scraper] URL actual: ${currentUrl} | Título de página: ${pageTitle}`);

  // 2. Si redirige al login, el archivo SÍ existía pero Moodle no reconoce la sesión
  if (currentUrl.includes('/login') || currentUrl.includes('microsoftonline.com')) {
    throw new Error(
      `[scraper-error] SESION_INVALIDA_O_BLOQUEADA: El portal redirigió al login (${currentUrl}). ` +
        `Moodle no reconoce la sesión guardada: caducó, o está atada a la IP/navegador donde se creó.`,
    );
  }

  // 'attached': los elementos pueden existir sin estar "visibles" para Playwright
  await page
    .waitForSelector(cfg.sel.eventCard, { state: 'attached', timeout: 10_000 })
    .catch(() => console.warn(`[scraper] Aviso: ${cfg.sel.eventCard} no apareció en el tiempo límite.`));

  // Además del selector de config, la materia suele ser un enlace a /course/view.php
  const sel = { ...cfg.sel, course: `${cfg.sel.course}, a[href*="/course/view.php"]` };

  const raw: RawEvent[] = await page
    .$$eval(
      cfg.sel.eventCard,
      (cards, s) =>
        cards.map((card) => ({
          id: card.getAttribute('data-event-id'),
          component: card.getAttribute('data-event-component'),
          title: card.querySelector(s.title)?.textContent ?? '',
          dateText: card.querySelector(s.date)?.textContent ?? '',
          course: card.querySelector(s.course)?.textContent ?? '',
          href: (card.querySelector(s.link) as HTMLAnchorElement | null)?.href ?? null,
        })),
      sel,
    )
    .catch((err) => {
      console.error('[scraper] Error al extraer tarjetas de eventos:', err);
      return [];
    });

  if (raw.length === 0) {
    const diag = await page
      .evaluate(() => ({
        url: window.location.href,
        title: document.title,
        bodySnippet: document.body.innerText.substring(0, 600),
      }))
      .catch(() => null);
    console.warn('[scraper] 0 eventos encontrados. Diagnóstico de página:', JSON.stringify(diag, null, 2));
  }

  const seen = new Set<string>();
  const tasks: Task[] = [];
  let skippedSite = 0;
  for (const r of raw) {
    // Eventos de sitio (semanas académicas, vacaciones) no tienen enlace a una actividad
    if (!r.href) {
      skippedSite++;
      continue;
    }
    const { title, phase } = splitPhase(r.title);
    if (!title) continue;
    const dateText = clean(r.dateText);
    const id = r.id ? `ev-${r.id}` : `h-${stableId(title, dateText)}`;
    if (seen.has(id)) continue;
    seen.add(id);

    tasks.push({
      id,
      type: classifyEvent(r.component, title),
      phase,
      title,
      course: clean(r.course) || null,
      dueAt: parseSpanishDate(dateText),
      dueAtRaw: dateText,
      url: r.href,
    });
  }
  console.log(`[scraper] ${tasks.length} tareas (${skippedSite} eventos de sitio omitidos)`);

  tasks.sort((a, b) => (a.dueAt ?? '9999').localeCompare(b.dueAt ?? '9999'));
  return tasks;
}
