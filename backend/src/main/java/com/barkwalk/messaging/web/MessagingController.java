package com.barkwalk.messaging.web;

import com.barkwalk.messaging.dto.MessagingDtos.ConversationResponse;
import com.barkwalk.messaging.dto.MessagingDtos.CreateConversationRequest;
import com.barkwalk.messaging.dto.MessagingDtos.MessageRequest;
import com.barkwalk.messaging.dto.MessagingDtos.MessageResponse;
import com.barkwalk.messaging.service.MessagingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messaging")
public class MessagingController {
    private final MessagingService messagingService;

    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping("/conversations")
    public List<ConversationResponse> mine() {
        return messagingService.mine();
    }

    @PostMapping("/conversations")
    public ConversationResponse create(@Valid @RequestBody CreateConversationRequest request) {
        return messagingService.create(request);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageResponse> messages(@PathVariable Long id) {
        return messagingService.messages(id);
    }

    @PostMapping("/conversations/{id}/messages")
    public MessageResponse post(@PathVariable Long id, @Valid @RequestBody MessageRequest request) {
        return messagingService.post(id, request);
    }

    @PostMapping("/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable Long userId) {
        messagingService.block(userId);
    }

    @GetMapping("/admin/conversations/{id}")
    public List<MessageResponse> adminRead(@PathVariable Long id, @RequestParam Long reportId) {
        return messagingService.adminReadForReport(id, reportId);
    }
}
