# TP 3.9 — Hibernate Envers

> Support de cours : [Hibernate Envers](README.md#jpa-envers)

## Objectifs

À l'issue de ce TP, le stagiaire doit comprendre comment Envers versionne les entités auditées, comment une entité de
récapitulatif de révision peut être enrichie par Spring Security, et pourquoi `@RevisionEntity` est le point de
jonction entre Envers et le code applicatif.

## Consignes

Lancer `./start-lab.sh 3.9` pour créer la branche `lab/3.9`.

1. Ouvrir `api/account-service/src/main/java/com/c4soft/resthero/account/PersistenceConfiguration.java` et repérer le
	bloc `Revinfo` qui décrit les métadonnées des révisions (`REV`, `REVTSTMP`, `USERNAME`).
2. Retrouver le repère `LAB:3.9` sur l'annotation qui relie cette classe à Hibernate Envers et la reconstruire.
3. Relancer `mvn -pl account-service -am verify -Popenapi,h2 -DskipTests` et vérifier que l'application démarre tout
	en conservant une entité de révision personnalisée.
4. Expliquer pourquoi le listener `SecurityAwareRevisionListener` ne suffit pas à lui seul : c'est
	`@RevisionEntity` qui dit à Envers quelle classe utiliser pour stocker les informations de révision.
