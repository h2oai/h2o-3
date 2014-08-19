package water.cascade;

import water.*;
import water.fvec.*;
import java.util.ArrayList;

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

      // Do the assignment
      this.exec(e);  // Special case exec => apply for assignment

    // Check if we have an ID node (can be an argument, or part of an assignment).
    } else if (this instanceof ASTId) {
      ASTId id = (ASTId) this;
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
        throw H2O.fail("Got a bad identifier: '" + id.value() + "'. It has no type '!' or '$'.");
      }

    // Check if we have just a plain-old slice
    } else if(this instanceof ASTSlice) {
      _asts[0].treeWalk(e);
      _asts[1].treeWalk(e);
      _asts[2].treeWalk(e);
      this.exec(e);

      // Check if String, Num, Key, or Frame
    } else if (this instanceof ASTString || this instanceof ASTNum || this instanceof ASTNull ||
            this instanceof ASTSeries || this instanceof ASTKey || this instanceof ASTSpan ||
            this._asts[0] instanceof ASTFrame) { this.exec(e); }

    else { throw H2O.fail("Unknown AST node. Don't know what to do with: " + this.toString());}
    return e;
  }

  protected StringBuilder indent( StringBuilder sb, int d ) {
    for( int i=0; i<d; i++ ) sb.append("  ");
    return sb.append(' ');
  }

  StringBuilder toString( StringBuilder sb, int d ) { return indent(sb,d).append(this); }
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

/**
 *  ASTSpan parses phrases like 1:10.
 */
class ASTSpan extends AST {
  final long _min;       final long _max;
  final ASTNum _ast_min; final ASTNum _ast_max;
  boolean _isCol; boolean _isRow;
  ASTSpan(ASTNum min, ASTNum max) { _ast_min = min; _ast_max = max; _min = (long)min._d; _max = (long)max._d; }
  ASTSpan parse_impl(Exec E) {
    AST l = E.parse();
    AST r = E.skipWS().parse();
    return new ASTSpan((ASTNum)l, (ASTNum)r);
  }
  boolean contains(long a) { return _min <= a && a <= _max; }
  boolean isColSelector() { return _isCol; }
  boolean isRowSelector() { return _isRow; }
  void setSlice(boolean row, boolean col) { _isRow = row; _isCol = col; }
  @Override void exec(Env e) { e.push(this); }
  @Override String value() { return null; }
  @Override int type() { return 0; }
  @Override public String toString() { return _min + ":" + _max; }

  long[] toArray() {
    long[] res = new long[(int)_max - (int)_min + 1];
    long min = _min;
    for (int i = 0; i < res.length; ++i) res[i] = min++;
    return res;
  }
}

class ASTSeries extends AST {
  final long[] _idxs;
  final ASTSpan[] _spans;
  boolean _isCol; boolean _isRow;
  ASTSeries(long[] idxs, ASTSpan[] spans) { _idxs = idxs; _spans = spans;}
  ASTSeries parse_impl(Exec E) {
    ArrayList<Long> l_idxs = new ArrayList<>();
    ArrayList<ASTSpan> s_spans = new ArrayList<>();
    String[] strs = E.parseString('}').split(";");
    for (String s : strs) {
      if (s.charAt(0) == '(') {
        s_spans.add( (ASTSpan)(new Exec(s,null)).parse());
      } else l_idxs.add(Long.valueOf(s));
    }
    long[] idxs = new long[l_idxs.size()]; ASTSpan[] spans = new ASTSpan[s_spans.size()];
    for (int i = 0; i < idxs.length; ++i) idxs[i] = l_idxs.get(i);
    for (int i = 0; i < spans.length; ++i) spans[i] = s_spans.get(i);
    return new ASTSeries(idxs, spans);
  }

