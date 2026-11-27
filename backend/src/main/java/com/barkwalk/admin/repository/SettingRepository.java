package com.barkwalk.admin.repository;

import com.barkwalk.admin.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;
// Lecture des réglages par clé.

public interface SettingRepository extends JpaRepository<Setting, String> {
}
