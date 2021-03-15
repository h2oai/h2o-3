package hex.faulttolerance;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import hex.Model;
import hex.grid.Grid;
import hex.grid.GridSearch;
import org.apache.log4j.Logger;
import water.*;
import water.api.GridSearchHandler;
import water.fvec.Frame;
import water.fvec.persist.FramePersist;
import water.fvec.persist.PersistUtils;
import water.util.FileUtils;
import water.util.IcedHashMap;

import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * <h2>H2O Auto-Recovery Support</h2>
 * 
 * <p>This class encapsulates what is the core of H2O's (auto)recovery support. It manages
 * storing of data needed to recover a {@link Recoverable} process as well as performing
 * the actual recovery and re-start of such process.</p>
 * 
 * <h3>Preparing for recovery</h3>
 * 
 * <p>A {@link Recoverable} will instantiate a {@link Recovery} instance at start and call
 * the {@link Recovery#onStart(Recoverable, Job)} method first. This should provide sufficient
 * data to re-instantiate the Recoverable later on. Every time a {@link Model} is successfully
 * built the Recoverable should call {@link Recovery#onModel(Recoverable, Key)} method so that
 * this Model can be later recovered and does not need to be trained again. If a Recoverable
 * process finishes successfully it will call {@link Recovery#onDone(Recoverable)}, this will
 * lead to all stored data being cleaned up.</p>
 * 
 * <h3>Recovering manually</h3>
 * 
 * <p>Recoverable objects may use this class to restore their state in case user has sent such a
 * request. This is useful since this class implements mechanisms not implemented by other
 * components in the system (such as storing parameter references).</p>
 * 
 * <h3>Auto-Recovery</h3>
 * 
 * <p>Calling {@link Recovery#autoRecover(Optional)} will trigger an auto-recovery. The method
 * will check if there is any recovery data present in the supplied directory and if there is
 * it will load all the stored data, references and models and resume the Recoverable process.
 * It is the responsibility of the Recoverable to check in what state was it stored (models
 * already trained) and continue on a best-effort basis with its job such that no unnecessary
 * repetition of work is done.</p>
 * 
 * @param <T> Type of object to be recovered
 */
public class Recovery<T extends Keyed> {
    
    private static final Logger LOG = Logger.getLogger(Recovery.class);

    public static final String REFERENCES_META_FILE_SUFFIX = "_references";
    public static final String RECOVERY_META_FILE = "recovery.json";
    
    public static final String INFO_CLASS = "class";
    public static final String INFO_RESULT_KEY = "resultKey";
    public static final String INFO_JOB_KEY = "jobKey";

    /**
     * Will check the supplied directory for presence of recovery metadata and
     * if found, trigger a recovery of interrupted Recoverable process.
     * 
     * @param autoRecoveryDirOpt directory from which to load recovery data
     */
    public static void autoRecover(Optional<String> autoRecoveryDirOpt) {
        if (!autoRecoveryDirOpt.isPresent() || autoRecoveryDirOpt.get().length() == 0) {
            LOG.debug("Auto recovery dir not configured.");
        } else {
            String autoRecoveryDir = autoRecoveryDirOpt.get();
            LOG.info("Initializing auto recovery from " + autoRecoveryDir);
            H2O.submitTask(new H2O.H2OCountedCompleter(H2O.MIN_PRIORITY) {
                @Override
                public void compute2() {
                    new Recovery(autoRecoveryDir).autoRecover();
                    tryComplete();
                }
            });
        }
    }

    /**
     * {@link Frame} object referenced by params are stored in a distributed manner, hence we need to
     * distinguish them from other types of {@link Keyed} references.
     */
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
    
    public String recoveryMetaFile() {
        return recoveryFile(RECOVERY_META_FILE);
    }

    /**
     * Called when the training begins, so that initial state can be persisted
     * 
     * @param r a Recoverable to persist
     */
    public void onStart(final Recoverable<T> r, final Job job) {
        writtenFiles.addAll(r.exportBinary(storagePath, true));
        exportReferences(r);
        writeRecoveryInfo(r, job.getKey());
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
            r.exportBinary(storagePath, false);
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
    
    private void writeRecoveryInfo(final Recoverable<T> r, Key<Job> jobKey) {
        Map<String, String> info = new HashMap<>();
        info.put(INFO_CLASS, r.getClass().getName());
        info.put(INFO_JOB_KEY, jobKey.toString());
        info.put(INFO_RESULT_KEY, r.getKey().toString());
        final URI infoUri = FileUtils.getURI(recoveryMetaFile());
        writtenFiles.add(infoUri.toString());
        PersistUtils.writeStream(infoUri, w -> w.write(new Gson().toJson(info)));
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

    /**
     * Will locate a references metadata file and load all objects mentioned in this file.
     * 
     * @param r a Recoverable whose references are to be loaded
     */
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
    
    void autoRecover() {
        URI recoveryMetaUri = FileUtils.getURI(recoveryMetaFile());
        if (!PersistUtils.exists(recoveryMetaUri)) {
            LOG.info("No auto-recovery information found.");
            return;
        }
        Map<String, String> recoveryInfo = PersistUtils.readStream(
            recoveryMetaUri, 
            r -> new Gson().fromJson(r, new TypeToken<Map<String, String>>(){}.getType())
        ); 
        String className = recoveryInfo.get(INFO_CLASS);
        Key<Job> jobKey = Key.make(recoveryInfo.get(INFO_JOB_KEY));
        Key<?> resultKey = Key.make(recoveryInfo.get(INFO_RESULT_KEY));
        if (Grid.class.getName().equals(className)) {
            LOG.info("Auto-recovering previously interrupted grid search.");
            Grid grid = Grid.importBinary(recoveryFile(resultKey), true);
            GridSearch.resumeGridSearch(
                jobKey, grid,
                new GridSearchHandler.DefaultModelParametersBuilderFactory(),
                (Recovery<Grid>) this
            );
        } else {
            LOG.error("Unable to recover object of class " + className);
        }
    }

}
