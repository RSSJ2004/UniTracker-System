import type { TaskType } from '../types';

const MONTHS: Record<string, number> = {
  enero: 1, febrero: 2, marzo: 3, abril: 4, mayo: 5, junio: 6,
  julio: 7, agosto: 8, septiembre: 9, setiembre: 9, octubre: 10,
  noviembre: 11, diciembre: 12,
};

// República Dominicana: UTC-4 todo el año (sin horario de verano).
const TZ_OFFSET = '-04:00';
const TZ_MINUTES = -4 * 60;

const pad = (n: number) => String(n).padStart(2, '0');

export const clean = (s: string | null | undefined): string =>
  (s ?? '').replace(/\s+/g, ' ').trim();

function localNow(now: Date) {
  const d = new Date(now.getTime() + TZ_MINUTES * 60_000);
  return { y: d.getUTCFullYear(), m: d.getUTCMonth() + 1, d: d.getUTCDate() };
}

function addDays(y: number, m: number, d: number, days: number) {
  const dt = new Date(Date.UTC(y, m - 1, d + days));
  return { y: dt.getUTCFullYear(), m: dt.getUTCMonth() + 1, d: dt.getUTCDate() };
}

/**
 * Convierte textos como:
 *  - "viernes, 14 de noviembre de 2026, 23:59"
 *  - "Viernes, 14 noviembre, 23:59"          (Moodle ES, sin año)
 *  - "14/11/2026 11:59 PM"
 *  - "hoy, 18:00" / "mañana, 08:30"
 *  - "lunes, 10 noviembre » martes, 11 noviembre, 23:59" (usa el final)
 * a ISO-8601 con offset de RD. Devuelve null si no logra interpretarlo.
 */
export function parseSpanishDate(raw: string, now = new Date()): string | null {
  let text = clean(raw).toLowerCase();
  if (!text) return null;

  if (text.includes('»')) text = clean(text.split('»').pop());

  // Hora (por defecto 23:59 si el portal no la muestra)
  let hour = 23, minute = 59;
  const t = text.match(/(\d{1,2}):(\d{2})\s*(a\.?\s?m\.?|p\.?\s?m\.?)?/);
  if (t) {
    hour = Number(t[1]);
    minute = Number(t[2]);
    const mer = t[3]?.replace(/[\s.]/g, '');
    if (mer === 'pm' && hour < 12) hour += 12;
    if (mer === 'am' && hour === 12) hour = 0;
  }

  const today = localNow(now);
  let y: number | undefined, m: number | undefined, d: number | undefined;

  // Relativos
  if (/pasado\s+ma[ñn]ana/.test(text)) ({ y, m, d } = addDays(today.y, today.m, today.d, 2));
  else if (/ma[ñn]ana/.test(text)) ({ y, m, d } = addDays(today.y, today.m, today.d, 1));
  else if (/\bhoy\b/.test(text)) ({ y, m, d } = today);

  // "14 de noviembre de 2026" / "14 noviembre"
  if (y === undefined) {
    for (const mm of text.matchAll(/(\d{1,2})\s+(?:de\s+)?([a-záéíóú]+)(?:\s+(?:de\s+)?(\d{4}))?/g)) {
      const month = MONTHS[mm[2]];
      if (month) {
        d = Number(mm[1]);
        m = month;
        y = mm[3] ? Number(mm[3]) : undefined;
        break;
      }
    }
    // Sin año: asumir el actual; si ya pasó hace mucho, es del año siguiente.
    if (m && d && y === undefined) {
      y = today.y;
      const guess = Date.parse(`${y}-${pad(m)}-${pad(d)}T00:00:00${TZ_OFFSET}`);
      if (guess < now.getTime() - 180 * 86_400_000) y += 1;
    }
  }

  // "14/11/2026"
  if (y === undefined) {
    const n = text.match(/(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{2,4})/);
    if (n) {
      d = Number(n[1]);
      m = Number(n[2]);
      y = Number(n[3]);
      if (y < 100) y += 2000;
    }
  }

  if (!y || !m || !d) return null;
  const iso = `${y}-${pad(m)}-${pad(d)}T${pad(hour)}:${pad(minute)}:00${TZ_OFFSET}`;
  if (Number.isNaN(new Date(iso).getTime())) return null;
  return iso;
}

/** Clasifica por componente de Moodle y, si no hay, por el título. */
export function classifyEvent(component: string | null, title: string): TaskType {
  const c = (component ?? '').toLowerCase();
  if (c.includes('assign')) return 'assignment';
  if (c.includes('forum')) return 'forum';
  if (c.includes('quiz')) return 'exam';
  if (/\bforo\b/i.test(title)) return 'forum';
  if (/examen|parcial|quiz|cuestionario|evaluaci[oó]n|prueba/i.test(title)) return 'exam';
  if (/tarea|asignaci[oó]n|entrega|trabajo|proyecto/i.test(title)) return 'assignment';
  return 'other';
}

/** Hash corto y estable para IDs cuando el portal no expone uno. */
export function stableId(...parts: string[]): string {
  let h = 5381;
  for (const ch of parts.join('|')) h = ((h << 5) + h + ch.charCodeAt(0)) >>> 0;
  return h.toString(36);
}
