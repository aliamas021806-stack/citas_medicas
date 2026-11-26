#!/usr/bin/env python3
"""
Verifica que tu base de datos de Firestore responde y MUESTRA qué colecciones
tiene y qué campos usa cada una.

Uso:
    python3 tools/verificar_firebase.py
    python3 tools/verificar_firebase.py --project mi-proyecto --key AIzaSy...

Lee los datos de local.properties si no se pasan por argumento.
No escribe nada: sólo consulta y reporta.
"""

import argparse
import json
import os
import ssl
import sys
import urllib.error
import urllib.request

NOMBRE_PROPIEDADES = "local.properties"
TIMEOUT = 20


# ─────────────────────────── lectura de credenciales ───────────────────────────

def leer_propiedades():
    valores = {}
    if not os.path.isfile(NOMBRE_PROPIEDADES):
        return valores
    with open(NOMBRE_PROPIEDADES, "r", encoding="utf-8") as f:
        for linea in f:
            linea = linea.strip()
            if not linea or linea.startswith("#") or "=" not in linea:
                continue
            clave, valor = linea.split("=", 1)
            valores[clave.strip()] = valor.strip()
    return valores


# ─────────────────────────── llamadas REST ───────────────────────────

def peticion(url, cuerpo=None):
    """GET (cuerpo=None) o POST. Devuelve (codigo, json_o_texto)."""
    datos = None
    cabeceras = {"Accept": "application/json"}
    if cuerpo is not None:
        datos = json.dumps(cuerpo).encode("utf-8")
        cabeceras["Content-Type"] = "application/json"

    peticion_http = urllib.request.Request(url, data=datos, headers=cabeceras,
                                           method="POST" if datos else "GET")
    contexto = ssl.create_default_context()
    try:
        with urllib.request.urlopen(peticion_http, timeout=TIMEOUT,
                                    context=contexto) as respuesta:
            texto = respuesta.read().decode("utf-8")
            try:
                return respuesta.status, json.loads(texto)
            except json.JSONDecodeError:
                return respuesta.status, texto
    except urllib.error.HTTPError as e:
        texto = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(texto)
        except json.JSONDecodeError:
            return e.code, texto
    except urllib.error.URLError as e:
        return 0, f"No se pudo conectar: {e.reason}"
    except Exception as e:  # noqa: BLE001 - reportamos cualquier fallo de red
        return 0, f"Error inesperado: {e}"


def listar_colecciones(project_id, api_key, database_id="(default)"):
    """
    Operación ADMINISTRATIVA.

    Ojo: listCollectionIds exige permisos de propietario y NO pasa por las
    Reglas de Firestore. Por eso devuelve 403 aunque las reglas estén bien
    puestas, y no sirve para saber si la app puede leer. Se usa sólo como
    pista secundaria.
    """
    url = ("https://firestore.googleapis.com/v1/projects/" + project_id
           + "/databases/" + database_id
           + "/documents:listCollectionIds?key=" + api_key)
    return peticion(url, cuerpo={})


def listar_documentos(project_id, api_key, coleccion, database_id="(default)"):
    """
    Lectura normal de una colección. ESTA sí respeta las Reglas de Firestore,
    así que es la que dice la verdad sobre si la app podrá leer los datos.
    """
    url = ("https://firestore.googleapis.com/v1/projects/" + project_id
           + "/databases/" + database_id + "/documents/" + coleccion
           + "?key=" + api_key + "&pageSize=3")
    return peticion(url)


def comprobar_lectura(project_id, api_key, database_id="(default)"):
    """
    Comprueba si las REGLAS dejan leer, que es lo que de verdad importa.

    Devuelve (codigo, colecciones_que_responden, fallo_representativo).
    Se prueba el catálogo real de la app; si ninguna colección responde, se
    devuelve el primer error para poder explicarlo.
    """
    colecciones = ["specialties", "doctors", "patients", "appointments"]
    disponibles = []
    primer_fallo = None
    for coleccion in colecciones:
        codigo, cuerpo = listar_documentos(project_id, api_key, coleccion, database_id)
        if codigo == 200:
            disponibles.append(coleccion)
        elif primer_fallo is None:
            primer_fallo = (codigo, cuerpo)
    if disponibles:
        return 200, disponibles, None
    if primer_fallo is None:
        return 0, [], "Sin respuesta"
    return primer_fallo[0], [], primer_fallo[1]


