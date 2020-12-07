package hex;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.H2O;
import water.Job;
import water.Key;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.dummy.DummyModelBuilder;
import water.test.dummy.DummyModelParameters;

import static org.junit.Assert.assertEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class CVModelBuilderTest {

    @Test
    @SuppressWarnings("unchecked")
    public void bulkBuildModels() {
        Job<Model> j = new Job<>(null, null, "BulkBuilding");
        Key key1 = Key.make(j._key + "-dummny-1");
        Key key2 = Key.make(j._key + "-dummny-2");
        try {
            j.start(new BulkRunner(j), 10).get();
            assertEquals("Computed Dummy 1", DKV.getGet(key1).toString());
            assertEquals("Computed Dummy 2", DKV.getGet(key2).toString());
        } finally {
            DKV.remove(key1);
            DKV.remove(key2);
        }
    }

    public static class BulkRunner extends H2O.H2OCountedCompleter<BulkRunner> {
        private Job _j;
        private BulkRunner(Job j) { _j = j; }
        @Override
        public void compute2() {
            ModelBuilder<?, ?, ?>[] builders = {
                new DummyModelBuilder(new DummyModelParameters("Dummy 1", Key.make(_j._key + "-dummny-1"))),
                new DummyModelBuilder(new DummyModelParameters("Dummy 2", Key.make(_j._key + "-dummny-2")))
            };
            new CVModelBuilder("dummy-group", _j, builders, 1 /*sequential*/).bulkBuildModels();
            tryComplete();
        }
    }

}
