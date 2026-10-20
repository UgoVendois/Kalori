#!/usr/bin/env python3
"""Génère les pictogrammes d'exercices de Kalori (créations originales, voir ASSETS_LICENSES.md).

Chaque pictogramme est une silhouette en traits dans une grille 24 × 24 : une tête (disque plein)
et des segments (corps, membres, barre, sol). Sortie : app/src/main/res/drawable/ic_exercise_*.xml.
La couleur est appliquée par le composable Icon (teinte), le noir n'est qu'une valeur par défaut.

Usage : python3 tools/gen_exercise_icons.py
"""

from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "drawable"

# nom : (tête (x, y), [polylignes])
ICONS = {
    "squat": ((8, 5), [[(9, 7), (11, 12)], [(11, 12), (16, 12), (15, 19), (18, 19)], [(10, 8), (16, 9)]]),
    "hinge": ((16, 6), [[(15, 8), (9, 11)], [(9, 11), (9, 19), (11, 19)], [(14, 9), (13, 15)], [(9.5, 15), (16.5, 15)]]),
    "lunge": ((11, 4), [[(11, 6), (11, 12)], [(11, 12), (16, 14), (16, 19), (18, 19)], [(11, 12), (7, 16), (4, 18)], [(11, 7), (9, 11)]]),
    "bridge": ((4, 17), [[(6, 18), (12, 13)], [(12, 13), (16, 14), (17, 19)], [(6, 19), (10, 19)], [(2, 20.5), (22, 20.5)]]),
    "calf": ((12, 4), [[(12, 6), (12, 13)], [(12, 13), (11, 18), (12, 20)], [(12, 13), (13, 18), (14, 20)], [(9, 20.5), (16, 20.5)],
                       [(18.5, 11), (18.5, 5)], [(17, 6.5), (18.5, 5), (20, 6.5)]]),
    "push_horizontal": ((5, 11), [[(6, 12.5), (19, 15)], [(8, 13), (8, 18)], [(19, 15), (21, 18)], [(2, 19), (22, 19)]]),
    "push_vertical": ((12, 7), [[(12, 9), (12, 16)], [(12, 16), (10, 21)], [(12, 16), (14, 21)],
                                [(12, 10), (8, 8), (8, 3)], [(12, 10), (16, 8), (16, 3)], [(5, 3), (19, 3)]]),
    "pull_horizontal": ((7, 8), [[(8, 10), (14, 13)], [(14, 13), (15, 19)], [(14, 13), (17, 18)], [(10, 11), (10, 15), (12, 15)], [(8.5, 16.5), (15.5, 16.5)]]),
    "pull_vertical": ((12, 7), [[(4, 3), (20, 3)], [(8, 3), (10, 8)], [(16, 3), (14, 8)], [(12, 9), (12, 15)], [(12, 15), (11, 21)], [(12, 15), (13, 21)]]),
    "dip": ((12, 5), [[(4, 12), (9, 12)], [(15, 12), (20, 12)], [(7, 12), (9, 8)], [(17, 12), (15, 8)], [(12, 7), (12, 14)], [(12, 14), (10, 19)], [(12, 14), (14, 19)]]),
    "raise": ((12, 4), [[(12, 6), (12, 14)], [(12, 8), (4, 8)], [(12, 8), (20, 8)], [(12, 14), (10, 21)], [(12, 14), (14, 21)], [(4, 6.5), (4, 9.5)], [(20, 6.5), (20, 9.5)]]),
    "curl": ((12, 4), [[(12, 6), (12, 14)], [(12, 8), (14, 12), (17, 8.5)], [(16, 7), (18.5, 9.5)], [(12, 8), (10, 13)], [(12, 14), (10, 21)], [(12, 14), (14, 21)]]),
    "triceps": ((12, 5), [[(12, 7), (12, 15)], [(12, 8), (14, 3), (17.5, 6)], [(12, 8), (10, 3), (6.5, 6)], [(12, 15), (10, 21)], [(12, 15), (14, 21)]]),
    "core": ((8, 13), [[(9, 15), (14, 18)], [(14, 18), (17, 14), (20, 18)], [(9, 14), (12, 12)], [(2, 19.5), (22, 19.5)]]),
    "plank": ((5, 12), [[(6, 13), (20, 16)], [(7, 14), (7, 18), (10, 18)], [(20, 16), (21, 18)], [(2, 19), (22, 19)]]),
    "cardio": ((14, 4), [[(13, 6), (11, 12)], [(11, 12), (15, 15), (14, 20)], [(11, 12), (8, 16), (5, 15)], [(12, 8), (16, 10)], [(12, 8), (9, 10)]]),
    "jump": ((12, 4), [[(12, 6), (12, 12)], [(12, 7), (8, 3)], [(12, 7), (16, 3)], [(12, 12), (8, 17)], [(12, 12), (16, 17)], [(6, 21), (18, 21)]]),
}

HEAD_RADIUS = 2.0
STROKE = 1.8


def fmt(v: float) -> str:
    return f"{v:g}"


def vector(head, lines) -> str:
    hx, hy = head
    r = HEAD_RADIUS
    head_path = f"M{fmt(hx - r)},{fmt(hy)}a{fmt(r)},{fmt(r)} 0 1,0 {fmt(2 * r)},0a{fmt(r)},{fmt(r)} 0 1,0 {fmt(-2 * r)},0Z"
    strokes = " ".join("M" + " L".join(f"{fmt(x)},{fmt(y)}" for x, y in line) for line in lines)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Pictogramme original Kalori, généré par tools/gen_exercise_icons.py : ne pas éditer à la main. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="{head_path}" />
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FF000000"
        android:strokeWidth="{STROKE}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="{strokes}" />
</vector>
'''


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (head, lines) in ICONS.items():
        (OUT / f"ic_exercise_{name}.xml").write_text(vector(head, lines), encoding="utf-8")
    print(f"{len(ICONS)} pictogrammes écrits dans {OUT}")


if __name__ == "__main__":
    main()
