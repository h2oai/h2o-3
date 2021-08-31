package water;

import water.util.UnsafeUtils;

import java.util.Random;

public class JavaSelfCheck {

    /**
     * Runs basic checks to ensure that H2O is compatible with the current JVM
     * 
     * @return true if all compatibility checks passed, false otherwise
     */
    public static boolean checkCompatibility() {
        if (!checkUnsafe())
            return false;
        if (!checkWeaver())
            return false;
        return checkSerialization();
    }

    // Are we able to serialize and deserialize data?
    private static boolean checkSerialization() {
        byte[] bytes = AutoBuffer.serializeBootstrapFreezable(new HeartBeat());
        return AutoBuffer.deserializeBootstrapFreezable(bytes) instanceof HeartBeat;
    }

    // Are we able to generate Icers? Does javassist work as expected?
    private static boolean checkWeaver() {
        // HeartBeat class is guaranteed to be part of boostrap classes, otherwise clouding couldn't work
        Freezable<?> f = TypeMap.newFreezable(HeartBeat.class.getName());
        if (! (f instanceof HeartBeat)) {
            return false;
        }
        // check that we can generate Icer
        return TypeMap.getIcer(f) != null;
    }

    // Is Unsafe available and working as expected?
    private static boolean checkUnsafe() {
        final int N = 1024;
        final Random r = new Random();
        final double[] doubleData = new double[N];
        final byte[] doubleBytes = new byte[N * 8];
        final long[] longData = new long[N];
        final byte[] longBytes = new byte[N * 8];
        for (int i = 0; i < N; i++) {
            doubleData[i] = r.nextDouble();
            UnsafeUtils.set8d(doubleBytes, i * 8, doubleData[i]);
            longData[i] = r.nextLong();
            UnsafeUtils.set8(longBytes, i * 8, longData[i]);
        }
        for (int i = 0; i < N; i++) {
            double d = UnsafeUtils.get8d(doubleBytes, i * 8);
            if (d != doubleData[i])
                return false;
            long l = UnsafeUtils.get8(longBytes, i * 8);
            if (l != longData[i])
                return false;
        }
        return true;
    }

}