def comprobar_auth(api_key):
    """
    Comprueba si el proveedor Correo/contraseña está habilitado.

    Se llama a signInWithPassword con un usuario que NO existe, a propósito:
    así distinguimos los dos estados sin crear cuentas basura en el proyecto.

      USER_NOT_FOUND        -> el proveedor SÍ está activo (rechaza por usuario)
      INVALID_LOGIN_CREDENTIALS -> el proveedor SÍ está activo (rechaza por clave)
      CONFIGURATION_NOT_FOUND   -> el proveedor NO está habilitado
      API_KEY_INVALID           -> la clave no es de este proyecto
    """
    url = ("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"
           "?key=" + api_key)
    cuerpo = {
        "email": "comprobacion.estado@example.invalid",
        "password": "SoloComprobar1234",
        "returnSecureToken": True,
    }
    return peticion(url, cuerpo=cuerpo)


def explicar_auth(codigo, cuerpo, project_id=""):
    """Devuelve (esta_habilitado, texto_explicativo)."""
    texto = json.dumps(cuerpo) if not isinstance(cuerpo, str) else cuerpo
    minus = texto.lower()

    if codigo == 0:
        return False, "Sin conexión: no se pudo comprobar Authentication."

    if "configuration_not_found" in minus:
        enlace = ("https://console.firebase.google.com/project/" + project_id
                  + "/authentication") if project_id else \
                 "https://console.firebase.google.com/"
        return False, (
            "Authentication NO está activo para Correo/contraseña.\n"
            "     Google responde CONFIGURATION_NOT_FOUND tanto al iniciar sesión\n"
            "     como al registrar, así que el proveedor sigue deshabilitado.\n"
            "     Solución exacta (1 minuto):\n"
            "       1. " + enlace + "\n"
            "       2. Pestaña 'Sign-in method' / 'Métodos de acceso'\n"
            "       3. 'Correo electrónico/contraseña' -> Habilitar -> GUARDAR\n"
            "     Ojo: si sólo marcaste la casilla y no pulsaste 'Guardar',\n"
            "     el cambio no se aplica. Verás 'Habilitado' en verde cuando sí."
        )

    if "api key not valid" in minus or "api_key_invalid" in minus:
        return False, "La API Key no es válida para este proyecto."

    if "user_not_found" in minus or "invalid_login_credentials" in minus \
            or "email_not_found" in minus or "invalid_password" in minus:
        return True, (
            "Authentication SÍ está activo para Correo/contraseña.\n"
            "     Google ha rechazado la prueba porque el usuario no existe,\n"
            "     que es justo lo que esperábamos. Todo correcto."
        )

    if "admin_only_operation" in minus:
        return False, ("El registro con correo/contraseña está desactivado.\n"
                       "     Revisa Authentication → Sign-in method → Correo electrónico.")

    if "unauthorized_domain" in minus:
        return True, ("El proveedor está activo, pero el dominio no está autorizado\n"
                      "     (sólo afecta a login con redes sociales, no a correo).")

    return False, "Respuesta inesperada al comprobar Authentication."


def proyecto_para_enlace(project_id):
    return project_id


# ─────────────────────────── interpretación de errores ───────────────────────────

