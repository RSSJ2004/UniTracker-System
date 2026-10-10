import type { Locator, Page } from 'playwright-core';
import type { AppConfig } from '../config';

export class AuthError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'AuthError';
  }
}

type AuthState =
  | 'microsoft-email'
  | 'microsoft-password'
  | 'moodle'
  | 'stay-signed-in'
  | 'account-picker'
  | 'mfa'
  | 'login-error'
  | 'authenticated'
  | 'unknown';

interface AuthObservation {
  state: AuthState;
  url: string;
  title: string;
  text: string;
  microsoftHost: boolean;
  markers: {
    email: boolean;
    microsoftPassword: boolean;
    moodleUsername: boolean;
    password: boolean;
    submit: boolean;
    mfa: boolean;
    accountPicker: boolean;
  };
}

const INITIAL_STATE_TIMEOUT_MS = 10_000;
const SSO_STATE_TIMEOUT_MS = 15_000;
const STEP_STATE_TIMEOUT_MS = 15_000;
const ACTION_TIMEOUT_MS = 7_000;
const POLL_INTERVAL_MS = 250;

const SSO_SELECTORS = [
  '.login-identityproviders a',
  'a[href*="auth/oidc"]',
  'a[href*="oauth2"]',
  'a[href*="microsoft"]',
  '[data-provider*="oidc"] a',
  'a:has-text("Office 365")',
  'a:has-text("Microsoft")',
  'button:has-text("Office 365")',
  'button:has-text("Microsoft")',
];

const MICROSOFT_EMAIL_SELECTORS = ['#i0116', 'input[name="loginfmt"]'];
const MICROSOFT_PASSWORD_SELECTORS = ['#i0118', 'input[name="passwd"]'];
const MOODLE_USERNAME_SELECTORS = ['#username', 'input[name="username"]'];
const MOODLE_PASSWORD_SELECTORS = ['#password', 'input[type="password"]'];
const SUBMIT_SELECTORS = [
  '#idSIButton9',
  '#loginbtn',
  'button[type="submit"]',
  'input[type="submit"]',
];

function safeUrl(rawUrl: string): string {
  try {
    const url = new URL(rawUrl);
    return `${url.origin}${url.pathname}`;
  } catch {
    return '[URL no disponible]';
  }
}

function isMicrosoftHost(hostname: string): boolean {
  const host = hostname.toLowerCase();
  return (
    /(^|\.)microsoftonline\.(com|us|cn)$/.test(host) ||
    /(^|\.)microsoft\.com$/.test(host) ||
    host === 'login.live.com' ||
    host.endsWith('.live.com') ||
    host === 'login.windows.net' ||
    host.endsWith('.msauth.net')
  );
}

function sanitizeSnippet(text: string, cfg: AppConfig): string {
  let result = text.replace(/\s+/g, ' ').trim();
  if (cfg.user) result = result.split(cfg.user).join('[usuario]');
  result = result.replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi, '[correo]');
  return result.slice(0, 180);
}

