import { timingSafeEqual } from 'node:crypto';
import type { APIGatewayProxyEventV2, APIGatewayProxyResultV2 } from 'aws-lambda';
import chromium from '@sparticuz/chromium';
import { chromium as playwright } from 'playwright-core';
import { getConfig } from './config';
import { AuthError } from './services/auth.service';
import { collectTasks } from './services/scraper.service';
import type { TasksResponse } from './types';
import * as path from 'node:path';


// Esto detecta si está en AWS Lambda o en local de forma segura
const statePath = path.join(process.env.LAMBDA_TASK_ROOT ?? process.cwd(), 'storageState.json');
const json = (statusCode: number, body: unknown): APIGatewayProxyResultV2 => ({
  statusCode,
  headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
  body: JSON.stringify(body),
});

function validKey(provided: string | undefined, expected: string): boolean {
  if (!provided) return false;
  const a = Buffer.from(provided);
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

export const handler = async (event: APIGatewayProxyEventV2): Promise<APIGatewayProxyResultV2> => {
  let cfg;
  try {
    cfg = getConfig();
  } catch (e) {
    console.error(e);
    return json(500, { ok: false, error: 'Configuración incompleta' });
  }

  // Validar API Key recibida en el encabezado x-api-key
  if (!cfg.apiKey || !validKey(event.headers?.['x-api-key'], cfg.apiKey)) {
    return json(401, { ok: false, error: 'Unauthorized' });
  }

  let browser;
  try {
    browser = await playwright.launch({
      args: chromium.args,
      executablePath: await chromium.executablePath(),
      headless: Boolean(chromium.headless),
    });

    const tasks = await collectTasks(browser, cfg, { storageState: statePath });
    const body: TasksResponse = {
      ok: true,
      generatedAt: new Date().toISOString(),
      count: tasks.length,
      tasks,
    };
    return json(200, body);
  } catch (e: any) {
    console.error('[handler]', e);
    const errorMsg = e instanceof Error ? e.message : 'SCRAPE_FAILED';
    if (e instanceof AuthError) return json(502, { ok: false, error: errorMsg });
    return json(500, { ok: false, error: errorMsg });
  } finally {
    await browser?.close().catch(() => undefined);
  }
};