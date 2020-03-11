package water;

import water.fvec.Frame;
import water.fvec.NewChunk;
import water.parser.FVecParseWriter;
import water.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class FrameSizeMonitor implements Runnable, Thread.UncaughtExceptionHandler {

    private static final String LOG_LEVEL_PROP = "util.frameSizeMonitor.logLevel";
    private static final String ENABLED_PROP = "util.frameSizeMonitor.enabled";
    private static final String SAFE_COEF_PROP = "util.frameSizeMonitor.safetyCoefficient";
    private static final String SAFE_FREE_MEM_DEFAULT_COEF = "0.2";

    private static final int LOG_LEVEL;
    private static final boolean ENABLED;
    private static final float SAFE_FREE_MEM_COEF;
    private static final int SLEEP_MS = 100;
    private static final int MB = 1024 * 1024;
    
    private static final Map<Key<Job>, FrameSizeMonitor> registry = new HashMap<>();
    
    static {
        LOG_LEVEL = Integer.parseInt(H2O.getSysProperty(LOG_LEVEL_PROP, String.valueOf(Log.DEBUG)));
        ENABLED = H2O.getSysBoolProperty(ENABLED_PROP, false);
        SAFE_FREE_MEM_COEF = Float.parseFloat(H2O.getSysProperty(SAFE_COEF_PROP, SAFE_FREE_MEM_DEFAULT_COEF));
    }

    private final Key<Job> jobKey;
    private final Set<FVecParseWriter> writers = new HashSet<>();
    private final long totalMemory = getTotalMemory();

    private long committedMemory = 0;
    
    FrameSizeMonitor(Key<Job> jobKey) {
        this.jobKey = jobKey;
    }
    
    public static void get(Key<Job> jobKey, Consumer<FrameSizeMonitor> c) {
        synchronized (registry) {
            if (registry.containsKey(jobKey)) {
                c.accept(registry.get(jobKey));
            } else if (ENABLED) {
                if (jobKey.get().stop_requested()) {
                    // throw an exception to stop the parsing
                    throw new IllegalStateException("Memory is running low. Forcefully terminating.");
                } else {
                    FrameSizeMonitor monitor = new FrameSizeMonitor(jobKey);
                    registry.put(jobKey, monitor);
                    Thread t = new Thread(monitor, "FrameSizeMonitor-" + jobKey.get()._result);
                    t.setUncaughtExceptionHandler(monitor);
                    t.start();
                    c.accept(monitor);
                }
            }
        }
    }
    
    private static void finish(Key<Job> jobKey) {
        synchronized (registry) {
            registry.remove(jobKey);
        }
    }

    @Override
    public void run() {
        float nextProgress = 0.02f;
        Job<Frame> job = jobKey.get();
        while (job.isRunning() && nextProgress < 1f) {
            if (!MemoryManager.canAlloc()) {
                Log.info("FrameSizeMonitor: MemoryManager is running low on memory, stopping job " + jobKey + " writing frame " + job._result);
                job.fail(new RuntimeException("Aborting due to critically low memory."));
                break;
            }
            float currentProgress = job.progress();
            if (currentProgress >= nextProgress) {
                if (isMemoryUsageOverLimit() && isFrameSizeOverLimit(currentProgress, job)) {
                    job.fail(new RuntimeException("Aborting due to projected memory usage too high."));
                    break;
                } else if (nextProgress < 0.1f) {
                    nextProgress = currentProgress + 0.01f;
                } else {
                    nextProgress = currentProgress + 0.1f;
                }
            } else if (Log.isLoggingFor(LOG_LEVEL)) {
                Log.log(LOG_LEVEL, "FrameSizeMonitor: waiting for progress " + currentProgress + " to jump over " + nextProgress);
            }
            synchronized (this) {
                try {
                    wait(SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (Log.isLoggingFor(LOG_LEVEL)) {
            if (!job.isStopped()) {
                job.get(); // wait for job to finish
            }
            if (job.isDone()) {
                Log.log(LOG_LEVEL, "FrameSizeMonitor: finished monitoring job " + jobKey +
                    ", final frame size is " + (job._result.get().byteSize() / MB) + " MB");
            }
        }
        finish(jobKey);
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Log.err(e);
        finish(jobKey);
    }

    private boolean isMemoryUsageOverLimit() {
        long availableMemory = getAvailableMemory();
        long minimumAvailableMemory = (long) (totalMemory * 2 * SAFE_FREE_MEM_COEF);
        if (availableMemory < minimumAvailableMemory) {
            Log.log(LOG_LEVEL, "FrameSizeMonitor: Checking output of job " + jobKey + " because the available memory " + 
                (availableMemory / MB) + " MB is lower than threshold " + (minimumAvailableMemory / MB) + " MB " +
                "(" + SAFE_FREE_MEM_COEF + " of " + (totalMemory / MB) + " MB total memory)");
            return true;
        } else {
            Log.log(LOG_LEVEL, "FrameSizeMonitor: Overall memory usage is ok, still have " + 
                (availableMemory / MB) + " MB available of " + (minimumAvailableMemory / MB) + " MB required.");
            return false;
        }
    }
    
    private boolean isFrameSizeOverLimit(float progress, Job job) {
        long currentCommittedMemory = committedMemory;
        long currentInProgressMemory = getInProgressMemory();
        long projectedTotalFrameSize = (long) (currentInProgressMemory + (currentCommittedMemory / progress));
        long projectedAdditionalFrameSize = projectedTotalFrameSize - currentCommittedMemory - currentInProgressMemory;
        long availableMemory = getAvailableMemory();
        long usableMemory = (long) (availableMemory - (totalMemory * SAFE_FREE_MEM_COEF));
        if (Log.isLoggingFor(LOG_LEVEL)) {
            Log.log(LOG_LEVEL, "FrameSizeMonitor: Frame " + job._result + ": \n" +
                " committed: " + (currentCommittedMemory / MB) + " MB\n" +
                " loading: " + (currentInProgressMemory / MB) + " MB\n" +
                " progress: " + progress + "\n" +
                " projected additional: " + (projectedAdditionalFrameSize / MB) + " MB\n" +
                " projected total: " + (projectedTotalFrameSize / MB) + " MB\n" +
                " availableMemory: " + (availableMemory / MB) + " MB\n" +
                " totalMemory: " + (totalMemory / MB) + " MB\n" +
                " usableMemory: " + (usableMemory / MB) + " MB\n" +
                " enough: " + (projectedAdditionalFrameSize <= usableMemory));
        }
        if (projectedAdditionalFrameSize > usableMemory) {
            Log.err("FrameSizeMonitor: Stopping job " + jobKey + " writing frame " + job._result +
                " because the projected size of " + (projectedAdditionalFrameSize / MB) + " MB " +
                " does not safely fit in " + (availableMemory / MB) + " MB of available memory.");
            return true;
        } else {
            if (Log.isLoggingFor(LOG_LEVEL)) {
                Log.log(LOG_LEVEL, "FrameSizeMonitor: Projected memory " + (projectedAdditionalFrameSize / MB) + "MB for frame " +
                    job._result + " fits safely into " + (availableMemory / MB) + " MB of available memory.");
            }
            return false;
        }
    }
    
    private long getInProgressMemory() {
        long usedMemory = 0;
        synchronized (writers) {
            for (FVecParseWriter writer : writers) {
                NewChunk[] nvs = writer.getNvs();
                if (nvs != null) {
                    usedMemory += getUsedMemory(nvs);
                }
            }
        }
        return usedMemory;
    }

    private long getUsedMemory(NewChunk[] nvs) {
        long usedMemory = 0;
        for (NewChunk nv : nvs) {
            if (nv != null) {
                usedMemory += nv.byteSize();
            }
        }
        return usedMemory;
    }

    private long getTotalMemory() {
        return H2O.SELF._heartbeat.get_kv_mem() + H2O.SELF._heartbeat.get_pojo_mem() + H2O.SELF._heartbeat.get_free_mem();
    }
    
    private long getAvailableMemory() {
        return H2O.SELF._heartbeat.get_free_mem();
    }

    public static void register(Key<Job> jobKey, FVecParseWriter writer) {
        get(jobKey, t -> t.register(writer));
    }

    public void register(FVecParseWriter writer) {
        synchronized (writers) {
            writers.add(writer);
        }
    }
    
    public static void closed(Key<Job> jobKey, FVecParseWriter writer, long mem) {
        get(jobKey, t -> t.closed(writer, mem));
    }
    
    public void closed(FVecParseWriter writer, long mem) {
        synchronized (writers) {
            writers.remove(writer);
            committedMemory += mem;
        }
    }
}
