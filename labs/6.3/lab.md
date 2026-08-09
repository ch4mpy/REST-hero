# TP 6.3 — Abonnement du frontend

> Support de cours : [Abonnement du frontend](README.md#messaging-frontend)

## Objectifs

À l'issue de ce TP, le stagiaire doit comprendre comment un flux Server-Sent Events s'intègre avec un cache de
requêtes côté client (invalidation plutôt que confiance dans le contenu de l'évènement), et être capable d'expliquer
les deux limites de sécurité et de scalabilité du mécanisme mis en place dans ce chapitre.

Ce TP n'a pas de marqueur `LAB:` à reconstruire : le mécanisme de marqueurs de ce dépôt ne s'applique pas au frontend (sous-module git séparé). C'est un TP d'observation.

## Consignes

Lancer `./lab.sh 6.3`.

1. Lancer l'environnement complet (`./deploy-dev.sh`, ou reprendre l'environnement du TP 6.2), puis `cd frontend &&
   npm run dev`.
2. Ouvrir l'application dans deux navigateurs différents (ou un onglet normal et un onglet de navigation privée), et
   se connecter avec deux comptes distincts qui ont chacun accès à un compte bancaire concerné par un même virement
   (par exemple, la source et la destination du virement).
3. Dans les outils de développement du navigateur, onglet *Réseau*, filtrer sur `eventsource` (ou `bff/events`) :
   vérifier qu'une requête de type `eventsource` reste ouverte pour chaque session authentifiée.
4. Faire un virement depuis la première session, puis observer dans la seconde la mise à jour du solde du compte
   sans rechargement manuel de la page. Retrouver, dans le message reçu par l'`EventSource`, le champ `resourceType`
   qui permet à `invalidateForEvent` (dans `notifications.ts`) de savoir quelles clés de cache invalider, et le
   champ `resourceId` qui indique lesquelles précisément.
5. Se connecter avec un troisième compte, non concerné par ce virement et sans autorité `account.read_any`, et
   vérifier qu'il ne reçoit rien. Expliquer pourquoi : où, précisément côté gateway, ce filtrage a-t-il lieu, et sur
   quels champs de l'évènement repose-t-il (indice : deux conditions, pas une seule) ?
6. Question de compréhension : le générateur de client TypeScript (`openapi-generator`) produit le type
   `DomainEvent` à partir de la spec OpenAPI documentée en 6.2. Pourquoi ne produit-il pas directement le code
   d'abonnement (`new EventSource(...)`) ? Qu'est-ce qu'une spec OpenAPI décrit, et que ne décrit-elle pas, sur un
   flux SSE ?
7. Question de compréhension (limite de scalabilité, cf. 6.2) : en quoi le choix du refetch REST plutôt que du
   transport de la donnée complète dans l'évènement limite-t-il l'impact d'un évènement perdu (registre en mémoire,
   plusieurs instances de gateway) ?