def explicar(codigo, cuerpo):
    """
    Traduce el error de Google a algo accionable en español.

    Los tres 403 posibles se distinguen por el texto del mensaje, porque Google
    devuelve el mismo código HTTP para causas muy distintas:

      "Permission denied on the service."  -> la API Key no sirve
      "Missing or insufficient permissions." -> las reglas de Firestore bloquean
      "PERMISSION_DENIED" + reason ACCESS_DENIED -> API deshabilitada en el proyecto
    """
    texto = json.dumps(cuerpo) if not isinstance(cuerpo, str) else cuerpo
    minus = texto.lower()

    if codigo == 0:
        return "Sin conexión a internet o el dominio firestore.googleapis.com está bloqueado."

    # ── Base de datos sin crear (el caso más frecuente) ─────────────
    # IMPORTANTE: este error llega como 404, igual que un Project ID inexistente.
    # Hay que distinguirlos por el mensaje, porque la solución es muy distinta.
    if "does not exist" in minus and "database" in minus:
        return (
            "La base de datos Firestore NO está creada en este proyecto.\n"
            "     El Project ID y la API Key SÍ son correctos: Google reconoce el\n"
            "     proyecto. Lo que falta es el paso de creación de la base.\n"
            "     Solución (2 minutos):\n"
            "       1. Ve a https://console.firebase.google.com/\n"
            "       2. Elige tu proyecto\n"
            "       3. Menú Build -> Firestore Database -> 'Crear base de datos'\n"
            "       4. Elige ubicación y modo (de prueba, para empezar)\n"
            "     Sin este paso no hay colecciones que mostrar ni datos que guardar."
        )

    # ── Clave incorrecta ────────────────────────────────────────────
    if "api key not valid" in minus or "api_key_invalid" in minus:
        return ("La API Key NO es válida. Cópiala otra vez desde Firebase Console →\n"
                "     Configuración del proyecto → General → Claves de API de la app web.")

    if "permission denied on the service" in minus or "api has not been used" in minus:
        return (
            "La API Key no tiene permiso sobre Firestore (o la API de Firestore está\n"
            "     deshabilitada en el proyecto).\n"
            "     Comprueba en Firebase Console:\n"
            "       1. Que la clave que copiaste es la de ESTE proyecto.\n"
            "       2. Configuración del proyecto → Claves de API → restricciones:\n"
            "          'Cloud Firestore API' debe estar permitida.\n"
            "       3. Google Cloud Console → APIs → 'Cloud Firestore API' habilitada."
        )

    # ── Reglas de seguridad ─────────────────────────────────────────
    if "missing or insufficient permissions" in minus:
        return (
            "La base de datos EXISTE y responde, pero las REGLAS bloquean la lectura\n"
            "     anónima. Es lo normal si la creaste en modo producción.\n"
            "     Para probar la conexión: Firebase Console → Firestore → Reglas, y\n"
            "     mientras desarrollas usa:\n"
            "         allow read, write: if true;\n"
            "     (antes de la entrega final se endurecen a request.auth != null)"
        )

    if codigo == 403:
        return (
            "Acceso denegado (HTTP 403). Causas habituales:\n"
            "       1. La API Key no es de este proyecto.\n"
            "       2. Las reglas de Firestore no permiten lectura.\n"
            "       3. La API de Firestore no está habilitada."
        )

    # ── Proyecto / base de datos inexistente ────────────────────────
    if codigo == 404:
        if "project" in minus and ("not found" in minus or "does not exist" in minus):
            return ("El Project ID no existe. Revisa la ortografía en Firebase Console →\n"
                    "     Configuración del proyecto → General → ID del proyecto.")
        return ("No existe la base de datos (default) en este proyecto.\n"
                "     Ve a Firebase Console → Firestore Database → Crear base de datos.\n"
                "     Sin ese paso NO hay nada que conectar.")

    if codigo == 429:
        return "Demasiadas peticiones. Espera un minuto y reintenta."

    return "Respuesta inesperada de Google: revisa el detalle técnico de abajo."


# ─────────────────────────── análisis de campos ───────────────────────────

def resumir_documento(doc):
    """Devuelve {campo: tipo} de un documento de Firestore."""
    campos = doc.get("fields") or {}
    tipos = {}
    for nombre, valor in campos.items():
        if not isinstance(valor, dict):
            tipos[nombre] = "desconocido"
            continue
        for tipo in valor:
            tipos[nombre] = tipo
            break
    return tipos


