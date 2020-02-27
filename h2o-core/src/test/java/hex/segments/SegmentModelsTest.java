package hex.segments;

import hex.ModelBuilderTest;
import hex.segments.SegmentModels;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

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
  public void testToFrame() {
    try {
      Scope.enter();
      Frame segments = new TestFrameBuilder()
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, new String[]{"seg_A", "seg_B", "seg_C"})
              .build();

      Key<SegmentModels> dest = Key.make();
      SegmentModels sm = SegmentModels.make(dest, segments);

      // seg_C - not started
      ModelBuilderTest.DummyModelBuilder dmbC = new ModelBuilderTest.DummyModelBuilder(new ModelBuilderTest.DummyModelParameters());
      sm.addResult(2, dmbC, null);

      // seg_A - finished ok
      ModelBuilderTest.DummyModelParameters parmsA = new ModelBuilderTest.DummyModelParameters();
      parmsA._makeModel = true;
      parmsA._response_column = "col_0";
      parmsA._train = TestFrameCatalog.oneChunkFewRows()._key;
      ModelBuilderTest.DummyModelBuilder dmbA = new ModelBuilderTest.DummyModelBuilder(parmsA);
      ModelBuilderTest.DummyModel dm = dmbA.trainModel().get();
      Scope.track_generic(dm);
      sm.addResult(0, dmbA, null);

      Frame f = sm.toFrame();
      System.out.println(f.toTwoDimTable());

      Frame expected = new TestFrameBuilder()
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_STR, Vec.T_STR, Vec.T_STR)
              .withColNames("col_0", "Status", "Model", "Errors", "Warnings")
              .withDataForCol(0, new String[]{"seg_A", "seg_B", "seg_C"})
              .withDataForCol(1, new String[]{"SUCCEEDED", null, "PENDING"})
              .withDataForCol(2, new String[]{dmbA.dest().toString(), null, dmbC.dest().toString()})
              .withDataForCol(3, new String[]{null, null, null})
              .withDataForCol(4, new String[]{null, null, null})
              .build();
      expected.replace(1, expected.vec("Status").adaptTo(Job.JobStatus.domain()));
      
      TestUtil.assertFrameEquals(expected, f, 0.0, 0.0);

      sm.remove();
    } finally {
      Scope.exit();
    }
  }

}
