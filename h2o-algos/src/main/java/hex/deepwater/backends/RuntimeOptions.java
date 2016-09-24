package hex.deepwater.backends;

public class RuntimeOptions {
    private boolean useGpu;
    private int seed;
    private int []deviceID;

    public int[] getDeviceID() {
        if (deviceID == null || deviceID.length == 0) {
            int[] zero = new int[1];
            zero[0] = 1;
            return zero;
        }
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

    public int getSeed() {
        return seed;
    }

    public void setSeed(int seed) {
        this.seed = seed;
    }

}
