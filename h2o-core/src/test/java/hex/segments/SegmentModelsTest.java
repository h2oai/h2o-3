package hex.segments;

import water.test.dummy.DummyModel;
import water.test.dummy.DummyModelBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.dummy.DummyModelParameters;

import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SegmentModelsTest {

  @Test
  public void testMake() {
    try {
      Scope.enter();
      Frame segments = new TestFrameBuilder()
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, new String[]{"seg_A", "seg_B", "seg_C"})
              .build();

      Key<SegmentModels> dest = Key.make();
      SegmentModels sm = SegmentModels.make(dest, segments);
      // check SM were installed in DKV
      assertNotNull(DKV.getGet(sm._key));
      // check that we have a defensive copy of Segments Frame
      segments.delete();
      Frame f = Scope.track(sm.toFrame());
      assertNotNull(f.vec(0).chunkForChunkIdx(0));
      // delete and expect no leak
      sm.remove();
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testConsolidatePendingToFailed() {
    DummyModelBuilder mb = new DummyModelBuilder(new DummyModelParameters());
    mb.error("field", "field validation error");
    assertEquals(2, mb.error_count());

    SegmentModels.SegmentModelResult result = new SegmentModels.SegmentModelResult(Key.make(), mb, null);
    assertEquals(Job.JobStatus.FAILED, result._status);
  }

  @Test
  public void testToFrame() {
    try {
      Scope.enter();
      Frame segments = new TestFrameBuilder()
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, new String[]{"seg_A", "seg_B", "seg_C", "seg_D"})
              .build();

      Key<SegmentModels> dest = Key.make();
      SegmentModels sm = SegmentModels.make(dest, segments);

      // seg_C - not started
      DummyModelBuilder dmbC = new DummyModelBuilder(new DummyModelParameters());
      sm.addResult(2, dmbC, null);

      // seg_D - failed in validation
      DummyModelBuilder dmbD = new DummyModelBuilder(new DummyModelParameters());
      dmbD.error("field", "field validation error");
      assertEquals(2, dmbD.error_count());
      sm.addResult(3, dmbD, null);

      // seg_A - finished ok
      DummyModelParameters parmsA = new DummyModelParameters();
      parmsA._makeModel = true;
      parmsA._response_column = "col_0";
      parmsA._train = TestFrameCatalog.oneChunkFewRows()._key;
      DummyModelBuilder dmbA = new DummyModelBuilder(parmsA);
      DummyModel dm = dmbA.trainModel().get();
      Scope.track_generic(dm);
      sm.addResult(0, dmbA, null);

      Frame f = sm.toFrame();
      System.out.println(f.toTwoDimTable());

      Frame expected = new TestFrameBuilder()
              .withVecTypes(Vec.T_CAT, Vec.T_STR, Vec.T_CAT, Vec.T_STR, Vec.T_STR)
              .withColNames("col_0", "model", "status", "errors", "warnings")
              .withDataForCol(0, new String[]{"seg_A", "seg_B", "seg_C", "seg_D"})
              .withDataForCol(1, new String[]{dmbA.dest().toString(), null, dmbC.dest().toString(), dmbD.dest().toString()})
              .withDataForCol(2, new String[]{"SUCCEEDED", null, "PENDING", "FAILED"})
              .withDataForCol(3, new String[]{null, null, null, "ERRR on field: field: field validation error\n"})
              .withDataForCol(4, new String[]{null, null, null, null})
              .build();
      System.out.println(expected.toTwoDimTable());
      expected.replace(2, expected.vec("status").adaptTo(Job.JobStatus.domain()));
      
      TestUtil.assertFrameEquals(expected, f, 0.0, 0.0);

      sm.remove();
    } finally {
      Scope.exit();
    }
  }

}
