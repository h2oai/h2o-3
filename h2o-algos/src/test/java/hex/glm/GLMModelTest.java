package hex.glm;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.H2O;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.H2ORunner;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
public class GLMModelTest extends TestUtil {

    @Test
    public void testNVIFModelsInParallel() {
        Key<Frame> smolFKey = Key.make();
        Key<Frame> bigFKey = Key.make();
        try {
            { // case small
                DKV.put(new DummySizedFrame(smolFKey, (long) 1e6 - 1));
                GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
                parms._train = smolFKey;
                assertEquals(H2O.ARGS.nthreads, GLMModel.nVIFModelsInParallel(parms));
            }
            { // case big data (> 1e6)
                DKV.put(new DummySizedFrame(bigFKey, (long) 1e6));
                GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
                parms._train = bigFKey;
                assertEquals(2, GLMModel.nVIFModelsInParallel(parms));
            }
            { // case CV
                GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
                parms._is_cv_model = true;
                assertEquals(1, GLMModel.nVIFModelsInParallel(parms));
            }
        } finally {
            DKV.remove(smolFKey);
            DKV.remove(bigFKey);
        }
    }

    @Test
    public void testNVIFModelsInParallel_userSpec() {
        final Key<Frame> key = Key.make();
        final String propertyKey = "sys.ai.h2o." + "glm.vif." + key + ".nparallelism";
        try {
            System.setProperty(propertyKey, "42");
            GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
            parms._train = key;
            assertEquals(42, GLMModel.nVIFModelsInParallel(parms));
        } finally {
            System.getProperties().remove(propertyKey);
        }
    }

    private static class DummySizedFrame extends Frame {

        private final long _byteSize;

        private DummySizedFrame(Key<Frame> key, long byteSize) {
            super(key);
            _byteSize = byteSize;
        }

        @Override
        public long byteSize() {
            return _byteSize;
        }
    }

}
