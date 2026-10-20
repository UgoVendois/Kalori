"""Lecture de la table Ciqual (format .xlsx) sans dépendance externe (stdlib uniquement).

Source : Anses, Table de composition nutritionnelle des aliments Ciqual 2025,
https://doi.org/10.57745/RDMHWY — Licence Ouverte / Etalab 2.0.
"""

from __future__ import annotations

import re
import zipfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass

_NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"


def _col_index(ref: str) -> int:
    letters = re.match(r"[A-Z]+", ref).group(0)
    idx = 0
    for ch in letters:
        idx = idx * 26 + (ord(ch) - ord("A") + 1)
    return idx - 1


def read_first_sheet(path: str) -> list[list[str]]:
    """Retourne les lignes de la première feuille (valeurs texte brutes)."""
    with zipfile.ZipFile(path) as z:
        shared: list[str] = []
        if "xl/sharedStrings.xml" in z.namelist():
            root = ET.fromstring(z.read("xl/sharedStrings.xml"))
            for si in root.findall("m:si", _NS):
                shared.append("".join(t.text or "" for t in si.iter(f"{{{_NS['m']}}}t")))

        workbook = ET.fromstring(z.read("xl/workbook.xml"))
        first_sheet = workbook.find("m:sheets/m:sheet", _NS)
        rel_id = first_sheet.get(f"{{{_REL_NS}}}id")
        rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
        target = next(r.get("Target") for r in rels if r.get("Id") == rel_id)
        sheet_path = "xl/" + target.lstrip("/").removeprefix("xl/")

        rows: list[list[str]] = []
        sheet = ET.fromstring(z.read(sheet_path))
        for row in sheet.iter(f"{{{_NS['m']}}}row"):
            values: dict[int, str] = {}
            for cell in row.findall("m:c", _NS):
                v = cell.find("m:v", _NS)
                if cell.get("t") == "inlineStr":
                    text = "".join(t.text or "" for t in cell.iter(f"{{{_NS['m']}}}t"))
                elif v is None:
                    continue
                elif cell.get("t") == "s":
                    text = shared[int(v.text)]
                else:
                    text = v.text or ""
                values[_col_index(cell.get("r"))] = text
            if values:
                width = max(values) + 1
                rows.append([values.get(i, "") for i in range(width)])
        return rows


@dataclass(frozen=True)
class CiqualFood:
    code: str
    name_fr: str
    group: str
    kcal: float | None
    protein: float | None
    fat: float | None
    carbs: float | None
    fiber: float | None
    lactose: float | None
    raw: dict[str, str]


def parse_value(text: str) -> float | None:
    """Ciqual : '-' = non renseigné, 'traces' = traces, '< 0,5' = inférieur à la limite."""
    t = (text or "").strip().replace(",", ".")
    if t in ("", "-"):
        return None
    if t.lower() == "traces":
        return 0.0
    if t.startswith("<"):
        # Valeur inférieure au seuil de quantification : on retient la moitié du seuil (convention prudente).
        try:
            return float(t[1:].strip()) / 2
        except ValueError:
            return None
    try:
        return float(t)
    except ValueError:
        return None


def _normalize(text: str) -> str:
    """Les en-têtes Ciqual contiennent des retours à la ligne à la place des espaces et des '/'."""
    return re.sub(r"\s+", " ", text).strip().lower()


def find_column(header: list[str], *needles: str) -> int:
    """Première colonne dont l'en-tête COMMENCE par le premier terme et contient les suivants.

    Le préfixe évite les faux positifs (« Lactose » est contenu dans « Galactose »).
    """
    first, *others = (_normalize(n) for n in needles)
    for i, h in enumerate(header):
        norm = _normalize(h)
        if norm.startswith(first) and all(n in norm for n in others):
            return i
    raise KeyError(f"Colonne introuvable : {needles}")


def load_foods(path: str) -> dict[str, CiqualFood]:
    rows = read_first_sheet(path)
    header = rows[0]
    c_code = find_column(header, "alim_code")
    c_name = find_column(header, "alim_nom_fr")
    c_group = find_column(header, "alim_ssgrp_nom_fr")
    c_kcal = find_column(header, "Energie, Règlement", "kcal")
    c_prot = find_column(header, "Protéines, N x facteur de Jones")
    c_fat = find_column(header, "Lipides (g")
    c_carb = find_column(header, "Glucides (g")
    c_fiber = find_column(header, "Fibres alimentaires")
    c_lactose = find_column(header, "Lactose (g")
    foods: dict[str, CiqualFood] = {}
    for r in rows[1:]:
        if len(r) <= c_code or not r[c_code].strip():
            continue
        get = lambda i: r[i] if i < len(r) else ""  # noqa: E731
        code = r[c_code].strip()
        foods[code] = CiqualFood(
            code=code,
            name_fr=get(c_name).strip(),
            group=get(c_group).strip(),
            kcal=parse_value(get(c_kcal)),
            protein=parse_value(get(c_prot)),
            fat=parse_value(get(c_fat)),
            carbs=parse_value(get(c_carb)),
            fiber=parse_value(get(c_fiber)),
            lactose=parse_value(get(c_lactose)),
            raw={header[i]: get(i) for i in range(len(header))},
        )
    return foods
