# TP 1.3 — Processeurs d’annotations à la compilation

> Support de cours : [Processeurs d’annotations à la compilation](README.md#maven-build-annotations-preprocessing)

## Objectifs

À l'issue de ce TP, le stagiaire doit intégrer le piège le plus fréquent sur ce sujet : dès qu'on déclare la section
`annotationProcessorPaths` explicitement dans le `maven-compiler-plugin`, Maven cesse de détecter automatiquement les
processeurs d'annotations présents sur le classpath de compilation. Tout processeur non listé explicitement s'arrête de
fonctionner, silencieusement, sans erreur de configuration.

## Consignes

Lancer `./start-lab.sh 1.3` pour créer la branche `lab/1.3`.

1. Lancer `mvn -pl account-service -am clean compile` depuis `api/`. Constater l'échec de compilation dès le module
   `rest-hero-starter-common`, avec des erreurs telles que "constructor ... cannot be applied to given types" ou
   "variable ... not initialized in the default constructor" sur des classes comme `Iban` ou `Amount`. Ces classes
   utilisent des annotations Lombok (`@AllArgsConstructor`, `@Getter`, etc.) dont le code généré est absent.
2. Retrouver dans `api/pom.xml` (repère `LAB:1.3`) le `<path>` manquant dans `annotationProcessorPaths` du
   `maven-compiler-plugin`, et le reconstruire. Relancer `mvn -pl account-service -am clean compile` et vérifier que
   tout compile de nouveau.
3. Expliquer pourquoi retirer uniquement le `<path>` de Lombok suffit à casser la compilation de tout le projet, alors
   que les autres `<path>` (MapStruct, `spring-boot-configuration-processor`, etc.) restent déclarés et fonctionnent :
   dès qu'un seul `<path>` est ajouté explicitement dans `annotationProcessorPaths`, la détection automatique par
   classpath est désactivée pour l'ensemble des processeurs, y compris ceux qu'on croyait toujours actifs faute d'y être
   listés. Expliquer également pourquoi ajouter le `<path>` de Lombok en dernière position ne permet pas au projet de
   compiler.
4. Vérifier dans son IDE (Eclipse ou IntelliJ) que le traitement des annotations est bien activé dans les préférences du
   projet. Expliquer pourquoi un projet qui compile parfaitement avec `mvn compile` peut malgré tout afficher des
   erreurs dans l'IDE (classes générées par Lombok/MapStruct non reconnues) si ce réglage IDE diverge de la
   configuration Maven.
