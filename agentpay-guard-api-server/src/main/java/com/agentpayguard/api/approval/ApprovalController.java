package com.agentpayguard.api.approval;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment-requests/{paymentRequestId}")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/approve")
    public ApprovalResponse approve(@PathVariable UUID paymentRequestId) {
        return approvalService.approve(paymentRequestId);
    }

    @PostMapping("/reject")
    public ApprovalResponse reject(@PathVariable UUID paymentRequestId) {
        return approvalService.reject(paymentRequestId);
    }
}
