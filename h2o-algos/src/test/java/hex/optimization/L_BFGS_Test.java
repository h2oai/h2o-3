package hex.optimization;

import hex.DataInfo;
import hex.glm.GLM;
import hex.glm.GLM.GLMGradientSolver;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.optimization.L_BFGS.GradientInfo;
import hex.optimization.L_BFGS.GradientSolver;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.util.ArrayUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
* Created by tomasnykodym on 9/16/14.
*/
public class L_BFGS_Test  extends TestUtil {
  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  // test on Rosenbrock's function (known optimum at (a,a^2), minimum = 0)
  @Test
  public void rosenbrock() {
    final double a = 1, b = 100;
    GradientSolver gs = new GradientSolver() {
      @Override
      public GradientInfo getGradient(double[] beta) {
        final double[] g = new double[2];
        final double x = beta[0];
        final double y = beta[1];
        final double xx = x * x;
        g[0] = -2 * a + 2 * x - 4 * b * (y * x - x * xx);
        g[1] = 2 * b * (y - xx);
        double objVal = (a - x) * (a - x) + b * (y - xx) * (y - xx);
        return new GradientInfo(objVal, g);
      }

      @Override
      public double[] getObjVals(double[] beta, double[] pk) {
        double [] res = new double[128];
        double step = 1;
        for(int i = 0; i < res.length; ++i) {
          double x = beta[0] + pk[0]*step;
          double y = beta[1] + pk[1]*step;
          double xx = x * x;
          res[i] = (a - x) * (a - x) + b * (y - xx) * (y - xx);
          step *= _step;
        }
        return res;
      }
    };
    int fails = 0;
    L_BFGS lbfgs = new L_BFGS().setGradEps(1e-12);
    L_BFGS.Result r = lbfgs.solve(gs, L_BFGS.startCoefs(2, 987654321));
    assertTrue("LBFGS failed to solve Rosenbrock function optimization",r.ginfo._objVal <  1e-4);
  }

  @Test
  public void logistic() {
    Key parsedKey = Key.make("prostate");
    DataInfo dinfo = null;
    try {
      GLMParameters glmp = new GLMParameters(Family.binomial, Family.binomial.defaultLink);
      glmp._alpha = new double[]{0};
      glmp._lambda = new double[]{1e-5};
      Frame source = parse_test_file(parsedKey, "smalldata/glm_test/prostate_cat_replaced.csv");
      source.add("CAPSULE", source.remove("CAPSULE"));
      source.remove("ID").remove();
      Frame valid = new Frame(source._names.clone(),source.vecs().clone());
      dinfo = new DataInfo(Key.make(),source, valid, 1, false, DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true);
      DKV.put(dinfo._key,dinfo);
      GLMGradientSolver solver = new GLMGradientSolver(glmp, dinfo, 1e-5,source.vec("CAPSULE").mean(), source.numRows());
      L_BFGS lbfgs = new L_BFGS().setGradEps(1e-8);

      double [] beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      beta[beta.length-1] = glmp.link(source.vec("CAPSULE").mean());
      L_BFGS.Result r = lbfgs.solve(solver, beta);
      assertEquals(378.34, r.ginfo._objVal * source.numRows(), 1e-1);
    } finally {
      if(dinfo != null)
        DKV.remove(dinfo._key);
      Value v = DKV.get(parsedKey);
      if (v != null) {
        v.<Frame>get().delete();
      }
    }
  }

  // Test LSM on arcene - wide dataset with ~10k columns
  // test warm start and max #iteratoions
  @Test
  public void testArcene() {
    Key parsedKey = Key.make("arcene_parsed");
    DataInfo dinfo = null;
    try {
      Frame source = parse_test_file(parsedKey, "smalldata/glm_test/arcene.csv");
      Frame valid = new Frame(source._names.clone(),source.vecs().clone());
      GLMParameters glmp = new GLMParameters(Family.gaussian);
      glmp._lambda = new double[]{1e-5};
      dinfo = new DataInfo(Key.make(),source, valid, 1, false, DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true);
      DKV.put(dinfo._key,dinfo);
      GradientSolver solver = new GLMGradientSolver(glmp, dinfo, 1e-5,source.lastVec().mean(), source.numRows());
      L_BFGS lbfgs = new L_BFGS().setMaxIter(20);
      double [] beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      beta[beta.length-1] = glmp.link(source.lastVec().mean());
      L_BFGS.Result r1 = lbfgs.solve(solver, beta.clone());
      lbfgs.setMaxIter(1000);
      L_BFGS.Result r2 = lbfgs.solve(solver, r1.coefs, r1.ginfo, new L_BFGS.ProgressMonitor());
      lbfgs = new L_BFGS();
      L_BFGS.Result r3 = lbfgs.solve(solver, beta.clone());
      assertEquals(r1.iter,20);
      assertEquals (r1.iter + r2.iter,r3.iter); // should be equal? got mismatch by 1
      assertEquals(r2.ginfo._objVal,r3.ginfo._objVal,1e-8);
      assertEquals( .5 * glmp._lambda[0] * ArrayUtils.l2norm(r3.coefs,true) + r3.ginfo._objVal, 1e-4, 5e-4);
      assertTrue("iter# expected < 100, got " + r3.iter, r3.iter < 100);
    } finally {
      if(dinfo != null)
        DKV.remove(dinfo._key);
      Value v = DKV.get(parsedKey);
      if (v != null) {
        v.<Frame>get().delete();
      }
    }
  }
}
