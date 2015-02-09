package water.rapids;

import water.*;
import water.fvec.*;
import water.nbhm.NonBlockingHashMap;
import water.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;


/** plyr's merge: Join by any other name.
 *  Sample AST: (merge $leftFrame $rightFrame allLeftFlag allRightFlag)
 *
 *  Two frames; all columns with the same names will be the join key.  If
 *  allLeftFlag is true, all rows in the leftFrame will be included, even if
 *  there is no matching row in the rightFrame, and vice-versa for
 *  allRightFlag.  Missing data will appear as NAs.  Both flags can be true.
 */
public class ASTMerge extends ASTOp {
  static final String VARS[] = new String[]{ "ary", "leftary", "rightary", "allleft", "allright"};
  boolean _allLeft, _allRite;
  public ASTMerge( ) { super(VARS); }
  @Override String opStr(){ return "merge";}
  @Override ASTOp make() {return new ASTMerge();}

  @Override ASTMerge parse_impl(Exec E) {
    // get the frames to work with
    AST left = E.parse();  if (left instanceof ASTId) left = Env.staticLookup((ASTId)left);
    AST rite = E.parse();  if (rite instanceof ASTId) rite = Env.staticLookup((ASTId)rite);

    AST a = E._env.lookup((ASTId)E.skipWS().parse());
    try {
      _allLeft = ((ASTNum) a).dbl() == 1;
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("Argument `allLeft` expected to be a boolean.");
    }

    a = E._env.lookup((ASTId)E.skipWS().parse());
    try {
      _allRite = ((ASTNum) a).dbl() == 1;
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("Argument `allRite` expected to be a boolean.");
    }
    // Finish the rest
    ASTMerge res = (ASTMerge) clone();
    res._asts = new AST[]{left,rite};
    return res;
  }

  @Override void exec(Env e, AST arg1, AST[] args) {
    arg1.exec(e);
    throw H2O.unimpl();
  }

  @Override void apply(Env e) { throw H2O.fail(); }

}
