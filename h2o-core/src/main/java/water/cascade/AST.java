package water.cascade;

import water.*;
import water.fvec.Frame;
import java.util.HashMap;

/**
 *   Each node in the syntax tree knows how to parse a piece of text from the passed tree.
 */
abstract public class AST extends Iced {
  AST[] _asts;
  AST parse_impl(Exec e) { throw H2O.fail("Missing parse_impl for class "+this.getClass()); }
  abstract void exec(Env e);
  abstract String value();
  abstract int type();
  public int numChildren() { return _asts.length; } // Must "apply" each arg, then put the results into ASTOp/UDF

  /**
   * Walk an AST and execute.
   */
  Env treeWalk(Env e) {

    // First check if we're a top-level node of type astop
    if (this instanceof ASTOp) {
      if (this instanceof ASTBinOp) {

        // Exec the right branch
        _asts[1].treeWalk(e);

        // Exec the left branch
        _asts[0].treeWalk(e);

        // Perform the binary operation
        ((ASTBinOp) this).apply(e);

      } else if (this instanceof ASTUniPrefixOp) {
        // TODO: do the prefix op thing
      } else {
        // TODO: do the udf op thing: capture env...
      }

      // Check if there's an assignment
    } else if (this instanceof ASTAssign) {

      // Exec the right branch
      _asts[1].treeWalk(e);

//      // Exec the left branch
//      _asts[0].treeWalk(e);

      // Do the assignment
      this.exec(e);  // Special case exec == apply for assignment

      // Check if we have an ID node (can be an argument, or part of an assignment).
    } else if (this instanceof ASTId) {
      ASTId id = (ASTId)this;
      assert id.isValid();
      if (id.isLookup()) {
        // lookup the ID and return an AST
        AST ast = e.lookup(id);
        e.put(id._id, ast.type(), id._id);
        ast.exec(e);
      } else if (id.isSet()) {
        e.put(((ASTId) this)._id, Env.ID, "");
        id.exec(e);
      } else {
        throw H2O.fail("Got a bad identifier: '"+ id.value() +"'. It has no type '!' or '$'.");
      }

      // Check if String, Num, Key, or Frame
    } else if (this instanceof ASTString || this instanceof ASTNum ||
            this instanceof ASTKey    || this._asts[0] instanceof ASTFrame) { this.exec(e); }

    else { throw H2O.fail("Unknown AST node. Don't know what to do with: " + this.toString());}
    return e;
  }
}

class ASTId extends AST {
  final String _id;
  final char _type; // either '$' or '!'
  ASTId(char type, String id) { _type = type; _id = id; }
  ASTId parse_impl(Exec E) { return new ASTId(_type, E.parseID()); }
  @Override public String toString() { return _type+_id; }
  @Override void exec(Env e) { e.push(this); } // should this be H2O.fail() ??
  @Override int type() { return Env.ID; }
  @Override String value() { return _id; }
  boolean isSet() { return _type == '!'; }
  boolean isLookup() { return _type == '$'; }
  boolean isValid() { return isSet() || isLookup(); }
}

class ASTKey extends AST {
  final String _key;
  ASTKey(String key) { _key = key; }
  ASTKey parse_impl(Exec E) { return new ASTKey(E.parseID()); }
  @Override public String toString() { return _key; }
  @Override void exec(Env e) { (new ASTFrame(_key)).exec(e); }
  @Override int type () { return Env.NULL; }
  @Override String value() { return _key; }
}

class ASTFrame extends AST {
  final String _key;
  final Frame _fr;
//  public Frame  fr() { return _fr; }
  ASTFrame(Frame fr) { _key = null; _fr = fr; }
  ASTFrame(String key) {
    if (DKV.get(Key.make(key)) == null) throw H2O.fail("Key "+ key +" no longer exists in the KV store!");
    _key = key;
    _fr = DKV.get(Key.make(_key)).get();
  }
  @Override public String toString() { return "Frame with key " + _key + ". Frame: :" +_fr.toString(); }
  @Override void exec(Env e) { e._locked.add(Key.make(_key)); e.push(this); }
  @Override int type () { return Env.ARY; }
  @Override String value() { return _key; }
}

