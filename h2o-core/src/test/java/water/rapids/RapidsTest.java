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
    String tree = "(+ $a.hex #5)";
    checkTree(tree);
  }

  @Test public void test2() {
    // Checking `hex + 5 + 10`
    String tree = "(+ $a.hex (+ #5 #10))";
    checkTree(tree);
  }

  @Test public void test3() {
    // Checking `hex + 5 - 1 * hex + 15 * (23 / hex)`
    String tree = "(+ (- (+ $a.hex #5) (* #1 $a.hex)) (* #15 (/ #23 $a.hex)))";
    checkTree(tree);
  }

  @Test public void test4() {
    //Checking `hex == 5`, <=, >=, <, >, !=
    String tree = "(n $a.hex #5)";
    checkTree(tree);
    tree = "(L $a.hex #5)";
    checkTree(tree);
    tree = "(G $a.hex #1.25132)";
    checkTree(tree);
    tree = "(g $a.hex #112.341e-5)";
    checkTree(tree);
    tree = "(l $a.hex #0.0123)";
    checkTree(tree);
    tree = "(N $a.hex #0)";
    checkTree(tree);
    tree = "(n $a.hex \"hello\")";
    checkTree(tree);
  }

  @Test public void test5() {
    // Checking `hex && hex`, ||, &, |
    String tree = "(&& $a.hex $a.hex)";
    checkTree(tree);
    tree = "(|| $a.hex $a.hex)";
    checkTree(tree);
    tree = "(& $a.hex $a.hex)";
    checkTree(tree);
    tree = "(| $a.hex $a.hex)";
    checkTree(tree);
  }

  @Test public void test6() {
//    Checking `hex[,1]`
    String tree = "([ $a.hex \"null\" #1)";
    checkTree(tree);
    // Checking `hex[1,5]`
    tree = "([ $a.hex #0 #5)";
    checkTree(tree);
    // Checking `hex[c(1:5,7,9),6]`
    tree = "([ $a.hex {(: #0 #4);6;8} #5)";
    checkTree(tree);
    // Checking `hex[1,c(1:5,7,8)]`
    tree = "([ $a.hex #0 {(: #0 #4);6;7})";
    checkTree(tree);
  }

  @Test public void test7() {
    Frame fr = parse_test_file(Key.make("iris.hex"),"smalldata/iris/iris_wheader.csv");
    String x = "(del $iris.hex 'class')";
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
      Frame f2 = env.pop0Ary();
      for (int i = 0; i < f2.numCols(); ++i) System.out.println(f2.vecs()[i].at(0));
      f2.delete();
    } else if (env.isNum()) {
      double d = env.popDbl();
      System.out.println(d);
    }
    fr.delete();
    r.delete();
  }
}
