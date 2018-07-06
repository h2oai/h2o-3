package water.persist;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

/**
 * A class wrapping {@link Storage}, enabling safe lazy initialization by only providing getStorage method, not risking for
 * developers to access storage field directly.
 */
final class GcsStorageProvider {

    private Storage storage;

    /**
     * Returns an existing instance of {@link Storage} or creates a new one, if not initialized.
     * Lazy-initialization of storage does not slow down startup of H2O (attempts are made to connect to GCS).
     * The connection status and {@link com.google.auth.Credentials} are checked at actual request-time.
     *
     * @return An instance of {@link Storage}, if initialized
     */
    protected Storage getStorage() {
        if (storage == null) {
            storage = StorageOptions.getDefaultInstance().getService();
        }

        return storage;
    }
}