async function inspectPage(page: Page, cfg: AppConfig): Promise<AuthObservation> {
  const rawUrl = page.url();
  let hostname = '';
  let pathname = '';
  try {
    const parsed = new URL(rawUrl);
    hostname = parsed.hostname;
    pathname = parsed.pathname;
  } catch {
    // about:blank
  }

  const campusHost = (() => {
    try {
      return new URL(cfg.baseUrl).hostname.toLowerCase();
    } catch {
      return '';
    }
  })();

  const dom = await page
    .evaluate(() => {
      const visible = (selector: string): boolean => {
        return Array.from(document.querySelectorAll<HTMLElement>(selector)).some((element) => {
          const style = window.getComputedStyle(element);
          const rect = element.getBoundingClientRect();
          return (
            !element.hidden &&
            style.display !== 'none' &&
            style.visibility !== 'hidden' &&
            rect.width > 0 &&
            rect.height > 0
          );
        });
      };

      const text = (document.body?.innerText ?? '').slice(0, 1600);
      return {
        title: document.title ?? '',
        text,
        msEmail: visible('#i0116, input[name="loginfmt"]'),
        msPassword: visible('#i0118, input[name="passwd"]'),
        moodleUsername: visible('#username, input[name="username"]'),
        genericUsername: visible('input[type="text"], input[type="email"]'),
        password: visible('#password, input[type="password"]'),
        submit: visible('#idSIButton9, #loginbtn, button[type="submit"], input[type="submit"]'),
        mfaControl: visible(
          '#idDiv_SAOTCS_Entry, #idTxtBx_SAOTCC_OTC, input[name="otc"], #idSubmit_SAOTCS_Continue, #idSubmit_SAOTCC_Continue, [data-testid*="authenticator"]',
        ),
        accountPickerControl: visible('#tilesHolder, [data-test-id="tile"], [data-testid*="account"]'),
      };
    })
    .catch(() => null);

  const microsoftHost = isMicrosoftHost(hostname);
  const emptyMarkers = {
    email: false,
    microsoftPassword: false,
    moodleUsername: false,
    password: false,
    submit: false,
    mfa: false,
    accountPicker: false,
  };

  if (!dom) {
    return {
      state: 'unknown',
      url: safeUrl(rawUrl),
      title: '',
      text: '',
      microsoftHost,
      markers: emptyMarkers,
    };
  }

  const markers = {
    email: dom.msEmail,
    microsoftPassword: dom.msPassword,
    moodleUsername: dom.moodleUsername || dom.genericUsername,
    password: dom.password,
    submit: dom.submit,
    mfa: dom.mfaControl,
    accountPicker: dom.accountPickerControl,
  };

  const normalizedText = dom.text.toLowerCase();
  const isStaySignedIn =
    /stay signed in\?|mantener la sesi[oó]n iniciada\??/i.test(dom.text) && dom.submit;
  const isMfa =
    markers.mfa ||
    /approve sign.?in request|verify your identity|two[-\s]?step verification|additional security verification|more information required|authentication request|enter (?:the )?(?:code|number)|verifica tu identidad|aprobar la solicitud|introduce el c[oó]digo|escribe el c[oó]digo|informaci[oó]n adicional requerida|we need more information/i.test(
      dom.text,
    );
  const isCredentialsError =
    /your account or password is incorrect|incorrect password|password is incorrect|account doesn.t exist|we couldn.t find an account|we didn.t recognize your sign-in info|contrase[nñ]a incorrecta|correo.*incorrecto|la cuenta o la contrase[nñ]a no es correcta/i.test(
      dom.text,
    );
  const isAccountPicker =
    markers.accountPicker ||
    /pick an account|choose an account|elegir una cuenta|selecciona una cuenta/i.test(dom.text);

  let state: AuthState = 'unknown';

  if (microsoftHost) {
    if (isStaySignedIn) state = 'stay-signed-in';
    else if (isMfa) state = 'mfa';
    else if (isCredentialsError) state = 'login-error';
    else if (dom.msPassword) state = 'microsoft-password';
    else if (dom.msEmail) state = 'microsoft-email';
    else if (isAccountPicker) state = 'account-picker';
  } else if (
    dom.moodleUsername && dom.password && hostname.toLowerCase() === campusHost
  ) {
    state = 'moodle';
  } else if (
    hostname.toLowerCase() === campusHost &&
    !/(^|\/)login(?:\/|\.php|$)|\/auth\/|\/oauth/i.test(pathname)
  ) {
    state = 'authenticated';
  } else if (isMfa) {
    state = 'mfa';
  }

  return {
    state,
    url: safeUrl(rawUrl),
    title: dom.title,
    text: normalizedText,
    microsoftHost,
    markers,
  };
}

async function firstVisibleLocator(page: Page, selectors: string[]): Promise<Locator | null> {
  for (const selector of selectors) {
    const matches = page.locator(selector);
    const count = await matches.count().catch(() => 0);
    for (let index = 0; index < Math.min(count, 8); index += 1) {
      const candidate = matches.nth(index);
      if (await candidate.isVisible().catch(() => false)) return candidate;
    }
  }
  return null;
}

async function clickFirstVisible(
  page: Page,
  selectors: string[],
  timeout = ACTION_TIMEOUT_MS,
): Promise<boolean> {
  const locator = await firstVisibleLocator(page, selectors);
  if (!locator) return false;

  try {
    await locator.click({ timeout, noWaitAfter: true });
    return true;
  } catch {
    console.warn('[Auth] Falló el clic sobre un control visible; se inspeccionará el estado actual.');
    return false;
  }
}

async function resolveAccountPicker(page: Page, cfg: AppConfig): Promise<boolean> {
  if (!cfg.user) return false;

  const userTile = page.locator('[role="button"], [data-test-id="tile"]').filter({ hasText: cfg.user }).first();
  if (await userTile.isVisible().catch(() => false)) {
    await userTile.click({ timeout: ACTION_TIMEOUT_MS, noWaitAfter: true }).catch(() => undefined);
    return true;
  }

  const exactUser = page.getByText(cfg.user, { exact: true }).first();
  if (await exactUser.isVisible().catch(() => false)) {
    await exactUser.click({ timeout: ACTION_TIMEOUT_MS, noWaitAfter: true }).catch(() => undefined);
    return true;
  }

  const anotherAccount = page.getByText(/use another account|usar otra cuenta/i).first();
  if (await anotherAccount.isVisible().catch(() => false)) {
    await anotherAccount.click({ timeout: ACTION_TIMEOUT_MS, noWaitAfter: true }).catch(() => undefined);
    return true;
  }

  return false;
}

