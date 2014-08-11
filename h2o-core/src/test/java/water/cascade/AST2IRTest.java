package water.cascade;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

public class AST2IRTest extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test public void test1() {
    // Checking `hex + 5`
    String tree = "(+ (KEY a.hex) (# 5))";
    checkTree(tree);
  }

  @Test public void test2() {
    // Checking `hex + 5 + 10`
    String tree = "(+ (KEY a.hex) (+ (# 5) (# 10)))";
    checkTree(tree);
  }

  @Test public void test3() {
    // Checking `hex + 5 - 1 * hex + 15 * (23 / hex)`
    String tree = "(+ (- (+ (KEY a.hex) (# 5) (* (# 1) (KEY a.hex) (* (# 15) (/ (# 23) (KEY a.hex)))))))";
    checkTree(tree);
  }

  private static void checkTree(String tree) {
    Exec e = new Exec(tree);
    AST ast = e.parse();
    System.out.println(ast.toString());
  }
}
