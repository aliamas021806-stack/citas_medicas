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
    url = ("https://firestore.googleapis.com/v1/projects/" + project_id
           + "/databases/" + database_id
           + "/documents:listCollectionIds?key=" + api_key)
    return peticion(url, cuerpo={})


def listar_documentos(project_id, api_key, coleccion, database_id="(default)"):
    url = ("https://firestore.googleapis.com/v1/projects/" + project_id
           + "/databases/" + database_id + "/documents/" + coleccion
           + "?key=" + api_key + "&pageSize=3")
    return peticion(url)


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

    # 1) ¿Responde la base de datos?
    print("\n[1/2] Consultando la base de datos...")
    codigo, cuerpo = listar_colecciones(project_id, api_key, database_id)

    if codigo != 200:
        print(f"  HTTP {codigo}")
        print("\n  " + explicar(codigo, cuerpo))
        print("\n  Detalle técnico:")
        detalle = json.dumps(cuerpo, indent=2, ensure_ascii=False)
        for linea in detalle.splitlines()[:14]:
            print("   ", linea)
        return 1

    colecciones = cuerpo.get("collectionIds", []) if isinstance(cuerpo, dict) else []
    print(f"  HTTP 200  →  la base de datos EXISTE y responde")
    print(f"  Colecciones encontradas: {len(colecciones)}")

    if not colecciones:
        print("\n  La base está vacía (no tiene colecciones todavía).")
        print("  Lo que necesita la app:")
        for nombre in ("specialties", "doctors", "patients", "appointments"):
            print(f"    - {nombre}")
        print("\n  Dime esto y te genero el script de carga con datos de ejemplo.")
        return 0

    # 2) Campos reales de cada colección
    print("\n[2/2] Leyendo los campos reales de cada colección...\n")
    for coleccion in colecciones:
        codigo_doc, cuerpo_doc = listar_documentos(project_id, api_key, coleccion,
                                                       database_id)
        if codigo_doc != 200:
            print(f"  {coleccion}: HTTP {codigo_doc} (no se pudo leer)")
            continue
        documentos = cuerpo_doc.get("documents", []) if isinstance(cuerpo_doc, dict) else []
        print(f"  {coleccion}  ({len(documentos)} documento(s) de muestra)")
        if not documentos:
            print("      (colección vacía)")
            continue
        tipos = resumir_documento(documentos[0])
        for campo, tipo in tipos.items():
            print(f"      {campo:<22} {tipo}")
        print()

    print("=" * 66)
    print(" Ya tengo lo que necesito.")
    print(" Pégame esta salida y ajusto el mapeo (FirestoreMapper) a tus campos")
    print(" reales, sin tocar Room ni la UI.")
    print("=" * 66)
    return 0


if __name__ == "__main__":
    sys.exit(main())
