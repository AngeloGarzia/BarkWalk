package com.barkwalk.storage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class StorageTxTest {

    @AfterEach
    void clearSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // Supprime le fichier uniquement si la transaction est annulée.
    @Test
    void deleteOnRollback_deletesOnlyWhenRolledBack() {
        TransactionSynchronizationManager.initSynchronization();
        StorageService storage = mock(StorageService.class);

        StorageTx.deleteOnRollback(storage, "profiles/1/a.jpg");

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        verify(storage, never()).deleteObject("profiles/1/a.jpg");

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(storage).deleteObject("profiles/1/a.jpg");
    }

    // Supprime le fichier lors du commit.
    @Test
    void deleteAfterCommit_deletesOnCommit() {
        TransactionSynchronizationManager.initSynchronization();
        StorageService storage = mock(StorageService.class);

        StorageTx.deleteAfterCommit(storage, "dogs/1/photos/a.jpg");
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(storage).deleteObject("dogs/1/photos/a.jpg");
    }
}
