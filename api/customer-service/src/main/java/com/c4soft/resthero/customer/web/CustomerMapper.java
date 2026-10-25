package com.c4soft.resthero.customer.web;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants.ComponentModel;
import com.c4soft.resthero.commons.domain.Iban;
import com.c4soft.resthero.commons.domain.IbanStringMapper;
import com.c4soft.resthero.customer.domain.Beneficiary;
import com.c4soft.resthero.customer.domain.Customer;

@Mapper(componentModel = ComponentModel.SPRING, uses = {IbanStringMapper.class})
public interface CustomerMapper {

  CustomerResponse map(Customer entity);

  default Customer map(CustomerCreationRequest dto) {
    return new Customer(dto.firstName(), dto.lastName(), dto.email());
  }

  BeneficiaryResponse map(Beneficiary entity);

  default Beneficiary map(BeneficiaryRequest dto, String customerId) {
    return Beneficiary.of(customerId, Iban.of(dto.iban()), dto.label());
  }

}
