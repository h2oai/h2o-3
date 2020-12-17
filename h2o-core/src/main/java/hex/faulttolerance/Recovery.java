package hex.faulttolerance;

import hex.Model;
import water.*;
import water.fvec.Frame;
import water.fvec.persist.FramePersist;
import water.fvec.persist.PersistUtils;
import water.util.FileUtils;
import water.util.IcedHashMap;

import java.io.IOException;
import java.net.URI;
import java.util.*;

public class Recovery<T extends Keyed> {

    public static final String REFERENCES_META_FILE_SUFFIX = "_references";

    public enum ReferenceType {
        FRAME, KEYED
    }

    private final String storagePath;
    private final List<String> writtenFiles = new ArrayList<>(); 

    /**
     * @param storagePath directory to use as base for recovery snapshots
     */
    public Recovery(String storagePath) {
        this.storagePath = storagePath;
    }
    
    private String recoveryFile(String f) {
        return storagePath + "/" + f;
    }

    private String recoveryFile(Key key) {
        return recoveryFile(key.toString());
    }
    
    public String referencesMetaFile(Recoverable<T> r) {
        return recoveryFile(r.getKey().toString() + REFERENCES_META_FILE_SUFFIX);
    }

    /**
     * Called when the training begins, so that initial state can be persisted
     * 
     * @param r a Recoverable to persist
     */
    public void onStart(final Recoverable<T> r) {
        writtenFiles.add(r.exportBinary(storagePath));
        exportReferences(r);
    }

    /**
     * Called by the Recoverable to notify of new model was trained and needs to persisted
     * 
     * @param r a Recoverable to update
     * @param modelKey key of the newly trained model
     */
    public void onModel(final Recoverable<T> r, Key<Model> modelKey) {
        try {
            String modelFile = recoveryFile(modelKey);
            modelKey.get().exportBinaryModel(modelFile, true);
            writtenFiles.add(modelFile);
            r.exportBinary(storagePath);
        } catch (IOException e) {
            // this should not happen since storagePath should be writable because
            // grid was already written to it
            throw new RuntimeException("Failed to store model for fault tolerance.", e);
        }
    }

    /**
     * Called by the recoverable that the training was finished successfully. This means that
     * recovery snapshots (persisted data) is no longer needed and can be deleted.
     */
    public void onDone(Recoverable<T> r) {
        final URI storageUri = FileUtils.getURI(storagePath);
        for (String path : writtenFiles) {
            URI pathUri = FileUtils.getURI(path);
            H2O.getPM().getPersistForURI(storageUri).delete(pathUri.toString());
        }
    }

    /**
     * Saves all of the keyed objects used by this Grid's params. Files are named by objects' keys.
     */
    public void exportReferences(final Recoverable<T> r) {
        final Set<Key<?>> keys = r.getDependentKeys();
        final IcedHashMap<String, String> referenceKeyTypeMap = new IcedHashMap<>();
        for (Key<?> k : keys) {
            persistObj(k.get(), referenceKeyTypeMap);
        }
        final URI referencesUri = FileUtils.getURI(referencesMetaFile(r));
        writtenFiles.add(referencesUri.toString());
        PersistUtils.write(referencesUri, ab -> ab.put(referenceKeyTypeMap));
    }

    private void persistObj(
        final Keyed<?> o,
        Map<String, String> referenceKeyTypeMap
    ) {
        if (o instanceof Frame) {
            referenceKeyTypeMap.put(o._key.toString(), ReferenceType.FRAME.toString());
            String[] writtenFrameFiles = new FramePersist((Frame) o).saveToAndWait(storagePath, true);
            writtenFiles.addAll(Arrays.asList(writtenFrameFiles));
        } else if (o != null) {
            referenceKeyTypeMap.put(o._key.toString(), ReferenceType.KEYED.toString());
            String destFile = storagePath + "/" + o._key;
            URI dest = FileUtils.getURI(destFile);
            PersistUtils.write(dest, ab -> ab.putKey(o._key));
            writtenFiles.add(destFile);
        }
    }

    public void loadReferences(final Recoverable<T> r) {
        final URI referencesUri = FileUtils.getURI(storagePath + "/" + r.getKey() + REFERENCES_META_FILE_SUFFIX);
        Map<String, String> referencesMap = PersistUtils.read(referencesUri, AutoBuffer::get);
        final Futures fs = new Futures();
        referencesMap.forEach((key, type) -> {
            switch (ReferenceType.valueOf(type)) {
                case FRAME: 
                    FramePersist.loadFrom(Key.make(key), storagePath).get();
                    break;
                case KEYED:
                    PersistUtils.read(URI.create(storagePath + "/" + key), ab -> ab.getKey(Key.make(key), fs));
                    break;
                default:
                    throw new IllegalStateException("Unknown reference type " + type);
            }
        });
        fs.blockForPending();
    }

}