class ASTNum extends AST {
  final double _d;
  ASTNum(double d) { _d = d; }
  ASTNum parse_impl(Exec E) { return new ASTNum(Double.valueOf(E.parseID())); }
  @Override public String toString() { return Double.toString(_d); }
  @Override void exec(Env e) { e.push(this); }
  @Override int type () { return Env.NUM; }
  @Override String value() { return Double.toString(_d); }
}

class ASTString extends AST {
  final String _s;
  final char _eq;
  ASTString(char eq, String s) { _eq = eq; _s = s; }
  ASTString parse_impl(Exec E) { return new ASTString(_eq, E.parseString(_eq)); }
  @Override public String toString() { return _s; }
  @Override void exec(Env e) { e.push(this); }
  @Override int type () { return Env.STR; }
  @Override String value() { return _s; }
}

class ASTAssign extends AST {
  ASTAssign parse_impl(Exec E) {
    AST l = E.parse();            // parse the ID on the left, or could be a column, or entire frame, or a row
    AST r = E.xpeek(' ').parse(); // parse double, String, or Frame on the right
    ASTAssign res = (ASTAssign)clone();
    res._asts = new AST[]{l,r};
    return res;
  }

  @Override int type () { throw H2O.fail(); }
  @Override String value() { throw H2O.fail(); }

