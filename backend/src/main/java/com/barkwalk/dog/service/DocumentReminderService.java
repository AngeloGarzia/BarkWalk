package com.barkwalk.dog.service;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.common.NotificationService;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.domain.DogDocument;
import com.barkwalk.dog.repository.DogDocumentRepository;
import com.barkwalk.dog.repository.DogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// R16 : rappels J-30 / J-7 avant expiration des documents chien.
@Service
public class DocumentReminderService {
    private final DogDocumentRepository documentRepository;
    private final DogRepository dogRepository;
    private final BusinessSettingsService businessSettingsService;
    private final NotificationService notificationService;

    public DocumentReminderService(
            DogDocumentRepository documentRepository,
            DogRepository dogRepository,
            BusinessSettingsService businessSettingsService,
            NotificationService notificationService) {
        this.documentRepository = documentRepository;
        this.dogRepository = dogRepository;
        this.businessSettingsService = businessSettingsService;
        this.notificationService = notificationService;
    }

    @Transactional
    public int sendDueReminders() {
        int j30 = businessSettingsService.docReminderJ30Days();
        int j7 = businessSettingsService.docReminderJ7Days();
        LocalDate today = LocalDate.now();
        int count = 0;
        count += sendKind(today.plusDays(j30), "J30", j30);
        count += sendKind(today.plusDays(j7), "J7", j7);
        return count;
    }

    private int sendKind(LocalDate expiresOn, String kind, int daysLeft) {
        List<DogDocument> docs = documentRepository.findDueReminders(expiresOn, kind);
        Instant now = Instant.now();
        int count = 0;
        for (DogDocument doc : docs) {
            Dog dog = dogRepository.findById(doc.getDogId()).orElse(null);
            if (dog == null) {
                continue;
            }
            notificationService.notifyDocReminder(dog.getOwnerId(), dog.getName(), doc.getDocType(), daysLeft);
            if ("J30".equals(kind)) {
                doc.setReminderJ30SentAt(now);
            } else {
                doc.setReminderJ7SentAt(now);
            }
            documentRepository.save(doc);
            count++;
        }
        return count;
    }
}
