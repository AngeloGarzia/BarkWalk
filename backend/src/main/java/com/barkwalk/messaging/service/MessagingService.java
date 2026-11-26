package com.barkwalk.messaging.service;

import com.barkwalk.common.AuditService;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
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
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final UserRepository userRepository;
    private final AuditService auditService;

    public MessagingService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            MessageRepository messageRepository,
            UserBlockRepository blockRepository,
            PrivacyService privacyService,
            UserRepository userRepository,
            AuditService auditService) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
        this.privacyService = privacyService;
        this.userRepository = userRepository;
        this.auditService = auditService;
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
        Long me = SecurityUtils.currentUserId();
        List<Long> members = new ArrayList<>(request.memberIds());
        if (!members.contains(me)) {
            members.add(me);
        }
        if ("DIRECT".equalsIgnoreCase(request.type()) || "PRIVATE".equalsIgnoreCase(request.type())) {
            assertCanDirectMessage(me, members);
        }
        Conversation conversation = new Conversation();
        conversation.setConversationType(request.type());
        conversation.setWalkId(request.walkId());
        conversation = conversationRepository.save(conversation);
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
        ensureCanRead(conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessage)
                .toList();
    }

    @Transactional
    public MessageResponse post(Long conversationId, MessageRequest request) {
        ensureMember(conversationId);
        Long me = SecurityUtils.currentUserId();
        for (ConversationMember member : memberRepository.findByConversationId(conversationId)) {
            if (member.getUserId().equals(me)) {
                continue;
            }
            if (blockRepository.existsByBlockerIdAndBlockedId(member.getUserId(), me)
                    || blockRepository.existsByBlockerIdAndBlockedId(me, member.getUserId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging blocked");
            }
        }
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(me);
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

    /** Accès admin aux messages uniquement dans le cadre d'un signalement (audit). */
    @Transactional(readOnly = true)
    public List<MessageResponse> adminReadForReport(Long conversationId, Long reportId) {
        if (!SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        auditService.log(
                SecurityUtils.currentUserId(),
                "MSG_ADMIN_ACCESS",
                "CONVERSATION",
                conversationId,
                "report:" + reportId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessage)
                .toList();
    }

    @Transactional
    public int archiveGroupThreadsOlderThanDays(int days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int count = 0;
        for (Conversation c : conversationRepository.findByConversationTypeAndArchivedAtIsNullAndCreatedAtBefore(
                "GROUP", cutoff)) {
            // Archive 7 j après la balade si walk liée : approx via createdAt + days
            c.setArchivedAt(Instant.now());
            conversationRepository.save(c);
            count++;
        }
        return count;
    }

    private void assertCanDirectMessage(Long me, List<Long> members) {
        boolean meMember = hasMemberRole(me);
        for (Long other : members) {
            if (other.equals(me)) {
                continue;
            }
            if (blockRepository.existsByBlockerIdAndBlockedId(other, me)
                    || blockRepository.existsByBlockerIdAndBlockedId(me, other)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging blocked");
            }
            // Non-adhérent ne peut pas écrire à un adhérent en privé.
            if (!meMember && hasMemberRole(other)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non-members cannot DM members");
            }
        }
    }

    private boolean hasMemberRole(Long userId) {
        return userRepository.findById(userId)
                .map(User::getRoles)
                .map(roles -> roles.contains(Role.MEMBER) || roles.contains(Role.ADMIN))
                .orElse(false);
    }

    private void ensureCanRead(Long conversationId) {
        if (SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            return;
        }
        ensureMember(conversationId);
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
