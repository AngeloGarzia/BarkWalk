package com.barkwalk.admin.web;

import com.barkwalk.admin.dto.AdminDtos.DashboardResponse;
import com.barkwalk.admin.dto.AdminDtos.FinanceResponse;
import com.barkwalk.admin.dto.AdminDtos.SettingResponse;
import com.barkwalk.admin.dto.AdminDtos.SettingUpdateRequest;
import com.barkwalk.admin.service.AdminService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private final AdminService adminService;

    public AdminDashboardController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return adminService.dashboard();
    }

    @GetMapping("/settings")
    public List<SettingResponse> settings() {
        return adminService.settings();
    }

    @PutMapping("/settings/{key}")
    public SettingResponse updateSetting(@PathVariable String key, @RequestBody SettingUpdateRequest body) {
        return adminService.updateSetting(key, body.value());
    }

    @GetMapping("/finance")
    public FinanceResponse finance() {
        return adminService.finance();
    }
}
