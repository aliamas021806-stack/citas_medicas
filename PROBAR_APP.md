# 📥 Guía para probar la app (profesor)

Esta guía explica cómo **instalar y probar** el Sistema de Citas Médicas sin
necesidad de Android Studio, sin emulador y sin conocimientos técnicos.

> 📌 Esta misma información está publicada como **issue fijado** del repositorio:
> **[Issue #1 — CÓMO PROBAR LA APP](https://github.com/aliamas021806-stack/citas_medicas/issues/1)**

---

## ⬇️ Paso 1 — Descargar el APK

**Descarga directa (no hace falta cuenta de GitHub):**

### 👉 [Descargar CitasMedicas-v6.2.apk](https://github.com/aliamas021806-stack/citas_medicas/releases/download/v6.2/CitasMedicas-v6.2.apk)

- Tamaño: **5.5 MB** (5 727 949 bytes)
- Versión: **6.2** (versionCode 8)
- Requisito: **Android 7.0 o superior** (API 24+)
- Firmado con certificado de release ✅

> 🆕 **Esta es la v6.2.** Corrige cinco fallos reportados con datos reales y añade la
> ficha profesional del médico. Detalle completo en el [README](README.md#-qué-arregla-la-v62).
> Las novedades de la v6.1 siguen aquí:
>
> 1. **La agenda del médico separa lo que falta de lo atendido**, con botones
>    «Marcar como atendida» y «El paciente no acudió».
> 2. **El registro pregunta si creas una cuenta de Paciente o de Médico.**
>
> Para confirmarlo dentro de la app: si eres **administrador** verás la pestaña
> **«Médicos»**; si eres **médico**, **«Mi Agenda»** con las dos secciones. Un paciente
> ve Doctores · Mis Citas · Perfil.

> ⚠️ **La v3.0 está obsoleta.** Es anterior a la integración de Firebase: no
> sincroniza las citas con la base de datos. Descarga siempre la **v6.2**.

> Si prefieres ver todas las versiones, entra en
> [Releases](https://github.com/aliamas021806-stack/citas_medicas/releases) o en la
> [página de la release v6.2](https://github.com/aliamas021806-stack/citas_medicas/releases/tag/v6.2).

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
adb install CitasMedicas-v6.2.apk
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
  antigua (v3.0 o anterior): desinstálala e instala la **v6.2**.
- Los contadores muestran **citas próximas** y **total**.
- 🆕 **Y confirma el rol**: si eres **administrador** debe aparecer la pestaña
  **«Médicos»** en la barra inferior; si eres **médico**, **«Mi Agenda»**.
  Si acabas de crear tu documento en `/users/{uid}` con `role: "admin"`, **cierra
  sesión y vuelve a entrar**: el rol se lee al iniciar sesión.

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
| **Ya hay backend real** | La v6.0 está conectada a **Firebase** (Authentication + Cloud Firestore). El catálogo de doctores y pacientes se lee de Firestore. |
| **La contraseña SÍ se valida en el servidor** | El login se resuelve contra Firebase Authentication. Si la cuenta no existe, la app lo dice. |
| **Internet** | Hace falta para **registrarse y entrar**. Ya dentro, la app funciona sin conexión: las citas se guardan en SQLite y el catálogo usa el respaldo local. |
| **Datos de ejemplo** | El sembrado de "Mis Citas" se hace una única vez, la primera vez que se abre la app. |
| **Keystore incluida** | El repositorio incluye la keystore de demostración para que CI pueda firmar el APK sin secretos. En producción nunca se subiría. |

> ✅ **Siguiente paso (ya hecho):** la base de datos real con
> **Firebase / Firestore** está integrada desde la v4.0, el **portal del médico**
> desde la v6.0 y la **agenda por prioridad** desde la v6.2. Para conectarla a tu propio proyecto, sigue
> **[CONECTAR_FIREBASE.md](CONECTAR_FIREBASE.md)**.

---

## 🔐 Verificar que el APK es el oficial

Si quieres comprobar que el archivo descargado es exactamente el que generó la CI:

**SHA-256** (APK de la release `v6.2`):

```
497dda5d608f44cb8e390058ad9e8a761de9453f71ca894e1323ef6edd3a5189
```

Tamaño exacto: **5 742 195 bytes** (5.5 MB).

Comprobar en Linux/macOS:

```bash
sha256sum CitasMedicas-v6.2.apk
```

En Windows (PowerShell):

```powershell
Get-FileHash CitasMedicas-v6.2.apk -Algorithm SHA256
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

**291 pruebas automatizadas · 0 fallos.** Incluyen pruebas que abren todas las
pantallas y recorren el flujo completo (login → agendar → ver cita → cancelar), las de
la capa Firebase por REST, las del **portal del médico** (31 en `PortalMedicoTest`) y
las de la **agenda por prioridad** (18 en `AgendaPrioridadTest`).

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

---

## 👨‍⚕️ Portal del médico y alta de médicos (novedad v6.0, ampliado en v6.2)

La app ahora tiene **tres roles** y cada uno ve una cosa distinta:

| Rol | Pestañas | Qué hace |
|---|---|---|
| **Paciente** | Doctores · Mis Citas · Perfil | Reserva y ve **sólo sus** citas |
| **Médico** | Mi Agenda · Perfil | Ve las citas **que le tocan** cada día |
| **Administrador** | Médicos · Perfil | **Da de alta médicos** sin tocar la base de datos |

### Paso 1 — Publicar las reglas (obligatorio)

Sin esto, el médico no verá sus citas y el alta de médicos dará error 403.

1. Abre **Firebase Console** → **Firestore Database** → pestaña **«Reglas»**.
2. Selecciona **todo** el texto y pega el contenido completo del archivo
   [`firestore.rules`](firestore.rules) del repositorio (reemplaza lo anterior).
3. Pulsa **«Publicar»**.

### Paso 2 — Crear el primer administrador (una sola vez, a mano)

Este paso **no se puede hacer desde la app a propósito**: si la app pudiera crear
administradores, cualquiera con la API key —que es pública por diseño— podría hacerse
admin y leer todos los expedientes.

1. En Firebase Console → **Authentication** → pestaña **Users**, copia tu **User UID**
   (una cadena larga de letras y números).
2. Ve a **Firestore Database** → **Datos** → colección **`users`**.
3. Crea el documento con **ID = tu UID** (pégalo tal cual) y estos campos:

   | Campo | Tipo | Valor |
   |---|---|---|
   | `role` | string | `admin` |
   | `email` | string | tu correo |
   | `fullName` | string | tu nombre |

4. Cierra sesión en la app y vuelve a entrar. Aparecerá la pestaña **«Médicos»**.

### Paso 3 — Dar de alta un médico

En la pestaña **«Médicos»** rellena: nombre, **correo del médico**, especialidad,
hospital, edad, años de experiencia y tarifa. Al guardar:

- La ficha se crea en la colección `doctors` y **aparece de inmediato** en la lista que
  ven los pacientes.
- La ficha queda marcada como **«Esperando que entre con su correo»**.

Cuando esa persona abra la app, pulse **Regístrate** y use **ese mismo correo**, su
cuenta se vincula a la ficha: el estado pasa a **«Cuenta vinculada»** y, al entrar,
aterriza directamente en **Mi Agenda** con las citas que le hayan reservado.

> ⚠️ **No le crees la cuenta tú.** El médico debe registrarse él mismo para elegir su
> contraseña. Crear contraseñas por otros es un riesgo de seguridad y además queda
> registrado en los logs.

### 🆕 v6.2 — Los cinco fallos que reportaste, corregidos

Todos compartían la misma raíz: al iniciar sesión la app **reescribía el documento de rol**
(`/users/{uid}`) con lo que creía saber, y cuando no lo sabía escribía `patient`.

| Lo que veías | Qué pasaba de verdad | Cómo comprobarlo en la v6.2 |
|---|---|---|
| «Entro como administrador y me registra como paciente» | Un error de permisos se leía como «no tienes perfil» y el perfil nuevo se creaba como `patient` | Entra con tu cuenta admin: **sigues viendo la pestaña Médicos** |
| El médico ve «no hay nada» y «sin conexión» con WiFi | Su perfil no tenía `doctorId` → la regla devolvía **403** y la app lo disfrazaba de red | La agenda del médico **carga desde Firestore** |
| Nombre del correo, edad **30**, teléfono vacío | El login sólo trae el correo; la edad se rellenaba con un `30` fijo | El perfil del médico muestra **su nombre y su edad reales** |
| Grupo sanguíneo y aseguradora en el perfil del médico | Se pintaba la tarjeta de paciente a todo el mundo | El médico ve **Ficha profesional**, no el expediente clínico |
| «En especialidad no se agrega al dar de alta un médico» | El `specialtyId` se derivaba del texto libre y no existía en `specialties` | La especialidad se **elige de una lista** |

**Nuevo: el médico completa su ficha.** En **Perfil → Completar mi ficha** rellena teléfono,
consultorio, reseña y tarifa. Antes no existía ese formulario y la ficha se quedaba a medias.

> ⚠️ **Requisito:** publica la [`firestore.rules`](firestore.rules) nueva en **Firebase
> Console → Firestore Database → Reglas**. Sin ese cambio el médico sigue recibiendo 403.

### 🆕 v6.1 — La agenda separa lo que falta de lo atendido

Al entrar como médico, **Mi Agenda** muestra dos secciones:

| Sección | Qué contiene | Orden |
|---|---|---|
| **Pendientes de atender** | Todo lo que aún requiere decisión, **incluida una cita pendiente de ayer** | Día consultado primero, luego por hora |
| **Ya atendidas** | Sólo las del día que estás mirando | Por hora |

Cada tarjeta pendiente tiene dos botones: **«Marcar como atendida»** (estado
`Attended`) y **«El paciente no acudió»** (estado `NoShow`, que sigue en pendientes
porque exige reprogramar o cerrar el hueco). El cambio se guarda en Firestore tocando
**sólo el campo `status`**, así no se pierden el paciente, la fecha ni el motivo.

### 🆕 v6.2 — El registro pregunta el tipo de cuenta

Al pulsar **Regístrate** verás un selector **Paciente / Médico**. Si eliges **Médico**,
además de crear la cuenta la app intenta **vincular** la ficha que el administrador creó
con ese correo, para que entres directo en **Mi Agenda**.

> ⚠️ **Si eliges «Médico» pero el administrador no te dio de alta con ese mismo correo**,
> verás el aviso *«Tu cuenta se ha creado, pero no hay ninguna ficha de médico con este
> correo»*. La cuenta existe y sirve como paciente; pide el alta y vuelve a entrar.
> Un médico **no puede crear su propia ficha**: eso permitiría a cualquiera
> autoproclamarse médico, porque la API key de Firebase es pública por diseño.

### ❓ «Soy médico y no me aparece mi cita» — las dos causas reales

| Lo que ves | Qué pasa | Solución |
|---|---|---|
| **«No se pudo leer tu agenda»** (v6.2) o «No tienes citas» (v6.0) | **No existe tu ficha en `doctors`**, así que Firestore devuelve 403 | Que el admin te dé de alta en **Médicos** con **tu mismo correo** |
| **«No tienes citas este día»** y la cita existe | El `doctorId` de la cita **no coincide** con el id de tu ficha | Abre la cita en Firestore: su `doctorId` debe ser el id del documento de tu ficha |

> 🕐 **No es un problema de hora.** La agenda trae todas las citas del médico y filtra
> por día en el móvil, así que una cita de hoy a las 12:00 se ve aunque sean las 18:46.

**Comprobación en 3 pasos en Firebase Console** (los ids deben coincidir):

1. **`doctors`** → tu ficha → apunta el **id del documento**.
2. **`appointments`** → tu cita → el campo `doctorId` debe ser ese id.
3. **`users`** → tu documento (uid de Authentication) → `doctorId` = ese id, `role: "doctor"`.

Si el paso 3 falla, **cierra sesión y vuelve a entrar**: la vinculación se escribe al
iniciar sesión, no al dar de alta la ficha.

> 🗂️ **Ojo con la colección.** Una ficha de médico creada por error en **`patients`** no
> sirve: la agenda sólo busca en **`doctors`**.

### Probar el flujo completo sin dos teléfonos

1. Con tu cuenta **admin**, da de alta un médico con un correo que controles
   (por ejemplo `medico.prueba@gmail.com`).
2. Cierra sesión y **regístrate** con ese correo: entrarás como médico en **Mi Agenda**
   (vacía, es correcto).
3. Cierra sesión, entra con **otra** cuenta de paciente, agenda una cita con ese médico.
4. Vuelve a entrar como médico: la cita aparece en el día correspondiente.