# ─────────────────────────── principal ───────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Comprueba la conexión con tu Firestore y describe su contenido.")
    parser.add_argument("--project", help="Project ID de Firebase")
    parser.add_argument("--key", help="API Key de Firebase (Web)")
    parser.add_argument("--database",
                        help="ID de la base de Firestore (por defecto '(default)')")
    args = parser.parse_args()

    props = leer_propiedades()
    project_id = args.project or props.get("FIREBASE_PROJECT_ID", "")
    api_key = args.key or props.get("FIREBASE_API_KEY", "")
    database_id = args.database or props.get("FIREBASE_DATABASE_ID", "") or "(default)"

    print("=" * 66)
    print(" Verificación de la base de datos de Firebase")
    print("=" * 66)

    if not project_id or not api_key:
        print("\n[FALTA CONFIGURACIÓN]")
        print("  Project ID :", project_id or "(vacío)")
        print("  API Key    :", (api_key[:8] + "...") if api_key else "(vacía)")
        print("\nPásalos por argumento o escríbelos en local.properties:")
        print("  python3 tools/verificar_firebase.py --project mi-proyecto --key AIza...")
        return 1

    print(f"\nProject ID : {project_id}")
    print(f"API Key    : {api_key[:8]}...{api_key[-4:]} ({len(api_key)} caracteres)")
    print(f"Base datos : {database_id}")

    # 1) ¿Responden las colecciones de la app? (esto SÍ respeta las reglas)
    print("\n" + "-" * 66)
    print(" PASO A — Base de datos Firestore")
    print("-" * 66)
    print("Leyendo las colecciones reales de la app...")
    codigo, colecciones, fallo = comprobar_lectura(project_id, api_key, database_id)
    firestore_ok = (codigo == 200)

    # Pista secundaria: listCollectionIds es administrativa y NO pasa por las
    # reglas, así que su 403 no significa que la app no pueda leer.
    codigo_admin, cuerpo_admin = listar_colecciones(project_id, api_key, database_id)
    texto_admin = json.dumps(cuerpo_admin) if not isinstance(cuerpo_admin, str) else cuerpo_admin
    minus_admin = texto_admin.lower()
    base_creada = (firestore_ok or codigo_admin == 200
                   or "missing or insufficient permissions" in minus_admin)

    if firestore_ok:
        print(f"  HTTP 200  ->  las reglas PERMITEN leer y escribir")
        print(f"  Colecciones que responden: {', '.join(colecciones)}")
    else:
        print(f"  HTTP {codigo}")
        print("\n  " + explicar(codigo, fallo))
        print("\n  Detalle técnico:")
        detalle = json.dumps(fallo, indent=2, ensure_ascii=False)
        for linea in detalle.splitlines()[:14]:
            print("   ", linea)

    # 2) ¿Está activo el inicio de sesión con correo?
    print("\n" + "-" * 66)
    print(" PASO B — Authentication (correo y contraseña)")
    print("-" * 66)
    print("Comprobando el proveedor de correo/contraseña...")
    codigo_auth, cuerpo_auth = comprobar_auth(api_key)
    auth_ok, texto_auth = explicar_auth(codigo_auth, cuerpo_auth, project_id)
    print("\n  " + texto_auth)
    if not auth_ok:
        print("\n  Detalle técnico:")
        detalle = json.dumps(cuerpo_auth, indent=2, ensure_ascii=False)
        for linea in detalle.splitlines()[:14]:
            print("   ", linea)

    # 3) Resumen accionable
    print("\n" + "=" * 66)
    print(" RESUMEN")
    print("=" * 66)
    print(f"  Paso A  Base de datos Firestore : "
          + ("LISTA (las reglas permiten leer y escribir)" if firestore_ok
             else ("CREADA, pero las reglas la bloquean"
                   if base_creada else "PENDIENTE")))
    print(f"  Paso B  Authentication          : "
          + ("LISTO" if auth_ok else "PENDIENTE"))

    # 4) Campos reales de las colecciones que responden
    if firestore_ok:
        print("\n[2/2] Leyendo los campos reales de cada colección...\n")
        for coleccion in colecciones:
            codigo_doc, cuerpo_doc = listar_documentos(project_id, api_key, coleccion,
                                                       database_id)
            if codigo_doc != 200:
                print(f"  {coleccion}: HTTP {codigo_doc} (no se pudo leer)")
                continue
            documentos = (cuerpo_doc.get("documents", [])
                          if isinstance(cuerpo_doc, dict) else [])
            print(f"  {coleccion}  ({len(documentos)} documento(s) de muestra)")
            if not documentos:
                print("      (colección vacía)")
                continue
            tipos = resumir_documento(documentos[0])
            for campo, tipo in tipos.items():
                print(f"      {campo:<22} {tipo}")
            print()

    # 5) Qué falta exactamente
    vacias = []
    if firestore_ok:
        for coleccion in ("specialties", "doctors", "patients"):
            if coleccion in colecciones:
                codigo_doc, cuerpo_doc = listar_documentos(project_id, api_key,
                                                           coleccion, database_id)
                documentos = (cuerpo_doc.get("documents", [])
                              if isinstance(cuerpo_doc, dict) else [])
                if not documentos:
                    vacias.append(coleccion)
        if vacias:
            print("\n  Colecciones sin datos todavía: " + ", ".join(vacias))
            print("  Cárgalas con:")
            print("    python3 tools/cargar_colecciones.py")
        else:
            print("\n  Todo listo. La app puede leer y escribir en Firestore.")
            print("  Prueba definitiva: agenda una cita en la app y aparecerá en")
            print("  la colección 'appointments'.")
    elif base_creada:
        print("\n  La base EXISTE (por eso Google no da 404). Falta que las reglas")
        print("  dejen pasar las peticiones. Firestore -> Reglas -> Publicar:")
        print("      rules_version = '2';")
        print("      service cloud.firestore {")
        print("        match /databases/{database}/documents {")
        print("          match /{document=**} {")
        print("            allow read, write: if true;")
        print("          }")
        print("        }")
        print("      }")
        print("\n  Si ya las publicaste, espera 1 minuto: tardan en propagarse.")
    else:
        print("\n  Falta crear la base de datos de Firestore (paso A).")

    if not auth_ok:
        print("\n  Nota: la app sigue funcionando en modo local gracias al respaldo")
        print("  (FirebaseAuthFallback). El proveedor de correo sólo hace falta para")
        print("  que las cuentas se creen en Firebase en lugar de en el teléfono.")

    print("\n  Aviso sobre el banner rojo de la consola: es NORMAL mientras uses")
    print("  'allow read, write: if true'. No es un error, es un recordatorio de")
    print("  que esas reglas son abiertas. Antes de entregar se cambian a")
    print("  'if request.auth != null' y el banner desaparece.")

    print("=" * 66)
    return 0 if (firestore_ok and auth_ok) else 1


if __name__ == "__main__":
    sys.exit(main())
