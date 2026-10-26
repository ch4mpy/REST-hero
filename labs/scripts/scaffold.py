#!/usr/bin/env python3
"""Scaffolde labs/<id>/lab.md pour chaque entrée numérotée du sommaire de README.md.

N'écrase jamais un lab.md déjà présent : à relancer sans risque après une mise à
jour du sommaire de README.md, pour ne créer que les squelettes manquants.
Voir labs/README.md pour la suite (ajout des marqueurs, rédaction de l'énoncé).
"""

import re
import sys
from pathlib import Path

TOC_ENTRY = re.compile(
    r"^\s*[-*]\s+\[(?P<id>\d+(?:\.\d+)*)\.\s*(?P<title>.+?)\]\(#(?P<anchor>[\w-]+)\)\s*$"
)


def parse_toc_entries(readme_path):
    entries = []
    for line in readme_path.read_text(encoding="utf-8").splitlines():
        m = TOC_ENTRY.match(line)
        if m:
            entries.append((m.group("id"), m.group("title"), m.group("anchor")))
    return entries


def lab_md_template(lab_id, title, anchor):
    return f"""# TP {lab_id} — {title}

> Support de cours : [{title}](README.md#{anchor})

## Objectifs

TODO: décrire ce que le stagiaire doit être capable de faire à l'issue du TP.

## Consignes

Lancer `./start-lab.sh {lab_id}` pour créer la branche `lab/{lab_id}`.

TODO: décrire ce qu'il doit reconstruire (s'appuyer sur les `// TODO` laissés
dans le code par les marqueurs `LAB:{lab_id}:...`, s'il y en a).
"""


def main():
    repo_root = Path(__file__).resolve().parents[2]
    readme_path = repo_root / "README.md"
    labs_root = repo_root / "labs"

    entries = parse_toc_entries(readme_path)
    if not entries:
        print("Aucune entrée numérotée trouvée dans le sommaire de README.md.", file=sys.stderr)
        sys.exit(1)

    created = []
    for lab_id, title, anchor in entries:
        lab_md = labs_root / lab_id / "lab.md"
        if lab_md.exists():
            continue
        lab_md.parent.mkdir(parents=True, exist_ok=True)
        lab_md.write_text(lab_md_template(lab_id, title, anchor), encoding="utf-8")
        created.append(lab_id)

    print(f"{len(entries)} entrée(s) numérotée(s) trouvée(s) dans le sommaire de README.md.")
    if created:
        print(f"{len(created)} squelette(s) créé(s) :")
        for lab_id in created:
            print(f"  - labs/{lab_id}/lab.md")
    else:
        print("Rien à créer, tous les labs/<id>/lab.md existent déjà.")


if __name__ == "__main__":
    main()