package com.barkwalk.user.service;

import com.barkwalk.common.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Suppression / anonymisation RGPD du compte connecté (avis & messages → « Membre supprimé » à l'affichage).
@Service
public class AccountDeletionService {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void deleteMyAccount() {
        Long userId = SecurityUtils.currentUserId();
        entityManager.createNativeQuery("SELECT barkwalk_anonymize_user(:id)")
                .setParameter("id", userId)
                .getSingleResult();
    }
}
