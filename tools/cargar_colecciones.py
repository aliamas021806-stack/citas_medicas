#!/usr/bin/env python3
"""
Carga las colecciones iniciales de la app en Firestore usando la API REST.

Uso:
    python3 tools/cargar_colecciones.py
    python3 tools/cargar_colecciones.py --dry-run     # sólo muestra qué haría

Lee el Project ID y la API Key de local.properties (los mismos que usa la app).

IMPORTANTE sobre permisos
-------------------------
Este script habla con la API REST de Firestore usando la API Key de la app, que es una
clave pública de cliente. Eso significa dos cosas:

  * Requiere que las REGLAS de Firestore permitan la escritura:
        allow read, write: if true;      (mientras se desarrolla)
  * Si las reglas están en modo producción, Firestore responderá 403 y habrá que
    cargar los datos desde la consola o con una cuenta de servicio.

Así se evita pedirte un archivo de cuenta de servicio (que sí es un secreto real).
"""

import argparse
import json
import ssl
import sys
import urllib.error
import urllib.request

NOMBRE_PROPIEDADES = "local.properties"
TIMEOUT = 25


# ─────────────────────────── Firebase: tipos de valor ───────────────────────────

def v_txt(valor):
    """Texto -> envoltorio de Firestore."""
    return {"stringValue": "" if valor is None else str(valor)}


def v_int(valor):
    """Entero -> envoltorio. Firestore exige que los enteros viajen como texto."""
    return {"integerValue": str(int(valor))}


def v_dbl(valor):
    return {"doubleValue": float(valor)}


def v_bool(valor):
    return {"booleanValue": bool(valor)}


# ─────────────────────────── lector de credenciales ───────────────────────────

def leer_propiedades():
    valores = {}
    try:
        with open(NOMBRE_PROPIEDADES, "r", encoding="utf-8") as f:
            for linea in f:
                linea = linea.strip()
                if not linea or linea.startswith("#") or "=" not in linea:
                    continue
                clave, valor = linea.split("=", 1)
                valores[clave.strip()] = valor.strip()
    except FileNotFoundError:
        pass
    return valores


# ─────────────────────────── datos a cargar ───────────────────────────

ESPECIALIDADES = [
    ("esp_01", "Cardiología", "\U0001FAF0",
     "Diagnóstico y tratamiento de enfermedades del corazón", 3),
    ("esp_02", "Pediatría", "\U0001F476",
     "Atención médica integral para niños y adolescentes", 2),
    ("esp_03", "Dermatología", "\U0001F9F4",
     "Cuidado de la piel, cabello y uñas", 2),
    ("esp_04", "Traumatología", "\U0001F9B4",
     "Lesiones del sistema músculo-esquelético", 2),
    ("esp_05", "Neurología", "\U0001F9E0",
     "Trastornos del sistema nervioso central y periférico", 2),
    ("esp_06", "Medicina General", "\U0001FA7A",
     "Atención primaria y consulta general", 3),
    ("esp_07", "Ginecología", "\U0001F338",
     "Salud reproductiva y femenina", 2),
    ("esp_08", "Oftalmología", "\U0001F441",
     "Cuidado de la visión y enfermedades oculares", 2),
]

