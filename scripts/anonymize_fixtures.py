#!/usr/bin/env python3
"""Anonimiza una respuesta JSON de Canvas para usarla como fixture de test.

Uso:
    python3 scripts/anonymize_fixtures.py <entrada.json> <salida.json>

La entrada puede empezar por el prefijo "while(1);" que Canvas añade con autenticación por cookie.

Qué hace:
- Elimina claves sensibles o inútiles para los tests (secure_params, avatar_url, ...).
- Sustituye nombres de personas por valores fijos.
- Sustituye el identificador del alumno (id de /users/self y user_id) por 1.
- Enmascara direcciones de correo y URLs firmadas que aparezcan dentro de textos.

Conserva IDs de cursos, módulos, páginas y entregas: son datos institucionales, no personales,
y los tests los necesitan para resolver relaciones.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

DROP_KEYS = {
    "secure_params",
    "avatar_url",
    "avatar_image_url",
    "lti_context_id",
    "lti_resource_link_lookup_uuid",
    "uuid_hash",
    "login_id",
    "email",
    "primary_email",
    "sis_user_id",
    "integration_id",
}

PERSON_NAME_KEYS = {
    "name": "Alumne Exemple",
    "sortable_name": "Exemple, Alumne",
    "short_name": "Alumne Exemple",
    "first_name": "Alumne",
    "last_name": "Exemple",
    "display_name": "Alumne Exemple",
}

STUDENT_ID_KEYS = {"user_id", "final_grader_id"}

EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
SIGNED_URL_RE = re.compile(r"(verifier|token|sig|signature)=[A-Za-z0-9%._~-]+")


def looks_like_user(obj: dict[str, Any]) -> bool:
    """Un objeto de usuario de Canvas tiene sortable_name o short_name junto a id."""
    return "id" in obj and ("sortable_name" in obj or "short_name" in obj)


def clean_string(value: str) -> str:
    value = EMAIL_RE.sub("alumne@example.org", value)
    return SIGNED_URL_RE.sub(r"\1=REDACTED", value)


def anonymize(node: Any) -> Any:
    if isinstance(node, dict):
        is_user = looks_like_user(node)
        result: dict[str, Any] = {}
        for key, value in node.items():
            if key in DROP_KEYS:
                continue
            if is_user and key in PERSON_NAME_KEYS:
                result[key] = PERSON_NAME_KEYS[key]
            elif is_user and key == "id":
                result[key] = 1
            elif key in STUDENT_ID_KEYS and isinstance(value, int):
                result[key] = 1
            else:
                result[key] = anonymize(value)
        return result
    if isinstance(node, list):
        return [anonymize(item) for item in node]
    if isinstance(node, str):
        return clean_string(node)
    return node


def load(path: Path) -> Any:
    text = path.read_text(encoding="utf-8").lstrip()
    if text.startswith("while(1);"):
        text = text[len("while(1);") :]
    return json.loads(text)


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    source, target = Path(argv[1]), Path(argv[2])
    data = anonymize(load(source))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{source} -> {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
