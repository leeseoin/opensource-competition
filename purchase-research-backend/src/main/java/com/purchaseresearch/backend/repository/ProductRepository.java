package com.purchaseresearch.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchaseresearch.backend.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

	Optional<Product> findBySiteAndSourceProductId(String site, String sourceProductId);
}
