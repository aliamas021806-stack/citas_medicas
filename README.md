
Alissa Milagro Alvarado Suárez: Gestión de GitHub, control de versiones y administración del repositorio.

Alejandra Constanza Cabeza Molina: Creación y diseño del prompt, aportando su conocimiento especializado sobre el manejo y estructura de historias médicas.

Erick Limber Mozombite Perez:  Búsqueda de información de apoyo, verificación lógica y testeo del código.

Joel Renato Caro Saldaña: Búsqueda de información, verificación y testeo del código.

Jesus Abdel Gonzales Quintanilla: Búsqueda de información, verificación y testeo del código.

Yuli Estefani Rimarachin Ortiz: Búsqueda de información, verificación y testeo del código.


# 🩺 Sistema de Citas Médicas

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

- LINK DE DESCARGA

 [⬇️ Descargar APK (Android)](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v2.0/CitasMedicas-v2.0.apk)
 [⬇️ Descargar AAB (Google Play)](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v2.0/CitasMedicas-v2.0.aab)

## 🏗️ Arquitectura (MVVM)

```
   UI (Activity / Fragment)          ←  Vista
            │  observa LiveData
            ▼
      ViewModel (LiveData)           ←  Lógica de presentación
            │
            ▼
        Repository                   ←  Fuente de datos
        ┌───┴─────────────────┐
        ▼                     ▼
   API Mock (Retrofit)    Room (SQLite)
   datos falsos offline   citas guardadas
```

- **Vista** → `Activity` / `Fragment` + `RecyclerView.Adapter`.
- **ViewModel** → `LiveData` / `MutableLiveData`, envuelto en `Resource<T>`
  (`LOADING` / `SUCCESS` / `ERROR`).
- **Repository** → accede a la API mock y a Room.
- **Model** → POJOs (`Doctor`, `Patient`, `Appointment`, `Specialty`, `TimeSlot`...).

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
| Pruebas unitarias | ✅ **51 tests · 0 fallos · 0 errores** |
| Android Lint | ✅ **0 problemas** (`No issues found`) |
| Archivos Kotlin (`.kt` / `.kts`) | ✅ **0** — proyecto 100% Java |
| APK release | 5.3 MB · firmado · instalable |
| AAB release | 4.8 MB |

### 🧪 Pruebas incluidas

| Clase | Tests | Qué cubre |
|---|---|---|
| `MockDataTest` | 20 | Especialidades, doctores (edad/rating/especialidad), pacientes (edad, datos clínicos, IMC), horarios y fechas |
| `BusinessLogicTest` | 23 | `AuthValidator` (email, contraseña, edad, login y registro), `AppointmentMapper` (edad preservada), `Doctor.getDisplayName()`, `User` |
| `UiSmokeTest` | 8 | Robolectric: infla los **11 layouts**, arranca las **3 Activities**, resuelve **plurales** y verifica `SessionManager` |

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
