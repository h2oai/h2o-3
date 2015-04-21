package water.rapids;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;

public class RapidsTest extends TestUtil {
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
    String tree = "(n %a.hex #5)";
    checkTree(tree);
    tree = "(L %a.hex #5)";
    checkTree(tree);
    tree = "(G %a.hex #1.25132)";
    checkTree(tree);
    tree = "(g %a.hex #112.341e-5)";
    checkTree(tree);
    tree = "(l %a.hex #0.0123)";
    checkTree(tree);
    tree = "(N %a.hex #0)";
    checkTree(tree);
    tree = "(n %a.hex \"hello\")";
    checkTree(tree);
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
//    Checking `hex[,1]`
    String tree = "([ %a.hex \"null\" #1)";
    checkTree(tree);
    // Checking `hex[1,5]`
    tree = "([ %a.hex #0 #5)";
    checkTree(tree);
    // Checking `hex[c(1:5,7,9),6]`
    tree = "([ %a.hex (llist (: #0 #4) #6 #8) #5)";
    checkTree(tree);
    // Checking `hex[1,c(1:5,7,8)]`
    tree = "([ %a.hex #0 (llist (: #0 #4) #6 #7))";
    checkTree(tree);
  }

  @Test public void test7() {
    Frame fr = parse_test_file(Key.make("iris.hex"),"smalldata/iris/iris_wheader.csv");
    String x = "(del %iris.hex 'class')";
    checkTree(x);
    fr.delete();
  }

  private static void checkTree(String tree) {
    Frame r = frame(new double[]{-1,1,2,3,4,5,6,254});
    Key ahex = Key.make("a.hex");
    Frame fr = new Frame(ahex, null, r.vecs());
    DKV.put(ahex, fr);
    Env env = Exec.exec(tree);
    System.out.println(env.toString());
    if (env.isAry()) {
      Frame f2 = env.popAry();
      for (int i = 0; i < f2.numCols(); ++i) System.out.println(f2.vecs()[i].at(0));
      f2.delete();
    } else if (env.isNum()) {
      double d = env.popDbl();
      System.out.println(d);
    }
    fr.delete();
    r.delete();
  }

  @Test public void testMerge() {
    Frame l=null,r=null,f=null;
    try {
      l = frame("name" ,vec(ar("Cliff","Arno","Tomas","Spencer"),ari(0,1,2,3)));
      l.    add("age"  ,vec(ar(">dirt" ,"middle","middle","young'n"),ari(0,1,2,3)));
      l = new Frame(l);
      DKV.put(l);
      System.out.println(l);
      r = frame("name" ,vec(ar("Arno","Tomas","Michael","Cliff"),ari(0,1,2,3)));
      r.    add("skill",vec(ar("science","linearmath","sparkling","hacker"),ari(0,1,2,3)));
      r = new Frame(r);
      DKV.put(r);
      System.out.println(r);
      String x = String.format("(merge %%%s %%%s #1 #0 )",l._key,r._key);
      Env env = Exec.exec(x);
      System.out.println(env.toString());
      f = env.popAry();
      System.out.println(f);
    } finally {
      if( f != null ) f.delete();
      if( r != null ) r.delete();
      if( l != null ) l.delete();
    }
  }


  @Test public void testQuantile() {
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
    Frame pr = frame(ard(ard(0.001), ard(0.005), ard(.01), ard(.02), ard(.05), ard(.10), ard(.50), ard(.8883), ard(.99), ard(.90)));
    String x = String.format("(quantile %%%s %%%s \"interpolate\")",fr._key,pr._key);
    Env env = Exec.exec(x);
    System.out.println(env.toString());
    Frame f = env.popAry();
    System.out.println(f);
    f.delete();
    pr.delete();
    fr.delete();
  }
}
