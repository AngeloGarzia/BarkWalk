package com.barkwalk.dog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.admin.service.BusinessSettingsService;
import com.barkwalk.common.NotificationService;
import com.barkwalk.dog.domain.Dog;
import com.barkwalk.dog.domain.DogDocument;
import com.barkwalk.dog.repository.DogDocumentRepository;
import com.barkwalk.dog.repository.DogRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentReminderServiceTest {

    @Mock
    private DogDocumentRepository documentRepository;
    @Mock
    private DogRepository dogRepository;
    @Mock
    private BusinessSettingsService businessSettingsService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DocumentReminderService documentReminderService;

    // Marque la notification J+30 comme envoyée.
    @Test
    void sendDueReminders_marksJ30Sent() {
        when(businessSettingsService.docReminderJ30Days()).thenReturn(30);
        when(businessSettingsService.docReminderJ7Days()).thenReturn(7);
        LocalDate target = LocalDate.now().plusDays(30);
        DogDocument doc = new DogDocument();
        doc.setDogId(5L);
        doc.setDocType("VACCINATION");
        doc.setExpiresAt(target);
        when(documentRepository.findDueReminders(eq(target), eq("J30"))).thenReturn(List.of(doc));
        when(documentRepository.findDueReminders(eq(LocalDate.now().plusDays(7)), eq("J7")))
                .thenReturn(List.of());
        Dog dog = new Dog();
        dog.setOwnerId(9L);
        dog.setName("Rex");
        when(dogRepository.findById(5L)).thenReturn(Optional.of(dog));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(documentReminderService.sendDueReminders()).isEqualTo(1);
        assertThat(doc.getReminderJ30SentAt()).isNotNull();
        verify(notificationService).notifyDocReminder(9L, "Rex", "VACCINATION", 30);
    }
}
