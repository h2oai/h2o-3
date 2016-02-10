package ai.h2o.automl.autocollect;

import autocollect.MetaConfig;
import org.junit.Test;

public class MetaConfigTest {

  @Test public void testPredictCols() {
    MetaConfig mc = new MetaConfig();
    mc.readX("1,2,3,4,5");
    printA(mc.x());
    mc.setncol(99);
    mc.readX("1:3, 5:10, 58,59, 88:99");
    printA(mc.x());
    mc.setncol(50);
    mc.readX("1:9,22:ncol");
    printA(mc.x());
    mc.readX("22:30,32:ncols");
    printA(mc.x());
  }


  private void printA(int[] a) {
    for(int i=0;i<a.length;++i)
      System.out.print(a[i] + (i==a.length-1 ? "":","));
    System.out.println();
  }
}
