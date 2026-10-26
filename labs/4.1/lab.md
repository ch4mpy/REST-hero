# TP 4.1 — `@RequestMapping`

> Support de cours : [`@RequestMapping`](README.md#rest-controller-request-mapping)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir lire une route Spring WebMvc à partir de ses annotations de mapping,
comprendre comment une classe `@RestController` et ses méthodes `@GetMapping` / `@PostMapping` définissent un point
d'entrée HTTP, et repérer l'effet d'une annotation de mapping manquante sur le routage.

## Consignes

Lancer `./start-lab.sh 4.1` pour créer la branche `lab/4.1`.

1. Lancer `mvn -pl customer-service test -Dtest=CustomerControllerTest -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater qu'un des endpoints n'est plus routé correctement et que les tests de lecture de la liste des clients
	échouent.
2. Retrouver dans `api/customer-service/src/main/java/com/c4soft/resthero/customer/web/CustomerController.java` le
	repère `LAB:4.1` placé sur l'annotation de mapping de `listCustomers(...)`, et la reconstruire.
3. Relancer la même commande et vérifier que les tests repassent au vert. Expliquer comment cette annotation complète
	le `@RequestMapping` de classe pour définir la route finale.
4. Ouvrir aussi `CurrencyController.java` et `MeController.java` pour comparer un endpoint simple à un contrôleur plus
	riche en mappings, et repérer la différence entre mapping de classe et mapping de méthode.
