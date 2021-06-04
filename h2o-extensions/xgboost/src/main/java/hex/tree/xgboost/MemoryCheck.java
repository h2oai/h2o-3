package hex.tree.xgboost;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import water.util.PrettyPrint;

public class MemoryCheck {

    public static Report runCheck(double offHeapRatio) {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        GlobalMemory globalMemory = hardware.getMemory();
        Runtime runtime = Runtime.getRuntime();
        long available = globalMemory.getAvailable();
        long availableOffHeap = Math.max(available - (runtime.maxMemory() - runtime.totalMemory()), 0);
        long desiredOffHeap = (long) (runtime.maxMemory() * offHeapRatio);
        return new Report(availableOffHeap, desiredOffHeap);
    }
    
    public static class Report {
        public final long _available_off_heap;
        public final long _desired_off_heap;

        public Report(long available_off_heap, long desired_off_heap) {
            _available_off_heap = available_off_heap;
            _desired_off_heap = desired_off_heap;
        }

        public boolean isOffHeapRequirementMet() {
            return _available_off_heap >= _desired_off_heap;
        }

        @Override
        public String toString() {
            return "Estimated Available Off-Heap (assuming JVM heap reaches maximum size): " + PrettyPrint.bytes(_available_off_heap) + 
                    ", Desired Off-Heap: " + PrettyPrint.bytes(_desired_off_heap);
        }
    }

}
