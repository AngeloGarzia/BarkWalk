package com.barkwalk.messaging.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.barkwalk.common.AuditService;
import com.barkwalk.common.PrivacyService;
import com.barkwalk.common.Role;
import com.barkwalk.messaging.domain.ConversationMember;
import com.barkwalk.messaging.dto.MessagingDtos.MessageRequest;
import com.barkwalk.messaging.repository.ConversationMemberRepository;
import com.barkwalk.messaging.repository.ConversationRepository;
import com.barkwalk.messaging.repository.MessageRepository;
import com.barkwalk.messaging.repository.UserBlockRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.UserRepository;
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
        ConversationMember other = new ConversationMember();
        other.setUserId(2L);
        when(memberRepository.findByConversationId(9L)).thenReturn(List.of(other));
        when(blockRepository.existsByBlockerIdAndBlockedId(2L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> messagingService.post(9L, new MessageRequest("hi")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThatStatus(ex, HttpStatus.FORBIDDEN));
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

    private static void assertThatStatus(Throwable ex, HttpStatus status) {
        org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(status);
    }
}
