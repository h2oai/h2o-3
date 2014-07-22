package water.cascade;


import water.H2O;
import water.Iced;

import java.util.HashMap;

public class AST extends Iced {

  final static HashMap<String, AST> SYMBOLS = new HashMap<>(); // somewhere do the new...
  static {
    SYMBOLS.put("+", new ASTPlus());
    SYMBOLS.put("ID", new ASTId(""));
    SYMBOLS.put("#", new ASTNum(0));
  }

  //execution
  AST[] _asts;
  AST parse_impl(Exec E) { throw H2O.fail("No parse_impl for class "+this.getClass()); }
}

class ASTId extends AST {
  final String _id;

  ASTId(String id) { _id = id; }
  ASTId parse_impl(Exec E) {
    return new ASTId(E.parseID());
  }
  @Override public String toString() { return _id; }

}

class ASTNum extends AST {
  final double _d;
  ASTNum(double d) { _d = d; }
  ASTNum parse_impl(Exec E) {
    return new ASTNum(Double.valueOf(E.parseID()));
  }

  @Override public String toString() { return Double.toString(_d); }

}
