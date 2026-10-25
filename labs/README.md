# Génération des énoncés de TP

Ce répertoire permet de générer, pour la quasi-totalité des entrées numérotées du sommaire de
`README.md` (ex: `1.4.3`), une branche `lab/<id>` qui reprend l'état courant de
`main` avec quelques éléments clefs retirés du code, à charge pour le stagiaire de les reconstruire.

## Pour le stagiaire

```bash
./labs/scripts/build-lab.sh 1.4.3
```

Ceci (re)crée la branche `lab/1.4.3` à partir de `origin/main`, retire les éléments à reconstruire, et place l'énoncé du
TP dans `lab.md` à la racine du repo.

La commande peut être relancée à tout moment (par exemple après une mise à jour de `main`). Si la branche `lab/1.4.3`
existe déjà en local, le script demande quoi en faire :

- **b**asculer dessus telle quelle, sans rien changer (pour reprendre un TP en cours) ;
- ou la **r**éinitialiser depuis `origin/main` (l'énoncé reflète alors l'état actuel de `main`, mais tout travail local
  fait directement sur `lab/<id>`
  est perdu — il faut donc l'avoir committé sur une autre branche au préalable).

## Pour la personne qui prépare les TPs

Chaque TP est composé de deux choses, versionnées sur `main` :

1. `labs/<id>/lab.md` : l'énoncé du TP, tel qu'il sera copié à la racine du dépôt sur la branche `lab/<id>`.
   `./labs/scripts/scaffold.py` génère un squelette pour chaque entrée du sommaire de `README.md` qui n'en a pas encore
   (il n'écrase jamais un `lab.md` existant).
2. Des marqueurs en commentaire, directement dans le code source de `main`, autour des éléments que le stagiaire doit
   reconstruire pour ce TP. Le script `build-lab.sh` parcourt tout le dépôt à la recherche de marqueurs correspondant à
   l'id demandé, quel que soit le fichier où ils se trouvent.

Un TP sans marqueur reste valide : c'est un TP purement théorique, la branche
`lab/<id>` ne contient alors que `lab.md` en plus de `main`.

### Syntaxe des marqueurs

Deux formes, selon que l'élément doit disparaître entièrement ou être remplacé par un repère pour le stagiaire :

**Retrait complet** (rien ne subsiste, y compris les marqueurs eux-mêmes) :

```java
// LAB:1.4.3:REMOVE:START
@Valid
// LAB:1.4.3:REMOVE:END
@RequestBody
MoneyTransferRequest dto
```

**Retrait avec un repère `TODO`** (le bloc est remplacé par un unique commentaire, avec un message optionnel) :

```java
// LAB:1.4.3:TODO:START Ajouter la validation des champs d'entrée
@Valid
// LAB:1.4.3:TODO:END
@RequestBody
MoneyTransferRequest dto
```

devient, sur la branche `lab/1.4.3` :

```java
// TODO: Ajouter la validation des champs d'entrée
@RequestBody
MoneyTransferRequest dto
```

Les deux formes fonctionnent aussi en XML (commentaire `<!-- ... -->`) et en YAML/`.properties` (commentaire `#`) :

```xml
<!-- LAB:3.9:REMOVE:START -->
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-envers</artifactId>
</dependency>
        <!-- LAB:3.9:REMOVE:END -->
```

```yaml
# LAB:2.3:TODO:START renseigner le mot de passe applicatif
spring:
  datasource:
    password: change-me
# LAB:2.3:TODO:END
```

Règles :

- Le marqueur `START` et le marqueur `END` doivent porter le même id et le même verbe (`REMOVE` ou `TODO`).
- Un marqueur tient sur une seule ligne, commentaire refermé compris : un commentaire XML ouvert sur une ligne et
  refermé sur la suivante n'est pas reconnu, et la branche sort identique à `main`.
- Les marqueurs ne s'imbriquent pas pour un même id : le premier `END`
  rencontré après un `START` referme le bloc.
- Un même fichier peut porter des marqueurs pour plusieurs TPs différents ; chaque génération de branche ne traite que
  l'id demandé et laisse les marqueurs des autres TPs intacts.
- Le retrait n'est qu'une opération texte : à l'auteur du TP de vérifier que le code obtenu après retrait compile
  toujours (ou, pour un retrait complet, que l'élément peut disparaître sans rien casser ailleurs).

### Commandes

```bash
# Scaffolder les labs/<id>/lab.md manquants pour toutes les entrées du sommaire
./labs/scripts/scaffold.py

# Prévisualiser le résultat du retrait pour un id, sans créer de branche
./labs/scripts/strip_markers.py 1.4.3 .
```

### Vérifier les branches de TP

Le retrait n'étant qu'une opération texte, un changement de `main` qui touche un fichier marqué peut casser une branche
de TP. Cette boucle génère chaque branche dans un dossier temporaire et dit si elle compile (à lancer depuis la racine
du dépôt, après une vérification complète qui a écrit les specs dans `frontend/openapi`) :

```bash
for id in 1.4 1.6 2.3 2.6 3.10 4.4 4.6; do   # les TP concernés ; la liste des dossiers de labs/ pour tous
  tmp=$(mktemp -d); git archive HEAD | tar -x -C "$tmp"
  mkdir -p "$tmp/frontend/openapi" && cp frontend/openapi/*.openapi.json "$tmp/frontend/openapi/"
  cp .env "$tmp/"
  ./labs/scripts/strip_markers.py "$id" "$tmp" >/dev/null
  if (cd "$tmp/api" && ./mvnw -q -DskipTests -Ph2 test-compile >"$tmp/build.log" 2>&1); then
    echo "COMPILES $id"
  else
    echo "BREAKS   $id  ($(grep -m1 -E 'ERROR.*\.java' "$tmp/build.log"))"
  fi
  rm -rf "$tmp"
done
```

Une branche qui compile doit ensuite échouer comme son énoncé l'annonce (lancer la commande de test de son `lab.md`
dans le dossier temporaire). Celles dont l'énoncé fait constater un échec de compilation (1.1.1, 1.3, 1.5, 2.1, 3.5,
3.6, 3.7, 6.1 et 6.2) ne compilent pas, par conception : vérifier que l'erreur est celle que l'énoncé décrit.

Aucun énoncé ne doit plus citer l'ancien point d'entrée `lab.sh` ; cette commande ne doit rien afficher :

```bash
grep -nE '(^|[^-])lab\.sh' labs/*/lab.md labs/scripts/* README.md | grep -vE 'start-lab|exit-lab|build-lab'
```

## Limite connue

Le contenu retiré reste consultable dans l'historique Git de `main` (et dans les autres branches). Ce mécanisme dissuade
la lecture directe de la solution, il ne l'empêche pas.