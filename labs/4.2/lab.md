# TP 4.2 — Convertisseurs automatiques de Spring

> Support de cours : [Convertisseurs automatiques de Spring](README.md#rest-controller-converters)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir faire reconnaître un type métier dans les paramètres de requête ou les
variables de chemin grâce au `FormatterRegistry`, comprendre le rôle d'un `WebMvcConfigurer`, et savoir où brancher
un convertisseur Spring pour un type réutilisé par plusieurs contrôleurs.

## Consignes

Lancer `./start-lab.sh 4.2` pour créer la branche `lab/4.2`.

1. Lancer `mvn -pl card-service test -Dtest=CardControllerTest -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater que les requêtes qui passent un `Iban` en paramètre ne sont plus converties correctement.
2. Retrouver dans `api/rest-hero-starter-common/src/main/java/com/c4soft/resthero/commons/CommonWebConfiguration.java`
	le repère `LAB:4.2` sur l'enregistrement du convertisseur et la reconstruire.
3. Relancer la même commande et vérifier que les paramètres `Iban` sont de nouveau convertis automatiquement.
	Expliquer le rôle de `WebMvcConfigurer.addFormatters(...)` et du `FormatterRegistry`.
4. Repérer les autres usages de `Iban` dans les contrôleurs du projet et vérifier qu'ils bénéficient tous du même
	convertisseur partagé.
