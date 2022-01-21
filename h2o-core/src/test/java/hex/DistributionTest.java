package hex;

import hex.genmodel.utils.DistributionFamily;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.test.dummy.DummyModelParameters;

public class DistributionTest extends TestUtil {
    
    @BeforeClass
    public static void stall() { stall_till_cloudsize(1); }
    
    @Test
    public void testHuber(){
        Model.Parameters param = new DummyModelParameters();
        param._distribution = DistributionFamily.huber;
        HuberDistribution dist = new HuberDistribution(param);
        double delta = 0.5;
        dist.setHuberDelta(delta);
        double[] w = new double[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0};
        double[] y = new double[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0};
        double[] f = new double[]{0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.9, 1, 0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.9, 1, 0.5, 0.5};
        // generated using https://docs.scipy.org/doc/scipy/reference/generated/scipy.special.huber.html
        double[] scipyHuber = new double[]{0.375, 0.325, 0.275, 0.225, 0.175, 0.125, 0.08 , 0.045, 0.005, 0, 0 , 0.005, 0.02 , 0.045, 0.08 , 0.125, 0.175, 0.225, 0.325, 0.375, 0, 0};
        double[] h2oHuber = new double[w.length];
        double[] huberByDef = new double[w.length];
        double[] oldHuber = new double[w.length];
        double tol = 1e-6;
        for (int i = 0; i < w.length; i++) {
            h2oHuber[i] = dist.deviance(w[i], y[i], f[i]) * 0.5; // the value differs from definition due to not divide by 2 
            huberByDef[i] = huberByDef(w[i], f[i]-y[i], delta);
            oldHuber[i] = oldDeviance(w[i], f[i], y[i], delta);
        }
        // test calculated values from other source
        Assert.assertArrayEquals(scipyHuber, h2oHuber, tol);
        Assert.assertArrayEquals(huberByDef, h2oHuber, tol);
        
        // test old implementation is not correct
        Assert.assertNotEquals("The values should not be same.", huberByDef[0], oldHuber[0]);
        Assert.assertNotEquals("The values should not be same.", huberByDef[1], oldHuber[1]);
        Assert.assertNotEquals("The values should not be same.", huberByDef[0], oldHuber[0]/2);
        Assert.assertNotEquals("The values should not be same.", huberByDef[1], oldHuber[1]/2);
        
        // test symmetry with weights
        Assert.assertEquals("The values should be same.", dist.deviance(0, 1, 0.5), dist.deviance(0, 0, 0.5), 0);
        Assert.assertEquals("The values should be same.", dist.deviance(0.3, 1, 0.5), dist.deviance(0.3, 0, 0.5), 0);
        Assert.assertEquals("The values should be same.", dist.deviance(0.7, 1, 0.5), dist.deviance(0.7, 0, 0.5), 0);
        Assert.assertEquals("The values should be same.", dist.deviance(1, 1, 0.5), dist.deviance(1, 0, 0.5), 0);
        
        Assert.assertEquals("The values should be same.", dist.deviance(0, 1, 1), dist.deviance(0, 0, 0), 0);
        Assert.assertEquals("The values should be same.", dist.deviance(0.3, 1, 1), dist.deviance(0.3, 0, 0), 0);
        Assert.assertEquals("The values should be same.", dist.deviance(0.7, 1, 1), dist.deviance(0.7, 0, 0), 0);
        Assert.assertEquals("The values should be same.", dist.deviance(1, 1, 1), dist.deviance(1, 0, 0), 0);

        Assert.assertEquals("The values should be same.", dist.deviance(0, 1, -0.3), dist.deviance(0, 0, 1.3), 0);
        Assert.assertEquals("The values should be same.", dist.deviance(0.3, 1, -0.3), dist.deviance(0.3, 0, 1.3), 0);
        Assert.assertEquals("The values should be same.", dist.deviance(0.7, 1, -0.3), dist.deviance(0.7, 0, 1.3), 0);
        Assert.assertEquals("The values should be same.", dist.deviance(1, 1, -0.3), dist.deviance(1, 0, 1.3), 0);

        // test convexity with weights
        Assert.assertFalse(oldDeviance(1, 1, 2, 0.9) > oldDeviance(1, 1, 1.5, 0.9));
        Assert.assertFalse(oldDeviance(0.8, 1, 2, 0.9) > oldDeviance(0.8, 1, 1.5, 0.9));
        dist.setHuberDelta(0.9);
        Assert.assertTrue(dist.deviance(1, 1, 2) > dist.deviance(1, 1, 1.5));
        Assert.assertTrue(dist.deviance(0.8, 1, 2) > dist.deviance(0.8, 1, 1.5));
    }

    /**
     * Source: https://en.wikipedia.org/wiki/Huber_loss
     * Use * 0.5 to avoid dividing (/2)
     * @param w weight
     * @param r residual
     * @param delta huber delta
     * @return huber loss value
     */
    private double huberByDef(double w, double r,  double delta){
        if (Math.abs(r) <= delta) {
            return 0.5 * w * (r * r); 
        } else {
            return w * delta * (Math.abs(r) - delta * 0.5);
        }
    }

    /**
     * Source: old version of huber deviation which was not correct.
     * @param w weight
     * @param y actual value
     * @param f prediction         
     * @param delta huber delta
     * @return huber loss value
     */
    private double oldDeviance(double w, double y, double f, double delta) {
        if (Math.abs(y - f) <= delta) {
            return w * (y - f) * (y - f); // same as wMSE
        } else {
            return 2 * w * (Math.abs(y - f) - delta) * delta; // note quite the same as wMAE
        }
    }
    
}
