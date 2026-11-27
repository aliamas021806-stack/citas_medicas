
# 🩺 Sistema de Citas Médicas

> ### 👨‍🏫 ¿Eres el profesor? Empieza por el issue fijado:
> ## 📌 [👉 CÓMO PROBAR LA APP — descarga del APK e instrucciones (Issue #1)](https://github.com/aliamas021806-stack/citas_medicas/issues/1)
>
> **Descarga directa del APK, sin cuenta de GitHub y sin Android Studio:**
>
> ## 📲 [⬇️ Descargar CitasMedicas-v6.2.apk](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v6.2/CitasMedicas-v6.2.apk)
>
> - **Android 7.0+** · 5.5 MB (5 742 195 bytes) · firmado y listo para instalar
> - ✅ **Esta es la versión con Firebase conectado.** La v3.0 es anterior a la integración: no sincroniza nada.
> - **Primero pulsa «Registro»** y crea tu cuenta (nombre, edad, correo y contraseña de **6+ caracteres**).
>   Después entra con ese correo. Con Firebase activo el login **valida de verdad**: un correo
>   inventado responde *«No hay ninguna cuenta con ese correo»*.
> - ⚠️ **Necesitas internet para registrarte o iniciar sesión** (Firebase valida de verdad).
>   Una vez dentro, la app **sigue funcionando sin conexión**: las citas se guardan en
>   SQLite y el catálogo se sirve desde el respaldo local.
> - 📄 Guía extendida: **[PROBAR_APP.md](PROBAR_APP.md)** · 🏷️ [Página de la release v6.2](https://github.com/aliamas021806-stack/citas_medicas/releases/tag/v6.2)
>
> 🔥 **¿Quieres conectar tu base de datos de Firebase?** → **[CONECTAR_FIREBASE.md](CONECTAR_FIREBASE.md)**
>
> ### 🔍 Cómo saber si tienes la versión correcta
>
> Abre la app → pestaña **Perfil**, y mira la etiqueta gris bajo tu correo:
>
> | Lo que muestra Perfil | Qué versión tienes |
> |---|---|
> | **«Conectado a Firebase (citas-medicas-e19ca)»** | ✅ **v6.2 — la correcta** |
> | **«Datos de prueba (sin base de datos)»** | ❌ versión antigua (v3.0 o anterior): vuelve a descargar la v6.2 |
>
> **Y para confirmar que trae las novedades de la v6.2** (agenda separada en pendientes /
> atendidas y selector Paciente/Médico al registrarse), mira la **barra inferior**, que
> cambia según tu rol:
>
> | Tu rol | Debe aparecer en la barra inferior | Si NO aparece |
> |---|---|---|
> | **Administrador** (tu caso) | **Médicos** · Perfil | el documento `/users/{uid}` no tiene `role: "admin"`, o hay que cerrar sesión y volver a entrar |
> | **Médico** | **Mi Agenda** · Perfil | el alta del médico no usó ese mismo correo |
> | **Paciente** | Doctores · Mis Citas · Perfil | es una versión anterior a la v6.2 |
>
> ⚠️ **Tras crear el documento `admin` hay que cerrar sesión y volver a entrar.** El rol
> se resuelve **al iniciar sesión**, así que la pestaña **Médicos** no aparece hasta
> entonces.

---

## 🆕 Qué arregla la v6.2

Esta versión corrige cinco fallos que se reportaron usando la app con datos reales. Todos
tenían la **misma raíz**: al iniciar sesión, la app **reescribía el documento de rol**
(`/users/{uid}`) con lo que creía saber, y cuando no lo sabía escribía `patient`.

| Síntoma que veías | Causa real | Estado |
|---|---|---|
| «Entro con mi cuenta de administrador y me registra como paciente» | Un error de permisos se interpretaba como «esta cuenta no tiene perfil», y al crearlo se escribía `role: "patient"` encima del tuyo | ✅ **Corregido.** Al entrar **nunca** se reescribe el rol |
| El médico ve «no hay nada» y luego «sin conexión» aunque haya WiFi | Su perfil no tenía `doctorId`, la regla de `appointments` devolvía **403** y la app lo disfrazaba de problema de red | ✅ **Corregido.** Se completa el `doctorId` y el 403 se explica aparte |
| El perfil del médico mostraba el nombre derivado del correo, edad **30** y teléfono vacío | El login sólo recibe el correo; la edad se rellenaba con un `30` fijo y el teléfono nunca se leía | ✅ **Corregido.** Se copia la **ficha real** (nombre, edad, teléfono) |
| Al médico le aparecían **grupo sanguíneo** y **aseguradora** que nunca rellenó | El perfil pintaba la tarjeta de paciente a todo el mundo | ✅ **Corregido.** El médico ve su **ficha profesional** |
| «En especialidad no se agrega cuando yo agrego un médico» | Se derivaba el `specialtyId` del texto libre (`"podologia"`), que no existe en `specialties` | ✅ **Corregido.** Se toma el id real del catálogo |

### 🔄 Cómo queda el ciclo del alta de un médico

Era justo lo que describiste: **el administrador crea la ficha con el correo → el médico
completa su información al crear la cuenta → todo queda vinculado.**

1. **El administrador** entra en **Médicos**, escribe nombre, correo y elige la
   especialidad del desplegable. La ficha nace *invitada* (`userId` vacío).
2. **El médico** se registra con **ese mismo correo**. La app detecta la coincidencia,
   escribe su `uid` en la ficha y le da el rol de médico. Entra directo en **Mi Agenda**.
3. **El médico completa su ficha** desde **Perfil → Completar mi ficha** (teléfono,
   consultorio, reseña y tarifa). Antes ese formulario no existía y la ficha se quedaba
   a medias para siempre.

### ✅ Cómo verificar que tienes la v6.2

| Paso | Qué debe pasar |
|---|---|
| Entra con tu cuenta de **administrador** | Sigues viendo la pestaña **Médicos**. **No** vuelves a aparecer como paciente |
| Perfil del **administrador** | **No** muestra grupo sanguíneo ni aseguradora |
| Entra con la cuenta del **médico** | Aterriza en **Mi Agenda** y sus citas **sí cargan** desde Firestore (sin el aviso de «sin conexión») |
| Perfil del **médico** | Muestra **Ficha profesional** con su especialidad, y el botón **Completar mi ficha**. El nombre es el de la ficha, no el del correo |
| Alta de un médico nuevo | La **especialidad** se elige de una lista, no se escribe a mano |

> ⚠️ **Las reglas de Firestore cambiaron.** La v6.2 necesita la versión nueva de
> [`firestore.rules`](firestore.rules) publicada en **Firebase Console → Firestore
> Database → Reglas**. Sin ese cambio, el médico seguirá recibiendo 403 en su agenda.

---

Aplicación Android **nativa en Java** para gestionar citas médicas, construida con
arquitectura **MVVM**, persistencia local con **Room (SQLite)** y sincronización
opcional con **Firebase** (Authentication + Cloud Firestore) por REST.

> Proyecto listo para **Android Studio** y con **GitHub Actions** que genera el APK
> automáticamente en cada push. **Funciona sin conexión**: Room es la fuente de
> verdad para la interfaz y Firestore es una capa adicional de sincronización.

---

## ✨ Características

- 🔐 **Login / Registro** con validación y campo de **edad** (real contra Firebase
  Authentication cuando está configurado; local en caso contrario).
- 🏥 **Listado de especialidades médicas** (8 especialidades).
- 👨‍⚕️ **Listado de doctores** con búsqueda, con su **edad**, rating y hospital.
- 📅 **Detalle del doctor** + selección de **fecha y horario** disponibles.
- 📋 **Mis Citas Médicas**: crear, ver y **cancelar** citas (guardadas en el teléfono).
- 👨‍⚕️ **Portal del médico (Mi Agenda)**: sus citas de Firestore, filtradas por su
  `doctorId`, con **pendientes de atender** y **ya atendidas** en secciones separadas.
- 🩺 **Marcar cita como atendida o como no acudida** (`Attended` / `NoShow`), guardado en
  Firestore sin tocar el resto del documento.
- 🔀 **Registro con tipo de cuenta** (Paciente / Médico): el médico vincula la ficha que
  el administrador creó con su correo, sin cerrar sesión.
- 👤 **Perfil de usuario** con expediente del paciente.
- 🎂 **Edad del PACIENTE** presente en la API y visible en la app (datos generados por IA).
- 📶 **Funciona sin conexión** una vez iniciada la sesión: el catálogo se sirve desde
  un respaldo local y las citas se guardan en SQLite.

---

## 📥 Descargas

| Archivo | Enlace | Uso |
|---|---|---|
| **APK (v6.2)** ✅ | [⬇️ CitasMedicas-v6.2.apk](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v6.2/CitasMedicas-v6.2.apk) | **Instala esta.** Firebase conectado |
| **AAB (v6.2)** | [⬇️ CitasMedicas-v6.2.aab](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v6.2/CitasMedicas-v6.2.aab) | Publicar en Google Play (4.9 MB) |
| Release v6.2 | [Página de la release](https://github.com/aliamas021806-stack/citas_medicas/releases/tag/v6.2) | Notas de la versión y verificación del APK |
| ~~v3.0~~ | [Release v3.0](https://github.com/aliamas021806-stack/citas_medicas/releases/tag/v3.0) | ⚠️ **Obsoleta**: sin Firebase, sin sincronización |
| Todas las versiones | [Releases](https://github.com/aliamas021806-stack/citas_medicas/releases) | Historial de versiones |

> 📖 Instrucciones detalladas de instalación y recorrido de prueba:
> **[PROBAR_APP.md](PROBAR_APP.md)** · **[Issue #1 (fijado)](https://github.com/aliamas021806-stack/citas_medicas/issues/1)**
>
> ℹ️ **¿Por qué Release y no el artifact de Actions?** Los *artifacts* de la pestaña
> **Actions** exigen tener cuenta en GitHub y vienen comprimidos en `.zip`. La **Release**
> es descarga pública, directa y sin login.

### 🔐 Verificación del APK publicado

```
Archivo    : CitasMedicas-v6.2.apk
Bytes      : 5 727 949  (5.5 MB)
SHA-256    : 4526dac5518af0617521046b2f58f76d107c474a4e58ed8890c35e0fd21d48ec
Firma      : v1 + v2 + v3 (keystore de release)
Cert SHA256: 3df1af33ce2aae8a300723a6509db9f061df334bad97d8a2e6ae485eff7ec5b4
Paquete    : com.tuempresa.citasmedicas
Versión    : 6.1 (versionCode 7)
minSdk     : 24 (Android 7.0+)   ·   targetSdk / compileSdk: 34

AAB        : CitasMedicas-v6.2.aab
Bytes      : 5 106 389  (4.9 MB)
SHA-256    : d95ae3ce90b15748230dbd27cb91c0c5ef36696ab1948c62aa5d0d18c41250e7
```

Para comprobar que la descarga no se ha corrompido:

```bash
# Windows (PowerShell)
Get-FileHash CitasMedicas-v6.2.apk -Algorithm SHA256

# macOS / Linux
shasum -a 256 CitasMedicas-v6.2.apk
```

El resultado debe empezar por `497dda5d…`.

> ⚠️ **Ojo con versiones anteriores.** El APK de la **v6.1** pesaba 5 727 949 bytes
> (hash `4526dac5…`). Si tu archivo pesa exactamente **5 742 195 bytes** y su hash
> empieza por `497dda5d…`, **es la v6.2**.
> Compara siempre el **tamaño y el hash juntos**, y confirma dentro de la app
> (Perfil → pestaña «Médicos» si eres admin, o «Mi Agenda» si eres médico).

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
 Firestore (REST)    Room (SQLite)
 + respaldo local    citas reales (offline)
```

- **Vista** → `Activity` / `Fragment` + `RecyclerView.Adapter`. **No conoce Room.**
- **ViewModel** → `LiveData` / `MutableLiveData`, envuelto en `Resource<T>`
  (`LOADING` / `SUCCESS` / `ERROR`). No construye repositorios: los pide al provider.
- **Domain** → casos de uso que dependen de las **interfaces** de `domain/repository`.
  No importa nada de `data.local` ni de Retrofit.
- **Repository** → implementa la interfaz correspondiente.
- **Model** → POJOs (`Doctor`, `Patient`, `Appointment`, `Specialty`, `TimeSlot`...).

### 🚑 «Soy médico pero no me aparecen mis citas» — diagnóstico

Es la consulta más frecuente y casi nunca es un problema de hora. La agenda del médico
filtra la colección `appointments` por el campo **`doctorId`**, y las reglas de Firestore
sólo autorizan esa consulta si tu documento `/users/{uid}` tiene un `doctorId` válido.
Ese campo se escribe **únicamente** al vincular una ficha de la colección `doctors`.

| Síntoma | Causa | Solución |
|---|---|---|
| «No se pudo leer tu agenda» (v6.2) o «No tienes citas» (v6.0 y antes) | No existe ninguna ficha en **`doctors`** con tu correo, así que la consulta recibe **403** | Que el administrador te dé de alta en **Médicos**, con el **mismo correo** con el que te registras |
| Tu ficha existe, pero está en la colección **`patients`** | Se creó en el sitio equivocado: la agenda sólo mira `doctors` | Bórrala de `patients` y créala desde la app (pestaña **Médicos**) o en la colección `doctors` |
| Ves «no tienes citas» y la cita existe | La cita tiene otro `doctorId`, o el `doctorId` de la cita no coincide con el de tu ficha | Revisa el campo `doctorId` de la cita en la consola: debe ser **exactamente** el id del documento de tu ficha en `doctors` |
| Acabas de ser dado de alta | El rol y el `doctorId` se resuelven **al iniciar sesión** | **Cierra sesión y vuelve a entrar** |

> 🕐 **La hora no es el problema.** La agenda trae **todas** las citas del médico y el
> filtro por día se hace en el móvil, así que una cita de hoy a las 12:00 aparece aunque
> sean las 18:46. Si no aparece, es por el `doctorId`, no por la hora.

**Comprobación rápida en la consola de Firebase** — los dos ids deben coincidir:

1. **Firestore → `doctors`** → abre tu ficha → apunta el **id del documento**.
2. **Firestore → `appointments`** → abre tu cita → el campo `doctorId` debe ser ese id.
3. **Firestore → `users`** → abre tu documento (el uid de Authentication) → debe tener
   `doctorId` con ese mismo id y `role: "doctor"`.

Si el paso 3 falla pero el 1 está bien, cierra sesión y entra otra vez: la vinculación
se escribe al iniciar sesión, no al dar de alta la ficha.

### 🔌 Implementación activa en la v6.2

Todo lo que decide qué backend se usa está concentrado en `data/RepositoryProvider`:

| Interfaz (dominio) | Implementación con Firebase activo | Respaldo si falla |
|---|---|---|
| `IAuthRepository` | `FirebaseAuthRepository` (Identity Toolkit) | `AuthRepository` (solo ante error de **configuración**) |
| `IDoctorRepository` | `FirestoreDoctorRepository` | `DoctorRepository` (mock Retrofit) |
| `IPatientRepository` | `FirestorePatientRepository` | `PatientRepository` (mock Retrofit) |
| `IAppointmentRepository` | `FirestoreAppointmentRepository` **+ Room** | Room siempre (funciona offline) |

**Para cambiar de backend basta el `return` de un método del provider.** Los
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

## 🔌 API simulada (Mock) — respaldo offline

> ℹ️ En la **v6.2** la fuente principal es **Firestore**. Esta API simulada se
> conserva como **red de seguridad**: si Firestore no responde (sin internet,
> cuota agotada o proyecto sin configurar), `FirestoreFallback` sirve estos datos
> para que la app siga siendo utilizable.

`MockInterceptor` (OkHttp) intercepta las peticiones y responde con datos generados
en `MockData.java`. Los **endpoints** disponibles son:

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
| Pruebas automatizadas | ✅ **294 tests · 0 fallos · 0 errores** |
| Android Lint | ✅ **0 errores** (sólo avisos de recursos sin usar y de layouts de tablet) |
| Archivos Kotlin (`.kt` / `.kts`) | ✅ **0** — proyecto 100% Java |
| APK release | 5.5 MB (5 742 195 bytes) · firmado · instalable |
| AAB release | 4.9 MB (5 115 268 bytes) |

### 🧪 Pruebas incluidas

| Clase | Tests | Qué cubre |
|---|---|---|
| `FirebaseRestTest` | 34 | **Capa Firebase por REST**: modo mock por defecto sin credenciales, mapeo de documentos de Firestore (enteros como texto, campos ausentes, rutas completas), construcción del JSON de escritura, y verificación de que **no se añade ningún SDK** (0 Kotlin) |
| `PortalMedicoTest` | 31 | **Portal del médico y alta de médicos (v6.0/v6.2)**: forma exacta del filtro `runQuery`, filtros por día, navegación de fechas, vinculación de la ficha por `uid` y por correo, y menús/pestañas según el rol |
| `BusinessLogicTest` | 23 | `AuthValidator` (email, contraseña, edad, login y registro), `AppointmentMapper` (edad preservada), `Doctor.getDisplayName()`, `User` |
| `MockDataTest` | 20 | Especialidades, doctores (edad/rating/especialidad), pacientes (edad, datos clínicos, IMC), horarios y fechas |
| `AutenticacionFirestoreTest` | 18 | Authentication real por REST: traducción de errores de Google al español, degradación segura ante fallo de configuración y **nunca** sesión regalada con credenciales incorrectas |
| `FaseCeroArquitecturaTest` | 17 | **Contratos para Firestore**: las 4 interfaces son públicas y bien formadas, el provider devuelve implementaciones usables, el mapeo modelo↔entidad no pierde campos, y **tests que congelan la arquitectura** (fallan si alguien vuelve a importar Room desde la UI o a instanciar repositorios en un ViewModel) |
| `AgendaPrioridadTest` | 18 | **Agenda del médico por prioridad (v6.2)**: pendientes y atendidas como grupos excluyentes, orden por hora, una cita pendiente de ayer sigue avisando, estados `Attended`/`NoShow` tolerantes a cómo se escriban en la consola, y **un 403 de Firestore se explica en pantalla en vez de disfrazarse de «no tienes citas»** |
| `DtoMappingTest` | 17 | Conversión DTO → modelo, valores ausentes, tipos envueltos y saneamiento |
| `SyncCitasFirestoreTest` | 15 | Sincronización de citas: se guarda en Room primero, se empuja a Firestore, se conserva `remote_id`, cancelación replicada y borrado remoto |
| `PantallasArrancanTest` | 15 | **Abre las pantallas de verdad** con el ciclo de vida completo: login, cambio a registro, login inválido, login válido → navegación, main con y sin sesión, las pestañas, detalle del doctor, confirmar sin horario, y **rotación** de pantalla |
| `ResponsiveLayoutTest` | 14 | Requisito de **tablet**: resuelve 1 columna en teléfono y 2 en tablet, espaciados escalados, e infla las pantallas en ambos tamaños verificando que no falte ningún ID |
| `FullFlowTest` | 13 | **Flujo completo**: sesión persistente, catálogo, horario disponible, agendar en Room, aparece en "Mis Citas", sobrevive a reabrir la app, cancelar cita y contadores |
| `DomainNavigationTest` | 13 | Casos de uso de dominio y navegación tipada (`Routes` / `Navigator`) |
| `PerfilYCitasPorUsuarioTest` | 12 | El perfil y las citas son **del usuario en sesión** (no del paciente de ejemplo `pat_01`), y las citas de un usuario no se mezclan con las de otro |
| `UiSmokeTest` | 9 | Robolectric: infla todos los layouts, arranca las Activities, resuelve **plurales** y verifica `SessionManager` |
| `NetworkPipelineTest` | 9 | Pipeline real Retrofit + `MockInterceptor` + `DtoMapper` |
| `AdaptacionesJavaTest` | 8 | Concurrencia de fechas (12 hilos × 300), singleton de `ApiClient`, validación de fecha pasada y equivalencia del orden cronológico |
| `CredencialesRealesFirestoreTest` | 5 | Comprobación de extremo a extremo de las credenciales reales del proyecto (`local.properties` / `firebase.properties`) |

**Total: 291 tests · 0 fallos · 0 errores**

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

### ✅ Portal del médico y alta de médicos — IMPLEMENTADO (v6.0)

La app ya distingue **tres roles** y cada uno ve una aplicación distinta. El rol no
viaja en el token (eso exigiría el Admin SDK), así que vive en el documento
`/users/{uid}` de Firestore y las reglas de seguridad lo leen con `get()`.

| Rol | Pestañas | Qué puede hacer |
|---|---|---|
| **Paciente** | Doctores · Mis Citas · Perfil | Reservar, ver y cancelar **sólo sus** citas |
| **Médico** | Mi Agenda · Perfil | Ver las citas **que le tocan** cada día |
| **Administrador** | Médicos · Perfil | **Dar de alta médicos** desde la app |

**Cómo entra un médico.** No se le crea una contraseña (sería un riesgo). El
administrador escribe su **correo** al darlo de alta; cuando el médico se registra en
la app con ese mismo correo, su cuenta queda vinculada a la ficha y pasa a ver su
agenda. Las reglas verifican esa vinculación: sólo se permite escribir el `userId` de
una ficha si el correo de la ficha coincide con el del token y nadie la había
reclamado antes. Un paciente no puede apropiarse de la ficha de otra persona.

**Cómo ve el médico sus citas del día.** La consulta filtra por `doctorId` con **una
sola igualdad** (sin índice compuesto, que es la causa habitual de que una consulta
devuelva 400 y la lista salga vacía sin error visible) y ordena por fecha y hora en
memoria. Tiene selector de día anterior/siguiente y, sin red, cae a lo guardado en el
teléfono.

#### 🆕 Novedades de la v6.2 — agenda por prioridad y registro con tipo de cuenta

**1. La agenda separa lo que falta de lo atendido.** Antes había una sola lista, así
que una cita ya atendida de las 09:00 empujaba hacia abajo la de las 12:00 que aún hay
que ver. Ahora:

| Sección | Qué muestra | Orden |
|---|---|---|
| **Pendientes de atender** | Todo lo que aún requiere decisión, **incluida una cita pendiente de ayer** | Día consultado primero, luego por hora ascendente |
| **Ya atendidas** | Sólo las del día que se está mirando | Por hora |

Dos estados nuevos, con botón en cada tarjeta pendiente:

- **`Attended`** — «Marcar como atendida». Una vez atendida sale del grupo de
  pendientes y ya no se puede volver a marcar.
- **`NoShow`** — «El paciente no acudió». Se queda en pendientes a propósito: una
  inasistencia exige una decisión (reprogramar o cerrar el hueco).

El cambio se envía a Firestore con un `PATCH` cuyo `updateMask` es **sólo `status`**;
sin esa máscara Firestore reemplazaría el documento entero y se perderían el paciente,
la fecha y el motivo. La regla de seguridad también lo limita a `status`/`reason`.

**2. El registro pregunta si eres paciente o médico.** El selector **no da el rol**
(eso lo resuelve Firestore al iniciar sesión, para que nadie pueda autoproclamarse
médico). Lo que hace es:

- Si eliges **Paciente**, no envía nada especial: la cuenta se crea como paciente.
- Si eliges **Médico**, intenta **vincular** la ficha que el administrador haya creado
  con ese mismo correo, para que no tengas que cerrar sesión y volver a entrar. Si no
  hay ficha, la cuenta **sí se crea** (es válida como paciente) y un aviso explica que
  hay que pedir el alta al administrador.

> ⚠️ **Un médico NO puede crear su propia ficha.** Se probó y se descartó: como la API
> key de Firebase es pública por diseño, cualquiera podría crearse una ficha con su
> propio correo y su propio `uid`, escribir el `doctorId` en su perfil y dejar de ser
> paciente. Eso rompería exactamente la separación médico/paciente que se quiere
> garantizar. El alta sigue siendo una acción de administración.

**3. Un rechazo de Firestore ya no se disfraza de «no tienes citas».** Este era el
fallo que se reportó: si la cuenta no está vinculada a ninguna ficha, las reglas
devuelven **403** a la consulta de agenda; la app caía al respaldo local vacío y
mostraba *«No tienes citas este día»*. El mensaje era cierto pero engañoso. Ahora la
pantalla distingue las dos situaciones:

| Lo que veías | Qué significaba realmente |
|---|---|
| «No tienes citas este día» | Firestore respondió **200** y no hay citas: es correcto |
| «No se pudo leer tu agenda» + motivo | Firestore respondió **403**: falta la vinculación con una ficha |

**Verificado en el emulador de Firestore (22/22 pruebas de reglas).** El
`rules-test/agenda_medico.test.js` comprueba contra las reglas reales que:
un médico ve **sólo** sus citas; **no** puede consultar la agenda de otro médico, ni
listar todas sin filtrar, ni leer o modificar una cita ajena, ni reasignarla, ni
cambiarle la fecha; un paciente **no** puede escribir `doctorId` en su perfil, ni
reclamar una ficha ya reclamada, ni crear fichas; y el administrador sí puede dar de
alta fichas y ver todas las citas.

```bash
cd rules-test
npx firebase emulators:exec --only firestore --project citas-medicas-e19ca \
    "node agenda_medico.test.js"
# RESULTADO: 22/22 pruebas de reglas correctas
```

📄 **Las reglas completas para publicar están en [`firestore.rules`](firestore.rules).**
Incluye las instrucciones para crear el **primer administrador** (paso manual, una
sola vez: si la app pudiera crear admins, cualquiera con la API key pública —que es
pública por diseño— se haría admin).

### ✅ Base de datos real con Firebase — IMPLEMENTADA

La app **ya está conectada** a Firebase (Authentication + Cloud Firestore) por REST,
manteniendo **Room** como caché y fuente de verdad offline. Así queda cada ámbito:

| Ámbito | Antes | Ahora (v6.0) |
|---|---|---|
| Autenticación | Simulada (valida formato) | ✅ **Firebase Authentication** (email/contraseña real) |
| Doctores, especialidades y pacientes | `MockData` + `MockInterceptor` | ✅ **Cloud Firestore** (`doctors`, `specialties`, `patients`) con respaldo local |
| Citas | Room local (SQLite) | ✅ **Firestore** (`appointments`) + Room como caché offline |
| Sincronización | — | ✅ Crear, **cancelar** y **borrar** se replican en Firestore |

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
- [ ] **Horarios:** hoy se calculan en local. Migrarlos requiere modelar la ocupación
      por doctor en Firestore.

#### ✅ Ya hecho (antes figuraba como pendiente)

- [x] **Borrado remoto:** al eliminar una cita se lanza un `DELETE` contra Firestore
      (`FirestoreAppointmentRepository.borrarEnFirestore`), además de borrarla en Room.
      Igual que la subida, es *best-effort*: si no hay red, la cita desaparece del
      teléfono y el fallo sólo queda en el log.
- [x] **Reglas de seguridad definitivas:** están publicadas y verificadas en
      [`firestore.rules`](firestore.rules) (26 pruebas contra el emulador en
      `rules-test/`). Ya **no** son `allow read, write: if true`: cada colección exige
      autenticación y el rol se comprueba contra `/users/{uid}`.
- [x] **Sin `orderBy()` junto a `where()`:** las consultas usan una sola igualdad y
      ordenan en memoria, así que no hacen falta índices compuestos.

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
