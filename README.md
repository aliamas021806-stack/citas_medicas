# 🩺 Sistema de Citas Médicas

Aplicación Android **nativa en Java** para gestionar citas médicas, construida con
arquitectura **MVVM**, una **API local simulada (Mock)** 100% offline y persistencia
local con **Room (SQLite)**.

> Proyecto listo para **Android Studio** y con **GitHub Actions** que genera el APK
> automáticamente en cada push. No requiere backend real ni conexión a internet.

---

## 📋 Tabla de contenido

1. [Características](#-características)
2. [Descargar el APK desde GitHub](#-descargar-el-apk-desde-github-actions)
3. [Instalación en el teléfono](#-instalación-en-el-teléfono)
4. [Arquitectura](#-arquitectura-mvvm)
5. [Estructura del proyecto](#-estructura-del-proyecto)
6. [API simulada (Mock)](#-api-simulada-mock)
7. [Abrir y compilar en Android Studio](#-abrir-y-compilar-en-android-studio)
8. [Compilar por terminal](#-compilar-por-terminal)
9. [Tecnologías y versiones](#-tecnologías-y-versiones)
10. [Solución de problemas](#-solución-de-problemas)

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

## ⬇️ Descargar el APK desde GitHub Actions

El proyecto **compila el APK automáticamente** en la nube (no necesitas Android Studio).
Cada vez que se sube código, GitHub genera un APK listo para instalar.

### Paso a paso

1. Entra a tu repositorio en **GitHub**.
2. Haz clic en la pestaña **`Actions`** (barra superior).
3. En la lista de la izquierda verás el workflow **`Build APK`**.
   - 🟡 **Amarillo** = está compilando (espera ~3-5 min).
   - ✅ **Verde** = terminó bien, el APK está listo.
   - ❌ **Rojo** = hubo un error (abre el log para verlo).
4. Haz clic en la **ejecución con ✅ verde** (la más reciente, arriba).
5. Baja hasta la sección **`Artifacts`** al final de la página.
6. Descarga:
   - **`citasmedicas-release-apk`** → contiene **`app-release.apk`** (el instalable).
   - **`citasmedicas-release-aab`** → contiene el bundle para Google Play.
7. **Descomprime** el archivo descargado (GitHub entrega los artifacts en `.zip`).

> 💡 Atajo: también puedes ir a **Releases** o usar la ejecución manual desde
> `Actions` → `Build APK` → botón **`Run workflow`**.

---

## 📲 Instalación en el teléfono

1. Pasa el **`app-release.apk`** al teléfono (cable, Google Drive, WhatsApp, etc.).
2. Ábrelo. Android pedirá **"permitir instalar apps de fuentes desconocidas"** → actívalo.
3. Toca **Instalar**.

### ⚠️ Si sale "Aplicación no instalada"

Este APK está firmado con una clave de **release** propia (`citasmedicas-release.jks`).
Si ya tenías instalada una versión anterior **con otra firma**, Android la rechaza por
conflicto de firmas. Solución:

> **Desinstala primero la app anterior** (ajustes → Aplicaciones → Citas Médicas →
> Desinstalar), y vuelve a instalar el APK nuevo.

A partir de ahí, las actualizaciones futuras se instalarán **encima** sin problemas,
porque todas usan la misma firma (versión actual del repo: `versionCode 2`).

---

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

---

## 🖥️ Abrir y compilar en Android Studio

1. Abre **Android Studio** (versión reciente, con **JDK 17 o superior**).
2. `File` → `Open...` y selecciona la carpeta del proyecto (donde está `settings.gradle`).
3. Espera a que termine el **Gradle Sync**.
4. Conecta un teléfono (o crea un emulador) y pulsa **Run ▶**.

> Si Android Studio pide la ubicación del SDK, se configura solo. No hace falta
> `local.properties` en el repositorio (está en `.gitignore` a propósito).

---

## ⌨️ Compilar por terminal

Requisitos: **JDK 17+** (el CI usa **JDK 21**) y el **Android SDK** configurado.

```bash
# APK de depuración
./gradlew assembleDebug

# APK de release (firmado)
./gradlew assembleRelease

# Bundle para Google Play
./gradlew bundleRelease
```

Los archivos generados aparecen en:

```
app/build/outputs/apk/release/app-release.apk
app/build/outputs/bundle/release/app-release.aab
```

---

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

## 🛠️ Solución de problemas

### El workflow de Actions falla al descargar Gradle
Asegúrate de que **`gradle/wrapper/gradle-wrapper.jar`** se subió **íntegro** (es un
binario). El archivo **`.gitattributes`** del repositorio existe precisamente para
protegerlo. **Nunca** edites ese JAR ni lo subas arrastrando archivos por la web de
GitHub; usa `git push` o Android Studio.

### "Aplicación no instalada" en el teléfono
Desinstala la versión previa (conflicto de firmas) e instala el APK nuevo. Ver
[Instalación en el teléfono](#-instalación-en-el-teléfono).

### El APK pesa poco / no abre
Descarga el APK **desde la sección `Artifacts`** de una ejecución **verde**, no desde
"Code" ni desde los Assets de un release vacío.

### Gradle no encuentra el SDK
Crea `local.properties` en la raíz (solo en tu PC, no se sube) con:
```properties
sdk.dir=/ruta/a/tu/Android/Sdk
```

---

## 🔐 Nota sobre la firma

> ⚠️ La keystore **`citasmedicas-release.jks`** y **`key.properties`** incluidas son de
> **demostración**, pensadas para que GitHub Actions genere un APK instalable sin
> configurar secretos. **En un proyecto de producción real** NO deben subirse al
> repositorio: guárdalas de forma segura y defínelas como **GitHub Secrets**.

---

## 📄 Licencia

Proyecto con fines educativos.
