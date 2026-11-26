# 🔥 Conectar tu base de datos de Firebase

Esta guía explica **exactamente qué datos necesito** para conectar tu proyecto de
Firebase, y **dónde encontrarlos**. Son 3 datos (uno opcional).

> **Mantiene Java puro.** No se instala ningún SDK de Firebase (que arrastraría
> Kotlin). La app habla con Firebase por su **API REST** usando el Retrofit que ya
> tenía. Resultado: **0 archivos Kotlin** y **0 bytecode Kotlin** en el APK.

---

## 🚀 "No sé cómo dártelo" → 3 formas, de más fácil a más manual

### Forma 1 — Adjunta el archivo (10 segundos) ⭐ la más fácil

Descarga el archivo **`google-services.json`** de tu proyecto y **adjúntalo a la
conversación**, como adjuntarías una foto. Yo hago todo lo demás.

**Cómo descargarlo:**

1. Entra en **https://console.firebase.google.com/**
2. Elige tu proyecto (el que ya creaste con la base de datos).
3. Arriba a la izquierda, pulsa el **engranaje ⚙️** → **Configuración del proyecto**.
4. Baja hasta **"Tus apps"**. Si no ves ninguna app, pulsa el icono **Android** y
   registra el paquete `com.tuempresa.citasmedicas` (tarda 20 segundos).
5. En la app Android, pulsa **"google-services.json"** → se descarga.

Luego en el chat: **arrastra ese archivo** o usa el botón de adjuntar. Fin.

### Forma 2 — Cópialo del archivo tú mismo (1 minuto)

Abre el `google-services.json` con el Bloc de notas y busca estas dos líneas:

```json
"project_id": "citas-medicas-4f3a1",     ← dato 1
"current_key": "AIzaSyB1x..."            ← dato 2
```

Pégamelas en el chat tal cual. Con eso ya conecta.

### Forma 3 — Verifica tú mismo y me pasas la salida (recomendada si quieres ver qué hay)

Ya tienes un script listo en el proyecto que **consulta tu base de datos, dice si
responde, y lista las colecciones y los campos reales** de cada una:

```bash
# Opción interactiva: primero guarda los datos
python3 tools/configurar_firebase.py ruta/a/tu/google-services.json

# Y luego comprueba la conexión
python3 tools/verificar_firebase.py
```

O directamente, sin escribir archivos:

```bash
python3 tools/verificar_firebase.py --project TU_PROJECT_ID --key TU_API_KEY
```

La salida te dice exactamente en qué estado está todo:

```
[1/2] Consultando la base de datos...
  HTTP 200  →  la base de datos EXISTE y responde
  Colecciones encontradas: 3

[2/2] Leyendo los campos reales de cada colección...

  doctors  (3 documento(s) de muestra)
      fullName               stringValue
      specialtyName          stringValue
      rating                 doubleValue
```

**Pégame esa salida** y yo ajusto el mapeo a tus campos reales. Si sale un error
(403, 404, clave no válida), el script **ya te dice qué hacer** — y me lo pegas igual.

---

## 🚨 Antes de nada: crea la base de datos (sólo se hace una vez)

**Tener el proyecto NO es tener la base de datos.** Son dos pasos distintos y el
segundo sólo puedes hacerlo tú desde la consola (Google exige una cuenta con permisos
de propietario; una API Key no puede crear bases de datos).

Si no haces esto, verás el error:

```
404  The database (default) does not exist for project ...
```

**Pasos (2 minutos):**

1. Entra en https://console.firebase.google.com/ y elige tu proyecto.
2. Menú **Build → Firestore Database** → botón **Crear base de datos**.
3. Elige ubicación (la más cercana) y **modo de prueba** para empezar.
4. Menú **Build → Authentication** → **Comenzar** → activa
   **Correo electrónico/contraseña**.
