# TP 3.8 — Transactions

> Support de cours : [Transactions](README.md#jpa-transactions)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir pourquoi une opération métier qui touche plusieurs ressources JPA doit
parfois être découpée en transactions distinctes, et comprendre la différence entre une transaction de service et une
transaction `REQUIRES_NEW` portée par un bean délégué.

## Consignes

Lancer `./start-lab.sh 3.8` pour créer la branche `lab/3.8`.

1. Lancer `mvn -pl card-service test -Dtest=CardPaymentServiceApplicationTests -Dsurefire.failIfNoSpecifiedTests=false`.
	Constater l'échec du test de paiement de carte : le paiement ne survit plus à l'échec du virement externe.
2. Retrouver dans `api/card-service/src/main/java/com/c4soft/resthero/card/web/CardController.java` le repère `LAB:3.8`
	sur la méthode `createPayemnt(...)` du helper transactionnel et le reconstruire.
3. Relancer la même commande et vérifier que le paiement reste bien enregistré comme `not accepted` lorsque le
	transfert échoue.
4. Expliquer pourquoi l'annotation doit être portée par le bean délégué, et non par un appel interne du même bean,
	pour que le proxy Spring puisse réellement l'intercepter.
