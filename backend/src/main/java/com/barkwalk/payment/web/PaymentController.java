package com.barkwalk.payment.web;

import com.barkwalk.payment.dto.PaymentDtos.PaymentResponse;
import com.barkwalk.payment.dto.PaymentDtos.WebhookAck;
import com.barkwalk.payment.service.PaymentService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/webhook")
    public WebhookAck webhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody Map<String, Object> payload) {
        return paymentService.handleWebhook(signature, payload);
    }

    @GetMapping("/me")
    public List<PaymentResponse> mine() {
        return paymentService.mine();
    }
}
