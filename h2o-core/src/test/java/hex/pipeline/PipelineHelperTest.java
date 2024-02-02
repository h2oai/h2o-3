package hex.pipeline;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.junit.rules.DKVIsolation;
import water.junit.rules.ScopeTracker;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.io.Serializable;

import static hex.pipeline.PipelineHelper.reassign;
import static hex.pipeline.PipelineHelper.reassignInplace;
import static org.junit.Assert.*;
import static water.TestUtil.ard;
import static water.TestUtil.assertFrameEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class PipelineHelperTest {
  
  private static class ReferenceFrameProvider extends ExternalResource implements Serializable {
    private static final Key<Frame> refKey = Key.make("refFrame");
    
    private Frame refFrame;
    
    @Override
    protected void before() throws Throwable {
      refFrame = new TestFrameBuilder()
              .withName(refKey.toString())
              .withColNames("one", "two")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1, 2, 3))
              .withDataForCol(1, ard(3, 2, 1))
              .build();
    }

    @Override
    protected void after() {
      assertNotNull(DKV.get(refKey));
      assertFrameEquals(DKV.getGet(refKey), refFrame, 0); // only comparing frame content as in multinode, can't guarantee that these will be the same objects 
      for (Key<Vec> k : refFrame.keys()) assertNotNull(DKV.get(k));
    }
    
    Frame get() {
      return refFrame;
    }
  }

  @Rule
  public ScopeTracker scope = new ScopeTracker();
  
  @Rule 
  public ReferenceFrameProvider refFrame = new ReferenceFrameProvider();
  
  @Rule
  public DKVIsolation isolation = new DKVIsolation();
  
  private void addRndVec(Frame fr) {
    int lockedStatus = fr._lockers == null ? 0 : fr._lockers.length;
    if (lockedStatus == 0) fr.write_lock();
    fr.add("rndvec", fr.anyVec().makeRand(0));
    fr.update();
    if (lockedStatus == 0) fr.unlock();
  }

  @Test
  public void test_reassign_frame_null_key_with_fresh_key() {
    Frame ref = refFrame.get();
    Frame copy = new Frame(null, ref.names(), ref.vecs());
    Key<Frame> copyKey = copy.getKey();
    assertNull(copyKey);

    Key<Frame> reassigned = Key.make("reassigned");
    Frame cc = reassign(copy, reassigned);
    addRndVec(cc);
    Scope.track_generic(cc); // tracking the key only (instead of the frame) to better see what can potentially leak
    assertNotSame(copy, cc);
    assertNull(copy.getKey()); //copy was not assigned any key
    assertNotNull(DKV.get(reassigned));
    assertSame(cc, DKV.getGet(reassigned));
  }

  @Test
  public void test_reassign_frame_not_in_DKV_with_fresh_key() {
    Frame ref = refFrame.get();
    Frame copy = new Frame(ref);
    Key<Frame> copyKey = copy.getKey();
    assertNotNull(copyKey);
    assertNull(DKV.get(copyKey));

    Key<Frame> reassigned = Key.make("reassigned");
    Frame cc = reassign(copy, reassigned);
    addRndVec(cc);
    Scope.track_generic(cc); // tracking the key only (instead of the frame) to better see what can potentially leak
    assertNotSame(copy, cc);
    assertNull(DKV.get(copyKey));
    assertEquals(copyKey, copy.getKey()); //copy key was not modified
    assertNotNull(DKV.get(reassigned));
    assertSame(cc, DKV.getGet(reassigned));
  }

  @Test
  public void test_reassign_frame_in_DKV_with_fresh_key() {
    Frame ref = refFrame.get();
    Frame copy = new Frame(ref);
    Key<Frame> copyKey = copy.getKey();
    DKV.put(Scope.track_generic(copy)); // tracking the key only (instead of the frame) to better see what can potentially leak
    assertNotNull(copyKey);
    assertNotNull(DKV.get(copyKey));
    Key<Frame> reassigned = Key.make("reassigned");
    Frame cc = reassign(copy, reassigned);
    addRndVec(cc);
    Scope.track_generic(cc); // tracking the key only (instead of the frame) to better see what can potentially leak
    assertNotNull(DKV.get(copyKey));
    assertSame(copy, DKV.getGet(copyKey)); // copy still assigned to previous key
    assertNotNull(DKV.get(reassigned));
    assertSame(cc, DKV.getGet(reassigned));
  }

  @Test
  public void test_reassign_inplace_frame_null_key_with_fresh_key() {
    Frame ref = refFrame.get();
    Frame copy = new Frame(null, ref.names(), ref.vecs());
    Key<Frame> copyKey = copy.getKey();
    assertNull(copyKey);

    Key<Frame> reassigned = Key.make("reassigned");
    reassignInplace(copy, reassigned);
    addRndVec(copy);
    Scope.track_generic(copy); // tracking the key only (instead of the frame) to better see what can potentially leak
    assertNotNull(DKV.get(reassigned));
    assertSame(copy, DKV.getGet(reassigned));
  }
  
  @Test
  public void test_reassign_inplace_frame_not_in_DKV_with_fresh_key() {
    Frame ref = refFrame.get();
    Frame copy = new Frame(ref);
    Key<Frame> copyKey = copy.getKey();
    assertNotNull(copyKey);
    assertNull(DKV.get(copyKey));

    Key<Frame> reassigned = Key.make("reassigned");
    reassignInplace(copy, reassigned);
    addRndVec(copy);
    Scope.track_generic(copy); // tracking the key only (instead of the frame) to better see what can potentially leak
    assertNull(DKV.get(copyKey));
    assertNotNull(DKV.get(reassigned));
    assertSame(copy, DKV.getGet(reassigned));
  }
  
  @Test
  public void test_reassign_inplace_frame_in_DKV_with_fresh_key() {
    Frame ref = refFrame.get();
    Frame copy = new Frame(ref);
    Key<Frame> copyKey = copy.getKey();
    DKV.put(copy);
    assertNotNull(copyKey);
    assertNotNull(DKV.get(copyKey));
    Key<Frame> reassigned = Key.make("reassigned");
    reassignInplace(copy, reassigned);
    addRndVec(copy);
    Scope.track_generic(copy); // tracking the key only (instead of the frame) to better see what can potentially leak
    assertNull(DKV.get(copyKey));
    assertNotNull(DKV.get(reassigned));
    assertSame(copy, DKV.getGet(reassigned));
  }

}
