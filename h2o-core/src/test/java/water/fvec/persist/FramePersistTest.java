package water.fvec.persist;

import hex.CreateFrame;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class FramePersistTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testSaveAndLoadDouble() throws IOException {
        Scope.enter();
        try {
            Vec v = Scope.track(createRandomDoubleVec(10_000, 42));
            DKV.put(v);
            Frame f = Scope.track(new Frame(Key.make(), new Vec[] { v }));
            DKV.put(f);
            Frame copy = Scope.track(f.deepCopy("double_copy"));
            File dest = temp.newFolder();
            new FramePersist(f).saveTo(dest.getAbsolutePath(), false).get();
            f.remove(true);
            Frame returned = Scope.track(FramePersist.loadFrom(f._key, dest.getAbsolutePath()).get());
            Frame loaded = DKV.get(f._key).get();
            assertEquals(returned._key, loaded._key);
            assertFrameEquals(copy, loaded, 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testSaveAndLoadCategorical() throws IOException {
        Scope.enter();
        try {
            Vec v = Scope.track(createRandomCategoricalVec(10_000, 42));
            DKV.put(v);
            Frame f = Scope.track(new Frame(Key.make(), new Vec[] { v }));
            DKV.put(f);
            Frame copy = Scope.track(f.deepCopy("cat_copy"));
            File dest = temp.newFolder();
            new FramePersist(f).saveTo(dest.getAbsolutePath(), false).get();
            f.remove(true);
            Frame returned = Scope.track(FramePersist.loadFrom(f._key, dest.getAbsolutePath()).get());
            Frame loaded = DKV.get(f._key).get();
            assertEquals(returned._key, loaded._key);
            assertFrameEquals(copy, loaded, 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testSaveAndLoadFrame() throws IOException {
        Scope.enter();
        try {
            Frame f = Scope.track(createTestFrame());
            String[] origNames = f.names();
            Key[] origKeys = f.keys();
            long origNrow = f.numRows();
            Frame copy = Scope.track(f.deepCopy("cat_copy"));
            File dest = temp.newFolder();
            new FramePersist(f).saveTo(dest.getAbsolutePath(), false).get();
            f.remove(true);
            Frame returned = Scope.track(FramePersist.loadFrom(f._key, dest.getAbsolutePath()).get());
            Frame loaded = DKV.get(f._key).get();
            assertArrayEquals(origNames, loaded._names);
            assertArrayEquals(origKeys, loaded.keys());
            assertEquals(origNrow, loaded.numRows());
            assertEquals(returned._key, loaded._key);
            assertFrameEquals(copy, loaded, 0);
        } finally {
            Scope.exit();
        }
    }

    private static Frame createTestFrame() {
        CreateFrame cf = new CreateFrame();
        cf.rows = 10_000;
        cf.cols = 20;
        cf.categorical_fraction = 0.1;
        cf.integer_fraction = 0.1;
        cf.binary_fraction = 0.1;
        cf.time_fraction = 0.1;
        cf.string_fraction = 0.1;
        cf.binary_ones_fraction = 0.1;
        cf.seed = 42;
        return cf.execImpl().get();
    }

}