  boolean contains(long a) {
    if (_spans != null)
      for (ASTSpan s:_spans) if(s.contains(a)) return true;
    if (_idxs != null)
      for (long l : _idxs) if (l == a) return true;
    return false;
  }
  boolean isColSelector() { return _isCol; }
  boolean isRowSelector() { return _isRow; }
  void setSlice(boolean row, boolean col) { _isRow = row; _isCol = col; }
  @Override void exec(Env e) { e.push(this); }
  @Override String value() { return null; }
  @Override int type() { return Env.SERIES; }
  @Override public String toString() {
    String res = "c(";
    if (_spans != null) {
      for (ASTSpan s : _spans) {
        res += s.toString(); res += ",";
      }
      if (_idxs == null) res = res.substring(0, res.length()-1); // remove last comma?
    }
    if (_idxs != null) {
      for (long l : _idxs) {
        res += l; res += ",";
      }
      res = res.substring(0, res.length()-1); // remove last comma.
    }
    res += ")";
    return res;
  }

  long[] toArray() {
    int res_length = 0;
    if (_spans != null) for (ASTSpan s : _spans) res_length += (int)s._max - (int)s._min + 1;
    if ( _idxs != null) res_length += _idxs.length;
    long[] res = new long[res_length];
    int cur = 0;
    if (_spans != null) {
      for (ASTSpan s : _spans) {
        long[] l = s.toArray();
        for (int i = 0; i < l.length; ++i) res[cur++] = l[i];
      }
    }
    if (_idxs != null) {
      for (int i = 0; i < _idxs.length; ++i) res[cur++] = _idxs[i];
    }
    return res;
  }
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

class ASTNull extends AST {
  ASTNull() {}
  @Override void exec(Env e) { e.push(this);}
  @Override String value() { return null; }
  @Override int type() { return Env.NULL; }
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
        Frame f = e.peekAry();
        Frame fr = new Frame(Key.make(id._id), f.names(), f.vecs());
        DKV.put(fr._key, fr);
        e._locked.add(fr._key);
//        fr.write_lock(null);
//        e.pop();
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

//  String argName() { return this._asts[0] instanceof ASTId ? ((ASTId)this._asts[0])._id : null; }
  @Override public String toString() { return "="; }
//  @Override public StringBuilder toString( StringBuilder sb, int d ) {
//    indent(sb,d).append(this).append('\n');
//    _lhs.toString(sb,d+1).append('\n');
//    _eval.toString(sb,d+1);
//    return sb;
//  }
}

// AST SLICE
class ASTSlice extends AST {
  ASTSlice() {}

  ASTSlice parse_impl(Exec E) {
    AST hex = E.parse();
    AST rows = E.skipWS().parse();
    if (rows instanceof ASTString) rows = new ASTNull();
    if (rows instanceof ASTSpan) ((ASTSpan) rows).setSlice(true, false);
    if (rows instanceof ASTSeries) ((ASTSeries) rows).setSlice(true, false);
    AST cols = E.skipWS().parse();
    if (cols instanceof ASTString) cols = new ASTNull();
    if (cols instanceof ASTSpan) ((ASTSpan) cols).setSlice(false, true);
    if (cols instanceof ASTSeries) ((ASTSeries) cols).setSlice(false, true);
    ASTSlice res = (ASTSlice) clone();
    res._asts = new AST[]{hex,rows,cols};
    return res;
  }

  @Override String value() { return null; }
  @Override int type() { return 0; }

  @Override void exec(Env env) {

    // stack looks like:  [....,hex,rows,cols], so pop, pop !
    int cols_type = env.peekType();
    Object cols = env.pop();
    int rows_type = env.peekType();
    Object rows = env.pop();

    // Scalar load?  Throws AIIOOB if out-of-bounds
    if(cols_type == Env.NUM && rows_type == Env.NUM) {
      // Known that rows & cols are simple positive constants.
      // Use them directly, throwing a runtime error if OOB.
      long row = (long)((ASTNum)rows)._d;
      int  col = (int )((ASTNum)cols)._d;
      Frame ary=env.popAry();
      if (ary.vecs()[col].isEnum()) { env.push(new ASTString('\"', ary.vecs()[col].domain()[(int)ary.vecs()[col].at(row)])); }
      else env.push( new ASTNum(ary.vecs()[col].at(row)));
      env.cleanup(ary);
    } else {
      // Else It's A Big Copy.  Some Day look at proper memory sharing,
      // disallowing unless an active-temp is available, etc.
      // Eval cols before rows (R's eval order).
      Frame ary= env.peekAry(); // Get without popping
      cols = select(ary.numCols(),(AST)cols, env);
      rows = select(ary.numRows(),(AST)rows,env);
      Frame fr2 = ary.deepSlice(rows,cols);
      if (cols instanceof Frame) for (Vec v : ((Frame)cols).vecs()) DKV.remove(v._key);
      if (rows instanceof Frame) for (Vec v : ((Frame)rows).vecs()) DKV.remove(v._key);
      if( fr2 == null ) fr2 = new Frame(); // Replace the null frame with the zero-column frame
      env.cleanup(ary, env.popAry());
      env.push(new ASTFrame(fr2));
    }
  }

