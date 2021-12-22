package ai.h2o.automl;

import ai.h2o.automl.WorkAllocations.Work;
import org.junit.Test;

import static ai.h2o.automl.Algo.*;
import static ai.h2o.automl.WorkAllocations.JobType.*;
import static org.junit.Assert.*;

public class WorkAllocationsTest {

    private WorkAllocations makeAllocations() {
        return new WorkAllocations()
                .allocate(new Work("gbm1", GBM, ModelBuild, 1, 10))
                .allocate(new Work("gbm2", GBM, ModelBuild, 1, 20))
                .allocate(new Work("drf1", DRF, ModelBuild, 1, 10))
                .allocate(new Work("gbm_grid1", GBM, HyperparamSearch, 1, 100))
                .allocate(new Work("drf_grid1", DRF, HyperparamSearch, 1, 60))
                .allocate(new Work("gbm3", GBM, ModelBuild, 1, 30))
                .allocate(new Work("gbm4", GBM, ModelBuild, 1, 40))
                .allocate(new Work("gbm_grid2", GBM, HyperparamSearch, 1, 200));
    }

    @Test
    public void test_getAllocation() {
        WorkAllocations allocations = makeAllocations().freeze();
        Work work = allocations.getAllocation("gbm3", GBM);
        assertNotNull(work);
        assertEquals(30, work._weight);
        assertEquals(ModelBuild, work._type);
    }

    @Test
    public void test_getAllocations() {
        WorkAllocations allocations = makeAllocations().freeze();
        assertEquals(0, allocations.getAllocations(w -> w._algo == XGBoost).length);
        Work[] work = allocations.getAllocations(w -> w._algo == GBM);
        assertEquals(6, work.length);
    }

    @Test(expected = IllegalStateException.class)
    public void test_cannot_allocate_new_work_after_freeze() {
        WorkAllocations allocations = makeAllocations().freeze();
        allocations.allocate(new Work("", GBM, ModelBuild, 1, 10));
    }

    @Test(expected = IllegalStateException.class)
    public void test_cannot_remove_allocations_after_freeze() {
        WorkAllocations allocations = makeAllocations().freeze();
        allocations.remove(DRF);
    }

    @Test
    public void test_remove_algo() {
        WorkAllocations allocations = makeAllocations();

        assertEquals(8, allocations.getAllocations(w -> true).length);
        assertEquals(2, allocations.getAllocations(w -> w._algo == DRF).length);
        assertNotNull(allocations.getAllocation("drf1", DRF));

        allocations.remove(DRF);

        assertEquals(6, allocations.getAllocations(w -> true).length);
        assertEquals(0, allocations.getAllocations(w -> w._algo == DRF).length);
        assertNull(allocations.getAllocation("drf1", DRF));
    }

    @Test
    public void test_remainingWork() {
        WorkAllocations allocations = makeAllocations().freeze();
        assertEquals(470, allocations.remainingWork());
        assertEquals(360, allocations.remainingWork(w -> w._type == HyperparamSearch));

        for (Work w : allocations.getAllocations(w -> w._id.endsWith("1"))) { w.consume(); }
        assertEquals(290, allocations.remainingWork());
        assertEquals(200, allocations.remainingWork(w -> w._type == HyperparamSearch));
    }

    @Test
    public void test_consume() {
        WorkAllocations allocations = makeAllocations().freeze();
        for (Work w : allocations.getAllocations(w -> true)) {
            assertTrue(w._weight > 0);
            w.consume();
            assertEquals(0, w._weight);
        }
    }
}
