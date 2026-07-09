package com.agentpayguard.api.service.approval;

import com.agentpayguard.api.dto.approval.ApprovalResponse;
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
