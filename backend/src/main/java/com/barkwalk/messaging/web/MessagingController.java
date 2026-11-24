package com.barkwalk.messaging.web;

import com.barkwalk.common.SecurityUtils;
import com.barkwalk.messaging.domain.Conversation;
import com.barkwalk.messaging.domain.ConversationMember;
import com.barkwalk.messaging.domain.Message;
import com.barkwalk.messaging.domain.UserBlock;
import com.barkwalk.messaging.repository.ConversationMemberRepository;
import com.barkwalk.messaging.repository.ConversationRepository;
import com.barkwalk.messaging.repository.MessageRepository;
import com.barkwalk.messaging.repository.UserBlockRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/messaging")
public class MessagingController {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final UserBlockRepository blockRepository;

    public MessagingController(ConversationRepository conversationRepository,
                               ConversationMemberRepository memberRepository,
                               MessageRepository messageRepository,
                               UserBlockRepository blockRepository) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
    }

    public record CreateConversationRequest(@NotBlank String type, @NotNull List<Long> memberIds, Long walkId) {}
    public record MessageRequest(@NotBlank String body) {}

    @GetMapping("/conversations")
    public List<Conversation> mine() {
        Long userId = SecurityUtils.currentUserId();
        List<Conversation> result = new ArrayList<>();
        for (ConversationMember m : memberRepository.findByUserId(userId)) {
            conversationRepository.findById(m.getConversationId()).ifPresent(result::add);
        }
        return result;
    }

    @PostMapping("/conversations")
    @Transactional
    public Conversation create(@RequestBody CreateConversationRequest request) {
        Conversation conversation = new Conversation();
        conversation.setConversationType(request.type());
        conversation.setWalkId(request.walkId());
        conversation = conversationRepository.save(conversation);
        List<Long> members = new ArrayList<>(request.memberIds());
        if (!members.contains(SecurityUtils.currentUserId())) {
            members.add(SecurityUtils.currentUserId());
        }
        for (Long memberId : members) {
            ConversationMember cm = new ConversationMember();
            cm.setConversationId(conversation.getId());
            cm.setUserId(memberId);
            memberRepository.save(cm);
        }
        return conversation;
    }

    @GetMapping("/conversations/{id}/messages")
    public List<Message> messages(@PathVariable Long id) {
        ensureMember(id);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(id);
    }

    @PostMapping("/conversations/{id}/messages")
    @Transactional
    public Message post(@PathVariable Long id, @RequestBody MessageRequest request) {
        ensureMember(id);
        Message message = new Message();
        message.setConversationId(id);
        message.setSenderId(SecurityUtils.currentUserId());
        message.setBody(request.body());
        return messageRepository.save(message);
    }

    @PostMapping("/blocks/{userId}")
    @Transactional
    public void block(@PathVariable Long userId) {
        UserBlock block = new UserBlock();
        block.setBlockerId(SecurityUtils.currentUserId());
        block.setBlockedId(userId);
        blockRepository.save(block);
    }

    private void ensureMember(Long conversationId) {
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, SecurityUtils.currentUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
