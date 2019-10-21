package hex.coxph;

import hex.DataInfo;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.MemoryManager;
import water.Scope;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static org.junit.Assert.*;

public class EfronDJKTermTaskTest extends TestUtil {

  @BeforeClass
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testDJKTermMatrix() throws Exception {
    try {
      Scope.enter();
      final Frame fr = Scope.track(new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC", "Event", "Stop")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(3.2, 1, 2, 3, 4, 5.6, 7))
              .withDataForCol(1, ar("A", "B", "C", "E", "F", "I", "J"))
              .withDataForCol(2, ar("A", "B,", "A", "C", "A", "B", "A"))
              .withDataForCol(3, ard(1, 0, 2, 3, 4, 3, 1))
              .withDataForCol(4, ard(1, 1, 1, 1, 1, 1, 1))
              .withChunkLayout(7)
              .build());

      final DataInfo dinfo = makeDataInfo(fr, 2);
      final DataInfo dinfoNoResp = makeDataInfo(fr.subframe(new String[]{"ColA", "ColB", "ColC"}), 0);

      CoxPH.CoxPHTask coxMR = new CoxPH.CoxPHTask(dinfo, new double[dinfo.fullN()], new double[1], 0, 0, false, null, false,
              CoxPHModel.CoxPHParameters.CoxPHTies.efron);

      EfronDJKSetupFun efronDJKSetupFun = new EfronDJKSetupFun();
      efronDJKSetupFun._cumsumRiskTerm = new double[]{0,1,2,3,4};
      efronDJKSetupFun._riskTermT2 = new double[]{1,2,3,4,5};
      EfronDJKTermTask djkTermTask = new EfronDJKTermTask(dinfo, coxMR, efronDJKSetupFun);
      double[][] djkTerm = MemoryManager.malloc8d(dinfo.fullN(), dinfo.fullN());
      djkTermTask._djkTerm = djkTerm;
      djkTermTask.setupLocal();

      Chunk[] cs = chunks(dinfo, 0);
      Chunk[] csNoResp = chunks(dinfoNoResp, 0);

      // Compare vector*vectorT products
      double[][] expected = MemoryManager.malloc8d(dinfo.fullN(), dinfo.fullN());
      for (int i = 0; i < cs[0]._len; i++) {
        DataInfo.Row rowNoResp = dinfoNoResp.extractDenseRow(csNoResp, i, dinfoNoResp.newDenseRow());
        vvT(rowNoResp, expected);

        DataInfo.Row row = dinfo.extractDenseRow(cs, i, dinfo.newDenseRow());
        djkTermTask.processRow(row);
      }
      djkTermTask.postGlobal();

      for (int j = 0; j < expected.length; j++) {
        assertArrayEquals(expected[j], djkTerm[j], 1e-8);
      }
    } finally {
      Scope.exit();
    }
  }

  private static void vvT(DataInfo.Row row, double[][] prod) {
    double[] v = row.expandCats();
    for (int j = 0; j < v.length; j++)
      for (int k = 0; k < v.length; k++)
        prod[j][k] += v[j] * v[k];
  }

  private static Chunk[] chunks(DataInfo dinfo, int chunkId) {
    Chunk[] cs = new Chunk[dinfo._adaptedFrame.numCols()];
    for (int i = 0; i < cs.length; i++)
      cs[i] = dinfo._adaptedFrame.vec(i).chunkForChunkIdx(chunkId);
    return cs;
  }

  private static DataInfo makeDataInfo(Frame fr, int nResponses) {
    final DataInfo dinfo = new DataInfo(fr, null, nResponses, false, DataInfo.TransformType.DEMEAN, DataInfo.TransformType.NONE, true, false, false, false, false, false, null)
            .disableIntercept();
    Scope.track_generic(dinfo);
    DKV.put(dinfo);
    return dinfo;
  }

}
