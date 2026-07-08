package com.agentpayguard.api.controller.merchant;

import com.agentpayguard.api.dto.merchant.QuoteRequest;
import com.agentpayguard.api.dto.merchant.QuoteResponse;
import com.agentpayguard.api.service.merchant.MockMerchantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mock-merchant")
public class MockMerchantController {

    private final MockMerchantService mockMerchantService;

    public MockMerchantController(MockMerchantService mockMerchantService) {
        this.mockMerchantService = mockMerchantService;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) {
        return mockMerchantService.quote(request);
    }
}
