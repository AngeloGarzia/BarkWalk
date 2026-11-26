package com.barkwalk.common;

import com.barkwalk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Notifications métier — Brevo API ou SMTP (R5 / R7 / R16 / rappels).
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final EmailSender emailSender;
    private final UserRepository userRepository;

    public NotificationService(EmailSender emailSender, UserRepository userRepository) {
        this.emailSender = emailSender;
        this.userRepository = userRepository;
    }

    public void notifyWalkCancelledByStaff(Long walkId, Long userId, String reason) {
        String email = emailOf(userId);
        emailSender.send(
                email,
                "Bark & Walk — balade annulée",
                "Votre réservation sur la balade #" + walkId + " a été annulée."
                        + (reason != null ? " Motif : " + reason : ""));
    }

    public void notifyWaitlistOffer(Long walkId, Long userId, Long entryId) {
        String email = emailOf(userId);
        emailSender.send(
                email,
                "Bark & Walk — place disponible",
                "Une place s'est libérée sur la balade #" + walkId
                        + ". Offre waitlist #" + entryId + " : répondez rapidement.");
    }

    public void notifyDocReminder(Long userId, String dogName, String docType, int daysLeft) {
        String email = emailOf(userId);
        emailSender.send(
                email,
                "Bark & Walk — document à renouveler (J-" + daysLeft + ")",
                "Le document " + docType + " de " + dogName + " expire dans " + daysLeft
                        + " jour(s). Merci de le renouveler.");
    }

    public void notifyDocReminderSweep(int j30Days, int j7Days) {
        log.debug("Doc reminder sweep settings j30={} j7={}", j30Days, j7Days);
    }

    public void notifyWalkTomorrow(Long walkId, Long userId, String walkTitle) {
        String email = emailOf(userId);
        emailSender.send(
                email,
                "Bark & Walk — rappel demain",
                "Rappel : vous êtes inscrit à « " + (walkTitle != null ? walkTitle : ("#" + walkId))
                        + " » demain.");
    }

    public void notifyWalkTomorrow(Long walkId, Long userId) {
        notifyWalkTomorrow(walkId, userId, null);
    }

    private String emailOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(u -> u.getEmail()).orElse(null);
    }
}
