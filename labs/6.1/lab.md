# TP 6.1 — Publication d'un événement métier

> Support de cours : [Publication d'un événement métier](README.md#messaging-publish)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir publier un évènement applicatif vers RabbitMQ depuis un contrôleur
Spring, comprendre pourquoi le nom de l'exchange est une propriété externalisée plutôt qu'une constante, et
comprendre comment un évènement porte l'information nécessaire à la gateway pour savoir à qui le diffuser
(propriétaire de la ressource et rôles d'audience), sans que la gateway n'ait besoin de connaître le métier.

## Consignes

Lancer `./lab.sh 6.1` pour créer la branche `lab/6.1`.

1. Lancer `mvn -pl account-service clean compile`. Constater l'échec de compilation :
   `package org.springframework.amqp.core does not exist`, `cannot find symbol: class RabbitTemplate` dans
   `MoneyTransferController.java`.
2. Retrouver dans `api/account-service/pom.xml` (repère `LAB:6.1`) la dépendance manquante, et la reconstruire.
   Relancer `mvn -pl account-service clean compile` et vérifier que la compilation réussit à nouveau.
3. Lancer `mvn -pl account-service test -Dtest=MoneyTransferControllerTest#givenServiceHasTransferAuthority_whenTransferMoney_thenCreated -Ph2`.
   Constater que le test échoue malgré tout : `Wanted but not invoked: RabbitTemplate#0.convertAndSend(...)`,
   `Actually, there were zero interactions with this mock`. La compilation seule ne suffit pas : rien n'est encore
   publié.
4. Retrouver dans `api/account-service/src/main/java/com/c4soft/resthero/account/web/MoneyTransferController.java`
   (repères `LAB:6.1`, il y en a deux : un pour le compte source, un pour le compte destinataire) les appels
   manquants, et les reconstruire.
5. Relancer la même commande de test et vérifier qu'elle passe (le test attend exactement deux publications).
   Expliquer pourquoi un virement publie deux évènements plutôt qu'un seul portant les deux IBAN : les deux comptes
   peuvent appartenir à deux clients différents, chacun avec son propre `subject` à notifier.
6. Expliquer le rôle de chacun des champs de `DomainEvent` (`resourceType`, `resourceId`, `resourceOwner`,
   `audienceRoles`, `eventType`, `occurredAt`) et pourquoi `audienceRoles` reprend exactement l'autorité
   `account.read_any` déjà utilisée dans les `@PreAuthorize` de `AccountController`/`MoneyTransferController`.
7. Si RabbitMQ tourne en local (`docker compose -f compose-rabbitmq.yml up -d`), faire un virement réel via l'API
   (Bruno, Postman ou le frontend), puis ouvrir `http://host.docker.internal:15672` (identifiants dans `.env`) et
   vérifier dans l'onglet *Exchanges* qu'un exchange `rest-hero.account-service` existe, de type `topic`. Expliquer
   pourquoi son nom correspond exactement à `rest-hero.${spring.application.name}` et ce qui se passerait si
   `card-service` publiait un jour ses propres évènements sur un exchange séparé.
