package hex.optimization;

import hex.optimization.OptimizationUtils.GradientInfo;
import hex.optimization.OptimizationUtils.GradientSolver;
import hex.optimization.OptimizationUtils.MoreThuente;
import org.junit.Test;
import water.TestUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by tomasnykodym on 9/29/15.
 */
public class LineSearchTest extends TestUtil {

  @Test public void testMoreThuenteMethod() {
    GradientSolver f = new GradientSolver(){
      @Override
      public GradientInfo getGradient(double[] beta) {
        GradientInfo ginfo = new GradientInfo(0,new double[1]);
        double x = beta[0];
        double b = 2;
        double xx = x*x;
        ginfo._gradient[0] = (xx - b)/((b+xx)*(b+xx));
        ginfo._objVal = -x/(xx+b);
        return ginfo;
      }
    };

    double stp = 1;
    double x = 100;
    MoreThuente ls = new OptimizationUtils.MoreThuente(1e-1,1e-1,1e-5).setInitialStep(stp);
    boolean succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{-1}, 1e-8, 1000,20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(18,ls.nfeval());
    assertEquals(-0.35355,ls.ginfo()._objVal,1e-5);
    assertEquals(98586,Math.round(1000*ls.step()),1e-5);

    x = 0;
    stp = 100;
    ls = new OptimizationUtils.MoreThuente(1e-1,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(5,ls.nfeval());
    assertEquals(-0.34992,ls.ginfo()._objVal,1e-5);
    assertEquals(1.6331,ls.step(),1e-5);

    x = 0;
    stp = 10;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(1, ls.nfeval());

    x = 0;
    stp = 1000;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertEquals(ls._returnStatus,1);
    assertTrue(succ);
    assertEquals(4,ls.nfeval());
    assertEquals(37,Math.round(ls.step()));

    x = 0;
    stp = 1e-3;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertEquals(ls._returnStatus,1);
    assertTrue(succ);
    assertEquals(6,ls.nfeval());
    assertEquals(14,Math.round(10*ls.step()));

    f = new GradientSolver(){
      @Override
      public GradientInfo getGradient(double[] beta) {
        GradientInfo ginfo = new GradientInfo(0,new double[1]);
        double x = beta[0];
        double b = 0.004;
        ginfo._objVal = Math.pow(x+b,5) - 2*Math.pow(x+b,4);
        ginfo._gradient[0] = Math.pow(b + x,3) * (5*(b + x)-8);
        return ginfo;
      }
    };

    ls = new OptimizationUtils.MoreThuente(1e-1,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(12,ls.nfeval());
    assertEquals(16,Math.round(10*ls.step()));

    stp = 0.1;
    ls = new OptimizationUtils.MoreThuente(1e-1,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(8,ls.nfeval());
    assertEquals(16,Math.round(10*ls.step()));

    stp = 10;
    ls = new OptimizationUtils.MoreThuente(1e-1,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(8,ls.nfeval());
    assertEquals(16,Math.round(10*ls.step()));

    stp = 1000;
    ls = new OptimizationUtils.MoreThuente(1e-1,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(11,ls.nfeval());
    assertEquals(16, Math.round(10 * ls.step()));


    f = new GradientSolver(){
      final double beta = 0.01;
      final double l = 39;

      double phi0(double x) {
        if(x <= 1 - beta) {
          return 1 - x;
        } else if(x >= 1 + beta) {
          return x - 1;
        } else {
          return .5*((x-1)*(x-1)/beta + beta);
        }
      }

      double phi0Prime(double x) {
        if(x <= 1 - beta) {
          return -1;
        } else if(x >= 1 + beta) {
          return 1;
        } else {
          return (x-1)/beta; // .5*((x-1)*(x-1)/beta + beta);
        }
      }

      @Override
      public GradientInfo getGradient(double[] ary) {
        GradientInfo ginfo = new GradientInfo(0,new double[1]);
        double x = ary[0];
        double a = 2*(1-beta)/(Math.PI*l);
        double b = .5*l*Math.PI;
        ginfo._objVal = phi0(x) + a*Math.sin(b*x);
        ginfo._gradient[0] = phi0Prime(x) + a*b*Math.cos(b*x);
        return ginfo;
      }
    };
    stp = 0.001;
    ls = new OptimizationUtils.MoreThuente(1e-1,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertEquals(ls._returnStatus,1);
    assertTrue(succ);
    assertEquals(12,ls.nfeval());
    assertEquals(10,Math.round(10*ls.step()));


    stp = 0.1;
    ls = new OptimizationUtils.MoreThuente(1e-1,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(12,ls.nfeval());
    assertEquals(10,Math.round(10*ls.step()));

    stp = 10;
    ls = new OptimizationUtils.MoreThuente(1e-1,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertEquals(ls._returnStatus,1);
    assertTrue(succ);
    assertEquals(10,ls.nfeval());
    assertEquals(10,Math.round(10*ls.step()));


    stp = 1000;
    ls = new OptimizationUtils.MoreThuente(1e-1,1e-1,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(13,ls.nfeval());
    assertEquals(10, Math.round(10 * ls.step()));

    f = new F(1e-3,1e-3);
    stp = 0.001;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(4,ls.nfeval());
    assertEquals(9, Math.round(100 * ls.step()));

    stp = 0.1;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(1,ls.nfeval());
    assertEquals(10, Math.round(100 * ls.step()));

    stp = 10;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(3,ls.nfeval());
    assertEquals(35, Math.round(100 * ls.step()));

    stp = 1000;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(4,ls.nfeval());
    assertEquals(83, Math.round(100 * ls.step()));

    f = new F(0.01,1e-3);
    stp = 0.001;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(6,ls.nfeval());
    assertEquals(75, Math.round(1000 * ls.step()));

    stp = 0.1;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(3,ls.nfeval());
    assertEquals(78, Math.round(1000 * ls.step()));

    stp = 10;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(7,ls.nfeval());
    assertEquals(73, Math.round(1000 * ls.step()));

    stp = 1000;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(8,ls.nfeval());
    assertEquals(76, Math.round(1000 * ls.step()));

    f = new F(1e-3,0.01);
    stp = 0.001;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(13,ls.nfeval());
    assertEquals(93, Math.round(100 * ls.step()));

    stp = 0.1;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(11,ls.nfeval());
    assertEquals(93, Math.round(100 * ls.step()));

    stp = 10;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(8,ls.nfeval());
    assertEquals(92, Math.round(100 * ls.step()));

    stp = 1000;
    ls = new OptimizationUtils.MoreThuente(1e-3,1e-3,1e-5).setInitialStep(stp);
    succ = ls.evaluate(f, f.getGradient(new double[]{x}), new double[]{x}, new double[]{1}, 1e-8, 1000, 20);
    assertTrue(succ);
    assertEquals(ls._returnStatus,1);
    assertEquals(11,ls.nfeval());
    assertEquals(92, Math.round(100 * ls.step()));
  }

  private static class F implements GradientSolver {
    final double a;
    final double b;

    public F(double a, double b) {
      this.a = a;
      this.b = b;
    }

    private double gamma(double x) {
      return Math.sqrt(x * x + 1)-x;
    }
    @Override
    public GradientInfo getGradient(double[] beta) {
      double x = beta[0];
      double ga = gamma(a);
      double gb = gamma(b);
      GradientInfo ginfo = new GradientInfo(0, new double[1]);
      ginfo._objVal = ga*Math.sqrt((1-x)*(1-x) + b*b) + gb*Math.sqrt(x * x + a * a);
      ginfo._gradient[0] = ga*(x-1)/Math.sqrt((1-x)*(1-x) + b*b) +gb*x/Math.sqrt(x * x + a * a);
      return ginfo;
    }
  }
}
