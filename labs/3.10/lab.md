# TP 3.10 — Migrations de schéma avec Liquibase

> Support de cours : [Migrations de schéma avec Liquibase](README.md#jpa-liquibase)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir faire évoluer un schéma par un nouveau changeset plutôt qu'en modifiant
un changeset déjà appliqué, comprendre comment la validation du schéma par Hibernate détecte un écart entre entités et
tables, et reconnaître une contrainte unique qui ne correspond pas à la règle métier.

## Consignes

Lancer `./start-lab.sh 3.10` pour créer la branche `lab/3.10`.

1. Lancer `mvn -pl customer-service -am test -Dtest=BeneficiaryPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater que le contexte ne démarre pas : la validation du schéma (`ddl-auto=validate`) ne trouve pas la table
	`beneficiaries` que l'entité `Beneficiary` attend. Les changesets appliqués ont créé `benficiaries`.
2. Ouvrir `api/customer-service/src/main/resources/db/changelog/db.changelog-master.xml` et retrouver le repère
	`LAB:3.10` en fin de fichier. Lire le changelog `changes/1792864800000-1-beneficiaries-renamed-unique-per-customer.xml`
	puis reconstruire son inclusion.
3. Relancer la même commande et vérifier que les quatre tests passent, dont celui où deux clients enregistrent chacun
	un bénéficiaire du même libellé. Expliquer pourquoi l'ancien schéma l'interdisait (une contrainte unique sur le
	libellé seul) et pourquoi la correction passe par de nouveaux changesets plutôt que par une modification de
	`db.changelog-master.xml`.
4. Repérer dans le même changelog les changesets réservés à une base (`dbms="postgresql"`, `dbms="h2"`) et expliquer
	pourquoi la migration doit fonctionner sur les deux.
5. Lancer `BeneficiaryRepositoryTest` et expliquer pourquoi la suppression d'un bénéficiaire exige que les colonnes
	métier de `beneficiaries_aud` acceptent `null`.