  @Override void exec(Env e) {

    // Check if lhs is ID, update the symbol table; Otherwise it's a slice!
    if( this._asts[0] instanceof ASTId ) {
      ASTId id = (ASTId)this._asts[0];
      assert id.isSet() : "Expected to set result into the LHS!.";
      if (e.isAry()) {
        Frame f = e.popAry();
        Frame fr = new Frame(Key.make(id._id), f.names(), f.vecs());
        DKV.put(fr._key, fr);
        e._locked.add(fr._key);
        e.push(new ASTFrame(fr));
        // f.delete() ??
        e.put(id._id, Env.ARY, id._id);
      }
    }


    // Peel apart a slice assignment
//    ASTSlice slice = (ASTSlice)_lhs;
//    ASTId id = (ASTId)slice._ast;
//    assert id._depth==0;        // Can only modify in the local scope.
//    // Simple assignment using the slice syntax
//    if( slice._rows==null & slice._cols==null ) {
//      env.tos_into_slot(id._depth,id._num,id._id);
//      return;
//    }
//    // Pull the LHS off the stack; do not lower the refcnt
//    Frame ary = env.frId(id._depth,id._num);
//    // Pull the RHS off the stack; do not lower the refcnt
//    Frame ary_rhs=null;  double d=Double.NaN;
//    if( env.isDbl() ) d = env._d[env._sp-1];
//    else        ary_rhs = env.peekAry(); // Pop without deleting
//
//    // Typed as a double ==> the row & col selectors are simple constants
//    if( slice._t == Type.DBL ) { // Typed as a double?
//      assert ary_rhs==null;
//      long row = (long)((ASTNum)slice._rows)._d-1;
//      int  col = (int )((ASTNum)slice._cols)._d-1;
//      Chunk c = ary.vecs()[col].chunkForRow(row);
//      c.set(row,d);
//      Futures fs = new Futures();
//      c.close(c.cidx(),fs);
//      fs.blockForPending();
//      env.push(d);
//      return;
//    }
//
//    // Execute the slice LHS selection operators
//    Object cols = ASTSlice.select(ary.numCols(),slice._cols,env);
//    Object rows = ASTSlice.select(ary.numRows(),slice._rows,env);
//
//    long[] cs1; long[] rs1;
//    if(cols != null && rows != null && (cs1 = (long[])cols).length == 1 && (rs1 = (long[])rows).length == 1) {
//      assert ary_rhs == null;
//      long row = rs1[0]-1;
//      int col = (int)cs1[0]-1;
//      if(col >= ary.numCols() || row >= ary.numRows())
//        throw H2O.unimpl();
//      if(ary.vecs()[col].isEnum())
//        throw new IllegalArgumentException("Currently can only set numeric columns");
//      ary.vecs()[col].set(row,d);
//      env.push(d);
//      return;
//    }
//
//    // Partial row assignment?
//    if( rows != null ) {
//
//      // Only have partial row assignment
//      if (cols == null) {
//
//        // For every col at the range of indexes, set the value to be the rhs.
//        // If the rhs is a double, then fill with doubles, NA where type is Enum.
//        if (ary_rhs == null) {
//          // Make a new Vec where each row to be written over has the value d
//          final long[] rows0 = (long[]) rows;
//          final double d0 = d;
//          Vec v = new MRTask2() {
//            @Override
//            public void map(Chunk cs) {
//              for (long er : rows0) {
//                er = Math.abs(er) - 1; // 1-based -> 0-based
//                if (er < cs._start || er > (cs._len + cs._start - 1)) continue;
//                cs.set0((int) (er - cs._start), d0);
//              }
//            }
//          }.doAll(ary.anyVec().makeZero()).getResult()._fr.anyVec();
//
//          // MRTask over the lhs array
//          new MRTask2() {
//            @Override public void map(Chunk[] chks) {
//              // Replace anything that is non-zero in the rep_vec.
//              Chunk rep_vec = chks[chks.length-1];
//              for (int row = 0; row < chks[0]._len; ++row) {
//                if (rep_vec.at0(row) == 0) continue;
//                for (Chunk chk : chks) {
//                  if (chk._vec.isEnum()) { chk.setNA0(row); } else { chk.set0(row, d0); }
//                }
//              }
//            }
//          }.doAll(ary.numCols(), ary.add("rep_vec",v));
//          UKV.remove(v._key);
//          UKV.remove(ary.remove(ary.numCols()-1)._key);
//
//          // If the rhs is an array, then fail if `height` of the rhs != rows.length. Otherwise, fetch-n-fill! (expensive)
//        } else {
//          throw H2O.unimpl();
//        }
//
//        // Have partial row and col assignment
//      } else {
//        throw H2O.unimpl();
//      }
////      throw H2O.unimpl();
//    } else {
//      assert cols != null; // all/all assignment uses simple-assignment
//
//      // Convert constant into a whole vec
//      if (ary_rhs == null)
//        ary_rhs = new Frame(ary.anyVec().makeCon(d));
//      // Make sure we either have 1 col (repeated) or exactly a matching count
//      long[] cs = (long[]) cols;  // Columns to act on
//      if (ary_rhs.numCols() != 1 &&
//              ary_rhs.numCols() != cs.length)
//        throw new IllegalArgumentException("Can only assign to a matching set of columns; trying to assign " + ary_rhs.numCols() + " cols over " + cs.length + " cols");
//      // Replace the LHS cols with the RHS cols
//      Vec rvecs[] = ary_rhs.vecs();
//      Futures fs = new Futures();
//      for (int i = 0; i < cs.length; i++) {
//        int cidx = (int) cs[i] - 1;      // Convert 1-based to 0-based
//        Vec rv = env.addRef(rvecs[rvecs.length == 1 ? 0 : i]);
//        if (cidx == ary.numCols()) {
//          if (!rv.group().equals(ary.anyVec().group())) {
//            env.subRef(rv);
//            rv = ary.anyVec().align(rv);
//            env.addRef(rv);
//          }
//          ary.add("C" + String.valueOf(cidx + 1), rv);     // New column name created with 1-based index
//        }
//        else {
//          if (!(rv.group().equals(ary.anyVec().group())) && rv.length() == ary.anyVec().length()) {
//            env.subRef(rv);
//            rv = ary.anyVec().align(rv);
//            env.addRef(rv);
//          }
//          fs = env.subRef(ary.replace(cidx, rv), fs);
//        }
//      }
//      fs.blockForPending();
//    }
//    // After slicing, pop all expressions (cannot lower refcnt till after all uses)
//    int narg = 0;
//    if( rows!= null ) narg++;
//    if( cols!= null ) narg++;
//    env.pop(narg);
  }

