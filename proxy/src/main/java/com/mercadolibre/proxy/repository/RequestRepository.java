package com.mercadolibre.proxy.repository;

import com.mercadolibre.proxy.model.Request;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestRepository extends CrudRepository<Request, String> {
}
