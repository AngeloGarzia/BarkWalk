package com.barkwalk.storage;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Helpers transactionnels pour purger les fichiers disque uniquement après commit / rollback. */
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

    /** Delete freshly stored object if the surrounding transaction rolls back. */
    public static void deleteOnRollback(StorageService storageService, String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storageService.deleteObject(storageKey);
                }
            }
        });
    }
}