  String argName() { return this._asts[0] instanceof ASTId ? ((ASTId)this._asts[0])._id : null; }
  @Override public String toString() { return "="; }
//  @Override public StringBuilder toString( StringBuilder sb, int d ) {
//    indent(sb,d).append(this).append('\n');
//    _lhs.toString(sb,d+1).append('\n');
//    _eval.toString(sb,d+1);
//    return sb;
//  }
}

// AST SLICE

//class ASTSlice extends AST {
//  final AST _ast, _cols, _rows; // 2-D slice of an expression
//  ASTSlice( Type t, AST ast, AST cols, AST rows ) {
//    super(t); _ast = ast; _cols = cols; _rows = rows;
//  }
//  static AST parse(Exec2 E, boolean EOS ) {
//    int x = E._x;
//    AST ast = ASTApply.parsePrefix(E, EOS);
//    if( ast == null ) return null;
//    if( !E.peek('[',EOS) )      // Not start of slice?
//      return ASTNamedCol.parse(E,ast,EOS); // Also try named col slice
//    if( !Type.ARY.union(ast._t) ) E.throwErr("Not an ary",x);
//    if(  E.peek(']',false) ) return ast; // [] ===> same as no slice
//    AST rows=E.xpeek(',',(x=E._x),parseCXExpr(E, false));
//    if( rows != null && !rows._t.union(Type.dblary()) ) E.throwErr("Must be scalar or array",x);
//    AST cols=E.xpeek(']',(x=E._x),parseCXExpr(E, false));
//    if( cols != null && !cols._t.union(Type.dblary()) ) E.throwErr("Must be scalar or array",x);
//    Type t =                    // Provable scalars will type as a scalar
//            rows != null && rows.isPosConstant() &&
//                    cols != null && cols.isPosConstant() ? Type.DBL : Type.ARY;
//    return new ASTSlice(t,ast,cols,rows);
//  }
//
//  @Override void exec(Env env) {
//    int sp = env._sp;  _ast.exec(env);  assert sp+1==env._sp;
//
//    // Scalar load?  Throws AIIOOB if out-of-bounds
//    if( _t.isDbl() ) {
//      // Known that rows & cols are simple positive constants.
//      // Use them directly, throwing a runtime error if OOB.
//      long row = (long)((ASTNum)_rows)._d;
//      int  col = (int )((ASTNum)_cols)._d;
//      Frame ary=env.popAry();
//      String skey = env.key();
//      double d = ary.vecs()[col-1].at(row-1);
//      env.subRef(ary,skey);     // Toss away after loading from it
//      env.push(d);
//    } else {
//      // Else It's A Big Copy.  Some Day look at proper memory sharing,
//      // disallowing unless an active-temp is available, etc.
//      // Eval cols before rows (R's eval order).
//      Frame ary=env._ary[env._sp-1];  // Get without popping
//      Object cols = select(ary.numCols(),_cols,env);
//      Object rows = select(ary.numRows(),_rows,env);
//      Frame fr2 = ary.deepSlice(rows,cols);
//      // After slicing, pop all expressions (cannot lower refcnt till after all uses)
//      if( rows!= null ) env.pop();
//      if( cols!= null ) env.pop();
//      if( fr2 == null ) fr2 = new Frame(); // Replace the null frame with the zero-column frame
//      env.pop();                // Pop sliced frame, lower ref
//      env.push(fr2);
//    }
//  }
//
//  // Execute a col/row selection & return the selection.  NULL means "all".
//  // Error to mix negatives & positive.  Negative list is sorted, with dups
//  // removed.  Positive list can have dups (which replicates cols) and is
//  // ordered.  numbers.  1-based numbering; 0 is ignored & removed.
//  static Object select( long len, AST ast, Env env ) {
//    if( ast == null ) return null; // Trivial "all"
//    ast.exec(env);
//    long cols[];
//    if( !env.isAry() ) {
//      int col = (int)env._d[env._sp-1]; // Peek double; Silent truncation (R semantics)
//      if( col < 0 && col < -len ) col=0; // Ignore a non-existent column
//      if( col == 0 ) return new long[0];
//      return new long[]{col};
//    }
//    // Got a frame/list of results.
//    // Decide if we're a toss-out or toss-in list
//    Frame ary = env._ary[env._sp-1];  // Peek-frame
//    if( ary.numCols() != 1 ) throw new IllegalArgumentException("Selector must be a single column: "+ary.toStringNames());
//    Vec vec = ary.anyVec();
//    // Check for a matching column of bools.
//    if( ary.numRows() == len && vec.min()>=0 && vec.max()<=1 && vec.isInt() )
//      return ary;    // Boolean vector selection.
//    // Convert single vector to a list of longs selecting rows
//    if(ary.numRows() > 10000000) throw H2O.fail("Unimplemented: Cannot explicitly select > 100000 rows in slice.");
//    cols = MemoryManager.malloc8((int)ary.numRows());
//    for(int i = 0; i < cols.length; ++i){
//      if(vec.isNA(i))throw new IllegalArgumentException("Can not use NA as index!");
//      cols[i] = vec.at8(i);
//    }
//    return cols;
//  }
//
//  @Override public String toString() { return "[,]"; }
//  @Override public StringBuilder toString( StringBuilder sb, int d ) {
//    indent(sb,d).append(this).append('\n');
//    _ast.toString(sb,d+1).append("\n");
//    if( _cols==null ) indent(sb,d+1).append("all\n");
//    else      _cols.toString(sb,d+1).append("\n");
//    if( _rows==null ) indent(sb,d+1).append("all");
//    else      _rows.toString(sb,d+1);
//    return sb;
//  }
//}



