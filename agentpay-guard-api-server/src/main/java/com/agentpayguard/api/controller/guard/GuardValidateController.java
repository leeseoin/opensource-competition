package com.agentpayguard.api.controller.guard;

import com.agentpayguard.api.dto.guard.GuardValidateRequest;
import com.agentpayguard.api.dto.guard.GuardValidateResponse;
import com.agentpayguard.api.service.guard.GuardValidateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 Agent가 유료 리소스 사용 직전에 호출하는 v1 Guard API이다.
 * sample-agent의 `/api/v1/guard/validate` 계약을 Spring Boot API server에서 제공한다.
 */
@RestController
@RequestMapping("/api/v1/guard")
public class GuardValidateController {

    private final GuardValidateService guardValidateService;

    public GuardValidateController(GuardValidateService guardValidateService) {
        this.guardValidateService = guardValidateService;
    }

    /**
     * 예상 비용과 작업 의도를 검증하고 ALLOW, REQUIRE_APPROVAL, DENY 중 하나를 반환한다.
     */
    @PostMapping("/validate")
    public GuardValidateResponse validate(@Valid @RequestBody GuardValidateRequest request) {
        return guardValidateService.validate(request);
    }
}
