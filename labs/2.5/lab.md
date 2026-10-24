# TP 2.5 — Proxies générés

> Support de cours : [Proxies générés](README.md#spring-proxies)

## Objectifs

À l'issue de ce TP, le stagiaire doit comprendre pourquoi certaines méthodes sont déléguées à une classe interne
séparée pour que `@Transactional` fonctionne correctement, et surtout comprendre qu'un tel délégué ne sert à rien
s'il n'est pas lui-même un bean Spring proxifiable.

## Consignes

Lancer `./start-lab.sh 2.5` pour créer la branche `lab/2.5`.

1. Ouvrir `CardController.java` (`card-service`) et repérer la classe imbriquée statique
   `TransactionalCardPaymentHelper`, injectée dans `CardController` et utilisée pour exécuter certaines opérations
   dans leur propre transaction (`@Transactional(propagation = Propagation.REQUIRES_NEW, ...)`). Expliquer
   pourquoi ce code n'appelle pas directement des méthodes `@Transactional` du `CardController` lui-même : un
   appel d'une méthode vers une autre méthode du même bean ne passe jamais par le proxy Spring, et
   `@Transactional` serait alors silencieusement ignoré (cf. exemple du support de cours).
2. Lancer `mvn -pl card-service -am clean compile`. La compilation réussit : rien n'empêche
   `TransactionalCardPaymentHelper` de compiler sans annotation de stéréotype.
3. Lancer `mvn -pl card-service -am verify -Popenapi,h2 -DskipTests`. Constater l'échec au démarrage :
   `APPLICATION FAILED TO START`, avec un message indiquant qu'aucun bean de type
   `CardController$TransactionalCardPaymentHelper` n'a pu être trouvé pour le constructeur de `CardController`.
4. Retrouver dans `CardController.java` (repère `LAB:2.5`) l'annotation manquante sur
   `TransactionalCardPaymentHelper`, et la reconstruire. Relancer `mvn -pl card-service -am verify -Popenapi,h2
   -DskipTests` et vérifier que l'application démarre.
5. Expliquer pourquoi ce délégué doit impérativement être un bean Spring (`@Service` ici) et non une simple classe
   instanciée à la main (`new TransactionalCardPaymentHelper(...)`) : c'est uniquement parce que c'est un bean que
   Spring peut en générer un proxy, seul capable d'intercepter les appels à ses méthodes `@Transactional` avant de
   déléguer à l'objet réel.
