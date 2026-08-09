package com.c4soft.resthero.commons.events;

/**
 * Taxonomy of resource areas a {@link DomainEvent} can be published for, shared by every business
 * service. A qualified name narrows a business entity down to one of its areas, so the frontend
 * can invalidate just that area's queries instead of everything related to the entity: an
 * {@code ACCOUNT_CARDS} event only concerns an account's card list, while an {@code ACCOUNT} event
 * concerns the account itself.
 */
public enum ResourceType {
  ACCOUNT, ACCOUNT_TRANSFERS, ACCOUNT_CARDS, CARD, CARD_PAYMENTS, CUSTOMER_BENEFICIARIES;
}
