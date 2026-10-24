# TP 4.5 — Génération de la documentation OpenAPI

> Support de cours : [Génération de la documentation OpenAPI](README.md#rest-controller-openapi)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir enrichir la spec OpenAPI générée à partir du code, comprendre les
limites de l'inférence automatique de Springdoc, et savoir documenter explicitement un paramètre ou un chemin qui
utilise une conversion Spring implicite.

## Consignes

Lancer `./start-lab.sh 4.5` pour créer la branche `lab/4.5`.

1. Lancer `mvn -pl account-service -am verify -Popenapi,h2 -DskipTests`.
	Constater que la documentation OpenAPI générée pour la route de consultation d'un virement est incomplète.
2. Retrouver dans `api/account-service/src/main/java/com/c4soft/resthero/account/web/MoneyTransferController.java` le
	repère `LAB:4.5` sur l'annotation `@Parameter` de la méthode `getMoneyTransfer(...)`, et la reconstruire.
3. Relancer la même commande et vérifier que la spec générée décrit à nouveau correctement l'identifiant de virement.
	Expliquer pourquoi Springdoc a besoin d'une annotation explicite alors que Spring sait déjà convertir le paramètre.
4. Ouvrir également `AccountController.java` et `CustomerController.java` pour repérer les autres paramètres documentés
	explicitement avec `@Parameter`.
