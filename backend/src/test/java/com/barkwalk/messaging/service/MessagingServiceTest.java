package com.barkwalk.messaging.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.BusinessSettingsKeys;
import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.common.AuditService;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.messaging.domain.Conversation;
import com.barkwalk.messaging.domain.ConversationMember;
import com.barkwalk.messaging.domain.Message;
import com.barkwalk.messaging.dto.MessagingDtos.MessageRequest;
import com.barkwalk.messaging.repository.ConversationMemberRepository;
import com.barkwalk.messaging.repository.ConversationRepository;
import com.barkwalk.messaging.repository.MessageRepository;
import com.barkwalk.messaging.repository.UserBlockRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import com.barkwalk.walker.repository.WalkerProfileRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MessagingServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMemberRepository memberRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserBlockRepository blockRepository;
    @Mock
    private PrivacyService privacyService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private WalkRepository walkRepository;
    @Mock
    private WalkerProfileRepository walkerProfileRepository;
    @Mock
    private BusinessSettingsService businessSettingsService;

    @InjectMocks
    private MessagingService messagingService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void post_rejectsWhenBlocked() {
        SecurityTestSupport.authenticate(1L, "a@example.com", Role.USER);
        when(memberRepository.existsByConversationIdAndUserId(9L, 1L)).thenReturn(true);
        Conversation conversation = new Conversation();
        conversation.setConversationType("WALK");
        when(conversationRepository.findById(9L)).thenReturn(Optional.of(conversation));
        when(businessSettingsService.getInt(BusinessSettingsKeys.LIMIT_MESSAGES_PER_HOUR)).thenReturn(60);
        when(messageRepository.countBySenderIdAndCreatedAtAfter(eq(1L), any())).thenReturn(0L);
        ConversationMember other = new ConversationMember();
        other.setUserId(2L);
        when(memberRepository.findByConversationId(9L)).thenReturn(List.of(other));
        when(blockRepository.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> messagingService.post(9L, new MessageRequest("hi")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThatStatus(ex, HttpStatus.FORBIDDEN));
    }

    @Test
    void post_rejectsArchivedConversation() {
        SecurityTestSupport.authenticate(1L, "a@example.com", Role.USER, Role.MEMBER);
        when(memberRepository.existsByConversationIdAndUserId(9L, 1L)).thenReturn(true);
        Conversation conversation = new Conversation();
        conversation.setConversationType("WALK");
        conversation.setArchivedAt(java.time.Instant.now());
        when(conversationRepository.findById(9L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> messagingService.post(9L, new MessageRequest("hi")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThatStatus(ex, HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_rejectsNonMemberDmToMember() {
        SecurityTestSupport.authenticate(1L, "guest@example.com", Role.USER);
        User me = new User();
        me.setRoles(new HashSet<>(Set.of(Role.USER)));
        User member = new User();
        member.setRoles(new HashSet<>(Set.of(Role.USER, Role.MEMBER)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(blockRepository.existsByBlockerIdAndBlockedId(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> messagingService.create(
                        new com.barkwalk.messaging.dto.MessagingDtos.CreateConversationRequest(
                                "DIRECT", List.of(2L), null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThatStatus(ex, HttpStatus.FORBIDDEN));
    }

    @Test
    void messages_rejectsAdminWithoutMembership() {
        SecurityTestSupport.authenticate(99L, "admin@example.com", Role.ADMIN);
        when(memberRepository.existsByConversationIdAndUserId(9L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> messagingService.messages(9L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThatStatus(ex, HttpStatus.FORBIDDEN));
        verify(messageRepository, never()).findByConversationIdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    void ensureWalkThread_createsOnceAndAddsOrganizer() {
        Walk walk = org.mockito.Mockito.mock(Walk.class);
        when(walk.getId()).thenReturn(5L);
        when(walk.getOrganizerUserId()).thenReturn(3L);
        when(walk.getWalkerId()).thenReturn(null);
        when(conversationRepository.findByWalkIdAndConversationType(5L, "WALK")).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            try {
                var field = Conversation.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(c, 42L);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            return c;
        });
        when(memberRepository.existsByConversationIdAndUserId(42L, 3L)).thenReturn(false);

        messagingService.ensureWalkThread(walk);

        verify(conversationRepository).save(any(Conversation.class));
        verify(memberRepository).save(any(ConversationMember.class));
    }

    @Test
    void post_enforcesRateLimit() {
        SecurityTestSupport.authenticate(1L, "a@example.com", Role.USER, Role.MEMBER);
        when(memberRepository.existsByConversationIdAndUserId(9L, 1L)).thenReturn(true);
        Conversation conversation = new Conversation();
        conversation.setConversationType("WALK");
        when(conversationRepository.findById(9L)).thenReturn(Optional.of(conversation));
        when(businessSettingsService.getInt(BusinessSettingsKeys.LIMIT_MESSAGES_PER_HOUR)).thenReturn(60);
        when(messageRepository.countBySenderIdAndCreatedAtAfter(eq(1L), any())).thenReturn(60L);

        assertThatThrownBy(() -> messagingService.post(9L, new MessageRequest("hi")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThatStatus(ex, HttpStatus.TOO_MANY_REQUESTS));
    }

    private static void assertThatStatus(Throwable ex, HttpStatus status) {
        org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(status);
    }
}
