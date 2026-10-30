#!/usr/bin/env python3
"""Read The Factory HKA sandbox catalogs and print the DIAN mapping used by the ERP.

Usage:
    python3 scripts/factory_sandbox_catalogs.py [--env .env] [--output FILE] [--offline]

Why this script exists
----------------------
The Factory HKA does not publish a catalog endpoint for taxes or payment means: its SOAP
WSDL only exposes document operations (``Enviar``, ``EstadoDocumento``, ``FoliosRestantes``,
...). HKA consumes the official DIAN codes directly, so the mapping the ERP needs is the
DIAN table, not a provider-owned ID list.

This script therefore:

1. Loads ``FACTORY_API_TOKEN`` / ``FACTORY_API_PASSWORD`` / ``FACTORY_API_URL`` from the
   environment (or the given ``.env`` file).
2. Validates connectivity and credentials against the sandbox by calling
   ``FoliosRestantes`` (a read-only operation).
3. Prints a JSON document mapping every tax and payment method used by the ERP to its
   ``provider_external_id``. For The Factory HKA that value is the DIAN code itself.

``--offline`` skips the network call and prints only the static DIAN mapping. Credentials
are never printed, logged, or included in the output.

Dependencies: Python 3 standard library only.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/"
TEMPURI_NS = "http://tempuri.org/"
SOAP_ACTION = "http://tempuri.org/IService/FoliosRestantes"
DEFAULT_URL = "https://demoemision21.thefactoryhka.com.co/ws/v1.0/Service.svc"
REQUEST_TIMEOUT_SECONDS = 15

# Official DIAN tribute codes consumed by The Factory HKA as ``codigoTOTALImp``.
# ``provider_external_id`` equals ``dian_code`` because HKA has no catalog of its own.
TAXES: list[dict[str, Any]] = [
    {
        "code": "EXENTO",
        "name": "Exento / IVA 0%",
        "dian_code": "01",
        "percentage": 0.0,
        "provider_external_id": "01",
    },
    {
        "code": "IVA_5",
        "name": "IVA 5%",
        "dian_code": "01",
        "percentage": 5.0,
        "provider_external_id": "01",
    },
    {
        "code": "IVA_19",
        "name": "IVA 19%",
        "dian_code": "01",
        "percentage": 19.0,
        "provider_external_id": "01",
    },
    {
        "code": "INC_8",
        "name": "INC 8%",
        "dian_code": "04",
        "percentage": 8.0,
        "provider_external_id": "04",
    },
]

# DIAN payment means used by the ERP (mirrors domain DianPaymentMeans).
# HKA receives these codes verbatim in ``mediosDePago[].medioPago``.
PAYMENT_METHODS: list[dict[str, Any]] = [
    {"method": "CASH", "name": "Efectivo", "dian_code": "10", "provider_external_id": "10"},
    {"method": "CHECK", "name": "Cheque", "dian_code": "20", "provider_external_id": "20"},
    {
        "method": "TRANSFER",
        "name": "Consignación bancaria",
        "dian_code": "42",
        "provider_external_id": "42",
    },
    {
        "method": "CARD",
        "name": "Tarjeta de crédito",
        "dian_code": "48",
        "provider_external_id": "48",
    },
]


def load_env(path: Path) -> None:
    """Load simple KEY=VALUE lines without overriding the real environment."""
    if not path.exists():
        return
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


def build_envelope(token_empresa: str, token_password: str) -> bytes:
    """Build the SOAP 1.1 ``FoliosRestantes`` request, escaping credentials."""
    def escape(value: str) -> str:
        return (
            value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace('"', "&quot;")
            .replace("'", "&apos;")
        )

    body = f"""<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="{SOAP_NS}">
  <soap:Body>
    <FoliosRestantes xmlns="{TEMPURI_NS}">
      <tokenEmpresa>{escape(token_empresa)}</tokenEmpresa>
      <tokenPassword>{escape(token_password)}</tokenPassword>
    </FoliosRestantes>
  </soap:Body>
