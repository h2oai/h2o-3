package water.util;

import water.*;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.parser.FVecParseWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class FrameSizeMonitor extends MrFun<FrameSizeMonitor> {

    private static final String ENABLED_PROP = "util.frameSizeMonitor.enabled";
    private static final String SAFE_COEF_PROP = "util.frameSizeMonitor.safetyCoefficient";
    private static final String SAFE_FREE_MEM_DEFAULT_COEF = "0.1";
    private static final int LOG_LEVEL = Log.INFO;

    private static final boolean ENABLED;
    private static final float SAFE_FREE_MEM_COEF;
    private static final int SLEEP_MS = 100;
    private static final int MB = 1024 * 1024;
    
    private static final Map<Key<Job>, FrameSizeMonitor> registry = new HashMap<>();
    
    static {
        ENABLED = H2O.getSysBoolProperty(ENABLED_PROP, false);
        SAFE_FREE_MEM_COEF = Float.parseFloat(H2O.getSysProperty(SAFE_COEF_PROP, SAFE_FREE_MEM_DEFAULT_COEF));
    }

    private final Key<Job> jobKey;
    private final Map<FVecParseWriter, Long> writers = new HashMap<>();
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
                FrameSizeMonitor task = new FrameSizeMonitor(jobKey);
                registry.put(jobKey, task);
                H2O.submitTask(new LocalMR(task, 1));
                c.accept(task);
            }
        }
    }
    
    private static void finish(Key<Job> jobKey) {
        synchronized (registry) {
            registry.remove(jobKey);
        }
    }

    @Override
    protected void map(int id) {
        float nextProgress = 0.02f;
        Job<Frame> job = DKV.getGet(jobKey);
        while (job.isRunning() && nextProgress < 1f) {
            float currentProgress = job.progress();
            if (currentProgress >= nextProgress) {
                if (isMemoryUsageOverLimit() && isFrameSizeOverLimit(currentProgress, job)) {
                    job.stop();
                    break;
                } else if (nextProgress < 0.1f) {
                    nextProgress = currentProgress + 0.01f;
                } else {
                    nextProgress = currentProgress + 0.1f;
                }
            } else if (Log.isLoggingFor(LOG_LEVEL)) {
                Log.log(LOG_LEVEL, "FrameSizeMonitor: waiting for progress " + currentProgress + " to jump over " + nextProgress);
            }
            try {
                Thread.sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
    
    private boolean isFrameSizeOverLimit(float progress, Job<Frame> job) {
        long currentFrameSize = getUsedMemory();
        long projectedAdditionalFrameSize = (long) ((1 - progress) * (currentFrameSize / progress));
        long availableMemory = getAvailableMemory();
        long usableMemory = (long) (availableMemory - (totalMemory * SAFE_FREE_MEM_COEF));
        if (Log.isLoggingFor(LOG_LEVEL)) {
            Log.log(LOG_LEVEL, "FrameSizeMonitor: Frame " + job._result + ": \n" +
                " used: " + (currentFrameSize / MB) + " MB\n" +
                " progress: " + (progress) + "\n" +
                " projected additional: " + (projectedAdditionalFrameSize / MB) + " MB\n" +
                " projected total: " + ((currentFrameSize + projectedAdditionalFrameSize) / MB) + " MB\n" +
                " availableMemory: " + (availableMemory / MB) + " MB\n" +
                " totalMemory: " + (totalMemory / MB) + " MB\n" +
                " usableMemory: " + (usableMemory / MB) + " MB\n" +
                " enough: " + (projectedAdditionalFrameSize > usableMemory));
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
    
    private long getUsedMemory() {
        long usedMemory;
        synchronized (writers) {
            usedMemory = committedMemory;
            for (Map.Entry<FVecParseWriter, Long> e : writers.entrySet()) {
                FVecParseWriter writer = e.getKey();
                NewChunk[] nvs = writer.getNvs();
                if (nvs != null) {
                    writers.put(writer, getUsedMemory(nvs));
                }
                usedMemory += e.getValue();
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
            writers.put(writer, 0L);
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
