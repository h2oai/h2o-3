package water.parser.parquet;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.fvec.Frame;
import water.fvec.HDFSFileVec;
import water.fvec.Vec;
import water.persist.VecFileSystem;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FileUtils;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class VecReaderEnvTest {

  @Test
  public void makeGenericVec() {
    try {
      Scope.enter();
      Vec v = Scope.track(Vec.makeZero(42L));
      VecReaderEnv env = VecReaderEnv.make(v);
      Assert.assertEquals(VecFileSystem.VEC_PATH, env.getPath());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void makeHdfsVec() throws IOException {
    try {
      Scope.enter();
      File f = FileUtils.getFile("smalldata/logreg/prostate.csv");
      Frame container = (Frame) HDFSFileVec.make(f.toURI().toString(), f.getTotalSpace()).get();
      Scope.track(container);
      Vec v = container.vec(0);
      assertTrue(v instanceof HDFSFileVec);
      VecReaderEnv env = VecReaderEnv.make(v);
      Assert.assertNotEquals(VecFileSystem.VEC_PATH, env.getPath());
    } finally {
      Scope.exit();
    }
  }

}