</soap:Envelope>"""
    return body.encode("utf-8")


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def find_first(element: ET.Element, name: str) -> ET.Element | None:
    for node in element.iter():
        if local_name(node.tag) == name:
            return node
    return None


def child_text(element: ET.Element, name: str) -> str | None:
    for child in element:
        if local_name(child.tag) == name:
            return (child.text or "").strip()
    return None


def call_folios_restantes(url: str, token_empresa: str, token_password: str) -> dict[str, Any]:
    """Call ``FoliosRestantes`` and return the parsed result, never the credentials."""
    request = urllib.request.Request(
        url,
        data=build_envelope(token_empresa, token_password),
        method="POST",
        headers={
            "Content-Type": "text/xml; charset=utf-8",
            "SOAPAction": f'"{SOAP_ACTION}"',
            "Accept": "text/xml, application/xml",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            raw = response.read()
    except urllib.error.HTTPError as error:
        raw = error.read()
        parsed = parse_response(raw)
        fault = parsed.get("fault")
        if fault:
            raise RuntimeError(f"SOAP Fault: {fault}") from error
        raise RuntimeError(
            f"HTTP {error.code} al llamar FoliosRestantes: {parsed.get('mensaje') or 'sin detalle'}"
        ) from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"No se pudo conectar con The Factory HKA: {error.reason}") from error

    parsed = parse_response(raw)
    if parsed.get("fault"):
        raise RuntimeError(f"SOAP Fault: {parsed['fault']}")
    return parsed


def parse_response(raw: bytes) -> dict[str, Any]:
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as error:
        raise RuntimeError(f"Respuesta XML inválida de The Factory HKA: {error}") from error

    fault = find_first(root, "Fault")
    if fault is not None:
        return {
            "fault": child_text(fault, "faultstring")
            or child_text(fault, "Reason")
            or "sin mensaje"
        }

    result = find_first(root, "FoliosRestantesResult")
    if result is None:
        raise RuntimeError("The Factory HKA no devolvió FoliosRestantesResult.")
    return {
        "codigo": child_text(result, "codigo"),
        "folios_restantes": child_text(result, "foliosRestantes"),
        "mensaje": child_text(result, "mensaje"),
        "resultado": child_text(result, "resultado"),
    }


def is_success(connectivity: dict[str, Any]) -> bool:
    """HKA reports business errors with HTTP 200, so check ``codigo``/``resultado``."""
    code = (connectivity.get("codigo") or "").strip()
    result = (connectivity.get("resultado") or "").strip().lower()
    return code == "0" and not result.startswith("error") and not result.startswith("fall")


def build_catalog(url: str, connectivity: dict[str, Any] | None) -> dict[str, Any]:
    return {
        "provider": "thefactory",
        "environment": "sandbox",
        "base_url": url,
        "source": "official DIAN codes (HKA has no catalog endpoint; it consumes DIAN codes directly)",
        "connectivity": connectivity,
        "taxes": TAXES,
        "payment_methods": PAYMENT_METHODS,
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Map the DIAN tax and payment codes used by The Factory HKA sandbox.",
    )
    parser.add_argument("--env", default=".env", help="Ruta al archivo .env (default: .env)")
    parser.add_argument("--output", help="Archivo JSON de salida (default: stdout)")
    parser.add_argument(
        "--offline",
        action="store_true",
        help="No llamar al sandbox; imprimir solo el mapa DIAN estático",
    )
    args = parser.parse_args()

    load_env(Path(args.env))

    url = os.environ.get("FACTORY_API_URL", DEFAULT_URL).strip() or DEFAULT_URL
    token_empresa = os.environ.get("FACTORY_API_TOKEN", "").strip()
    token_password = os.environ.get("FACTORY_API_PASSWORD", "").strip()

    connectivity: dict[str, Any] | None = None
    exit_code = 0

    if args.offline:
        print(
            "Modo offline: no se valida el sandbox; el mapa DIAN no requiere credenciales.",
            file=sys.stderr,
        )
    elif not token_empresa or not token_password:
        print(
            "Faltan FACTORY_API_TOKEN y/o FACTORY_API_PASSWORD en el entorno o en "
            f"{args.env}. Usá --offline para imprimir solo el mapa DIAN.",
            file=sys.stderr,
        )
        exit_code = 2
    else:
        try:
            connectivity = call_folios_restantes(url, token_empresa, token_password)
            if is_success(connectivity):
                print(
                    "FoliosRestantes OK: "
                    f"codigo={connectivity.get('codigo')} "
                    f"folios={connectivity.get('folios_restantes')} "
                    f"resultado={connectivity.get('resultado')}",
                    file=sys.stderr,
                )
            else:
                print(
                    "The Factory HKA rechazó FoliosRestantes: "
                    f"codigo={connectivity.get('codigo')} "
                    f"resultado={connectivity.get('resultado')} "
                    f"mensaje={connectivity.get('mensaje')}",
                    file=sys.stderr,
                )
                exit_code = 2
        except RuntimeError as error:
            print(f"Error de conectividad con The Factory HKA: {error}", file=sys.stderr)
            exit_code = 2

    catalog = build_catalog(url, connectivity)
    payload = json.dumps(catalog, ensure_ascii=False, indent=2)

    if args.output:
        Path(args.output).write_text(payload + "\n", encoding="utf-8")
        print(f"Catálogo escrito en {args.output}", file=sys.stderr)
    else:
        print(payload)

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
