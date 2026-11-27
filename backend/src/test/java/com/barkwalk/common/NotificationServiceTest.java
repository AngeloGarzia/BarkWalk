package com.barkwalk.common;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private EmailSender emailSender;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    // Notifie l'annulation d'une balade avec le motif.
    @Test
    void notifyWalkCancelledByStaff_sendsEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("destinataire@example.com")));

        notificationService.notifyWalkCancelledByStaff(10L, 1L, "météo");

        verify(emailSender)
                .send(eq("destinataire@example.com"), contains("balade annulée"), contains("météo"));
    }

    // Propose une place libérée en liste d'attente.
    @Test
    void notifyWaitlistOffer_sendsEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("destinataire@example.com")));

        notificationService.notifyWaitlistOffer(10L, 1L, 55L);

        verify(emailSender)
                .send(eq("destinataire@example.com"), contains("place disponible"), contains("55"));
    }

    // Rappelle l'échéance d'un document chien.
    @Test
    void notifyDocReminder_sendsEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("destinataire@example.com")));

        notificationService.notifyDocReminder(1L, "Rex", "RABIES", 7);

        verify(emailSender).send(eq("destinataire@example.com"), contains("J-7"), contains("Rex"));
    }

    // Envoie le rappel de balade du lendemain.
    @Test
    void notifyWalkTomorrow_sendsEmailWithTitle() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("destinataire@example.com")));

        notificationService.notifyWalkTomorrow(10L, 1L, "Balade forêt");

        verify(emailSender)
                .send(eq("destinataire@example.com"), contains("rappel demain"), contains("Balade forêt"));
    }

    // Utilise le numéro de balade si le titre est absent.
    @Test
    void notifyWalkTomorrow_usesWalkIdWhenNoTitle() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("destinataire@example.com")));

        notificationService.notifyWalkTomorrow(10L, 1L, null);

        verify(emailSender).send(eq("destinataire@example.com"), contains("rappel demain"), contains("#10"));
    }

    // Accepte un destinataire inconnu sans lever d'exception.
    @Test
    void notifyWalkCancelledByStaff_toleratesUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        notificationService.notifyWalkCancelledByStaff(10L, 99L, null);

        verify(emailSender).send(eq(null), anyString(), anyString());
    }

    private static User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("n/a");
        user.setEnabled(true);
        user.setDeleted(false);
        return user;
    }
}
