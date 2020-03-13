package water.fvec;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.Value;
import water.persist.PersistManager;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class FileVecUtilsTest {

  @Test
  public void getFirstBytes_cached() {
    try {
      Scope.enter();
      FileVec vec = TestUtil.makeNfsFileVec("./smalldata/logreg/prostate.csv");
      Scope.track(vec);
      
      Key key = vec.chunkKey(0); 
      Value val = vec.chunkIdx(0);
      assertNotNull(val);
      
      Map<Key, Value> h2oStore = Collections.singletonMap(key, val);
      PersistManager pm = mock(PersistManager.class);
      
      assertArrayEquals(val.memOrLoad(), FileVecUtils.getFirstBytes(h2oStore, pm, vec));
      verifyZeroInteractions(pm);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void getFirstBytes_load() throws IOException  {
    FileVec vecMock = mock(FileVec.class);
    vecMock._key = Key.make();
    vecMock._len = 42L;
    vecMock._chunkSize = 50;

    Map<Key, Value> h2oStore = Collections.emptyMap();
    PersistManager pm = mock(PersistManager.class);
    byte[] expected = new byte[3];
    
    when(pm.load(Value.HDFS, vecMock._key, 0, 42)).thenReturn(expected);
    
    assertSame(expected, FileVecUtils.getFirstBytes(h2oStore, pm, vecMock));
  }

}
