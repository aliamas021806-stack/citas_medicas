# 📥 Guía para probar la app (profesor)

Esta guía explica cómo **instalar y probar** el Sistema de Citas Médicas sin
necesidad de Android Studio, sin emulador y sin conocimientos técnicos.

> 📌 Esta misma información está publicada como **issue fijado** del repositorio:
> **[Issue #1 — CÓMO PROBAR LA APP](https://github.com/aliamas021806-stack/citas_medicas/issues/1)**

---

## ⬇️ Paso 1 — Descargar el APK

**Descarga directa (no hace falta cuenta de GitHub):**

### 👉 [Descargar CitasMedicas-v3.0.apk](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v3.0/CitasMedicas-v3.0.apk)

- Tamaño: **5.4 MB** (5 631 143 bytes)
- Versión: **3.0**
- Requisito: **Android 7.0 o superior** (API 24+)
- Firmado con certificado de release ✅

> Si prefieres ver todas las versiones, entra en
> [Releases](https://github.com/aliamas021806-stack/citas_medicas/releases) o en la
> [página de la release v3.0](https://github.com/aliamas021806-stack/citas_medicas/releases/tag/v3.0).

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
adb install CitasMedicas-v3.0.apk
```

---

## 🔑 Paso 3 — Iniciar sesión

La app **no necesita internet** ni crear una cuenta real: la autenticación es
simulada y valida el formato de los datos.

| Campo | Qué escribir |
|---|---|
| **Correo** | Cualquier correo con formato válido, por ejemplo `profesor@universidad.edu` |
| **Contraseña** | Cualquier texto de **4 caracteres o más**, por ejemplo `1234` |

Para probar también el **registro** (con nombre y edad):
toca el texto azul de abajo del formulario, **"¿No tienes cuenta? Regístrate"**.
Aparecerán los campos de **nombre** y **edad**, que son obligatorios en ese modo.

---

## 🧪 Paso 4 — Recorrido recomendado para la evaluación

Todo lo que sigue funciona **sin conexión a internet**.

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
  paciente** (grupo sanguíneo, teléfono, seguro, alergias) que llega desde la API simulada.
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
| **No hay backend real** | Los datos de doctores y pacientes son sintéticos y los genera un interceptor de red local (`MockInterceptor`). El stack Retrofit + OkHttp + Gson es real; solo se sustituye la respuesta. |
| **Sin conexión** | La app funciona 100 % offline a propósito. |
| **La contraseña no se valida contra un servidor** | Solo se comprueba el formato. Es una autenticación simulada. |
| **Datos de ejemplo** | El sembrado de "Mis Citas" se hace una única vez, la primera vez que se abre la app. |
| **Keystore incluida** | El repositorio incluye la keystore de demostración para que CI pueda firmar el APK sin secretos. En producción nunca se subiría. |

> 🚧 **Siguiente paso previsto:** conectar una base de datos real
> (**Firebase / Firestore**) para sustituir los datos simulados por datos
> persistentes en la nube con autenticación real.

---

## 🔐 Verificar que el APK es el oficial

Si quieres comprobar que el archivo descargado es exactamente el que generó la CI:

**SHA-256** (versión 3.0, APK publicado en la release `v3.0`):

```
dc2de6f64d2031e0a73a4edc0b4a53eab71707dc9650c440185e41dc807340b7
```

Tamaño exacto: **5 631 143 bytes** (5.4 MB).

Comprobar en Linux/macOS:

```bash
sha256sum CitasMedicas-v3.0.apk
```

En Windows (PowerShell):

```powershell
Get-FileHash CitasMedicas-v3.0.apk -Algorithm SHA256
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
| API simulada (Retrofit + Mock) | `data/remote/` |
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
