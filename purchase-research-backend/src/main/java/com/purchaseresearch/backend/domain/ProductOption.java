package com.purchaseresearch.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_option")
@Getter
@Setter
@NoArgsConstructor
public class ProductOption {

	public enum OptionType {
		COLOR, SIZE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Enumerated(EnumType.STRING)
	@Column(name = "option_type", nullable = false, length = 20)
	private OptionType optionType;

	@Column(name = "option_value", nullable = false, length = 50)
	private String optionValue;
}
