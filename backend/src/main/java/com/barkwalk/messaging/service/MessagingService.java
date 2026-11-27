package com.barkwalk.messaging.service;

import com.barkwalk.admin.BusinessSettingsKeys;
import com.barkwalk.admin.service.BusinessSettingsService;
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
import com.barkwalk.messaging.dto.MessagingDtos.MemberSummary;
import com.barkwalk.messaging.dto.MessagingDtos.MessageRequest;
import com.barkwalk.messaging.dto.MessagingDtos.MessageResponse;
import com.barkwalk.messaging.dto.MessagingDtos.OpenDirectRequest;
import com.barkwalk.messaging.repository.ConversationMemberRepository;
import com.barkwalk.messaging.repository.ConversationRepository;
import com.barkwalk.messaging.repository.MessageRepository;
import com.barkwalk.messaging.repository.UserBlockRepository;
import com.barkwalk.moderation.domain.Report;
import com.barkwalk.moderation.repository.ReportRepository;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private final WalkRepository walkRepository;
    private final WalkerProfileRepository walkerProfileRepository;
    private final BusinessSettingsService businessSettingsService;
    private final ReportRepository reportRepository;

    public MessagingService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository memberRepository,
            MessageRepository messageRepository,
            UserBlockRepository blockRepository,
            PrivacyService privacyService,
            UserRepository userRepository,
            AuditService auditService,
            WalkRepository walkRepository,
            WalkerProfileRepository walkerProfileRepository,
            BusinessSettingsService businessSettingsService,
            ReportRepository reportRepository) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
        this.privacyService = privacyService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.walkRepository = walkRepository;
        this.walkerProfileRepository = walkerProfileRepository;
        this.businessSettingsService = businessSettingsService;
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> mine() {
        Long userId = SecurityUtils.currentUserId();
        List<ConversationResponse> result = new ArrayList<>();
        for (ConversationMember m : memberRepository.findByUserId(userId)) {
            conversationRepository.findById(m.getConversationId()).ifPresent(c -> result.add(toConversation(c, userId)));
        }
        result.sort((a, b) -> {
            Instant aa = a.lastMessageAt() != null ? a.lastMessageAt() : a.createdAt();
            Instant bb = b.lastMessageAt() != null ? b.lastMessageAt() : b.createdAt();
            return bb.compareTo(aa);
        });
        return result;
    }

    @Transactional
    public ConversationResponse create(CreateConversationRequest request) {
        Long me = SecurityUtils.currentUserId();
        String type = normalizeType(request.type());
        List<Long> members = new ArrayList<>(new LinkedHashSet<>(request.memberIds()));
        if (!members.contains(me)) {
            members.add(me);
        }
        if ("DIRECT".equals(type)) {
            if (members.size() != 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DIRECT requires exactly two members");
            }
            assertCanDirectMessage(me, members);
            Long other = members.stream().filter(id -> !id.equals(me)).findFirst().orElseThrow();
            return openDirectInternal(me, other);
        }
        if ("WALK".equals(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "WALK threads are created with the walk, not manually");
        }
        // SUPPORT / MODERATION : réservés à l'admin ; pas de walkId arbitraire ni d'invitations libres.
        if ("SUPPORT".equals(type) || "MODERATION".equals(type)) {
            if (!SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admin can open " + type + " threads");
            }
            if (request.walkId() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, type + " threads cannot reference a walk");
            }
            members = validateStaffThreadMembers(me, members);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported conversation type");
        }
        Conversation conversation = new Conversation();
        conversation.setConversationType(type);
        conversation.setWalkId(null);
        conversation = conversationRepository.save(conversation);
        for (Long memberId : members) {
            addMemberIfAbsent(conversation.getId(), memberId);
        }
        return toConversation(conversation, me);
    }

    /** Ouvre (ou réutilise) un DM avec un autre utilisateur. */
    @Transactional
    public ConversationResponse openDirect(OpenDirectRequest request) {
        Long me = SecurityUtils.currentUserId();
        if (request.userId() == null || request.userId().equals(me)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid recipient");
        }
        assertCanDirectMessage(me, List.of(me, request.userId()));
        return openDirectInternal(me, request.userId());
    }

    /**
     * Crée le fil de groupe WALK avec la balade (orga + encadrant éventuel).
     * Idempotent.
     */
    @Transactional
    public void ensureWalkThread(Walk walk) {
        if (walk == null || walk.getId() == null) {
            return;
        }
        Conversation conversation =
                conversationRepository.findByWalkIdAndConversationType(walk.getId(), "WALK").orElse(null);
        if (conversation == null) {
            Conversation created = new Conversation();
            created.setConversationType("WALK");
            created.setWalkId(walk.getId());
            conversation = conversationRepository.save(created);
        }
        final Long conversationId = conversation.getId();
        if (walk.getOrganizerUserId() != null) {
            addMemberIfAbsent(conversationId, walk.getOrganizerUserId());
        }
        if (walk.getWalkerId() != null) {
            walkerProfileRepository
                    .findById(walk.getWalkerId())
                    .ifPresent(wp -> addMemberIfAbsent(conversationId, wp.getUserId()));
        }
    }

    /** Ajoute un participant confirmé au fil de balade. */
    @Transactional
    public void addMemberToWalkThread(Long walkId, Long userId) {
        if (walkId == null || userId == null) {
            return;
        }
        Conversation conversation =
                conversationRepository.findByWalkIdAndConversationType(walkId, "WALK").orElse(null);
        if (conversation == null) {
            Walk walk = walkRepository.findById(walkId).orElse(null);
            if (walk == null) {
                return;
            }
            ensureWalkThread(walk);
            conversation =
                    conversationRepository.findByWalkIdAndConversationType(walkId, "WALK").orElse(null);
        }
        if (conversation != null) {
            addMemberIfAbsent(conversation.getId(), userId);
        }
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
        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (conversation.getArchivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation archived");
        }
        Long me = SecurityUtils.currentUserId();
        String body = request.body() == null ? "" : request.body().trim();
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty message");
        }
        if (body.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message too long");
        }

        assertRateLimit(me);

        List<ConversationMember> members = memberRepository.findByConversationId(conversationId);
        for (ConversationMember member : members) {
            if (member.getUserId().equals(me)) {
                continue;
            }
            assertNotBlocked(me, member.getUserId());
        }
        if ("DIRECT".equalsIgnoreCase(conversation.getConversationType())) {
            List<Long> memberIds = members.stream().map(ConversationMember::getUserId).toList();
            assertCanDirectMessage(me, memberIds);
        }

        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderId(me);
        message.setBody(body);
        Message saved = messageRepository.save(message);

        Instant now = Instant.now();
        memberRepository.findByConversationIdAndUserId(conversationId, me).ifPresent(self -> {
            self.setLastReadAt(now);
            self.setUpdatedAt(now);
            memberRepository.save(self);
        });
        return toMessage(saved);
    }

    @Transactional
    public ConversationResponse markRead(Long conversationId) {
        Long me = SecurityUtils.currentUserId();
        ensureMember(conversationId);
        ConversationMember self = memberRepository
                .findByConversationIdAndUserId(conversationId, me)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        self.setLastReadAt(Instant.now());
        self.setUpdatedAt(Instant.now());
        memberRepository.save(self);
        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toConversation(conversation, me);
    }

    @Transactional
    public void block(Long userId) {
        Long me = SecurityUtils.currentUserId();
        if (userId == null || userId.equals(me)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot block yourself");
        }
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(me, userId)) {
            return;
        }
        UserBlock block = new UserBlock();
        block.setBlockerId(me);
        block.setBlockedId(userId);
        blockRepository.save(block);
        auditService.log(me, "MSG_BLOCK", "USER", userId, null);
    }

    /** Accès admin aux messages uniquement dans le cadre d'un signalement (audit). */
    @Transactional(readOnly = true)
    public List<MessageResponse> adminReadForReport(Long conversationId, Long reportId) {
        if (!SecurityUtils.currentUser().getRoles().contains(Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (reportId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reportId required");
        }
        Report report = reportRepository
                .findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        // Le signalement doit viser cette conversation (ou un message / membre du fil).
        boolean okTarget = "CONVERSATION".equalsIgnoreCase(report.getTargetType())
                && conversationId.equals(report.getTargetId());
        if (!okTarget) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Report does not cover this conversation");
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

    /**
     * Archive les fils WALK 7 jours après la fin de la balade (completedAt, sinon startsAt + durée).
     */
    @Transactional
    public int archiveGroupThreadsOlderThanDays(int days) {
        Instant now = Instant.now();
        int count = 0;
        for (Conversation c : conversationRepository.findByConversationTypeAndArchivedAtIsNull("WALK")) {
            if (c.getWalkId() == null) {
                continue;
            }
            Walk walk = walkRepository.findById(c.getWalkId()).orElse(null);
            if (walk == null) {
                continue;
            }
            Instant end = walk.getCompletedAt();
            if (end == null && walk.getStartsAt() != null) {
                int minutes = walk.getDurationMinutes() > 0 ? walk.getDurationMinutes() : 60;
                end = walk.getStartsAt().plus(Duration.ofMinutes(minutes));
            }
            if (end == null) {
                continue;
            }
            if (end.plus(days, ChronoUnit.DAYS).isBefore(now)
                    || (walk.getArchivedAt() != null
                            && walk.getArchivedAt().plus(days, ChronoUnit.DAYS).isBefore(now))) {
                c.setArchivedAt(now);
                conversationRepository.save(c);
                count++;
            }
        }
        return count;
    }

    private ConversationResponse openDirectInternal(Long me, Long other) {
        List<Conversation> existing = conversationRepository.findDirectBetween(me, other);
        if (!existing.isEmpty()) {
            return toConversation(existing.getFirst(), me);
        }
        Conversation conversation = new Conversation();
        conversation.setConversationType("DIRECT");
        conversation = conversationRepository.save(conversation);
        addMemberIfAbsent(conversation.getId(), me);
        addMemberIfAbsent(conversation.getId(), other);
        return toConversation(conversation, me);
    }

    private void addMemberIfAbsent(Long conversationId, Long userId) {
        if (conversationId == null || userId == null) {
            return;
        }
        if (memberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            return;
        }
        ConversationMember cm = new ConversationMember();
        cm.setConversationId(conversationId);
        cm.setUserId(userId);
        memberRepository.save(cm);
    }

    private void assertRateLimit(Long userId) {
        int limit = businessSettingsService.getInt(BusinessSettingsKeys.LIMIT_MESSAGES_PER_HOUR);
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        long sent = messageRepository.countBySenderIdAndCreatedAtAfter(userId, since);
        if (sent >= limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Message rate limit exceeded");
        }
    }

    private void assertCanDirectMessage(Long me, List<Long> members) {
        boolean meMember = hasMemberRole(me);
        for (Long other : members) {
            if (other.equals(me)) {
                continue;
            }
            assertNotBlocked(me, other);
            // Non-adhérent ne peut pas écrire à un adhérent en privé.
            if (!meMember && hasMemberRole(other)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non-members cannot DM members");
            }
        }
    }

    private void assertNotBlocked(Long a, Long b) {
        if (blockRepository.existsByBlockerIdAndBlockedId(b, a)
                || blockRepository.existsByBlockerIdAndBlockedId(a, b)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging blocked");
        }
    }

    private List<Long> validateStaffThreadMembers(Long me, List<Long> members) {
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        out.add(me);
        for (Long id : members) {
            if (id == null || id.equals(me)) {
                continue;
            }
            if (!userRepository.existsById(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown member " + id);
            }
            out.add(id);
        }
        if (out.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one other member required");
        }
        return new ArrayList<>(out);
    }

    private boolean hasMemberRole(Long userId) {
        return userRepository
                .findById(userId)
                .map(User::getRoles)
                .map(roles -> roles.contains(Role.MEMBER) || roles.contains(Role.ADMIN))
                .orElse(false);
    }

    private void ensureMember(Long conversationId) {
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, SecurityUtils.currentUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type required");
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if ("PRIVATE".equals(normalized)) {
            return "DIRECT";
        }
        if ("GROUP".equals(normalized)) {
            return "WALK";
        }
        return switch (normalized) {
            case "WALK", "SUPPORT", "DIRECT", "MODERATION" -> normalized;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown conversation type");
        };
    }

    private ConversationResponse toConversation(Conversation c, Long viewerId) {
        List<ConversationMember> members = memberRepository.findByConversationId(c.getId());
        List<MemberSummary> memberSummaries = members.stream()
                .map(m -> new MemberSummary(m.getUserId(), privacyService.displayName(m.getUserId())))
                .toList();

        String title = switch (c.getConversationType() == null ? "" : c.getConversationType().toUpperCase(Locale.ROOT)) {
            case "WALK" -> walkRepository
                    .findById(c.getWalkId() == null ? -1L : c.getWalkId())
                    .map(Walk::getTitle)
                    .orElse("Balade");
            case "DIRECT" -> memberSummaries.stream()
                    .filter(m -> !m.userId().equals(viewerId))
                    .map(MemberSummary::displayName)
                    .findFirst()
                    .orElse("Message privé");
            case "SUPPORT" -> "Support";
            case "MODERATION" -> "Modération";
            default -> "Conversation";
        };

        Message last = messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(c.getId()).orElse(null);
        Instant lastRead = members.stream()
                .filter(m -> m.getUserId().equals(viewerId))
                .map(ConversationMember::getLastReadAt)
                .findFirst()
                .orElse(null);
        int unread = (int) messageRepository.countUnread(c.getId(), lastRead);

        return new ConversationResponse(
                c.getId(),
                c.getConversationType(),
                c.getWalkId(),
                title,
                last != null ? truncate(last.getBody(), 120) : null,
                last != null ? last.getCreatedAt() : null,
                unread,
                c.getCreatedAt(),
                c.getArchivedAt(),
                memberSummaries);
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

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max - 1) + "…";
    }
}
