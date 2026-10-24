# TP 2.6 — Tests

> Support de cours : [Tests](README.md#spring-testing)

## Objectifs

À l'issue de ce TP, le stagiaire doit comprendre le fonctionnement d'un test de tranche `@WebMvcTest`, et savoir
qu'un collaborateur nécessaire à un bean utilisé dans cette tranche mais qui n'en fait pas partie doit être importé
explicitement avec `@Import`, sans quoi le contexte de test échoue à se charger.

## Consignes

Lancer `./start-lab.sh 2.6` pour créer la branche `lab/2.6`.

1. Ouvrir `AccountControllerTest.java` (`account-service`) et observer sa structure : `@WebMvcTest(controllers =
   AccountController.class)` ne charge qu'une tranche réduite du contexte (le contrôleur et l'infrastructure web),
   complétée par `@Import({...})` pour les beans supplémentaires nécessaires (mappers MapStruct, configuration de
   sécurité, etc.) et par des `@MockitoBean` pour remplacer les dépendances qu'on ne veut pas faire fonctionner
   réellement (`AccountRepository`, `MoneyTransferRepository`, `CustomersApi`).
2. Lancer `mvn -pl account-service test -Dtest=AccountControllerTest -Dsurefire.failIfNoSpecifiedTests=false`.
   Constater l'échec : `NoSuchBeanDefinitionException: No qualifying bean of type
   com.c4soft.resthero.commons.domain.IbanStringMapper available`.
3. Retrouver dans `AccountControllerTest.java` (repère `LAB:2.6`) l'entrée manquante dans `@Import({...})`, et la
   reconstruire. Relancer la même commande et vérifier que tous les tests passent.
4. Expliquer pourquoi `IbanStringMapper` doit être importé explicitement ici alors qu'il est auto-configuré pour
   l'application réelle (voir le TP 2.7) : `@WebMvcTest` désactive l'auto-configuration complète de l'application
   et ne charge qu'une tranche ciblée, l'auto-configuration du starter n'en fait pas partie. `AccountMapperImpl`
   (lui-même importé) en a besoin comme collaborateur MapStruct (`uses = {IbanStringMapper.class}` dans
   `AccountMapper`), d'où l'échec de son instanciation en cascade.
5. Expliquer pourquoi ce test remplace `AccountRepository`, `MoneyTransferRepository` et `CustomersApi` par des
   `@MockitoBean` plutôt que d'utiliser les implémentations réelles : un `@WebMvcTest` ne démarre ni base de
   données, ni client REST vers un autre service, ce qui serait de toute façon hors du périmètre d'un test de
   tranche pour un contrôleur.
