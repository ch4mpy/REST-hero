# TP 3.4 — Conversion de types

> Support de cours : [Conversion de types](README.md#jpa-type-converter)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir transformer un type métier en type de stockage via un
`AttributeConverter`, comprendre la différence entre `autoApply = true` et un `@Convert` explicite, et voir pourquoi
le même convertisseur peut servir à plusieurs entités du projet.

## Consignes

Lancer `./start-lab.sh 3.4` pour créer la branche `lab/3.4`.

1. Lancer `mvn -pl account-service test -Dtest=AccountServiceApplicationTests -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater l'échec au démarrage ou à la persistance : Hibernate ne sait plus convertir correctement le type `Iban`.
2. Retrouver dans `api/rest-hero-starter-common/src/main/java/com/c4soft/resthero/commons/jpa/IbanStringAttributeConverter.java`
	le repère `LAB:3.4` et reconstruire l'annotation manquante.
3. Relancer la même commande et vérifier que le type `Iban` est de nouveau persisté et relu sans code de conversion
	manuel dans les services.
4. Expliquer pourquoi ce convertisseur est déclaré une seule fois dans le starter commun, puis réutilisé par plusieurs
	entités avec ou sans `@Convert` explicite selon le besoin.
