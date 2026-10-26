# TP 3.5 — `@Repository` Spring Data JPA

> Support de cours : [`@Repository` Spring Data JPA](README.md#jpa-repositories)

## Objectifs

À l'issue de ce TP, le stagiaire doit comprendre qu'une interface Spring Data ne devient un repository qu'en
héritant d'un contrat JPA approprié, et savoir distinguer ce rôle du simple fait d'être injecté dans un contrôleur.

## Consignes

Lancer `./start-lab.sh 3.5` pour créer la branche `lab/3.5`.

1. Lancer `mvn -pl customer-service -am compile`. Constater l'échec de compilation dans `CustomerController.java` et
	`WebConfiguration.java` (le convertisseur du chemin `{beneficiaryId}`) : les méthodes de `BeneficiaryRepository` ne
	sont plus visibles.
2. Retrouver dans `api/customer-service/src/main/java/com/c4soft/resthero/customer/jpa/BeneficiaryRepository.java` le
	repère `LAB:3.5` sur l'héritage de l'interface et le reconstruire.
3. Relancer la même commande et vérifier que le repository redevient un vrai bean Spring Data.
4. Expliquer pourquoi `JpaRepository` fournit à la fois le CRUD, l'intégration transactionnelle et l'enregistrement du
	repository dans le contexte Spring.
