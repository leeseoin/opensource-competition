package com.agentpayguard.api.approval;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApprovalService {

    public ApprovalResponse approve(UUID paymentRequestId) {
        return new ApprovalResponse(paymentRequestId, "APPROVED", "Approval flow scaffold only.");
    }

    public ApprovalResponse reject(UUID paymentRequestId) {
        return new ApprovalResponse(paymentRequestId, "REJECTED", "Approval flow scaffold only.");
    }
}
