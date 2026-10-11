# 🎓 UniTracker & Campus Scraper System

UniTracker es un ecosistema inteligente diseñado para la vida universitaria. Está compuesto por una aplicación móvil nativa en **Android (Jetpack Compose)** para la gestión de tareas, control de alarmas y autenticación segura mediante WebView, y un backend **Serverless** ejecutado en **AWS Lambda mediante contenedores Docker**, encargado de realizar el scraping automatizado de eventos y tareas del campus virtual.

---

## 📂 Estructura del Repositorio (Monorepo)

```text
UniTracker-System/
├── backend-lambda/          # Backend Serverless en Node.js/TypeScript con Playwright y Docker
│   ├── .env                 # [IGNORADO] Credenciales y claves privadas locales
│   ├── .env.example         # Plantilla pública de variables de entorno
│   ├── storageState.json    # [IGNORADO] Tokens y cookies de sesión locales
│   ├── Dockerfile           # Configuración de contenedor para AWS Lambda
│   └── src/                 # Código fuente del scraper y API
├── UniTracker android/      # Aplicación móvil nativa (Android Studio / Kotlin)
│   └── local.properties     # [IGNORADO] Configuración de endpoints y API Keys de la app
└── .gitignore               # Reglas globales de seguridad y exclusión de Git

```

---

## 🏗️ Arquitectura y Flujo de Autenticación

Para superar las estrictas restricciones de seguridad corporativas y los bloqueos contra bots de los proveedores de identidad (como Microsoft Azure AD y autenticación de doble factor MFA), el sistema implementa una **estrategia híbrida de Persistencia de Sesión (`storageState.json`)**:

1. **Autenticación Local:** Se ejecuta un script en un entorno local de confianza (la PC del desarrollador) para iniciar sesión manualmente una sola vez y resolver los retos de MFA de la institución.
2. **Exportación de Cookies:** Se genera un archivo cifrado con las cookies y tokens de sesión (`storageState.json`).
3. **Inyección en la Nube:** La función AWS Lambda reutiliza este estado de sesión preautenticado para saltarse por completo el flujo de login y realizar el scraping de manera silenciosa y rápida.

---

## 🛠️ Guía de Configuración Local y Desarrollo

### Prerrequisitos

* **Node.js** (versión 18 o superior).
* **Docker** instalado y en ejecución (necesario para empaquetar y probar la Lambda).
* **Android Studio** para compilar y ejecutar la app móvil.

### 1. Configuración del Backend

1. Entra a la carpeta `backend-lambda`.
2. Duplica el archivo `.env.example` y renómbralo a `.env`. Rélleno con tus datos institucionales de prueba.
3. Instala las dependencias:
```bash
npm install

```



### 2. Generación de Sesión Local (Bypassing Microsoft SSO)

Para evitar que AWS sea bloqueado por las políticas de inicio de sesión de la universidad, genera tu sesión localmente:

1. Ejecuta el script de login local:
```bash
npm run login:local

```


2. Se abrirá una ventana de navegador real. Inicia sesión con tus credenciales y completa el MFA.
3. Cuando estés dentro del campus virtual, regresa a la terminal y presiona **ENTER**. Esto generará tu archivo local `storageState.json`.

---

## ☁️ Despliegue en AWS (Amazon ECR & Lambda mediante Contenedores)

Dado que la función utiliza Playwright y un entorno de navegador headless, está diseñada para desplegarse utilizando **Imágenes de Contenedor** en AWS Lambda.

### Paso A: Subir la imagen a Amazon ECR

1. Autentícate en tu registro de contenedores de AWS desde la terminal:
```bash
aws ecr get-login-password --region tu-region | docker login --username AWS --password-stdin tu-cuenta-id.dkr.ecr.tu-region.amazonaws.com

```


2. Construye la imagen Docker optimizada para producción:
```bash
docker build -t unitracker-backend .

```


3. Etiqueta y sube la imagen a tu repositorio ECR (ej. `unitracker-backend`):
```bash
docker tag unitracker-backend:latest [tu-cuenta-id.dkr.ecr.tu-region.amazonaws.com/unitracker-backend:latest](https://tu-cuenta-id.dkr.ecr.tu-region.amazonaws.com/unitracker-backend:latest)
docker push [tu-cuenta-id.dkr.ecr.tu-region.amazonaws.com/unitracker-backend:latest](https://tu-cuenta-id.dkr.ecr.tu-region.amazonaws.com/unitracker-backend:latest)

```



### Paso B: Configuración en AWS Lambda

1. En la consola de AWS Lambda, crea una nueva función seleccionando la opción **Imagen del contenedor** y vincula la imagen subida a tu repositorio de ECR.
2. Configura los recursos básicos recomendados:
* **Memoria:** `2048 MB` (garantiza la ejecución fluida de Playwright).
* **Almacenamiento efímero (/tmp):** `512 MB`.
* **Tiempo de espera (Timeout):** `1 min 30 s`.


3. En la pestaña **Configuration > Environment variables**, añade las variables de entorno necesarias:
* `API_KEY`: Tu clave de seguridad para proteger el endpoint.
* `CAMPUS_BASE_URL`: URL del campus virtual.
* `STORAGE_STATE_JSON`: (Opcional según tu implementación) Contenido minificado de tu `storageState.json` si se inyecta por entorno.



---

## 🔌 Uso de la API y Consumo desde Android

La Lambda expone su funcionalidad mediante la URL de la función o API Gateway. Para conectarla con la app de Android, define en el archivo `local.properties` (dentro de la carpeta de Android) tu endpoint seguro:

```properties
lambda.url=[https://tu-url-de-lambda.lambda-url.us-east-2.on.aws/](https://tu-url-de-lambda.lambda-url.us-east-2.on.aws/)
lambda.apiKey=tu_api_key_segura_aqui

```

---

## 🔄 ¿Qué hacer cuando expire la sesión?

Las cookies institucionales tienen un tiempo de vida limitado. Si la Lambda empieza a reportar fallos de autenticación:

1. Vuelve a ejecutar `npm run login:local` en tu máquina.
2. Copia el nuevo contenido de `storageState.json` o actualiza la variable/archivo correspondiente.
3. Actualiza la imagen o las variables en AWS Lambda.

---

## 🔒 Seguridad y Privacidad

* Los archivos con credenciales reales (`.env`), tokens de sesión (`storageState.json`) y configuraciones locales de compilación (`local.properties`) están estrictamente protegidos mediante el archivo `.gitignore` global.
* Ninguna información personal o institucional se expone en este repositorio público.

```

### ¿Cómo proceder ahora?
1. Crea un archivo de texto en la raíz de `UniTracker-System`, nómbralo `README.md` y pega todo este contenido.
2. Ejecuta los comandos de Git que vimos antes para hacer el `git add`, `git commit` y `git push` a tu repositorio en GitHub. ¡Todo quedará impecable y seguro!

```