  // Execute a col/row selection & return the selection.  NULL means "all".
  // Error to mix negatives & positive.  Negative list is sorted, with dups
  // removed.  Positive list can have dups (which replicates cols) and is
  // ordered.  numbers.  1-based numbering; 0 is ignored & removed.
  static Object select( long len, AST ast, Env env ) {
    if( ast.type() == Env.NULL ) return null; // Trivial "all"
    ast.exec(env); // this pushes the object back onto the stack
    long cols[];
    if( env.isNum() ) {
      int col = (int)((ASTNum)env.pop())._d; // Peek double; Silent truncation (R semantics)
      if( col < 0 && col < -len ) col=0; // Ignore a non-existent column
//      if( col == 0 ) return new long[0];
      return new long[]{col};
    }
    if (env.isSeries()) {
      ASTSeries a = env.popSeries();
      // if selecting out columns, build a long[] cols and return that.
      if (a.isColSelector()) return a.toArray();

      // Otherwise, we have rows selected: Construct a compatible "predicate" vec
      Frame ary = env.peekAry();
      Vec v0 = ary.anyVec().makeZero();
      final ASTSeries a0 = a;
      Frame fr = new MRTask() {
        @Override public void map(Chunk cs) {
          for (long i = cs.start(); i < cs.len() + cs.start(); ++i) {
            if (a0.contains(i)) cs.set0( (int)(i - cs.start()),1);
          }
        }
      }.doAll(v0).getResult()._fr;
//      DKV.remove(v0._key);
      return fr;
    }
    if (env.isSpan()) {
      ASTSpan a = env.popSpan();
      // if selecting out columns, build a long[] cols and return that.
      if (a.isColSelector()) return a.toArray();

      // Otherwise, we have rows selected: Construct a compatible "predicate" vec
      Frame ary = env.peekAry();
      final ASTSpan a0 = a;
      Frame fr = new MRTask() {
        @Override public void map(Chunk cs) {
          for (long i = cs.start(); i < cs.len() + cs.start(); ++i) {
            if (a0.contains(i)) cs.set0( (int)(i - cs.start()),1);
          }
        }
      }.doAll(ary.anyVec().makeZero()).getResult()._fr;
      return fr;
    }
    // Got a frame/list of results.
    // Decide if we're a toss-out or toss-in list
    Frame ary = env.peekAry();  // Peek-frame
    if( ary.numCols() != 1 ) throw new IllegalArgumentException("Selector must be a single column: "+ary.names());
    Vec vec = ary.anyVec();
    // Check for a matching column of bools.
    if( ary.numRows() == len && vec.min()>=0 && vec.max()<=1 && vec.isInt() )
      return ary;    // Boolean vector selection.
    // Convert single vector to a list of longs selecting rows
    if(ary.numRows() > 10000000) throw H2O.fail("Unimplemented: Cannot explicitly select > 10000000 rows in slice.");
    cols = MemoryManager.malloc8((int)ary.numRows());
    for(int i = 0; i < cols.length; ++i){
      if(vec.isNA(i))throw new IllegalArgumentException("Can not use NA as index!");
      cols[i] = vec.at8(i);
    }
    return cols;
  }

  @Override public String toString() { return "[,]"; }
  @Override public StringBuilder toString( StringBuilder sb, int d ) {
    indent(sb,d).append(this).append('\n');
    _asts[0].toString(sb,d+1).append("\n");
    if( _asts[2]==null ) indent(sb,d+1).append("all\n");
    else      _asts[2].toString(sb,d+1).append("\n");
    if( _asts[1]==null ) indent(sb,d+1).append("all");
    else      _asts[1].toString(sb,d+1);
    return sb;
  }
}