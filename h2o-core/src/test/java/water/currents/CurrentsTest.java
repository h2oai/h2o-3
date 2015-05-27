package water.currents;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;

public class CurrentsTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void test1() {
    // Checking `hex + 5`
    String tree = "(+ %a.hex #5)";
    checkTree(tree);
  }

  @Test public void test2() {
    // Checking `hex + 5 + 10`
    String tree = "(+ %a.hex (+ #5 #10))";
    checkTree(tree);
  }

  @Test public void test3() {
    // Checking `hex + 5 - 1 * hex + 15 * (23 / hex)`
    String tree = "(+ (- (+ %a.hex #5) (* #1 %a.hex)) (* #15 (/ #23 %a.hex)))";
    checkTree(tree);
  }

  @Test public void test4() {
    //Checking `hex == 5`, <=, >=, <, >, !=
    String tree = "(== %a.hex #5)";
    checkTree(tree);
    tree = "(<= %a.hex #5)";
    checkTree(tree);
    tree = "(>= %a.hex #1.25132)";
    checkTree(tree);
    tree = "(< %a.hex #112.341e-5)";
    checkTree(tree);
    tree = "(> %a.hex #0.0123)";
    checkTree(tree);
    tree = "(!= %a.hex #0)";
    checkTree(tree);
  }
  @Test public void test4_throws() {
    String tree = "(== %a.hex \"hello\")";
    checkTree(tree,true);
  }
  
  @Test public void test5() {
    // Checking `hex && hex`, ||, &, |
    String tree = "(&& %a.hex %a.hex)";
    checkTree(tree);
    tree = "(|| %a.hex %a.hex)";
    checkTree(tree);
    tree = "(& %a.hex %a.hex)";
    checkTree(tree);
    tree = "(| %a.hex %a.hex)";
    checkTree(tree);
  }

  @Test public void test6() {
    // Checking `hex[,1]`
    String tree = "(cols %a.hex [0])";
    checkTree(tree);
    // Checking `hex[1,5]`
    tree = "(rows (cols %a.hex [0]) [5])";
    checkTree(tree);
    // Checking `hex[c(1:5,7,9),6]`
    tree = "(cols (rows %a.hex [0:4 6 7]) [0])";
    checkTree(tree);
    // Checking `hex[1,c(1:5,7,8)]`
    //tree = "([ %a.hex #0 (llist (: #0 #4) #6 #7))";
    //checkTree(tree);
  }

  private static void checkTree(String tree) { checkTree(tree,false); }
  private static void checkTree(String tree, boolean expectThrow) {
    Frame r = frame(new double[][]{{-1},{1},{2},{3},{4},{5},{6},{254}});
    Key ahex = Key.make("a.hex");
    Frame fr = new Frame(ahex, null, r.vecs()); 
    DKV.put(ahex, fr);
    try {
      Val val = Exec.exec(tree);
      Assert.assertFalse(expectThrow);
      System.out.println(val.toString());
      if( val instanceof ValFrame ) {
        Frame fr2= ((ValFrame)val)._fr;
        System.out.println(fr2.vec(0));
        fr2.remove();
      } else if( val instanceof ValNum ) {
        System.out.println(((ValNum)val)._d);
      }
    } catch( IllegalArgumentException iae ) {
      if( !expectThrow ) throw iae;
    } finally {
      fr.delete();
      r.delete();
    }
  }

//  @Test public void testMerge() {
//    Frame l=null,r=null,f=null;
//    try {
//      l = frame("name" ,vec(ar("Cliff","Arno","Tomas","Spencer"),ari(0,1,2,3)));
//      l.    add("age"  ,vec(ar(">dirt" ,"middle","middle","young'n"),ari(0,1,2,3)));
//      l = new Frame(l);
//      DKV.put(l);
//      System.out.println(l);
//      r = frame("name" ,vec(ar("Arno","Tomas","Michael","Cliff"),ari(0,1,2,3)));
//      r.    add("skill",vec(ar("science","linearmath","sparkling","hacker"),ari(0,1,2,3)));
//      r = new Frame(r);
//      DKV.put(r);
//      System.out.println(r);
//      String x = String.format("(merge %%%s %%%s #1 #0 )",l._key,r._key);
//      Env env = Exec.exec(x);
//      System.out.println(env.toString());
//      f = env.popAry();
//      System.out.println(f);
//    } finally {
//      if( f != null ) f.delete();
//      if( r != null ) r.delete();
//      if( l != null ) l.delete();
//    }
//  }
//
//
//  @Test public void testQuantile() {
//    Frame f = null;
//    try {
//      Frame fr = frame(ard(ard(1.223292e-02),
//                           ard(1.635312e-25),
//                           ard(1.601522e-11),
//                           ard(8.452298e-10),
//                           ard(2.643733e-10),
//                           ard(2.671520e-06),
//                           ard(1.165381e-06),
//                           ard(7.193265e-10),
//                           ard(3.383532e-04),
//                           ard(2.561221e-05)));
//      Frame pr = frame(ard(ard(0.001), ard(0.005), ard(.01), ard(.02), ard(.05), ard(.10), ard(.50), ard(.8883), ard(.90), ard(.99)));
//      String x = String.format("(quantile %%%s %%%s \"interpolate\")", fr._key, pr._key);
//      Env env = Exec.exec(x);
//      fr.delete();
//      pr.delete();
//      f = env.popAry();
//      Assert.assertEquals(2,f.numCols());
//      // Expected values computed as golden values from R's quantile call
//      double[] exp = ard(1.4413698000016206E-13, 7.206849000001562E-13, 1.4413698000001489E-12, 2.882739600000134E-12, 7.20684900000009E-12,
//                         1.4413698000000017E-11, 5.831131148999999E-07, 3.3669567275300000E-04, 0.00152780988        , 0.011162408988      );
//      for( int i=0; i<exp.length; i++ )
//        Assert.assertTrue( "expected "+exp[i]+" got "+f.vec(1).at(i), water.util.MathUtils.compare(exp[i],f.vec(1).at(i),1e-6,1e-6) );
//    } finally {
//      if( f != null ) f.delete();
//    }
//  }
}
