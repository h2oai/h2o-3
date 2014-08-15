package water.cascade;

import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.parser.ParseDataset2;
import water.parser.ParserTest;


public class CascadeTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

//  @Test public void test1() {
//    // Checking `hex + 5`
//    String tree = "(+ (KEY a.hex) (# 5))";
//    checkTree(tree);
//  }
//
//  @Test public void test2() {
//    // Checking `hex + 5 + 10`
//    String tree = "(+ (KEY a.hex) (+ (# 5) (# 10)))";
//    checkTree(tree);
//  }
//
//  @Test public void test3() {
//    // Checking `hex + 5 - 1 * hex + 15 * (23 / hex)`
//    String tree = "(+ (- (+ (KEY a.hex) (# 5) ) (* (# 1) (KEY a.hex))) (* (# 15) (/ (# 23) (KEY a.hex))))";
//    checkTree(tree);
//  }
//
//  @Test public void test4() {
//    // Checking `hex == 5`, <=, >=, <, >, !=
//    String tree = "(== (KEY a.hex) (# 5))";
//    checkTree(tree);
//    tree = "(<= (KEY a.hex) (# 5))";
//    checkTree(tree);
//    tree = "(>= (KEY a.hex) (# 5))";
//    checkTree(tree);
//    tree = "(> (KEY a.hex) (# 5))";
//    checkTree(tree);
//    tree = "(< (KEY a.hex) (# 5))";
//    checkTree(tree);
//    tree = "(!= (KEY a.hex) (# 5))";
//    checkTree(tree);
//  }

  @Test public void test5() {
    // Checking `hex && hex`, ||, &, |
    String tree = "(&& (KEY a.hex) (KEY a.hex))";
    checkTree(tree);
    tree = "(|| (KEY a.hex) (KEY a.hex))";
    checkTree(tree);
    tree = "(& (KEY a.hex) (KEY a.hex))";
    checkTree(tree);
    tree = "(| (KEY a.hex) (KEY a.hex))";
    checkTree(tree);
  }

  private static void checkTree(String tree) {
    String [] data = new String[] {
            "Col1\n"  +
            "-1\n",
            "1\n" ,
            "2\n" ,
            "3\n" ,
            "4\n" ,
            "5\n" ,
            "6\n" ,
            "254\n" ,
    };

    Key rkey = ParserTest.makeByteVec(data);
    Frame fr = ParseDataset2.parse(Key.make("a.hex"), rkey);
    Env env = Exec.exec(tree);
//    System.out.println(env.toString()); //TODO
    Object result = env.pop();
    if (result instanceof ASTFrame) {
      Frame f2 = ((ASTFrame)result)._fr;
      for (int i = 0; i < f2.anyVec().length(); ++i) System.out.println(f2.anyVec().at(i));
      f2.delete();
    }
    if (result instanceof ASTNum) {
      double d = ((ASTNum)result)._d;
      System.out.println(d);
    }
    fr.delete();
    DKV.remove(Key.make("a.hex"));
  }
}