#!/usr/bin/env python3
"""
Extrae las credenciales de Firebase desde un google-services.json y las escribe
en local.properties (que NO se sube al repositorio).

Uso:
    python3 tools/configurar_firebase.py ruta/al/google-services.json
    python3 tools/configurar_firebase.py          # busca el archivo automáticamente

También acepta los datos a mano (sin google-services.json):
    python3 tools/configurar_firebase.py --project mi-proyecto --key AIzaSy...

Comprueba además que el package_name del JSON coincide con el de la app, que es la
causa número uno de que Firebase "no conecte" sin dar un error claro.
"""

import argparse
import json
import os
import sys
import pathlib

# Paquete real de la app (debe coincidir con applicationId en app/build.gradle)
PAQUETE_APP = "com.tuempresa.citasmedicas"
NOMBRE_PROPIEDADES = "local.properties"


def buscar_json():
    """Busca google-services.json en las ubicaciones habituales."""
    candidatos = [
        "google-services.json",
        "app/google-services.json",
        os.path.expanduser("~/Downloads/google-services.json"),
        os.path.expanduser("~/Descargas/google-services.json"),
    ]
    for c in candidatos:
        if os.path.isfile(c):
            return c
    return None


def extraer(datos):
    """Saca projectId, apiKey y appId del JSON."""
    info = datos.get("project_info", {}) or {}
    project_id = info.get("project_id", "")

    # El bloque "client" puede tener varias apps (android/ios/web).
    app_id = ""
    api_key = ""
    package_name = ""

    clientes = datos.get("client", []) or []
    for cliente in clientes:
        cliente_info = cliente.get("client_info", {}) or {}
        # Preferimos la app Android cuyo paquete coincide con el nuestro.
        android_info = cliente_info.get("android_client_info", {}) or {}
        pkg = android_info.get("package_name", "")
        candidato_app = cliente_info.get("mobilesdk_app_id", "")

        claves = cliente.get("api_key", []) or []
        candidato_key = claves[0].get("current_key", "") if claves else ""

        if pkg == PAQUETE_APP:
            app_id, api_key, package_name = candidato_app, candidato_key, pkg
            break
        if not api_key and candidato_key:
            app_id, api_key, package_name = candidato_app, candidato_key, pkg

    return {
        "project_id": project_id,
        "api_key": api_key,
        "app_id": app_id,
        "package_name": package_name,
        "sender_id": info.get("project_number", ""),
    }


def escribir_propiedades(valores):
    """Escribe/actualiza las claves de Firebase en local.properties sin borrar el resto."""
    nuevas = {
        "FIREBASE_PROJECT_ID": valores["project_id"],
        "FIREBASE_API_KEY": valores["api_key"],
        "FIREBASE_APP_ID": valores["app_id"],
        "FIREBASE_SENDER_ID": valores["sender_id"],
    }

    lineas = []
    if os.path.isfile(NOMBRE_PROPIEDADES):
        with open(NOMBRE_PROPIEDADES, "r", encoding="utf-8") as f:
            for linea in f.read().splitlines():
                clave = linea.split("=", 1)[0].strip()
                if clave in nuevas:
                    continue  # se reemplaza abajo
                lineas.append(linea)

    lineas.append("")
    lineas.append("# --- Firebase (generado por tools/configurar_firebase.py) ---")
    for k, v in nuevas.items():
        lineas.append(f"{k}={v}")

    with open(NOMBRE_PROPIEDADES, "w", encoding="utf-8") as f:
        f.write("\n".join(lineas).strip() + "\n")

    # Asegurar que local.properties está ignorado por git
    gitignore = pathlib.Path(".gitignore")
    if gitignore.exists():
        contenido = gitignore.read_text(encoding="utf-8")
        if "local.properties" not in contenido:
            with open(gitignore, "a", encoding="utf-8") as f:
                f.write("\nlocal.properties\n")
            print("  + local.properties añadido a .gitignore")


