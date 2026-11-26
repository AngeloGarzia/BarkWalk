package com.barkwalk.messaging.service;

import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.messaging.domain.Conversation;
import com.barkwalk.messaging.domain.ConversationMember;
import com.barkwalk.messaging.domain.Message;
import com.barkwalk.messaging.domain.UserBlock;
import com.barkwalk.messaging.dto.MessagingDtos.ConversationResponse;
import com.barkwalk.messaging.dto.MessagingDtos.CreateConversationRequest;
import com.barkwalk.messaging.dto.MessagingDtos.MessageRequest;
import com.barkwalk.messaging.dto.MessagingDtos.MessageResponse;
import com.barkwalk.messaging.repository.ConversationMemberRepository;
import com.barkwalk.messaging.repository.ConversationRepository;
import com.barkwalk.messaging.repository.MessageRepository;
import com.barkwalk.messaging.repository.UserBlockRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MessagingService {
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final UserBlockRepository blockRepository;
    private final PrivacyService privacyService;

    public MessagingService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            MessageRepository messageRepository,
            UserBlockRepository blockRepository,
            PrivacyService privacyService) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
        this.privacyService = privacyService;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> mine() {
        Long userId = SecurityUtils.currentUserId();
        List<ConversationResponse> result = new ArrayList<>();
        for (ConversationMember m : memberRepository.findByUserId(userId)) {
            conversationRepository.findById(m.getConversationId()).ifPresent(c -> result.add(toConversation(c)));
        }
        return result;
    }

    @Transactional
    public ConversationResponse create(CreateConversationRequest request) {
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
        return toConversation(conversation);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> messages(Long conversationId) {
        ensureMember(conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessage)
                .toList();
    }

    @Transactional
    public MessageResponse post(Long conversationId, MessageRequest request) {
        ensureMember(conversationId);
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(SecurityUtils.currentUserId());
        message.setBody(request.body());
        return toMessage(messageRepository.save(message));
    }

    @Transactional
    public void block(Long userId) {
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

    private ConversationResponse toConversation(Conversation c) {
        return new ConversationResponse(
                c.getId(), c.getConversationType(), c.getWalkId(), c.getCreatedAt(), c.getArchivedAt());
    }

    private MessageResponse toMessage(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                privacyService.displayName(m.getSenderId()),
                m.getBody(),
                m.getCreatedAt(),
                m.getReadAt());
    }
}