5. En **Firestore → Reglas**, mientras desarrollas usa:

   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /{document=*} {
         allow read, write: if true;
       }
     }
   }
   ```

Sin el paso 2 **no hay colecciones que ver** ni datos que guardar: la consola aparece
vacía porque no existe el contenedor, no porque falten datos.

> **Ojo con el paso 4.** El proveedor de correo/contraseña sólo queda activo cuando la
> consola muestra la etiqueta **«Habilitado»** en verde junto a
> «Correo electrónico/contraseña». Si marcaste la casilla y cerraste sin pulsar
> **Guardar**, Google responde `CONFIGURATION_NOT_FOUND` y las cuentas se quedan en el
> teléfono en vez de crearse en Firebase.

---

## 🩺 Comprobar los dos pasos de golpe

Un único comando te dice en qué estado está cada cosa, sin que tengas que buscar en la
consola:

```bash
python3 tools/verificar_firebase.py
```

Interpretación de la salida:

| Mensaje del resumen | Significado | Qué hacer |
|---|---|---|
| `LISTA` | La base existe y las reglas dejan leer | Nada, ya funciona |
| `CREADA, pero las reglas la bloquean` | **El paso A está hecho.** Google responde 403 (no 404) | Publicar las reglas del paso 5 |
| `PENDIENTE` | La base no existe (404) | Hacer el paso 2 |

Para **Authentication** el script distingue tres casos reales:

| Respuesta de Google | Significado |
|---|---|
| `CONFIGURATION_NOT_FOUND` | El proveedor **no** está habilitado (falta pulsar Guardar) |
| `USER_NOT_FOUND` / `INVALID_LOGIN_CREDENTIALS` | El proveedor **sí** está activo (rechaza la prueba porque el usuario no existe, que es lo esperado) |
| `API_KEY_INVALID` | La clave no pertenece a este proyecto |

El script prueba con un correo inventado a propósito: así sabe si el proveedor está
activo sin crear cuentas de prueba en tu proyecto.

> **La app no se queda inutilizada si Authentication aún no está activo.** El respaldo
> `FirebaseAuthFallback` degrada a validación local **sólo** ante problemas de
> configuración, nunca ante una contraseña incorrecta (eso sería un agujero de
> seguridad). Puedes hacer la demo mientras terminas de activar el proveedor.

---

## 📥 Cargar las colecciones iniciales

Con la base ya creada, un solo comando rellena `specialties`, `doctors` y `patients`
con datos coherentes con lo que espera la app:

```bash
python3 tools/cargar_colecciones.py
```

Escribe **23 documentos** (8 especialidades, 10 doctores, 5 pacientes) con los nombres
de campo exactos que lee `FirestoreMapper.java`. Es idempotente: se puede ejecutar
varias veces sin duplicar (usa `PATCH` con id fijo, no `POST`).

> **`appointments` no se carga desde el script.** Esa colección la escribe la propia
> app cada vez que agendas una cita: es la prueba de que la conexión funciona.

---

## 📋 Los 3 datos que necesito

| # | Dato | Ejemplo | ¿Obligatorio? |
|---|---|---|---|
| 1 | **Project ID** | `citas-medicas-4f3a1` | ✅ Sí |
| 2 | **API Key** (Web) | `AIzaSyB1x...` (39 caracteres) | ✅ Sí |
| 3 | **App ID** de Android | `1:123456789:android:abc123` | ⬜ Opcional |

> ⚠️ **Ninguno de estos 3 datos es un secreto crítico.** La API Key de Firebase es
> una clave pública de cliente: su función es *identificar* el proyecto, no
> autorizar el acceso. La seguridad real la dan las **Reglas de Firestore**.
> Aun así, **no se suben al repositorio**: se guardan en `local.properties`, que está
> en `.gitignore`.

---

## 🔍 Dónde encontrar cada dato

### Opción A — Desde el archivo `google-services.json` (lo más rápido)

Si ya descargaste el archivo de configuración de tu app Android, los 3 datos están
dentro. Busca estas claves:

```json
{
  "project_info": {
    "project_id": "citas-medicas-4f3a1",        ← DATO 1
    "project_number": "123456789",
    ...
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:123456789:android:abc123",   ← DATO 3
        ...
      },
      "api_key": [
        {
          "current_key": "AIzaSyB1x..."           ← DATO 2
        }
      ]
    }
  ]
}
```

**Puedes simplemente arrastrar/adjuntar el `google-services.json` a la conversación**
y yo extraigo los 3 datos. No hace falta que los copies a mano.

> ⚠️ Ese archivo contiene la API Key de Android. Si lo prefieres, pega solo los 3
> valores en lugar del archivo completo.

### Opción B — Desde la consola de Firebase

1. Entra en **https://console.firebase.google.com/**
2. Selecciona tu proyecto.
3. **Project ID (DATO 1):** ⚙️ *Configuración del proyecto* → pestaña **General** →
   campo **ID del proyecto**.
4. **API Key (DATO 2):** en la misma pantalla, baja hasta **Tus apps** → selecciona la
   app Android → aparece el bloque `apiKey`.
   - Si no tienes app Android registrada, en **Tus apps** pulsa el icono **Android**,
     escribe el paquete `com.tuempresa.citasmedicas` y regístrala.
5. **App ID (DATO 3):** en el mismo bloque del punto 4, campo **App ID**.

---

## 🎯 Lo que necesito de Firestore

Además de los datos de arriba, necesito saber si tus **colecciones ya tienen datos**.
Yo no puedo verlas sin credenciales, así que hay dos caminos:

### Camino 1 — Ya tienes colecciones creadas ✅

Dime los **nombres exactos** y **un ejemplo de documento** de cada una. Lo ideal es
que pegues algo así (lo sacas de Firestore → *Data*):

```
Colección: doctors
Documento doc_01:
  fullName: "Dra. Elena Ruiz"
  specialtyId: "sp_cardio"
  specialtyName: "Cardiología"
  rating: 4.8
  yearsExperience: 15
  age: 46