async function waitForState(
  page: Page,
  cfg: AppConfig,
  timeoutMs: number,
  phase: string,
): Promise<AuthObservation> {
  const deadline = Date.now() + timeoutMs;
  let last = await inspectPage(page, cfg);
  let accountPickerAttempts = 0;

  while (Date.now() < deadline) {
    last = await inspectPage(page, cfg);

    if (last.state === 'account-picker' && accountPickerAttempts < 2) {
      accountPickerAttempts += 1;
      const clicked = await resolveAccountPicker(page, cfg);
      if (clicked) {
        await page.waitForTimeout(350).catch(() => undefined);
        continue;
      }
      return last;
    }

    if (last.state !== 'unknown' && last.state !== 'account-picker') return last;
    await page.waitForTimeout(POLL_INTERVAL_MS).catch(() => undefined);
  }

  console.warn(
    `[Auth] Timeout esperando estado durante ${phase}. Estado=${last.state}; URL=${last.url}; título=${last.title}`,
  );
  return last;
}

function diagnostic(observation: AuthObservation, cfg: AppConfig): string {
  const text = sanitizeSnippet(observation.text, cfg);
  return `estado=${observation.state}; url=${observation.url}; título=${observation.title || '[vacío]'}` +
    (text ? `; mensaje=${text}` : '');
}

function throwForUnexpectedState(
  observation: AuthObservation,
  cfg: AppConfig,
  phase: string,
): never {
  const details = diagnostic(observation, cfg);

  if (observation.state === 'mfa') {
    throw new AuthError(
      `Microsoft requiere MFA o verificación adicional durante ${phase}. Completa ese requisito mediante un flujo autorizado y vuelve a probar. ${details}`,
    );
  }
  if (observation.state === 'login-error') {
    throw new AuthError(`Microsoft rechazó las credenciales durante ${phase}. ${details}`);
  }
  if (observation.state === 'stay-signed-in' || observation.state === 'account-picker') {
    throw new AuthError(`Microsoft dejó una pantalla intermedia sin resolver durante ${phase}. ${details}`);
  }

  throw new AuthError(`El inicio de sesión no avanzó al estado esperado durante ${phase}. ${details}`);
}

async function handleStaySignedInIfNeeded(page: Page, cfg: AppConfig): Promise<AuthObservation> {
  let observation = await inspectPage(page, cfg);
  if (observation.state === 'stay-signed-in') {
    console.log('[Auth] Detectada pantalla de mantener sesión iniciada. Respondiendo...');
    const clickedNo = await clickFirstVisible(page, [
      '#idBtn_Back',
      'button:has-text("No")',
      'input[value="No"]',
      'button:has-text("No, gracias")',
      'button:has-text("No, thanks")',
    ]);
    if (!clickedNo) {
      const clickedContinue = await clickFirstVisible(page, ['#idSIButton9']);
      if (!clickedContinue) {
        throwForUnexpectedState(observation, cfg, 'pantalla mantener sesión iniciada');
      }
    }
    observation = await waitForState(page, cfg, STEP_STATE_TIMEOUT_MS, 'redirección tras mantener sesión iniciada');
  }
  return observation;
}