# Deben coincidir con los campos que espera FirestoreMapper.java
DOCTORES = [
    ("doc_01", "Dr. Carlos Ramírez", "esp_01", "Cardiología",
     "Cardiólogo con 15 años de experiencia en hospitales de tercer nivel.",
     4.8, 15, 46, 60.0),
    ("doc_02", "Dra. María González", "esp_02", "Pediatría",
     "Pediatra especializada en desarrollo infantil y vacunación.",
     4.9, 12, 38, 50.0),
    ("doc_03", "Dra. Andrea López", "esp_03", "Dermatología",
     "Dermatóloga con formación en dermoscopia y cirugía menor.",
     4.7, 9, 41, 55.0),
    ("doc_04", "Dr. Javier Morales", "esp_04", "Traumatología",
     "Traumatólogo experto en medicina deportiva y rehabilitación.",
     4.6, 18, 50, 65.0),
    ("doc_05", "Dra. Lucía Fernández", "esp_05", "Neurología",
     "Neuróloga especializada en cefaleas y trastornos del movimiento.",
     4.9, 11, 40, 70.0),
    ("doc_06", "Dr. Manuel Ortega", "esp_06", "Medicina General",
     "Médico de familia con vocación de atención continuada.",
     4.5, 20, 52, 40.0),
    ("doc_07", "Dra. Paula Navarro", "esp_07", "Ginecología",
     "Ginecóloga con experiencia en obstetricia y salud reproductiva.",
     4.8, 13, 43, 60.0),
    ("doc_08", "Dr. Sergio Ibáñez", "esp_08", "Oftalmología",
     "Oftalmólogo especializado en cirugía de cataratas.",
     4.7, 16, 48, 58.0),
    ("doc_09", "Dra. Elena Ruiz", "esp_01", "Cardiología",
     "Cardióloga especializada en prevención cardiovascular.",
     4.9, 10, 37, 62.0),
    ("doc_10", "Dr. Andrés Molina", "esp_06", "Medicina General",
     "Médico general con formación en urgencias.",
     4.4, 7, 34, 42.0),
]

PACIENTES = [
    ("pat_01", "Laura Jiménez", 34, "+34 600 123 456", "A+", "Sanitas",
     "Penicilina"),
    ("pat_02", "Marcos Delgado", 28, "+34 611 222 333", "O-", "Adeslas", "Ninguna"),
    ("pat_03", "Carmen Ruiz", 45, "+34 622 444 555", "B+", "Asisa", "Ibuprofeno"),
    ("pat_04", "Diego Navarro", 61, "+34 633 666 777", "AB+", "Sanitas",
     "Marisco"),
    ("pat_05", "Ana Belén Soto", 52, "+34 644 888 999", "A-", "DKV", "Ninguna"),
]


# ─────────────────────────── llamadas REST ───────────────────────────

def crear_documento(proyecto, clave, coleccion, doc_id, campos):
    """
    Crea (o reemplaza) un documento con id fijo.

    Se usa PATCH con el id en la ruta: es idempotente, así que ejecutar el script dos
    veces no duplica datos. Con POST se generaría un id aleatorio cada vez.
    """
    url = (f"https://firestore.googleapis.com/v1/projects/{proyecto}"
           f"/databases/(default)/documents/{coleccion}/{doc_id}?key={clave}")
    cuerpo = json.dumps({"fields": campos}).encode("utf-8")
    req = urllib.request.Request(
        url, data=cuerpo, method="PATCH",
        headers={"Content-Type": "application/json", "Accept": "application/json"})

    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT,
                                    context=ssl.create_default_context()) as r:
            return r.status, ""
    except urllib.error.HTTPError as e:
        texto = e.read().decode("utf-8", errors="replace")
        try:
            dj = json.loads(texto)
            return e.code, dj.get("error", {}).get("message", texto)[:120]
        except json.JSONDecodeError:
            return e.code, texto[:120]
    except urllib.error.URLError as e:
        return 0, f"Sin conexión: {e.reason}"


def explicar(codigo, detalle):
    d = (detalle or "").lower()
    if codigo == 200:
        return None
    if codigo == 0:
        return "No hay conexión a internet desde este equipo."
    if "does not exist" in d and "database" in d:
        return ("La base de datos Firestore no existe todavía.\n"
                "     Créala en Firebase Console -> Firestore Database -> Crear base de datos")
    if "permission" in d or codigo == 403:
        return ("Las reglas de Firestore no permiten escribir.\n"
                "     Firebase Console -> Firestore -> Reglas, y mientras desarrollas:\n"
                "         allow read, write: if true;")
    if codigo == 404:
        return "El Project ID no existe o no tienes acceso."
    if codigo == 401:
        return "La API Key no es válida para este proyecto."
    return f"Respuesta inesperada: {detalle}"


