# TP 6.2 — Relais par la gateway : RabbitMQ vers Server-Sent Events

> Support de cours : [Relais par la gateway](README.md#messaging-gateway-sse)

## Objectifs

À l'issue de ce TP, le stagiaire doit savoir exposer un endpoint Server-Sent Events avec `SseEmitter`, comprendre
comment un registre d'abonnements peut diffuser un évènement à son propriétaire *ou* à des rôles d'audience sans
connaître le métier de la ressource, et savoir documenter un flux SSE dans une spec OpenAPI (et pourquoi cette
documentation ne couvre que la donnée, pas le protocole SSE en entier).

## Consignes

Lancer `./lab.sh 6.2` pour créer la branche `lab/6.2`.

1. Lancer `mvn -pl gateway clean compile`. Constater l'échec de compilation sur tout le package
   `com.c4soft.resthero.gateway.events` : `package org.springframework.amqp.core does not exist`,
   `cannot find symbol: class RabbitListener`, etc. Une seule dépendance manquante suffit à casser la compilation de
   classes qui, elles, sont déjà entièrement écrites.
2. Retrouver dans `api/gateway/pom.xml` (repère `LAB:6.2`) la dépendance manquante, et la reconstruire. Relancer
   `mvn -pl gateway clean compile` et vérifier que la compilation réussit.
3. Ouvrir `SseController.java` : repérer les deux marqueurs `LAB:6.2`, l'un sur une annotation (documentation OpenAPI
   du flux), l'autre dans le corps de la méthode `subscribeToServerStateChangedEvents` (extraction des rôles de
   l'utilisateur depuis son `Authentication`, puis enregistrement auprès du registre). Les reconstruire.
4. Ouvrir `DomainEventListener.java`, repérer le marqueur `LAB:6.2` dans `onDomainEvent`, et le reconstruire.
5. Ouvrir `SseEmitterRegistry.java` (donné, non marqué) et expliquer la méthode `broadcast` : pourquoi teste-t-elle à
   la fois `subscription.subject().equals(event.resourceOwner())` et une intersection avec `event.audienceRoles()`,
   plutôt que de ne garder que le `subject` ? Donner un exemple concret où ça change le résultat (un conseiller avec
   l'autorité `account.read_any` qui consulte un compte qui n'est pas le sien).
6. Avec l'environnement de dev lancé (`./deploy-dev.sh`, ou au moins `docker compose -f compose-rabbitmq.yml
   -f compose-keycloak.yml -f compose-reverse-proxy.yml up -d` et l'API démarrée), se connecter avec un utilisateur de
   test puis, depuis un autre terminal, s'abonner au flux : `curl -N --cookie "<cookie de session>"
   https://host.docker.internal/gateway/bff/events`. Faire un virement concernant cet utilisateur depuis une autre
   session, et observer la ligne `data: {...}` arriver sur le `curl` resté ouvert.
7. Rejouer le même essai sans être connecté (sans cookie) et constater un `401`. Expliquer pourquoi
   `@PreAuthorize("isAuthenticated()")` est nécessaire ici alors que `/me` (chapitre gateway) accepte, lui, les
   utilisateurs anonymes.
8. Question de compréhension : que se passerait-il si la gateway tournait avec deux instances derrière le
   reverse-proxy, et que la connexion SSE d'un utilisateur était ouverte sur l'instance A pendant qu'un évènement le
   concernant arrive sur l'instance B ? (voir la limite documentée dans le support de cours).
