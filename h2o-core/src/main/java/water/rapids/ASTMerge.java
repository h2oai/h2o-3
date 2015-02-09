package water.rapids;

import water.*;
import water.fvec.*;


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
    throw H2O.fail();
  }

  @Override void apply(Env env) {
    Frame l = env.popAry();
    System.out.println(l);
    Frame r = env.popAry();
    System.out.println(r);
    System.out.println(_allLeft+" "+_allRite);

    // Look for the set of columns in common; resort left & right to make the
    // leading prefix of column names match.
    int len=0;                  // Number of columns in common
    for( int i=0; i<l._names.length; i++ ) {
      int idx = r.find(l._names[i]);
      if( idx != -1 ) {
        l.swap(i  ,len);
        r.swap(idx,len);
        len++;
      }
    }
    if( len == 0 ) 
      throw new IllegalArgumentException("Frames must have at least one column in common to merge them");

    // Pick the frame to replicate & hash; smallest bytesize of the non-key
    // columns.  Hashed dataframe is completely replicated per-node
    long lsize = 0, rsize = 0;
    for( int i=len; i<l.numCols(); i++ ) lsize += l.vecs()[i].byteSize();
    for( int i=len; i<r.numCols(); i++ ) rsize += r.vecs()[i].byteSize();
    Frame repl = lsize < rsize ? l : r;

    // hash keys: just hash the raw double bits from the leading columns

    // hashSET is from local (non-replicated) chunks/row to other-chunks/row.
    // HashKey object in table has e.g. chunks and a row number; passed-in
    // hashkey object can also have chunks & a row number.  Hash based on
    // contents of chunks.  Returns matched hashkey object (which has
    // replicated chunk ptrs & row).

    // Need a unique merge-in-progress set, same as ddply
    
    // run a local parallel hash of all data to populate hashSET

    // run a global parallel work: lookup non-hashed rows in hashSet; find
    // matching row; append matching column data

    throw H2O.unimpl(); 
  }

}
