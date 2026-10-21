#!/usr/bin/env python3
"""Read Siigo sandbox catalogs and print the IDs required by the ERP.

Usage:
    python3 scripts/siigo_sandbox_catalogs.py [--env .env]

It reads credentials from the environment (or the given .env file), authenticates against
Siigo, and prints /v1/taxes and /v1/payment-types?document_type=FV. It never prints the
access key or the bearer token.

Dependencies: Python 3 standard library only.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


def load_env(path: Path) -> None:
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


def request_json(method: str, url: str, *, body: dict[str, Any] | None = None,
                 token: str | None = None) -> tuple[int, Any]:
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json",
        "Partner-Id": os.environ["SIIGO_PARTNER_ID"],
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = {"raw": raw[:1000]}
        return exc.code, parsed


def require(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        print(f"Falta {name}. Configurala en .env o en el entorno.", file=sys.stderr)
        raise SystemExit(2)
    return value


def auth(base_url: str) -> str:
    username = require("SIIGO_USERNAME")
    access_key = require("SIIGO_ACCESS_KEY")
    require("SIIGO_PARTNER_ID")
    status, body = request_json(
        "POST", f"{base_url}/auth",
        body={"username": username, "access_key": access_key},
    )
    if status != 200 or not isinstance(body, dict) or not body.get("access_token"):
        raise SystemExit(f"Autenticación Siigo falló (HTTP {status}): {body}")
    return str(body["access_token"])


def get_list(base_url: str, path: str, token: str) -> list[dict[str, Any]]:
    status, body = request_json("GET", f"{base_url}{path}", token=token)
    if status != 200:
        raise SystemExit(f"GET {path} falló (HTTP {status}): {body}")
    if not isinstance(body, list):
        raise SystemExit(f"GET {path} devolvió un JSON inesperado: {body}")
    return [item for item in body if isinstance(item, dict)]


def suggest_taxes(taxes: list[dict[str, Any]]) -> None:
    active = [t for t in taxes if t.get("active", True)]
    for rate in (0, 5, 19):
        candidates = [t for t in active if float(t.get("percentage") or 0) == rate]
        if len(candidates) == 1:
            print(f"SIIGO_TAX_ID_IVA_{rate}={candidates[0]['id']}  # {candidates[0].get('name')}")
        elif candidates:
            print(f"# IVA {rate}% tiene {len(candidates)} candidatos; elegí manualmente:")
            for candidate in candidates:
                print(f"#   id={candidate['id']} name={candidate.get('name')}")


def suggest_payments(payments: list[dict[str, Any]]) -> None:
    active = [p for p in payments if p.get("active", True)]

    def find(*terms: str) -> list[dict[str, Any]]:
        return [
            p for p in active
            if any(term in str(p.get("name", "")).lower() for term in terms)
        ]

    mappings = {
        "CASH": find("efectivo", "contado"),
        "CARD": find("tarjeta", "crédito", "credito"),
        "TRANSFER": find("transferencia", "consignación", "consignacion"),
        "CHECK": find("cheque"),
    }
    for key, candidates in mappings.items():
        if len(candidates) == 1:
            env = "SIIGO_PAYMENT_TYPE_" + key
            print(f"{env}={candidates[0]['id']}  # {candidates[0].get('name')}")
        elif candidates:
            print(f"# {key} tiene {len(candidates)} candidatos; elegí manualmente:")
            for candidate in candidates:
                print(f"#   id={candidate['id']} name={candidate.get('name')}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env", default=".env", help="Ruta al archivo .env (default: .env)")
    args = parser.parse_args()
    load_env(Path(args.env))

    base_url = os.environ.get("SIIGO_BASE_URL", "https://api.siigo.com").rstrip("/")
    token = auth(base_url)

    taxes = get_list(base_url, "/v1/taxes", token)
    payments = get_list(base_url, "/v1/payment-types?document_type=FV", token)

    print("\n== Impuestos Siigo (/v1/taxes) ==")
    for tax in taxes:
        print(
            f"id={tax.get('id')} type={tax.get('type')} "
            f"percentage={tax.get('percentage')} active={tax.get('active')} "
            f"name={tax.get('name')}"
        )
    print("\nSugerencias para .env:")
    suggest_taxes(taxes)

    print("\n== Formas de pago Siigo (/v1/payment-types?document_type=FV) ==")
    for payment in payments:
        print(
            f"id={payment.get('id')} type={payment.get('type')} "
            f"active={payment.get('active')} due_date={payment.get('due_date')} "
            f"name={payment.get('name')}"
        )
    print("\nSugerencias para .env:")
    suggest_payments(payments)


if __name__ == "__main__":
    main()
