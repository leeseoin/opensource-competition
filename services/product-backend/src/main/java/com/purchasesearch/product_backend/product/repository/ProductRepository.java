package com.purchasesearch.product_backend.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.purchasesearch.product_backend.product.entity.Product;

/**
 * ProductRepository는 공통 상품 기본 정보의 영속화를 담당한다.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
}
