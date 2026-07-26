package com.purchaseresearch.backend.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 상품 배치 적재 API(/api/v1/products/**)를 보호하는 최소한의 API Key 검증 필터.
 * Spring Security 없이 서블릿 Filter로 가장 단순하게 구현했다(설계서 §6.3).
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

	private static final String HEADER_NAME = "X-API-Key";
	private static final String PROTECTED_PATH_PREFIX = "/api/v1/products";

	@Value("${app.security.api-key}")
	private String apiKey;

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		if (!request.getRequestURI().startsWith(PROTECTED_PATH_PREFIX)) {
			filterChain.doFilter(request, response);
			return;
		}

		String providedKey = request.getHeader(HEADER_NAME);
		if (apiKey == null || apiKey.isBlank() || !apiKey.equals(providedKey)) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().write("{\"error\":\"invalid or missing X-API-Key\"}");
			return;
		}

		filterChain.doFilter(request, response);
	}
}
