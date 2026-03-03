package water;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope.Safe;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.*;
import static water.TestUtil.ard;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ScopeTest {

  @Test
  public void testTrackGeneric() {
    final Key<DummyKeyed> k = Key.make();
    try {
      Scope.enter();
      DKV.put(Scope.track_generic(new DummyKeyed(k)));
      Scope.exit();
      assertNull("DKV value should be automatically removed", DKV.get(k));
    } finally {
      DKV.remove(k);
    }
  }
  
  @Test
  public void testTrackNulls() {
    Frame nfr1 = null, nfr2 = null;
    Vec nv1 = null;
    Key nk1 = null;
    Keyed nkd1 = null;
    try (Scope.Safe safe = Scope.safe(nfr1)) {
      Scope.protect(nfr2);
      try {
        Scope.enter();
        Scope.protect(nfr1, nfr2);
        Scope.protect(nfr1);
        Scope.track(nfr1, nfr2);
        Scope.track(nfr1);
        Scope.track(nv1);
        Scope.track_generic(nkd1);
      } finally {
        Scope.exit(nk1);
      }
    }
  }
  
  @Test
  public void testTrackFrame() {
    Scope.enter();
    Frame fr = Scope.track(dummyFrame());
    DKV.put(fr);
    Key frK = fr.getKey();
    Key[] vecKs = fr.keys();
    assertNotNull(DKV.get(frK));
    for (Key vecK : vecKs) {
      assertNotNull(DKV.get(vecK));
    }
    Scope.exit();
    assertNull(DKV.get(frK));
    for (Key vecK : vecKs) {
      assertNull(DKV.get(vecK));
    }
  }

  @Test
  public void testKeepKeys() {
    Frame ori = dummyFrame(); //outside scope as it's tracked by default in TestFrameBuilder
    try {
      Scope.enter();
      Frame fr = new Frame(Key.make(), ori.names(), ori.vecs());
      Key[] keep = fr.keys();
      Vec addedVec = fr.anyVec().makeZero();
      fr.add("added", addedVec);
      DKV.put(Scope.track(fr));
      assertNotNull(DKV.get(fr.getKey()));
      for (Key vecK : fr.keys()) {
        assertNotNull(DKV.get(vecK));
      }
      Scope.exit(keep);
      assertNull(DKV.get(fr.getKey()));
      assertNull(DKV.get(addedVec.getKey()));
      for (Key vecK : fr.keys()) {
        if (vecK.equals(addedVec.getKey())) continue;
        assertNotNull(DKV.get(vecK));
      }
    } finally {
      Keyed.remove(ori.getKey());
    }
  }

  @Test
  public void testProtectFrame() {
    Frame ori = dummyFrame(); //outside scope as it's tracked by default in TestFrameBuilder
    try {
      Scope.enter();
      Scope.protect(ori);
      Frame fr = new Frame(Key.make(), ori.names(), ori.vecs());
      Vec addedVec = fr.anyVec().makeZero();
      fr.add("added", addedVec);
      DKV.put(Scope.track(fr));
      assertNotNull(DKV.get(fr.getKey()));
      for (Key vecK : fr.keys()) {
        assertNotNull(DKV.get(vecK));
      }
      Scope.exit();
      assertNull(DKV.get(fr.getKey()));
      assertNull(DKV.get(addedVec.getKey()));
      for (Key vecK : fr.keys()) {
        if (vecK.equals(addedVec.getKey())) continue;
        assertNotNull(DKV.get(vecK));
      }
      assertNotNull(DKV.get(ori.getKey()));
    } finally {
      Keyed.remove(ori.getKey());
    }
  }

  @Test
  public void testProtectFrameNestedScope() {
    Frame ori = dummyFrame(); //outside scope as it's tracked by default in TestFrameBuilder
    try {
      Scope.enter(); //level1
      Scope.track(ori);

      Frame fr_l2 = null;
      Vec addedVec_l2 = null;
      try {
        Scope.enter(); //level2
        Scope.protect(ori);
        fr_l2 = new Frame(Key.make(), ori.names(), ori.vecs());
        addedVec_l2 = fr_l2.anyVec().makeZero();
        fr_l2.add("added_l2", addedVec_l2);
        DKV.put(Scope.track(fr_l2));
        assertNotNull(DKV.get(fr_l2.getKey()));
        for (Key vecK : fr_l2.keys()) {
          assertNotNull(DKV.get(vecK));
        }

        Frame fr_l3 = null;
        Vec addedVec_l3 = null;
        try {
          Scope.enter(); //level3
          fr_l3 = new Frame(Key.make(), fr_l2.names(), fr_l2.vecs());
          addedVec_l3 = fr_l3.anyVec().makeZero();
          fr_l3.add("added_l3", addedVec_l3);
          DKV.put(Scope.track(fr_l3));
          assertNotNull(DKV.get(fr_l3.getKey()));
          for (Key vK : fr_l3.keys()) {
            assertNotNull(DKV.get(vK));
          }
        } finally {
          Scope.exit(); //level3
        }
        assertNull(DKV.get(fr_l3.getKey()));
        assertNull(DKV.get(addedVec_l3.getKey()));
        assertNull(DKV.get(addedVec_l2.getKey())); // we didn't protect fr_l2, so its vecs are also not in the nested scope.
        for (Key vK : fr_l3.keys()) {
          if (vK.equals(addedVec_l2.getKey())) continue;
          if (vK.equals(addedVec_l3.getKey())) continue;
          assertNotNull(DKV.get(vK)); // other vecs are protected from the outermost scope
        }
        
      } finally {
        Scope.exit(); //level2
      }
      assertNull(DKV.get(fr_l2.getKey()));
      assertNotNull(DKV.get(ori.getKey())); // original frame still protected at level 2
      for (Key vecK : ori.keys()) {
        assertNotNull(DKV.get(vecK));
      }
    } finally {
      Scope.exit(); //level1
      assertNull(DKV.get(ori.getKey()));  // original frame not protected anymore at level 1
      for (Key vecK : ori.keys()) {
        assertNull(DKV.get(vecK));
      }
    }
  }


  @Test
  public void testProtectFrameNestedScopeAsResources() {
    Frame ori = dummyFrame(); //outside scope as it's tracked by default in TestFrameBuilder
    try (Safe l1 = Scope.safe()) {
      Scope.track(ori);

      Frame fr_l2 = null;
      Vec addedVec_l2 = null;
      try (Safe l2 = Scope.safe(ori)) {
        fr_l2 = new Frame(Key.make(), ori.names(), ori.vecs());
        addedVec_l2 = fr_l2.anyVec().makeZero();
        fr_l2.add("added_l2", addedVec_l2);
        DKV.put(Scope.track(fr_l2));
        assertNotNull(DKV.get(fr_l2.getKey()));
        for (Key vecK : fr_l2.keys()) {
          assertNotNull(DKV.get(vecK));
        }

        Frame fr_l3 = null;
        Vec addedVec_l3 = null;
        try (Safe l3 = Scope.safe()) {
          fr_l3 = new Frame(Key.make(), fr_l2.names(), fr_l2.vecs());
          addedVec_l3 = fr_l3.anyVec().makeZero();
          fr_l3.add("added_l3", addedVec_l3);
          DKV.put(Scope.track(fr_l3));
          assertNotNull(DKV.get(fr_l3.getKey()));
          for (Key vK : fr_l3.keys()) {
            assertNotNull(DKV.get(vK));
          }
        }
        assertNull(DKV.get(fr_l3.getKey()));
        assertNull(DKV.get(addedVec_l3.getKey()));
        assertNull(DKV.get(addedVec_l2.getKey())); // we didn't protect fr_l2, so its vecs are also not in the nested scope.
        for (Key vK : fr_l3.keys()) {
          if (vK.equals(addedVec_l2.getKey())) continue;
          if (vK.equals(addedVec_l3.getKey())) continue;
          assertNotNull(DKV.get(vK)); // other vecs are protected from the outermost scope
        }
      }
      assertNull(DKV.get(fr_l2.getKey()));
      assertNotNull(DKV.get(ori.getKey())); // original frame still protected at level 2
      for (Key vecK : ori.keys()) {
        assertNotNull(DKV.get(vecK));
      }
    } finally {
      assertNull(DKV.get(ori.getKey()));  // original frame not protected anymore at level 1
      for (Key vecK : ori.keys()) {
        assertNull(DKV.get(vecK));
      }
    }
  }
  

  private static final class DummyKeyed extends Keyed<DummyKeyed> {
    public DummyKeyed() {
      super();
    }
    private DummyKeyed(Key<DummyKeyed> key) {
      super(key);
    }
  }
  
  private Frame dummyFrame() {
    return new TestFrameBuilder()
            .withColNames("one", "two", "three")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ard(1, 1, 1))
            .withDataForCol(1, ard(2, 2, 2))
            .withDataForCol(2, ard(3, 3, 3))
            .build();
  }

}
