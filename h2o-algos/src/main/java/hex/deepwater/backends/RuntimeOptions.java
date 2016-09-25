package hex.deepwater.backends;

public class RuntimeOptions {
    private boolean useGpu = true;
    private long seed = System.nanoTime();
    private int []deviceID = new int[]{0};

    public int[] getDeviceID() {
        return deviceID;
    }

    public void setDeviceID(int ... deviceID) {
        this.deviceID = deviceID;
    }

    public boolean useGPU() {
        return useGpu;
    }

    public void setUseGPU(boolean use_gpu) {
        this.useGpu = use_gpu;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

}