def main():
    print("=" * 62)
    print(" Configurador de Firebase para Citas Médicas")
    print("=" * 62)

    parser = argparse.ArgumentParser(
        description="Escribe las credenciales de Firebase en local.properties.")
    parser.add_argument("json", nargs="?", help="Ruta a google-services.json")
    parser.add_argument("--project", help="Project ID (si no hay google-services.json)")
    parser.add_argument("--key", help="API Key (si no hay google-services.json)")
    parser.add_argument("--app-id", default="", help="App ID de Android (opcional)")
    parser.add_argument("--sender-id", default="", help="Project number (opcional)")
    args = parser.parse_args()

    # ── Modo manual: los datos vienen por argumento ─────────────────
    if args.project or args.key:
        if not args.project or not args.key:
            print("\n[ERROR] En modo manual hacen falta los dos datos:")
            print("        --project TU_PROJECT_ID --key TU_API_KEY")
            return 1

        clave = args.key.strip()
        if not clave.startswith("AIza"):
            print("\n[AVISO] La API Key de Firebase suele empezar por 'AIza'.")
            print(f"        La tuya empieza por '{clave[:6]}'. Revísala si falla.")

        valores = {
            "project_id": args.project.strip(),
            "api_key": clave,
            "app_id": args.app_id.strip(),
            "package_name": PAQUETE_APP,
            "sender_id": args.sender_id.strip(),
        }
        print("\nModo manual (sin google-services.json)")
        return rematar(valores)

    # ── Modo JSON ───────────────────────────────────────────────────
    ruta = args.json if args.json else buscar_json()
    if not ruta or not os.path.isfile(ruta):
        print("\n[ERROR] No encuentro google-services.json.")
        print("        Opciones:")
        print("          python3 tools/configurar_firebase.py ruta/al/archivo.json")
        print("          python3 tools/configurar_firebase.py --project ID --key API_KEY")
        return 1

    print(f"\nLeyendo: {ruta}")
    try:
        with open(ruta, "r", encoding="utf-8") as f:
            datos = json.load(f)
    except (json.JSONDecodeError, OSError) as e:
        print(f"[ERROR] No se pudo leer el JSON: {e}")
        return 1

    valores = extraer(datos)

    # ── Verificaciones ──────────────────────────────────────────────
    errores = []
    if not valores["project_id"]:
        errores.append("Falta project_id en el JSON")
    if not valores["api_key"]:
        errores.append("Falta api_key en el JSON")
    if valores["package_name"] and valores["package_name"] != PAQUETE_APP:
        errores.append(
            f"El package_name del JSON ({valores['package_name']}) NO coincide con "
            f"el de la app ({PAQUETE_APP}). Firebase no conectaría."
        )

    if errores:
        print("\n[ERRORES]")
        for e in errores:
            print("  x", e)
        return 1

    return rematar(valores)


def rematar(valores):
    """Muestra el resumen, escribe local.properties y da el siguiente paso."""
    print("\nDatos encontrados:")
    print(f"  Project ID : {valores['project_id']}")
    print(f"  API Key    : {valores['api_key'][:8]}...{valores['api_key'][-4:]} "
          f"({len(valores['api_key'])} caracteres)")
    print(f"  App ID     : {valores['app_id'] or '(no presente)'}")
    print(f"  Paquete    : {valores['package_name'] or '(no especificado)'}")

    escribir_propiedades(valores)

    print(f"\n[OK] Escrito en {os.path.abspath(NOMBRE_PROPIEDADES)}")
    print("     (este archivo está en .gitignore: no se sube a GitHub)")
    print("\nSiguiente paso — comprobar que la base responde:")
    print("     python3 tools/verificar_firebase.py")
    print("\nY después compilar:")
    print("     ./gradlew assembleDebug")
    print("\nLa app detectará las credenciales y usará Firebase automáticamente.")
    print("Si borras esas líneas de local.properties, vuelve al modo mock.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
