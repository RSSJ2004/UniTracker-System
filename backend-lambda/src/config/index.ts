function required(name: string): string {
  const v = process.env[name];
  if (!v) throw new Error(`Falta la variable de entorno ${name}`);
  return v;
}

/** Se evalúa en cada invocación (no al importar) para dar errores claros. */
export function getConfig() {
  const baseUrl = required('CAMPUS_BASE_URL').replace(/\/+$/, '');
  return {
    baseUrl,
    // Opcionales: en local se puede reutilizar una sesión guardada (npm run login).
    user: process.env.CAMPUS_USER ?? '',
    pass: process.env.CAMPUS_PASS ?? '',
    apiKey: process.env.API_KEY ?? '',
    loginPath: process.env.CAMPUS_LOGIN_PATH ?? '/login/index.php',
    upcomingPath:
      process.env.CAMPUS_UPCOMING_PATH ??
      '/calendar/view.php?view=upcoming&lookahead=60',
    navTimeoutMs: Number(process.env.NAV_TIMEOUT_MS ?? 30000),
    // Selectores por defecto: Moodle. Ajústalos si tu portal es distinto.
    sel: {
      username: '#username',
      password: '#password',
      submit: '#loginbtn',
      loginError: '#loginerrormessage, .loginerrors, .alert-danger',
      eventCard: '[data-region="event-item"], .event[data-event-id]',
      title: 'h3.name, .card-title, h3',
      date: '.date, .col-11 .row .col-11',
      course: '.course a, [data-region="event-course"] a',
      link: 'a[href*="/mod/"], h3 a, .card-link',
    },
  };
}
export type AppConfig = ReturnType<typeof getConfig>;
