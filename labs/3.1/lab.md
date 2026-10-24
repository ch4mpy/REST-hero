# TP 3.1 — `@Entity`

> Support de cours : [`@Entity`](README.md#jpa-entity)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir reconnaître une entité JPA, comprendre qu'une simple classe décorée de `@Table`
ou utilisée par un repository ne suffit pas, et savoir pourquoi `@Entity` est le point d'entrée de la prise en charge
JPA par Hibernate.

## Consignes

Lancer `./start-lab.sh 3.1` pour créer la branche `lab/3.1`.

1. Lancer `mvn -pl customer-service -am verify -Popenapi,h2 -DskipTests`. Constater l'échec au démarrage : Hibernate ne
	considère plus `Beneficiary` comme une entité gérée.
2. Retrouver dans `api/customer-service/src/main/java/com/c4soft/resthero/customer/domain/Beneficiary.java` le repère
	`LAB:3.1` sur l'annotation de classe et la reconstruire.
3. Relancer la même commande et vérifier que l'application démarre de nouveau. Expliquer pourquoi `@Table` seul ne
	suffit pas : c'est `@Entity` qui enregistre la classe dans le modèle JPA.
4. En complément, repérer dans les autres entités du projet les annotations `@Embedded` / `@Embeddable` et expliquer
	la différence entre une entité persistée dans sa propre table et un objet valeur embarqué dans une autre entité.
