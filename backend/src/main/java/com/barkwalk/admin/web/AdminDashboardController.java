package com.barkwalk.admin.web;

import com.barkwalk.admin.domain.Setting;
import com.barkwalk.admin.repository.SettingRepository;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.membership.repository.MembershipRepository;
import com.barkwalk.payment.domain.Payment;
import com.barkwalk.payment.repository.PaymentRepository;
import com.barkwalk.user.repository.UserRepository;
import com.barkwalk.walk.repository.WalkRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final WalkRepository walkRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final SettingRepository settingRepository;

    public AdminDashboardController(UserRepository userRepository, MembershipRepository membershipRepository,
                                    WalkRepository walkRepository, BookingRepository bookingRepository,
                                    PaymentRepository paymentRepository, SettingRepository settingRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.walkRepository = walkRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.settingRepository = settingRepository;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("users", userRepository.count());
        stats.put("walks", walkRepository.count());
        stats.put("bookings", bookingRepository.count());
        stats.put("memberships", membershipRepository.count());
        int revenue = paymentRepository.findAll().stream()
                .filter(p -> "SUCCEEDED".equals(p.getStatus()))
                .mapToInt(Payment::getAmountCents).sum();
        int commissions = paymentRepository.findAll().stream()
                .filter(p -> "SUCCEEDED".equals(p.getStatus()))
                .mapToInt(Payment::getCommissionCents).sum();
        stats.put("revenueCents", revenue);
        stats.put("commissionCents", commissions);
        return stats;
    }

    @GetMapping("/settings")
    public List<Setting> settings() { return settingRepository.findAll(); }

    @PutMapping("/settings/{key}")
    @Transactional
    public Setting updateSetting(@PathVariable String key, @RequestBody Map<String, String> body) {
        Setting setting = settingRepository.findById(key).orElseGet(() -> {
            Setting s = new Setting();
            s.setKey(key);
            return s;
        });
        setting.setValue(body.get("value"));
        setting.setUpdatedAt(java.time.Instant.now());
        return settingRepository.save(setting);
    }

    @GetMapping("/finance")
    public Map<String, Object> finance() {
        Map<String, Object> map = new HashMap<>();
        map.put("payments", paymentRepository.findAll());
        return map;
    }
}