# ─────────────────────────── principal ───────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Carga especialidades, doctores y pacientes en Firestore.")
    parser.add_argument("--project", help="Project ID")
    parser.add_argument("--key", help="API Key")
    parser.add_argument("--dry-run", action="store_true",
                        help="Muestra lo que haría sin escribir nada")
    args = parser.parse_args()

    props = leer_propiedades()
    proyecto = args.project or props.get("FIREBASE_PROJECT_ID", "")
    clave = args.key or props.get("FIREBASE_API_KEY", "")

    print("=" * 66)
    print(" Carga de colecciones iniciales en Firestore")
    print("=" * 66)

    if not proyecto or not clave:
        print("\n[FALTA CONFIGURACIÓN]")
        print("  Ejecuta antes: python3 tools/configurar_firebase.py --project ID --key KEY")
        return 1

    print(f"\nProyecto : {proyecto}")
    print(f"API Key  : {clave[:8]}...{clave[-4:]}")

    trabajo = []
    for esp in ESPECIALIDADES:
        trabajo.append(("specialties", esp[0], {
            "name": v_txt(esp[1]),
            "iconEmoji": v_txt(esp[2]),
            "description": v_txt(esp[3]),
            "doctorCount": v_int(esp[4]),
        }))
    for d in DOCTORES:
        trabajo.append(("doctors", d[0], {
            "fullName": v_txt(d[1]),
            "specialtyId": v_txt(d[2]),
            "specialtyName": v_txt(d[3]),
            "bio": v_txt(d[4]),
            "rating": v_dbl(d[5]),
            "yearsExperience": v_int(d[6]),
            "age": v_int(d[7]),
            "consultationFee": v_dbl(d[8]),
        }))
    for p in PACIENTES:
        trabajo.append(("patients", p[0], {
            "fullName": v_txt(p[1]),
            "age": v_int(p[2]),
            "phone": v_txt(p[3]),
            "bloodType": v_txt(p[4]),
            "insurance": v_txt(p[5]),
            "allergies": v_txt(p[6]),
        }))

    print(f"\nDocumentos a escribir: {len(trabajo)}")
    print(f"  specialties : {len(ESPECIALIDADES)}")
    print(f"  doctors     : {len(DOCTORES)}")
    print(f"  patients    : {len(PACIENTES)}")
    print("\nNota: 'appointments' no se carga desde aquí. Esa colección la escribe")
    print("      la propia app cada vez que agendas una cita.")

    if args.dry_run:
        print("\n[DRY-RUN] No se ha escrito nada. Ejemplos:")
        for coleccion, doc_id, campos in trabajo[:3]:
            print(f"  {coleccion}/{doc_id}: {list(campos.keys())}")
        return 0

    print("\nEscribiendo...\n")
    ok = 0
    fallos = {}
    for coleccion, doc_id, campos in trabajo:
        codigo, detalle = crear_documento(proyecto, clave, coleccion, doc_id, campos)
        if codigo == 200:
            ok += 1
        else:
            fallos.setdefault(codigo, (detalle, 0))
            motivo, n = fallos[codigo]
            fallos[codigo] = (motivo, n + 1)
            print(f"  x {coleccion}/{doc_id}: HTTP {codigo}")

    print(f"\n{'-' * 66}")
    print(f"Escritos correctamente: {ok} de {len(trabajo)}")

    if fallos:
        print("\n[ERRORES]")
        for codigo, (detalle, n) in fallos.items():
            print(f"\n  HTTP {codigo} ({n} documento(s)):")
            explicacion = explicar(codigo, detalle)
            print(f"  {explicacion}")
        print("\n  Detalle técnico del servidor:")
        for codigo, (detalle, _) in fallos.items():
            print(f"    {codigo}: {detalle}")
        return 1

    print("\n[OK] Colecciones cargadas. Ábrelas en Firebase Console -> Firestore.")
    print("     Ahora ejecuta: python3 tools/verificar_firebase.py")
    return 0


if __name__ == "__main__":
    sys.exit(main())
