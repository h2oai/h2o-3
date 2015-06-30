package water.currents;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.Arrays;

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

  @Test public void testFun() {
    // Compute 3*3; single variable defined in function body
    String tree = "({var1 . (* var1 var1)} 3)";
    checkTree(tree);
    // Unknown var2
    tree = "({var1 . (* var1 var2)} 3)";
    checkTree(tree,true);
    // Compute 3* a.hex[0,0]
    tree = "({var1 . (* var1 (rows %a.hex [0]))} 3)";
    checkTree(tree);

    // Some more horrible functions.  Drop the passed function and return a 3
    tree = "({fun . 3} {y . (* y y)})";
    checkTree(tree);
    // Apply a 3 to the passed function
    tree = "({fun . (fun 3)} {y . (* y y)})";
    checkTree(tree);
    // Pass the squaring function thru the ID function
    tree = "({fun . fun} {y . (* y y)})";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply-3 function
    tree = "({fun . (fun (fun 3))} {y . (* y y)})";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply-x function
    tree = "({fun x . (fun (fun x))} {y . (* y y)} 3)";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply function
    tree = " ({fun . {x . (fun (fun x))}} {y . (* y y)})   ";
    checkTree(tree);
    // Pass the squaring function thru the twice-apply function, and apply it
    tree = "(({fun . {x . (fun (fun x))}} {y . (* y y)}) 3)";
    checkTree(tree);
  }

  @Test public void testCBind() {
    String tree = "(cbind 1 2)";
    checkTree(tree);

    tree = "(cbind 1 a.hex 2)";
    checkTree(tree);
    
    tree = "(cbind a.hex (cols a.hex 0) 2)";
    checkTree(tree);
  }

  @Test public void testRBind() {
    String tree = "(rbind 1 2)";
    checkTree(tree);

    //tree = "(rbind a.hex 1 2)";
    //checkTree(tree);
  }

  @Test public void testApply() {
    // Sum, reduction.  1 row result
    String tree = "(apply a.hex 2 {x . (sum x)})";
    checkTree(tree);

    // Return ID column results.  Shared data result.
    tree = "(apply a.hex 2 {x . x})";
    checkTree(tree);

    // Return column results, new data result.
    tree = "(apply a.hex 2 abs)";
    checkTree(tree);

    // Return two results
    tree = "(apply a.hex 2 {x . (rbind (sumNA x) (mean x 0 FALSE))})";
    checkTree(tree);
  }

  @Test public void testMath() {
    for( String s : new String[] {"abs", "cos", "sin", "acos", "ceiling", "floor", "cosh", "exp", "log", "round", "sqrt", "tan", "tanh"} )
      checkTree("("+s+" a.hex)");
  }


  private void checkTree(String tree) { checkTree(tree,false); }
  private void checkTree(String tree, boolean expectThrow) {
    //Frame r = frame(new double[][]{{-1},{1},{2},{3},{4},{5},{6},{254}});
    //Key ahex = Key.make("a.hex");
    //Frame fr = new Frame(ahex, null, new Vec[]{r.remove(0)});
    //r.delete();
    //DKV.put(ahex, fr);
    Frame fr = parse_test_file(Key.make("a.hex"),"smalldata/iris/iris_wheader.csv");
    fr.remove(4).remove();
    try {
      Val val = Exec.exec(tree);
      Assert.assertFalse(expectThrow);
      System.out.println(val.toString());
      if( val instanceof ValFrame ) {
        Frame fr2= ((ValFrame)val)._fr;
        System.out.println(fr2.vec(0));
        fr2.remove();
      }
    } catch( IllegalArgumentException iae ) {
      if( !expectThrow ) throw iae;
    } finally {
      fr.delete();
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


  @Test public void testQuantile() {
    Frame f = null;
    try {
      Frame fr = frame(ard(ard(1.223292e-02),
                           ard(1.635312e-25),
                           ard(1.601522e-11),
                           ard(8.452298e-10),
                           ard(2.643733e-10),
                           ard(2.671520e-06),
                           ard(1.165381e-06),
                           ard(7.193265e-10),
                           ard(3.383532e-04),
                           ard(2.561221e-05)));
      double[] probs = new double[]{0.001, 0.005, .01, .02, .05, .10, .50, .8883, .90, .99};
      String x = String.format("(quantile %%%s %s \"interpolate\")", fr._key, Arrays.toString(probs));
      Val val = Exec.exec(x);
      fr.delete();
      f = val.getFrame();
      Assert.assertEquals(2,f.numCols());
      // Expected values computed as golden values from R's quantile call
      double[] exp = ard(1.4413698000016206E-13, 7.206849000001562E-13, 1.4413698000001489E-12, 2.882739600000134E-12, 7.20684900000009E-12,
                         1.4413698000000017E-11, 5.831131148999999E-07, 3.3669567275300000E-04, 0.00152780988        , 0.011162408988      );
      for( int i=0; i<exp.length; i++ )
        Assert.assertTrue( "expected "+exp[i]+" got "+f.vec(1).at(i), water.util.MathUtils.compare(exp[i],f.vec(1).at(i),1e-6,1e-6) );
    } finally {
      if( f != null ) f.delete();
    }
  }
}
