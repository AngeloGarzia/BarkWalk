package com.barkwalk.storage;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Helpers transactionnels pour purger les fichiers disque uniquement après commit. */
public final class StorageTx {
    private StorageTx() {}

    public static void deleteAfterCommit(StorageService storageService, String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storageService.deleteObject(storageKey);
                }
            });
        } else {
            storageService.deleteObject(storageKey);
        }
    }
}
