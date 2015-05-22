package water.currents;

import water.H2O;

abstract class ASTPrim extends AST {
  ASTPrim() { super(null); }
}

class ASTPlus extends ASTPrim {
  @Override Val exec( Env env ) {
    throw H2O.unimpl();
  }
}
