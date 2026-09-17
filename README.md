
Alissa Milagro Alvarado Suárez: Gestión del repositorio en GitHub, control de versiones, fusiones de código (merges) y despliegue.

Alejandra Constanza Cabeza Molina: Diseño y optimización del prompt, además del análisis funcional y experto sobre el flujo de las historias médicas.

Joel Renato Caro Saldaña: Desarrollo de api y construcción de la lógica de programación principal del sistema.

Jesus Abdel Gonzales Quintanilla: Diseño, estructuración y  esquemas de almacenamiento de la información.

Erick Limber Mozombite Perez: Desarrollo del Frontend e implementación visual de la interfaz de usuario.

Yuli Estefani Rimarachin Ortiz: Documentación técnica del proyecto, redacción de manuales de usuario y estructuración de informes de entrega.


# 🩺 Citas Médicas — Sistema de Citas Médicas (Android / Java)

Aplicación Android nativa desarrollada en **Java puro** con arquitectura **MVVM**, **API Mock local** y persistencia con **Room (SQLite)**. No requiere backend real: funciona 100% offline.

---

## 📥 Descargar la APK (instalable)

> **Descarga directa (sin cuenta de GitHub):**
>
> ### 👉 [**Descargar CitasMedicas-v1.0.apk**](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v1.0/CitasMedicas-v1.0.apk)

También disponible el App Bundle para Play Store:
👉 [CitasMedicas-v1.0.aab](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v1.0/CitasMedicas-v1.0.aab)

Página de la versión: **[Releases v1.0](https://github.com/aliamas021806-stack/citas_medicas/releases/tag/v1.0)**

---

## 📲 Cómo instalar la APK en el teléfono

1. **Descarga** el archivo `CitasMedicas-v1.0.apk` en el teléfono (o pásalo por USB/WhatsApp/Drive).
2. Abre el archivo con el gestor de archivos del teléfono.
3. Si aparece el aviso **"Instalar apps desconocidas"**, actívalo para el navegador/gestor de archivos:
   - *Ajustes → Aplicaciones → Acceso especial → Instalar apps desconocidas*.
4. Toca **Instalar** → **Abrir**.
5. La app aparece como **"Citas Médicas"** en el cajón de aplicaciones.

> ⚠️ **Nota:** al ser una APK firmada con clave de *debug*, algunos teléfonos muestran una advertencia de seguridad. Es normal en compilaciones de desarrollo; basta con aceptar e instalar.

**Requisitos:** Android 7.0 (API 24) o superior.

---

## ✨ Funcionalidades

- 🔐 **Login / Registro simulado** (incluye campo de **edad**)
- 🏥 **Listado de especialidades y doctores** (con edad y valoración)
- 👨‍⚕️ **Detalle del doctor** + selección de **fecha y horario** disponibles
- 📋 **"Mis Citas Médicas"**: crear, ver y cancelar citas (persistencia local con Room)
- 👤 **Perfil del paciente** con expediente (edad, grupo sanguíneo, alergias, aseguradora…)

### 🎂 Edad del paciente generada por IA

Los datos del paciente (incluida su **edad**) se generan de forma aleatoria en la **API Mock** y se exponen mediante los endpoints:

```
GET  .../api/patients        → lista de pacientes (con su edad)
GET  .../api/patients/{id}   → paciente por id (con su edad)
```

La edad se visualiza en el **detalle del doctor** (al agendar), en **"Mis Citas Médicas"** (`🎂 Paciente: Nombre · NN años`) y en el **perfil**.

---

## 🏗️ Arquitectura

```
View (Activity / Fragment)
        │  observa LiveData
        ▼
ViewModel (Auth, Doctor, Patient, Appointment)
        │
        ▼
Repository  ──────────────►  Retrofit + OkHttp MockInterceptor  (API Mock → MockData)
        │
        └───────────────►  Room (SQLite)  →  citas locales persistentes
```

### Estructura de paquetes

```
app/src/main/java/com/tuempresa/citasmedicas/
├── data/
│   ├── local/        → Room: AppDatabase, AppointmentDao, AppointmentEntity
│   ├── mock/         → MockData (especialidades, doctores, pacientes IA, horarios)
│   ├── remote/       → MedicalApiService, MockInterceptor, ApiClient
│   └── repository/   → Auth, Doctor, Patient, Appointment
├── model/            → Specialty, Doctor, Patient, TimeSlot, Appointment, User…
├── util/             → Resource, SessionManager, AppointmentMapper
├── view/
│   ├── auth/         → LoginActivity
│   ├── main/         → MainActivity, DoctorsFragment, ProfileFragment + adapters
│   └── appointment/  → DoctorDetailActivity, CitasFragment + adapters
└── viewmodel/        → Auth, Doctor, Patient, Appointment ViewModels
```

---

## 🛠️ Tecnologías

| Componente | Versión |
|---|---|
| Lenguaje | **Java** (sin Kotlin) |
| JDK | **21** |
| Gradle | 8.7 |
| Android Gradle Plugin | 8.5.2 |
| compileSdk / targetSdk | 34 / 34 |
| minSdk | 24 (Android 7.0) |
| Arquitectura | MVVM + LiveData |
| Red | Retrofit 2.9 + OkHttp 4.12 (MockInterceptor) |
| Persistencia | Room 2.6.1 (SQLite) |
| UI | Material Design 3, RecyclerView, ViewBinding |

---

## 🚀 Compilar el proyecto

```bash
# Clonar
git clone https://github.com/aliamas021806-stack/citas_medicas.git
cd citas_medicas

# Compilar (requiere JDK 21 y Android SDK)
./gradlew assembleDebug        # APK de debug
./gradlew assembleRelease      # APK de release
./gradlew bundleRelease        # App Bundle
```

O ábrelo directamente en **Android Studio** → *Sync Gradle* → *Run*.

---

## ⚙️ Integración continua (GitHub Actions)

Cada push a `main` compila automáticamente la **APK** y el **AAB** de release.
Los artefactos se descargan desde la pestaña **Actions** del repositorio.

---

## 👤 Autor

**aliamas021806-stack** — Proyecto académico
