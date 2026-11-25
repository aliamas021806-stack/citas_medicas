
# 🩺 Sistema de Citas Médicas

> ### 👨‍🏫 ¿Eres el profesor? Empieza por el issue fijado:
> ## 📌 [👉 CÓMO PROBAR LA APP — descarga del APK e instrucciones (Issue #1)](https://github.com/aliamas021806-stack/citas_medicas/issues/1)
>
> **Descarga directa del APK, sin cuenta de GitHub y sin Android Studio:**
>
> ## 📲 [⬇️ Descargar CitasMedicas-v3.0.apk](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v3.0/CitasMedicas-v3.0.apk)
>
> - **Android 7.0+** · 5.4 MB · firmado y listo para instalar
> - Login: cualquier correo válido + contraseña de 4 caracteres (ej. `profesor@universidad.edu` / `1234`)
> - **Funciona 100 % sin internet** · no requiere crear cuenta
> - 📄 Guía extendida: **[PROBAR_APP.md](PROBAR_APP.md)** · 🏷️ [Página de la release v3.0](https://github.com/aliamas021806-stack/citas_medicas/releases/tag/v3.0)
>
> 🔥 **¿Quieres conectar tu base de datos de Firebase?** → **[CONECTAR_FIREBASE.md](CONECTAR_FIREBASE.md)**

---

Aplicación Android **nativa en Java** para gestionar citas médicas, construida con
arquitectura **MVVM**, una **API local simulada (Mock)** 100% offline y persistencia
local con **Room (SQLite)**.

> Proyecto listo para **Android Studio** y con **GitHub Actions** que genera el APK
> automáticamente en cada push. No requiere backend real ni conexión a internet.

---

## ✨ Características

- 🔐 **Login / Registro simulado** con validación y campo de **edad**.
- 🏥 **Listado de especialidades médicas** (8 especialidades).
- 👨‍⚕️ **Listado de doctores** con búsqueda, con su **edad**, rating y hospital.
- 📅 **Detalle del doctor** + selección de **fecha y horario** disponibles.
- 📋 **Mis Citas Médicas**: crear, ver y **cancelar** citas (guardadas en el teléfono).
- 👤 **Perfil de usuario** con expediente del paciente.
- 🎂 **Edad del PACIENTE** presente en la API y visible en la app (datos generados por IA).
- 📶 **100% offline**: la "API" responde localmente mediante un interceptor de red.

---

## 📥 Descargas

| Archivo | Enlace | Uso |
|---|---|---|
| **APK (v3.0)** | [⬇️ CitasMedicas-v3.0.apk](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v3.0/CitasMedicas-v3.0.apk) | Instalar y probar en un teléfono Android |
| **AAB (v3.0)** | [⬇️ CitasMedicas-v3.0.aab](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v3.0/CitasMedicas-v3.0.aab) | Publicar en Google Play |
| Release v3.0 | [Página de la release](https://github.com/aliamas021806-stack/citas_medicas/releases/tag/v3.0) | Notas de la versión y verificación del APK |
| Todas las versiones | [Releases](https://github.com/aliamas021806-stack/citas_medicas/releases) | Historial de versiones |

> 📖 Instrucciones detalladas de instalación y recorrido de prueba:
> **[PROBAR_APP.md](PROBAR_APP.md)** · **[Issue #1 (fijado)](https://github.com/aliamas021806-stack/citas_medicas/issues/1)**
>
> ℹ️ **¿Por qué Release y no el artifact de Actions?** Los *artifacts* de la pestaña
> **Actions** exigen tener cuenta en GitHub y vienen comprimidos en `.zip`. La **Release**
> es descarga pública, directa y sin login.

### 🔐 Verificación del APK publicado

```
Archivo    : CitasMedicas-v3.0.apk
Bytes      : 5631143  (5.4 MB)
SHA-256    : dc2de6f64d2031e0a73a4edc0b4a53eab71707dc9650c440185e41dc807340b7
Firma      : v1 + v2 + v3 (keystore de release)
Cert SHA256: 3df1af33ce2aae8a300723a6509db9f061df334bad97d8a2e6ae485eff7ec5b4
Paquete    : com.tuempresa.citasmedicas
Versión    : 3.0 (versionCode 3)
```

## 🏗️ Arquitectura (MVVM + Clean Architecture)

```
   UI (Activity / Fragment)          ←  Vista  · solo conoce "model"
            │  observa LiveData<List<Appointment>>
            ▼
      ViewModel (LiveData)           ←  Lógica de presentación
            │  consume casos de uso
            ▼
        UseCase (domain/usecase)     ←  Reglas de negocio · Java puro
            │  depende de INTERFACES, no de clases
            ▼
   domain/repository/IXxxRepository  ←  CONTRATO (inversión de dependencias)
            │
            ▼
   data/RepositoryProvider           ←  Raíz de composición (único punto de decisión)
            │
   ┌────────┴─────────┐
   ▼                  ▼
 API Mock (Retrofit)  Room (SQLite)
 datos simulados     citas reales
```

- **Vista** → `Activity` / `Fragment` + `RecyclerView.Adapter`. **No conoce Room.**
- **ViewModel** → `LiveData` / `MutableLiveData`, envuelto en `Resource<T>`
  (`LOADING` / `SUCCESS` / `ERROR`). No construye repositorios: los pide al provider.
- **Domain** → casos de uso que dependen de las **interfaces** de `domain/repository`.
  No importa nada de `data.local` ni de Retrofit.
- **Repository** → implementa la interfaz correspondiente.
- **Model** → POJOs (`Doctor`, `Patient`, `Appointment`, `Specialty`, `TimeSlot`...).

### 🔌 Puntos de extensión preparados para Firestore

La app está estructurada para que conectar un backend real **no obligue a tocar la UI**.
Todo lo que hay que cambiar está concentrado en `data/RepositoryProvider`:

| Interfaz (dominio) | Implementación actual | Sustituible por |
|---|---|---|
| `IAuthRepository` | `AuthRepository` (simulada) | `FirebaseAuthRepository` |
| `IDoctorRepository` | `DoctorRepository` (mock Retrofit) | `FirestoreDoctorRepository` |
| `IPatientRepository` | `PatientRepository` (mock Retrofit) | `FirestorePatientRepository` |
| `IAppointmentRepository` | `AppointmentRepository` (Room) | **se conserva**: Firestore se añade como sincronizador |

**Para migrar bastará cambiar el `return` de un método del provider.** Los
`ViewModel`, los casos de uso y las pantallas no se tocan.

> 💡 Las citas son el caso especial: **Room sigue siendo la fuente de verdad de la UI**
> (de ahí la reactividad y el funcionamiento offline) y Firestore se añade detrás como
> capa de sincronización. Ese es el patrón recomendado por Google para apps
> *offline-first*.

---

## 📁 Estructura del proyecto

```
CitasMedicas/
├── .github/workflows/android.yml      # CI: compila el APK en GitHub
├── app/
│   ├── build.gradle                   # Configuración del módulo + firma
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/tuempresa/citasmedicas/
│       │   ├── data/
│       │   │   ├── mock/              # MockData (datos de doctores y pacientes)
│       │   │   ├── remote/            # Retrofit + MockInterceptor + ApiClient
│       │   │   ├── local/             # Room: Entity, Dao, Database
│       │   │   └── repository/        # Repositorios
│       │   ├── model/                 # Modelos de dominio
│       │   ├── util/                  # Resource, SessionManager, Mapper
│       │   ├── viewmodel/             # ViewModels (MVVM)
│       │   └── view/
│       │       ├── auth/              # LoginActivity
│       │       ├── main/              # MainActivity + fragments
│       │       └── appointment/       # Detalle y Mis Citas
│       └── res/                       # Layouts, drawables, values, mipmaps
├── gradle/wrapper/                    # Wrapper de Gradle
├── build.gradle                       # Configuración raíz
├── settings.gradle
├── gradle.properties
├── key.properties                     # Credenciales de firma (demo)
└── citasmedicas-release.jks           # Keystore de release (demo)
```

---

## 🔌 API simulada (Mock)

No hay servidor real. `MockInterceptor` (OkHttp) intercepta las peticiones y responde
con datos generados en `MockData.java`. Los **endpoints** disponibles son:

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/specialties` | Lista de especialidades |
| `GET` | `/api/doctors` | Lista de doctores |
| `GET` | `/api/doctors/specialty/{id}` | Doctores por especialidad |
| `GET` | `/api/doctors/{id}` | Detalle de un doctor |
| `GET` | `/api/slots/{doctorId}` | Horarios disponibles |
| `GET` | `/api/patients` | Lista de pacientes **(incluye edad)** |
| `GET` | `/api/patients/{id}` | Paciente por id **(incluye edad)** |

> 🎂 La **edad del paciente** forma parte de la información del paciente y se
> visualiza en la app (detalle de cita, "Mis Citas" y perfil).

## 🧰 Tecnologías y versiones

| Componente | Versión |
|---|---|
| Lenguaje | **Java** (bytecode Java 17) |
| JDK de compilación (CI) | **21 (Temurin)** |
| Gradle | **8.7** |
| Android Gradle Plugin | **8.5.2** |
| compileSdk / targetSdk | **34** |
| minSdk | **24** (Android 7.0+) |
| Arquitectura | **MVVM** (ViewModel + LiveData) |
| Red | Retrofit 2.9 + OkHttp 4.12 (con **MockInterceptor**) |
| Persistencia | Room 2.6.1 (SQLite) |
| UI | Material Design 3, RecyclerView, CardView |


---

## ✅ Estado de calidad (verificado)

Todas las cifras siguientes se obtuvieron ejecutando los comandos en un entorno
limpio con **JDK 21** y **Android SDK 34**:

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease
```

| Comprobación | Resultado |
|---|---|
| Compilación (`assembleDebug` / `assembleRelease` / `bundleRelease`) | ✅ **BUILD SUCCESSFUL** |
| Pruebas automatizadas | ✅ **203 tests · 0 fallos · 0 errores** |
| Android Lint | ✅ **0 problemas** (`No issues found`) |
| Archivos Kotlin (`.kt` / `.kts`) | ✅ **0** — proyecto 100% Java |
| APK release | 5.4 MB · firmado · instalable |
| AAB release | 4.9 MB |

### 🧪 Pruebas incluidas

| Clase | Tests | Qué cubre |
|---|---|---|
| `FirebaseRestTest` | 19 | **Capa Firebase por REST**: modo mock por defecto sin credenciales, mapeo de documentos de Firestore (enteros como texto, campos ausentes, rutas completas), construcción del JSON de escritura, y verificación de que **no se añade ningún SDK** (0 Kotlin) |
| `FaseCeroArquitecturaTest` | 17 | **Contratos para Firestore**: las 4 interfaces son públicas y bien formadas, el provider devuelve implementaciones usables, el mapeo modelo↔entidad no pierde campos, y **tests que congelan la arquitectura** (fallan si alguien vuelve a importar Room desde la UI o a instanciar repositorios en un ViewModel) |
| `PantallasArrancanTest` | 15 | **Abre las pantallas de verdad** con el ciclo de vida completo: login, cambio a registro, login inválido, login válido → navegación, main con y sin sesión, las 3 pestañas, detalle del doctor, confirmar sin horario, y **rotación** de pantalla |
| `FullFlowTest` | 13 | **Flujo completo**: sesión persistente, catálogo, horario disponible, agendar en Room, aparece en "Mis Citas", sobrevive a reabrir la app, cancelar cita y contadores |
| `ResponsiveLayoutTest` | 14 | Requisito de **tablet**: resuelve 1 columna en teléfono y 2 en tablet, espaciados escalados, e infla las 6 pantallas en ambos tamaños verificando que no falte ningún ID |
| `AdaptacionesJavaTest` | 8 | Concurrencia de fechas (12 hilos × 300), singleton de `ApiClient`, validación de fecha pasada y equivalencia del orden cronológico |
| `MockDataTest` | 20 | Especialidades, doctores (edad/rating/especialidad), pacientes (edad, datos clínicos, IMC), horarios y fechas |
| `BusinessLogicTest` | 23 | `AuthValidator` (email, contraseña, edad, login y registro), `AppointmentMapper` (edad preservada), `Doctor.getDisplayName()`, `User` |
| `DtoMappingTest` | 17 | Conversión DTO → modelo, valores ausentes, tipos envueltos y saneamiento |
| `DomainNavigationTest` | 13 | Casos de uso de dominio y navegación tipada (`Routes` / `Navigator`) |
| `NetworkPipelineTest` | 9 | Pipeline real Retrofit + `MockInterceptor` + `DtoMapper` |
| `UiSmokeTest` | 9 | Robolectric: infla todos los layouts, arranca las Activities, resuelve **plurales** y verifica `SessionManager` |

Las pruebas de UI usan **Robolectric 4.14.1**, así que se ejecutan en la JVM
**sin emulador** (también en GitHub Actions).

### 🎂 La edad del paciente

- Se genera aleatoriamente (datos "de IA") en `MockData.getPatients()` y viaja en la
  API: `GET /api/patients` y `GET /api/patients/{id}`.
- Se muestra en el **perfil** (expediente), en el **detalle de la cita** y en cada
  tarjeta de **"Mis Citas"**.
- **Prioridad**: si el usuario registró su edad en el login, prevalece esa; la edad de
  la API actúa solo como respaldo.

---

## ▶️ Cómo ejecutar

### Android Studio
1. `File → Open` y selecciona la carpeta del proyecto.
2. Espera a la sincronización de Gradle (AGP 8.5.2 / Gradle 8.7).
3. Pulsa **Run** con un emulador o dispositivo (Android 7.0+).

### Línea de comandos
```bash
# Requiere JDK 17+ (probado con JDK 21) y el Android SDK 34
echo "sdk.dir=/ruta/a/Android/Sdk" > local.properties

./gradlew testDebugUnitTest     # pruebas
./gradlew assembleDebug         # APK de desarrollo
./gradlew assembleRelease       # APK firmado de release
./gradlew bundleRelease         # AAB para Google Play
```

### CI/CD
`.github/workflows/android.yml` se ejecuta en cada push a `main`/`master` con **JDK 21**:
pruebas → APK de release → AAB → subida de artefactos (`app-release-apk`,
`app-release-aab`, `test-report`).

---

## 🚧 Hoja de ruta

### Próximo paso: base de datos real con Firebase

La app funciona hoy con una **API simulada** y **Room**. El siguiente paso previsto
es sustituir los datos sintéticos por persistencia real en la nube:

| Ámbito | Hoy | Con Firebase |
|---|---|---|
| Autenticación | Simulada (valida formato) | **Firebase Authentication** (email/contraseña real) |
| Doctores, especialidades y pacientes | `MockData` + `MockInterceptor` | **Cloud Firestore** (`doctors`, `specialties`, `patients`) |
| Citas | Room local (SQLite) | **Firestore** (`appointments`) + Room como caché offline |
| Sincronización | — | Firestore offline persistence |

**Por qué la arquitectura actual ya está preparada:** la app nunca habla con
Retrofit ni con Room directamente. La cadena es

```
View → ViewModel → Caso de uso → INTERFAZ de repositorio → Implementación
```

El dominio declara contratos en `domain/repository/` y la infraestructura los
implementa. La implementación concreta se elige en un único sitio:
`data/RepositoryProvider`.

### ✅ Fase 0 — Preparación (COMPLETADA)

Sin tocar Firebase todavía, se dejó la estructura lista y verificada:

- [x] **4 interfaces de dominio** creadas: `IAuthRepository`, `IDoctorRepository`,
      `IPatientRepository`, `IAppointmentRepository`.
- [x] Las implementaciones actuales (`AuthRepository`, `DoctorRepository`,
      `PatientRepository`, `AppointmentRepository`) las implementan.
- [x] **`RepositoryProvider`** como raíz de composición: los ViewModels ya **no**
      hacen `new XRepository()`.
- [x] **`AppointmentEntity` (Room) sacada de la UI y del dominio**: la presentación
      usa `model/Appointment` y `LiveData<List<Appointment>>`. La conversión la hace
      `AppointmentMapper`, única clase que conoce ambas representaciones.
- [x] Identificadores como **`String`** (el tipo que usa Firestore).
- [x] **17 tests nuevos** (`FaseCeroArquitecturaTest`) que, además de validar los
      contratos, **congelan la arquitectura**: fallan si alguien vuelve a importar
      `data.local` desde la UI o el dominio, o a instanciar repositorios concretos
      en un ViewModel.

**Resultado:** 203 tests, 0 fallos; 0 incidencias de Lint; 0 archivos Kotlin.
La app funciona exactamente igual que antes.

### ✅ Fases 1 y 2 — Implementadas (Firebase por REST)

Ya existe la capa completa de Firebase **sin añadir ningún SDK** (por tanto, sin
Kotlin). Se usan las APIs REST de Google con el Retrofit que la app ya tenía:

| Archivo | Qué hace |
|---|---|
| `data/firebase/FirebaseConfig.java` | Lee las credenciales de `BuildConfig` y decide si hay conexión |
| `data/firebase/FirebaseAuthApi.java` | Endpoints de Authentication (`signInWithPassword`, `signUp`) |
| `data/firebase/FirebaseAuthRepository.java` | `IAuthRepository` real + traducción de errores al español |
| `data/firebase/FirestoreApi.java` | Endpoints de Firestore (listar, leer, crear, consultar) |
| `data/firebase/FirestoreClient.java` | Retrofit apuntando a `firestore.googleapis.com` |
| `data/firebase/FirestoreMapper.java` | Convierte documentos de Firestore a modelos de dominio |
| `data/firebase/FirestoreDoctorRepository.java` | `IDoctorRepository` real |
| `data/firebase/FirestorePatientRepository.java` | `IPatientRepository` real |

**El interruptor es automático:** si `local.properties` tiene `FIREBASE_PROJECT_ID` y
`FIREBASE_API_KEY`, se usa Firebase; si no, la app arranca en **modo mock** (idéntico
a hoy). En la pantalla de **Perfil** aparece un chip que indica el origen de datos.

👉 Guía paso a paso con los datos necesarios: **[CONECTAR_FIREBASE.md](CONECTAR_FIREBASE.md)**

**Herramientas incluidas** (no necesitan instalar nada, solo Python 3):

| Comando | Para qué sirve |
|---|---|
| `python3 tools/configurar_firebase.py ruta/google-services.json` | Extrae las credenciales del archivo y las guarda en `local.properties` (valida el `package_name`) |
| `python3 tools/configurar_firebase.py --project ID --key API_KEY` | Misma cosa, pero escribiendo los 2 datos a mano |
| `python3 tools/verificar_firebase.py` | **Prueba la conexión** y lista las colecciones y los campos reales de tu Firestore |
| `python3 tools/cargar_colecciones.py` | Carga especialidades, doctores y pacientes de ejemplo en Firestore (23 documentos) |

`tools/verificar_firebase.py` es el que responde a "¿está bien conectada mi base de
datos?" sin compilar nada: consulta el proyecto, dice si responde y traduce cada
error de Google (403 de reglas, 403 de clave, 404, proyecto inexistente) a un paso
concreto en español.

### ✅ Fase 3 — Sincronización de citas (implementada)

Las citas que se agendan en la app **ya se guardan en Firestore**, en la colección
`appointments`. La pieza es `data/firebase/FirestoreAppointmentRepository.java`:

| Paso | Qué ocurre |
|---|---|
| 1 | La cita se guarda en **Room** primero (la UI se actualiza al instante y funciona sin internet) |
| 2 | Se responde al usuario con el id local: la cita ya está agendada |
| 3 | En paralelo se **empuja a Firestore** (`appointments`) |
| 4 | Si Firestore acepta, se guarda su id en la columna `remote_id` para no duplicar |

**Por qué Room sigue siendo la fuente de verdad:** da reactividad inmediata y permite
agendar sin conexión. Firestore es *adicional*, no un sustituto: si la nube falla, la
cita sigue existiendo en el móvil y sólo queda pendiente de subir.

**Migración de base de datos:** la versión de Room sube a **2** con una migración
explícita que añade `remote_id` sin borrar las citas existentes.

#### Pendiente (futuro)

- [ ] **Reintento de subidas fallidas:** hoy si no hay internet al agendar, la cita
      queda en el móvil pero no se reintenta sola. Falta un `WorkManager`.
- [ ] **Borrado remoto:** al eliminar una cita se registra en el log, pero falta el
      `DELETE` contra Firestore.
- [ ] **Horarios:** hoy se calculan en local. Migrarlos requiere modelar la ocupación
      por doctor en Firestore.
- [ ] Reglas de seguridad definitivas (`allow read, write: if request.auth != null`).
- [ ] **No usar `orderBy()` con `where()`** sin índices compuestos: filtrar y ordenar
      en memoria, como ya hace el repositorio.

> 💡 **Atajo si se quiere mantener "Java puro":** Firestore y Auth tienen **API REST**
> (`https://firestore.googleapis.com/v1/...` y `identitytoolkit`). Se pueden consumir
> con el mismo **Retrofit/OkHttp** que ya está integrado, sin añadir ninguna
> dependencia Kotlin. Es la vía recomendada si la restricción de Java puro sigue vigente.

---

## 🔐 Nota sobre la firma

`key.properties` y `citasmedicas-release.jks` se incluyen en el repositorio **a
propósito y solo como demostración**, para que GitHub Actions pueda generar un APK de
release instalable sin configurar secretos.

**En un proyecto real NO se debe subir la keystore:** guárdala de forma segura y
expón sus valores como *GitHub Secrets*.

---

## 📦 Versiones ancladas

Las dependencias y el `compileSdk` están **fijados deliberadamente** para garantizar
una compilación reproducible con JDK 21 / AGP 8.5.2 / SDK 34. Los avisos de "hay una
versión más nueva" (`GradleDependency`, `OldTargetApi`) y la sugerencia de rendimiento
`Overdraw` están documentados y suprimidos en [`app/lint.xml`](app/lint.xml).
