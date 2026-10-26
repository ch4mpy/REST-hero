# TP 1.1.1 — Structure

> Support de cours : [Structure](README.md#maven-build-structure)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir lire un POM multi-module et expliquer le rôle de chacune de ses
sections (`groupId`/`artifactId`/`version`, `packaging`, `properties`, `modules`, `dependencyManagement`,
`dependencies`, `build`, `profiles`). Il doit surtout avoir compris la différence entre "gérer" une version
(`dependencyManagement`) et "ajouter" une dépendance effective (`dependencies`), erreur de débutant la plus fréquente
sur ce sujet.

## Consignes

Lancer `./start-lab.sh 1.1.1` pour créer la branche `lab/1.1.1`.

1. Dans `api/pom.xml`, un `// TODO` a remplacé la déclaration du module `card-service` dans `<modules>`. Le
   restaurer, puis expliquer avec `mvn -pl card-service -am validate` la différence entre un module simplement
   présent sur le disque (buildable seul, `cd api/card-service && mvn compile`, car son POM référence son parent via
   `relativePath`) et un module réellement intégré au reactor du parent.
2. Lancer `mvn -pl account-service -am clean compile` et constater l'échec de compilation (nombreuses erreurs
   `cannot find symbol` sur des classes comme `Iban`, `Amount` ou `IbanStringMapper`). Retrouver dans
   `api/account-service/pom.xml` la dépendance manquante vers `rest-hero-starter-common` (marquée par un
   commentaire `LAB:1.1.1`) et la reconstruire, sans lui ajouter de `<version>`.
3. Avant de reconstruire cette dépendance, vérifier dans `api/pom.xml` qu'une entrée `rest-hero-starter-common`
   existe déjà dans `<dependencyManagement>`. Expliquer pourquoi sa seule présence ne suffisait pas à faire
   compiler `account-service`, et pourquoi il ne faut pas non plus recopier de `<version>` une fois la dépendance
   ajoutée.
4. Une fois les deux corrections faites, relancer `mvn -pl account-service -am clean compile` depuis `api/` et
   vérifier que tout compile. Expliquer à quoi sert l'option `-am` dans cette commande.
