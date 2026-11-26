# 📥 Guía para probar la app (profesor)

Esta guía explica cómo **instalar y probar** el Sistema de Citas Médicas sin
necesidad de Android Studio, sin emulador y sin conocimientos técnicos.

> 📌 Esta misma información está publicada como **issue fijado** del repositorio:
> **[Issue #1 — CÓMO PROBAR LA APP](https://github.com/aliamas021806-stack/citas_medicas/issues/1)**

---

## ⬇️ Paso 1 — Descargar el APK

**Descarga directa (no hace falta cuenta de GitHub):**

### 👉 [Descargar CitasMedicas-v5.0.apk](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v5.0/CitasMedicas-v5.0.apk)

- Tamaño: **5.4 MB** (5 659 699 bytes)
- Versión: **5.0** (versionCode 5)
- Requisito: **Android 7.0 o superior** (API 24+)
- Firmado con certificado de release ✅

> ⚠️ **La v3.0 está obsoleta.** Es anterior a la integración de Firebase: no
> sincroniza las citas con la base de datos. Descarga siempre la **v5.0**.

> Si prefieres ver todas las versiones, entra en
> [Releases](https://github.com/aliamas021806-stack/citas_medicas/releases) o en la
> [página de la release v5.0](https://github.com/aliamas021806-stack/citas_medicas/releases/tag/v5.0).

---

## 📲 Paso 2 — Instalar en el teléfono

1. Descarga el archivo `.apk` en el teléfono (puedes abrir este README desde el
   móvil y tocar el enlace de arriba).
2. Al abrirlo, Android pedirá permiso para **"Instalar apps desconocidas"**.
   Acepta para el navegador o el gestor de archivos que estés usando.
3. Toca **Instalar** y luego **Abrir**.

### Si Android te avisa que "la app no es segura"

Es normal en cualquier APK que no venga de Play Store. El aviso aparece porque la
app está firmada con un certificado propio del proyecto, no con uno de Google.
Toca **"Instalar de todas formas"**.

### Instalación desde el ordenador (opcional)

Con el teléfono conectado por USB y la *Depuración USB* activada:

```bash
adb install CitasMedicas-v5.0.apk
```

---

## 🔑 Paso 3 — Crear la cuenta e iniciar sesión

> ⚠️ **Cambio importante respecto a la v3.0.** Esta versión sí está conectada a
> Firebase, así que el login **valida de verdad**: **no** entra con un correo
> inventado. Si escribes uno que no existe, la app responde
> *«No hay ninguna cuenta con ese correo»*.

**Lo más rápido: regístrate primero.**

1. En la pantalla de login, toca el texto azul **«¿No tienes cuenta? Regístrate»**.
2. Rellena los campos. En modo registro hay dos más, obligatorios:

   | Campo | Qué escribir |
   |---|---|
   | **Nombre** | Tu nombre (ej. `Profesor`) |
   | **Edad** | Un número (ej. `40`) |
   | **Correo** | Un correo con formato válido (ej. `profesor@universidad.edu`) |
   | **Contraseña** | **Mínimo 6 caracteres** (ej. `123456`) — Firebase rechaza menos |

3. Pulsa **Registrarse**. Entrarás directamente y la cuenta quedará creada en
   Firebase Authentication.
4. La próxima vez, ya puedes usar el modo **login** con ese mismo correo y contraseña.

> 💡 **¿Sin internet?** Si Firebase no responde por un problema de *configuración*
> del proyecto, la app recurre a la validación local para que puedas seguir
> probando. Pero **si las credenciales son incorrectas, NO deja pasar**: es
> intencionado, para no abrir un agujero de seguridad.

---

## 🧪 Paso 4 — Recorrido recomendado para la evaluación

Una vez has iniciado sesión, todo lo que sigue funciona **aunque te quedes sin conexión**.

### 1. Pantalla de Doctores (inicio)
- Se muestran las **8 especialidades** en una fila horizontal y la **lista de doctores**.
- Cada doctor muestra nombre, especialidad, **valoración**, **edad**, hospital y tarifa.
- Prueba el **buscador**: escribe `cardio` o el nombre de un doctor.
- Toca una **especialidad** para filtrar la lista.

### 2. Agendar una cita
- Toca un doctor → se abre el **detalle** con su ficha, biografía, rating y edad.
- Elige una **fecha** (carrusel horizontal) y un **horario** de los disponibles.
  Los horarios no disponibles aparecen tachados y deshabilitados.
- Escribe el **motivo** de la consulta (opcional).
- Toca **Confirmar cita** → mensaje de confirmación y vuelve atrás.

### 3. Mis Citas
- En la barra inferior, toca **Mis Citas**.
- Verás las citas guardadas en el teléfono (**Room / SQLite**), incluidas
  **3 citas de ejemplo** que se crean la primera vez que se abre la app.
- Cada tarjeta muestra doctor, especialidad, **edad del paciente**, fecha, hora y estado.
- Puedes **cancelar** una cita con el botón rojo; el estado cambia a *Cancelled*.

### 4. Perfil
- Toca **Perfil** para ver tus datos, tus iniciales, tu edad y el **expediente del
  paciente** (grupo sanguíneo, teléfono, seguro, alergias).
- **Aquí puedes comprobar la versión:** bajo tu correo hay una etiqueta gris.
  Debe decir **«Conectado a Firebase (citas-medicas-e19ca)»**.
  Si dice **«Datos de prueba (sin base de datos)»**, tienes instalada una versión
  antigua (v3.0 o anterior): desinstálala e instala la **v5.0**.
- Los contadores muestran **citas próximas** y **total**.

### 5. Persistencia (punto importante)
- Cierra la app por completo y vuelve a abrirla: **la sesión y las citas se conservan**.
- Usa **Cerrar sesión** para volver al login.

### 6. Responsive: teléfono y tablet
- En un **teléfono** la interfaz es de una columna.
- En una **tablet** (o girando a horizontal en pantallas grandes) las pantallas pasan
  a **dos paneles** y las listas a **2 columnas**.
- **Gira el teléfono** en cualquier pantalla: no se pierde nada y el título de la
  barra superior se mantiene.

---

## ⚠️ Cosas que conviene saber (transparencia)

| Punto | Detalle |
|---|---|
| **Ya hay backend real** | La v5.0 está conectada a **Firebase** (Authentication + Cloud Firestore). El catálogo de doctores y pacientes se lee de Firestore. |
| **La contraseña SÍ se valida en el servidor** | El login se resuelve contra Firebase Authentication. Si la cuenta no existe, la app lo dice. |
| **Internet** | Hace falta para **registrarse y entrar**. Ya dentro, la app funciona sin conexión: las citas se guardan en SQLite y el catálogo usa el respaldo local. |
| **Datos de ejemplo** | El sembrado de "Mis Citas" se hace una única vez, la primera vez que se abre la app. |
| **Keystore incluida** | El repositorio incluye la keystore de demostración para que CI pueda firmar el APK sin secretos. En producción nunca se subiría. |

> ✅ **Siguiente paso (ya hecho):** la base de datos real con
> **Firebase / Firestore** está integrada desde la v4.0. Para conectarla a tu propio
> proyecto, sigue **[CONECTAR_FIREBASE.md](CONECTAR_FIREBASE.md)**.

---

## 🔐 Verificar que el APK es el oficial

Si quieres comprobar que el archivo descargado es exactamente el que generó la CI:

**SHA-256** (versión 5.0, APK publicado en la release `v5.0`):

```
c11be226dac0d5e2f0e4d6a574614c99c7a0dfe5c61a467a90ac643dfc5d6b5b
```

Tamaño exacto: **5 659 699 bytes** (5.4 MB).

Comprobar en Linux/macOS:

```bash
sha256sum CitasMedicas-v5.0.apk
```

En Windows (PowerShell):

```powershell
Get-FileHash CitasMedicas-v5.0.apk -Algorithm SHA256
```

Los dos valores deben coincidir.

### Firma del certificado

```
CN=Citas Medicas, OU=Mobile, O=TuEmpresa, L=Madrid, ST=Madrid, C=ES
SHA-256: 3df1af33ce2aae8a300723a6509db9f061df334bad97d8a2e6ae485eff7ec5b4
```

---

## 🛠️ Para revisar el código

> **Nota de arquitectura:** el dominio declara **interfaces** (`domain/repository/`)
> y la infraestructura las implementa. La implementación concreta se elige en un solo
> archivo, `data/RepositoryProvider.java`. Gracias a eso, la capa de presentación
> **no conoce Room** (usa `model/Appointment`) y conectar un backend real como
> Firestore no obligará a tocar pantallas ni ViewModels. Hay 17 tests
> (`FaseCeroArquitecturaTest`) que verifican y **congelan** esa separación.

| Quiero ver… | Dónde está |
|---|---|
| Arquitectura MVVM | `app/src/main/java/com/tuempresa/citasmedicas/` |
| Vistas (Activities / Fragments) | `view/` |
| Lógica de presentación (ViewModels) | `viewmodel/` |
| Casos de uso (Clean Architecture) | `domain/usecase/` |
| **Contratos de repositorio (interfaces)** | `domain/repository/` |
| **Raíz de composición (qué implementación se usa)** | `data/RepositoryProvider.java` |
| Implementaciones de repositorio | `data/repository/` |
| **Firebase por REST (Auth + Firestore)** | `data/firebase/` |
| API simulada (Retrofit + Mock) | `data/remote/` y `data/mock/` |
| Base de datos local (Room) | `data/local/` |
| Navegación tipada | `navigation/` |
| Tema, colores y tamaños (Material 3) | `res/values/` |
| Versión para tablet | `res/values-sw600dp/` y `res/layout-sw600dp/` |
| Pruebas automatizadas | `app/src/test/java/com/tuempresa/citasmedicas/` |

### Ejecutar los tests

```bash
./gradlew testDebugUnitTest
```

**158 pruebas automatizadas · 0 fallos.** Incluyen pruebas que abren todas las
pantallas y recorren el flujo completo (login → agendar → ver cita → cancelar).

### Compilar el APK desde cero

```bash
# Requiere JDK 17+ y Android SDK 34
echo "sdk.dir=/ruta/a/Android/Sdk" > local.properties
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`.

---

## ❓ Problemas frecuentes

| Problema | Solución |
|---|---|
| **"App no instalada"** | Desinstala una versión anterior de `Citas Medicás` y vuelve a instalar, o revisa que el teléfono tenga Android 7.0+. |
| **"Análisis del paquete falló"** | El archivo se descargó incompleto. Bórralo y descárgalo otra vez; compara el SHA-256. |
| **No aparece "Instalar apps desconocidas"** | Ajustes → Apps → Acceso especial → Instalar apps desconocidas → elige tu navegador. |
| **La app se ve con una sola columna en la tablet** | Es correcto en pantallas pequeñas; a partir de 600 dp de ancho se activa la vista de dos paneles. |
| **No hay citas en "Mis Citas"** | Solo se crean la primera vez. Desinstala y reinstala para volver a verlas, o agenda una nueva. |
