package hex.tree.xgboost;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class XGBoostGPUCVModelBuilderTest {

    @Test
    public void testInitAllocator() {
        assertArrayEquals(new int[]{0}, new XGBoostGPUCVModelBuilder.GPUAllocator(Collections.singletonList(0))._gpu_utilization);
        assertArrayEquals(new int[]{-1, -1, -1, 0}, new XGBoostGPUCVModelBuilder.GPUAllocator(Collections.singletonList(3))._gpu_utilization);
        assertArrayEquals(new int[]{-1, 0, -1, 0}, new XGBoostGPUCVModelBuilder.GPUAllocator(Arrays.asList(1, 3))._gpu_utilization);
    }

    @Test
    public void testTakeLeastUtilizedGPU_noUtilization() {
        XGBoostGPUCVModelBuilder.GPUAllocator allocator = new XGBoostGPUCVModelBuilder.GPUAllocator(new int[]{-1, 0, -1, 0});
        assertEquals(1, allocator.takeLeastUtilizedGPU()); // take the first one available
    }

    @Test
    public void testTakeLeastUtilizedGPU_someUtilized() {
        XGBoostGPUCVModelBuilder.GPUAllocator allocator = new XGBoostGPUCVModelBuilder.GPUAllocator(new int[]{-1, 1, -1, 0});
        assertEquals(3, allocator.takeLeastUtilizedGPU()); // take the one that is not utilized
    }

    @Test
    public void testTakeLeastUtilizedGPU_allUtilized() {
        XGBoostGPUCVModelBuilder.GPUAllocator allocator = new XGBoostGPUCVModelBuilder.GPUAllocator(new int[]{-1, 3, -1, 3});
        assertEquals(1, allocator.takeLeastUtilizedGPU()); // take the first one
    }

    @Test
    public void testReleaseGPU() {
        XGBoostGPUCVModelBuilder.GPUAllocator allocator = new XGBoostGPUCVModelBuilder.GPUAllocator(new int[]{-1, 3, -1, 42});
        allocator.releaseGPU(1);
        assertArrayEquals(new int[]{-1, 2, -1, 42}, allocator._gpu_utilization);
    }

}