//class ASTParseTest {
//  private static void test1() {
//    // Checking `hex + 5`
//    String tree = "(+ (KEY a.hex) (# 5))";
//    checkTree(tree);
//  }
//
//  private static void test2() {
//    // Checking `hex + 5 + 10`
//    String tree = "(+ (KEY a.hex) (+ (# 5) (# 10))";
//    checkTree(tree);
//  }
//
//  private static void test3() {
//    // Checking `hex + 5 - 1 * hex + 15 * (23 / hex)`
//    String tree = "(+ (- (+ (KEY a.hex) (# 5) (* (# 1) (KEY a.hex) (* (# 15) (/ (# 23) (KEY a.hex)";
//    checkTree(tree);
//  }
//
//  public static void main(String[] args) {
//    test1();
//    test2();
//    test3();
//  }
//
//  private static void checkTree(String tree) {
//    String [] data = new String[]{
//            "'Col1\n"  +
//            "1\n" ,
//            "2\n" ,
//            "3\n" ,
//            "4\n" ,
//            "5\n" ,
//            "6\n" ,
//            "254\n" ,
//    };
//
//    Key rkey = ParserTest.makeByteVec(data);
//    Frame fr = ParseDataset2.parse(Key.make("a.hex"), rkey);
//    Exec e = new Exec(tree, new Env(new ArrayList<Key>()));
//    Env env = Exec.asdf(tree);
////    System.out.println(ast.toString());
//    System.out.println(env.toString());
//  }
//}