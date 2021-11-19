package water;

/**
 * H2O wrapper around Runtime class exposing modified versions of some functions
 */
public class H2ORuntime {

    static final int ACTIVE_PROCESSOR_COUNT = getActiveProcessorCount();

    /**
     * Returns the number of processors available to H2O.
     * 
     * @return number of available processors
     */
    public static int availableProcessors() {
        return availableProcessors(ACTIVE_PROCESSOR_COUNT);
    }

    static int availableProcessors(int activeProcessorCount) {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        if (activeProcessorCount > 0 && activeProcessorCount < availableProcessors) {
            availableProcessors = activeProcessorCount;
        }
        return availableProcessors;
    }

    public static int getActiveProcessorCount() {
        return Integer.parseInt(System.getProperty("sys.ai.h2o.activeProcessorCount", "0"));
    }
    
}
