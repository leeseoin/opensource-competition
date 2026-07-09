package com.agentpayguard.api.controller.payment;

import com.agentpayguard.api.dto.payment.CreatePaymentRequestRequest;
import com.agentpayguard.api.dto.payment.PaymentRequestResponse;
import com.agentpayguard.api.service.payment.PaymentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment-requests")
public class PaymentRequestController {

    private final PaymentService paymentService;

    public PaymentRequestController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public PaymentRequestResponse create(@Valid @RequestBody CreatePaymentRequestRequest request) {
        return paymentService.create(request);
    }

    @GetMapping("/{id}")
    public PaymentRequestResponse get(@PathVariable UUID id) {
        return paymentService.get(id);
    }
}