export async function login(page: Page, cfg: AppConfig): Promise<void> {
  if (!cfg.user || !cfg.pass) {
    throw new AuthError('Faltan CAMPUS_USER o CAMPUS_PASS en la configuración.');
  }

  const loginUrl = `${cfg.baseUrl}${cfg.loginPath}`;
  const gotoTimeout = Math.max(15_000, Math.min(cfg.navTimeoutMs || 20_000, 25_000));

  console.log(`[Auth] Abriendo el login del campus: ${safeUrl(loginUrl)}`);
  try {
    await page.goto(loginUrl, {
      waitUntil: 'domcontentloaded',
      timeout: gotoTimeout,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (!/timeout|timed out/i.test(message)) {
      throw new AuthError(`No se pudo abrir la página de login del campus: ${message.slice(0, 180)}`);
    }
    console.warn('[Auth] La navegación tardó más de lo esperado; se inspeccionará el estado actual.');
  }

  let observation = await waitForState(page, cfg, INITIAL_STATE_TIMEOUT_MS, 'carga inicial');

  if (!observation.microsoftHost && observation.state !== 'authenticated') {
    const urlBeforeSso = page.url();
    const clickedSso = await clickFirstVisible(page, SSO_SELECTORS, ACTION_TIMEOUT_MS);
    const afterSsoAttempt = await inspectPage(page, cfg);
    const pageChanged = page.url() !== urlBeforeSso || afterSsoAttempt.state !== observation.state;

    if (clickedSso || pageChanged || afterSsoAttempt.microsoftHost) {
      console.log('[Auth] Se detectó una transición hacia el proveedor SSO. Esperando el estado de autenticación.');
      observation = await waitForState(page, cfg, SSO_STATE_TIMEOUT_MS, 'redirección al SSO');
    } else {
      observation = afterSsoAttempt;
    }
  }

  observation = await handleStaySignedInIfNeeded(page, cfg);

  if (observation.state === 'authenticated') {
    console.log('[Auth] El campus ya muestra una página autenticada.');
    return;
  }

  if (observation.state === 'mfa') {
    throwForUnexpectedState(observation, cfg, 'verificación adicional');
  }

  // --- PASO 1: CORREO DE MICROSOFT ---
  if (observation.state === 'microsoft-email') {
    const emailField = await firstVisibleLocator(page, MICROSOFT_EMAIL_SELECTORS);
    if (!emailField) throwForUnexpectedState(observation, cfg, 'campo de correo');

    console.log('[Auth] Escribiendo correo institucional...');
    await emailField.click();
    await emailField.fill('');
    await emailField.type(cfg.user, { delay: 30 }); // type simula pulsaciones reales para asegurar el evento de input

    const submitted = await clickFirstVisible(page, SUBMIT_SELECTORS);
    if (!submitted) {
      throw new AuthError(`No se encontró el botón Siguiente de Microsoft. ${diagnostic(await inspectPage(page, cfg), cfg)}`);
    }

    observation = await waitForState(page, cfg, STEP_STATE_TIMEOUT_MS, 'transición correo → contraseña');
    if (observation.state === 'authenticated') {
      console.log('[Auth] Microsoft completó el acceso sin solicitar contraseña.');
      return;
    }
  }

  // --- PASO 2: CONTRASEÑA DE MICROSOFT ---
  // Si por alguna razón la página se quedó en correo o avanzó a contraseña, manejamos ambos de forma segura
  if (observation.state === 'microsoft-email') {
    const emailField = await firstVisibleLocator(page, MICROSOFT_EMAIL_SELECTORS);
    if (emailField) {
      await emailField.fill(cfg.user);
      await clickFirstVisible(page, SUBMIT_SELECTORS);
      observation = await waitForState(page, cfg, STEP_STATE_TIMEOUT_MS, 'reintento correo → contraseña');
    }
  }

  if (observation.state === 'microsoft-password') {
    const passwordField = await firstVisibleLocator(page, MICROSOFT_PASSWORD_SELECTORS);
    if (!passwordField) throwForUnexpectedState(observation, cfg, 'campo de contraseña');

    console.log('[Auth] Escribiendo contraseña...');
    await passwordField.click();
    await passwordField.fill('');
    await passwordField.type(cfg.pass, { delay: 30 });

    const submitted = await clickFirstVisible(page, SUBMIT_SELECTORS);
    if (!submitted) {
      throw new AuthError(`No se encontró el botón de envío de Microsoft. ${diagnostic(await inspectPage(page, cfg), cfg)}`);
    }

    observation = await waitForState(page, cfg, STEP_STATE_TIMEOUT_MS, 'envío de contraseña');
    observation = await handleStaySignedInIfNeeded(page, cfg);
  }

  if (observation.state === 'moodle') {
    const usernameField = await firstVisibleLocator(page, MOODLE_USERNAME_SELECTORS.concat(['input[type="email"]', 'input[type="text"]']));
    const passwordField = await firstVisibleLocator(page, MOODLE_PASSWORD_SELECTORS);
    if (!usernameField || !passwordField) {
      throwForUnexpectedState(observation, cfg, 'formulario clásico de Moodle');
    }

    console.log('[Auth] Usando formulario clásico de Moodle.');
    await usernameField.fill(cfg.user);
    await passwordField.fill(cfg.pass);
    const submitted = await clickFirstVisible(page, ['#loginbtn', 'button[type="submit"]', 'input[type="submit"]']);
    if (!submitted) {
      throw new AuthError(`No se encontró el botón de login de Moodle. ${diagnostic(await inspectPage(page, cfg), cfg)}`);
    }

    observation = await waitForState(page, cfg, STEP_STATE_TIMEOUT_MS, 'envío del formulario de Moodle');
  }

  // Comprobación final exhaustiva
  const deadlineFinal = Date.now() + 15_000;
  while (Date.now() < deadlineFinal) {
    const currentObs = await inspectPage(page, cfg);
    if (currentObs.state === 'authenticated' || !currentObs.microsoftHost) {
      console.log(`[Auth] Autenticación completada con éxito. Destino final: ${currentObs.url}`);
      return;
    }
    await page.waitForTimeout(500).catch(() => {});
  }

  observation = await inspectPage(page, cfg);
  if (observation.state !== 'authenticated') {
    throwForUnexpectedState(observation, cfg, 'validación final');
  }

  console.log(`[Auth] Autenticación completada. Destino: ${observation.url}`);
}