```

Con eso ajusto el mapeo al **nombre real de tus campos** en 5 minutos.

### Camino 2 — Aún no hay colecciones ⬜

Te genero un **script de carga** (Python, con el Admin SDK) que crea las 4 colecciones
con datos realistas:

| Colección | Contenido | Registros |
|---|---|---|
| `specialties` | 8 especialidades médicas | 8 |
| `doctors` | doctores con edad, rating y hospital | 10 |
| `patients` | expedientes con edad y datos clínicos | 5 |
| `appointments` | citas de ejemplo | 5 |

Para el script solo necesitaría el **service account key** (⚙️ *Configuración del
proyecto* → **Cuentas de servicio** → **Generar nueva clave privada**).
> Ese archivo **sí es un secreto real**: no lo subas al repositorio, solo pásamelo
> para ejecutar el script.

---

## ⚙️ Cómo se configura (una vez tenga los datos)

Yo escribiré un archivo `local.properties` (no se sube a git):

```properties
FIREBASE_PROJECT_ID=citas-medicas-4f3a1
FIREBASE_API_KEY=AIzaSyB1x...
FIREBASE_APP_ID=1:123456789:android:abc123
```

Y compilaré. **Nada más.** No hay que tocar ni una línea de Java: el
`RepositoryProvider` detecta automáticamente que hay credenciales y cambia de
implementación.

### Para GitHub Actions (opcional)

Si quieres que la CI también genere el APK conectado a tu base de datos, hay que
añadir 2 **GitHub Secrets** en el repositorio
(*Settings → Secrets and variables → Actions*):

- `FIREBASE_PROJECT_ID`
- `FIREBASE_API_KEY`

> ⚠️ Si no los añades, la CI sigue funcionando: genera el APK en **modo mock**
> (datos simulados). Eso es lo que se le entrega al profesor.

---

## 🔄 Interruptor de seguridad

El sistema está diseñado para que **nunca se rompa nada**:

| Estado | Comportamiento |
|---|---|
| Sin `local.properties` | **Modo mock** — idéntico a hoy. Es lo que compila la CI |
| Con solo `PROJECT_ID` | **Modo mock** (faltan credenciales) |
| Con `PROJECT_ID` + `API_KEY` | **Firebase activo** |
| Firebase caído / sin internet | Error claro en pantalla con el motivo (401, 403, red...) |

Para volver al modo mock basta **comentar una línea** en `local.properties`.

---

## 🔐 Reglas de seguridad de Firestore

Mientras no haya usuarios reales autenticados, las colecciones de solo lectura
necesitan permiso. Para **probar** (y solo mientras se prueba):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read: if true;
      allow write: if false;   // la app no escribe catálogo
    }
  }
}
```

Cuando conectes el login real, se cambia a `allow read: if request.auth != null;`.

> ⚠️ Aunque `allow write: if false` bloquee escrituras, las **citas siguen guardándose
> en el móvil (Room)** porque no pasan por Firestore en esta fase. Es justo la
> ventaja del diseño: la app funciona aunque la nube esté cerrada.

---

## ⚠️ Errores frecuentes (y qué significan)

| Síntoma | Causa | Solución |
|---|---|---|
| `Firestore ha rechazado la lectura (403)` | Reglas de seguridad | Publicar las reglas de arriba |
| `La clave de Firebase no es válida` | API Key mal copiada | Revisar `FIREBASE_API_KEY` |
| `La colección 'doctors' está vacía` | Existe pero sin documentos | Cargar datos (Camino 2) |
| `No se encontró la colección (404)` | Nombre distinto al esperado | Revisar nombres en Firestore |
| `OPERATION_NOT_ALLOWED` | Email/contraseña desactivado | Firebase → Authentication → *Sign-in method* → activar **Correo electrónico** |
| `EMAIL_EXISTS` al registrarse | Ese correo ya está dado de alta | Usar otro o iniciar sesión |

---

## ✅ Resumen: lo que necesito de ti

**Mínimo imprescindible (2 datos):**

```
FIREBASE_PROJECT_ID = ...
FIREBASE_API_KEY    = ...
```

**Y luego, elige uno:**

- **A)** Pégame/adjunta tu `google-services.json` → yo extraigo todo.
- **B)** Pégame los nombres de tus colecciones + un documento de ejemplo → ajusto el mapeo.
- **C)** Dime "no tengo colecciones" → te genero el script de carga.
