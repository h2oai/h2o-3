package water.cascade;

import water.H2O;
import water.Iced;
import java.util.HashMap;

/**
 *   Each node in the syntax tree knows how to parse a piece of text from the passed tree.
 */
abstract public class AST extends Iced {

  //FIXME: Move this somewhere else
  final static HashMap<String, AST> SYMBOLS = new HashMap<>(); // somewhere do the new...
  static {
    SYMBOLS.put("+", new ASTPlus());
    SYMBOLS.put("/", new ASTDiv());
    SYMBOLS.put("*", new ASTMul());
    SYMBOLS.put("-", new ASTSub());
    SYMBOLS.put("ID", new ASTId(""));
    SYMBOLS.put("KEY", new ASTKey(""));
    SYMBOLS.put("#", new ASTNum(0));
  }

  //execution
  AST[] _asts;
  AST parse_impl(Exec e) { throw H2O.fail("No parse_impl for class "+this.getClass()); }
  abstract void exec(Env e);
}

class ASTId extends AST {
  final String _id;
  ASTId(String id) { _id = id; }
  ASTId parse_impl(Exec E) { return new ASTId(E.parseID()); }
  @Override public String toString() { return _id; }
  @Override void exec(Env e) {

  }
}

class ASTKey extends AST {
  final String _key;
  ASTKey(String key) { _key = key; }
  ASTKey parse_impl(Exec E) { return new ASTKey(E.parseID()); }
  @Override public String toString() { return _key; }
  @Override void exec(Env e) {

  }
}

class ASTNum extends AST {
  final double _d;
  ASTNum(double d) { _d = d; }
  ASTNum parse_impl(Exec E) { return new ASTNum(Double.valueOf(E.parseID())); }
  @Override public String toString() { return Double.toString(_d); }
  @Override void exec(Env e) {

  }
}

class ASTString extends AST {
  final String _s;
  ASTString(String s) { _s = s; }
  ASTString parse_impl(Exec E) { return new ASTString(E.parseID()); }
  @Override public String toString() { return _s; }
  @Override void exec(Env e) {

  }
}


class ASTParseTest {
  private static void test1() {
    // Checking `hex + 5`
    String tree = "(+ (KEY a.hex) (# 5))";
    checkTree(tree);
  }

  private static void test2() {
    // Checking `hex + 5 + 10`
    String tree = "(+ (KEY a.hex) (+ (# 5) (# 10))";
    checkTree(tree);
  }

  private static void test3() {
    // Checking `hex + 5 - 1 * hex + 15 * (23 / hex)`
    String tree = "(+ (- (+ (KEY a.hex) (# 5) (* (# 1) (KEY a.hex) (* (# 15) (/ (# 23) (KEY a.hex)";
    checkTree(tree);
  }

  public static void main(String[] args) {
    test1();
    test2();
    test3();
  }

  private static void checkTree(String tree) {
    Exec e = new Exec(tree);
    AST ast = e.parse();
    System.out.println(ast.toString());
  }
}