package com.barkwalk.admin.service;

import com.barkwalk.admin.dto.AdminDtos.DashboardResponse;
import com.barkwalk.admin.dto.AdminDtos.FinanceResponse;
import com.barkwalk.admin.dto.AdminDtos.SettingResponse;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.membership.repository.MembershipRepository;
import com.barkwalk.payment.domain.Payment;
import com.barkwalk.payment.repository.PaymentRepository;
import com.barkwalk.payment.service.PaymentService;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.repository.WalkRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final WalkRepository walkRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BusinessSettingsService businessSettingsService;
    private final PaymentService paymentService;

    public AdminService(
            UserRepository userRepository,
            MembershipRepository membershipRepository,
            WalkRepository walkRepository,
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            BusinessSettingsService businessSettingsService,
            PaymentService paymentService) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.walkRepository = walkRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.businessSettingsService = businessSettingsService;
        this.paymentService = paymentService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        int revenue = paymentRepository.findAll().stream()
                .filter(p -> "SUCCEEDED".equals(p.getStatus()))
                .mapToInt(Payment::getAmountCents)
                .sum();
        int commissions = paymentRepository.findAll().stream()
                .filter(p -> "SUCCEEDED".equals(p.getStatus()))
                .mapToInt(Payment::getCommissionCents)
                .sum();
        return new DashboardResponse(
                userRepository.count(),
                walkRepository.count(),
                bookingRepository.count(),
                membershipRepository.count(),
                revenue,
                commissions);
    }

    @Transactional(readOnly = true)
    public List<SettingResponse> settings() {
        return businessSettingsService.listBusinessSettings();
    }

    @Transactional
    public SettingResponse updateSetting(String key, String value) {
        return businessSettingsService.update(key, value);
    }

    @Transactional(readOnly = true)
    public FinanceResponse finance() {
        return new FinanceResponse(paymentService.allForAdmin());
    }
}
