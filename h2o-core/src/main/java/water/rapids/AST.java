package water.rapids;

import water.*;
import water.fvec.*;

import java.util.ArrayList;
import java.util.Arrays;

/**
 *   Each node in the syntax tree knows how to parse a piece of text from the passed tree.
 */
abstract public class AST extends Iced {
  String[] _arg_names;
  AST[] _asts;
  AST parse_impl(Exec e) { throw H2O.fail("Missing parse_impl for "+this.getClass()); }
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
        for (int i = 0; i < _asts.length; ++i) _asts[i].treeWalk(e);
        ((ASTUniPrefixOp) this).apply(e);
      } else if (this instanceof ASTReducerOp) {
        for (int i = 0; i < _asts.length; ++i) _asts[i].treeWalk(e);
        ((ASTReducerOp) this).apply(e);
      } else if (this instanceof ASTLs) {
        ((ASTLs) this).apply(e);
      } else if (this instanceof ASTFunc) {
        ((ASTFunc) this).apply(e);
      } else if (this instanceof ASTApply) {
        _asts[0].treeWalk(e);  // push the frame we're `apply`ing over
        ((ASTApply) this).apply(e);

      } else if (this instanceof ASTddply) {
        _asts[0].treeWalk(e);
        ((ASTddply)this).apply(e);
      } else {
        throw H2O.fail("Unknown AST in tree walk: " + this.getClass());
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
//        e.put(id._id, ast.type(), id._id);
        ast.exec(e);
      } else if (id.isLocalSet() || id.isGlobalSet()) {
        e.put(((ASTId) this)._id, Env.ID, "");
        id.exec(e);
      } else {
        throw H2O.fail("Got a bad identifier: '" + id.value() + "'. It has no type '!' or '$'.");
      }


    } else if(this instanceof ASTStatement) {

      if (this instanceof ASTIf) { this.exec(e); }
      else if (this instanceof ASTElse) { this.exec(e); }
      else if (this instanceof ASTFor) { throw H2O.unimpl("`for` loops are currently unsupported."); }
      else if (this instanceof ASTReturn) { this.exec(e); return e; }
      else this.exec(e);

    // Check if we have a slice.
    } else if(this instanceof ASTSlice) {
      _asts[0].treeWalk(e); // push hex
      _asts[1].treeWalk(e); // push rows
      _asts[2].treeWalk(e); // push cols
      this.exec(e);         // do the slice

    // Check if String, Num, Null, Series, Key, Span, Frame, or Raft
    } else if (this instanceof ASTString || this instanceof ASTNum || this instanceof ASTNull ||
            this instanceof ASTSeries || this instanceof ASTKey || this instanceof ASTSpan ||
            this instanceof ASTRaft || this instanceof ASTFrame || this._asts[0] instanceof ASTFrame) { this.exec(e); }

    else { throw H2O.fail("Unknown AST: " + this.getClass());}
    return e;
  }

  protected StringBuilder indent( StringBuilder sb, int d ) {
    for( int i=0; i<d; i++ ) sb.append("  ");
    return sb.append(' ');
  }

  StringBuilder toString( StringBuilder sb, int d ) { return indent(sb,d).append(this); }
}

class ASTRaft extends AST {
  final Key _key;
  ASTRaft(String id) { _key = Key.make(id); }
  @Override public String toString() { return _key.toString(); }
  @Override void exec(Env e) {
    Key k;
    Raft r = DKV.get(_key).get();
    if ((k=r.get_key())==null) r.get_ast().treeWalk(e);
    else (new ASTFrame(k)).exec(e);
  }
  @Override int type() { return Env.AST; }
  @Override String value() { return _key.toString(); }

}

class ASTId extends AST {
  final String _id;
  final char _type; // either '$' or '!' or '&'
  ASTId(char type, String id) { _type = type; _id = id; }
  ASTId parse_impl(Exec E) {
    return new ASTId(_type, E.parseID());
  }
  @Override public String toString() { return _type+_id; }
  @Override void exec(Env e) { e.push(new ValId(_type, _id)); } // should this be H2O.fail() ??
  @Override int type() { return Env.ID; }
  @Override String value() { return _id; }
  boolean isLocalSet() { return _type == '&'; }
  boolean isGlobalSet() { return _type == '!'; }
  boolean isLookup() { return _type == '%'; }
  boolean isValid() { return isLocalSet() || isGlobalSet() || isLookup(); }
}

class ASTKey extends AST {
  final String _key;
  ASTKey(String key) { _key = key; }
  ASTKey parse_impl(Exec E) {
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    return new ASTKey(E.parseID());
  }
  @Override public String toString() { return _key; }
  @Override void exec(Env e) { (new ASTFrame(_key)).exec(e); }
  @Override int type () { return Env.NULL; }
  @Override String value() { return _key; }
}

class ASTFrame extends AST {
  final String _key;
  final Frame _fr;
  ASTFrame(Frame fr) { _key = fr._key == null ? null : fr._key.toString(); _fr = fr; }
  ASTFrame(Key key) { this(key.toString()); }
  ASTFrame(String key) {
    Key k = Key.make(key);
    Keyed val = DKV.getGet(k);
    if (val == null) throw H2O.fail("Key "+ key +" no longer exists in the KV store!");
    _key = key;
    _fr = val instanceof Frame ? (Frame)val : new Frame((Vec)val);
  }
  @Override public String toString() { return "Frame with key " + _key + ". Frame: :" +_fr.toString(); }
  @Override void exec(Env e) {
    if (_key != null) {
      if (e._local_locked != null) {
        e._local_locked.add(Key.make(_key));
        e._local_frames.add(Key.make(_key));
      }
      else {
        e._locked.add(Key.make(_key));
        e._global_frames.add(Key.make(_key));
      }
      if (H2O.containsKey(Key.make(_key))) e._locked.add(Key.make(_key));
    }
    e.addKeys(_fr); e.push(new ValFrame(_fr));
  }
  @Override int type () { return Env.ARY; }
  @Override String value() { return _key; }
}

class ASTNum extends AST {
  final double _d;
  ASTNum(double d) { _d = d; }
  ASTNum parse_impl(Exec E) {
    try {
      return new ASTNum(Double.valueOf(E.parseID()));
    } catch (NumberFormatException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Unexpected numerical argument. Badly formed AST.");
    }
  }
  @Override public String toString() { return Double.toString(_d); }
  @Override void exec(Env e) { e.push(new ValNum(_d)); }
  @Override int type () { return Env.NUM; }
  @Override String value() { return Double.toString(_d); }
  double dbl() { return _d; }
}

/**
 *  ASTSpan parses phrases like 1:10.
 */
class ASTSpan extends AST {
  final long _min;       final long _max;
  final ASTNum _ast_min; final ASTNum _ast_max;
  boolean _isCol; boolean _isRow;
  ASTSpan(ASTNum min, ASTNum max) { _ast_min = min; _ast_max = max; _min = (long)min._d; _max = (long)max._d;
    if (_min > _max) throw new IllegalArgumentException("min > max: min <= max for `:` operator.");
  }
  ASTSpan(long min, long max) { _ast_min = new ASTNum(min); _ast_max = new ASTNum(max); _min = min; _max = max;
    if (_min > _max) throw new IllegalArgumentException("min > max for `:` operator.");
  }
  ASTSpan parse_impl(Exec E) {
    AST l = E.parse();
    AST r = E.skipWS().parse();
    return new ASTSpan((ASTNum)l, (ASTNum)r);
  }
  boolean contains(long a) {
    if (all_neg()) return _max <= a && a <= _min;
    return _min <= a && a <= _max;
  }
  boolean isColSelector() { return _isCol; }
  boolean isRowSelector() { return _isRow; }
  void setSlice(boolean row, boolean col) { _isRow = row; _isCol = col; }
  @Override void exec(Env e) { ValSpan v = new ValSpan(_ast_min, _ast_max); v.setSlice(_isRow, _isCol); e.push(v); }
  @Override String value() { return null; }
  @Override int type() { return Env.SPAN; }
  @Override public String toString() { return _min + ":" + _max; }

  long[] toArray() {
    long[] res = new long[(int)_max - (int)_min + 1];
    long min = _min;
    for (int i = 0; i < res.length; ++i) res[i] = min++;
    return res;
  }
  boolean all_neg() { return _min < 0; }
  boolean all_pos() { return !all_neg(); }
  boolean isNum() { return _min == _max; }
  long toNum() { return _min; }
//  @Override public AutoBuffer write_impl(AutoBuffer ab) {
//    ab.put8(_min);
//    ab.put8(_max);
//    return ab;
//  }
//  @Override public ASTSpan read_impl(AutoBuffer ab) {
//    return new ASTSpan(ab.get8(), ab.get8());
//  }
}

class ASTSeries extends AST {
  final long[] _idxs;
  final ASTSpan[] _spans;
  boolean _isCol;
  boolean _isRow;
  int[] _order; // a sequence of 0s and 1s. 0 -> span; 1 -> index

  ASTSeries(long[] idxs, ASTSpan[] spans) {
    _idxs = idxs;
    _spans = spans;
  }

  ASTSeries parse_impl(Exec E) {
    ArrayList<Long> l_idxs = new ArrayList<>();
    ArrayList<ASTSpan> s_spans = new ArrayList<>();
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    String[] strs = E.parseString('}').split(";");
    _order = new int[strs.length];
    int o = 0;
    for (String s : strs) {
      if (s.charAt(0) == '(') {
        _order[o] = 0;
        // get a non ASTSpan as next elt
        try {
          s_spans.add((ASTSpan) (new Exec(s, null)).parse());
        } catch (ClassCastException e) {
          try {
            ASTOp anum = (ASTOp) (new Exec(s, null)).parse();
            long n = (long)anum.treeWalk(E._env).popDbl();
            _order[o] = 1;
            l_idxs.add(n);
          } catch (ClassCastException e2) {
            throw new IllegalArgumentException("AST in sequence did not evaluate to a range or number.\n Only (: min max), #, and ASTs that evaluate to # are valid.");
          }
        }
        o++;
      } else {
        _order[o++] = 1;
        if (s.charAt(0) == '#') s = s.substring(1, s.length());
        try {
          Long.valueOf(s);
        } catch (Exception e) {
          throw new IllegalArgumentException("Invalid input. Value was not long or int: "+s);
        }
        l_idxs.add(Long.valueOf(s));
      }
    }
    long[] idxs = new long[l_idxs.size()];
    ASTSpan[] spans = new ASTSpan[s_spans.size()];
    for (int i = 0; i < idxs.length; ++i) idxs[i] = l_idxs.get(i);
    for (int i = 0; i < spans.length; ++i) spans[i] = s_spans.get(i);
    ASTSeries aa = new ASTSeries(idxs, spans);
    aa._order = _order;
    return aa;
  }

  boolean contains(long a) {
    if (_spans != null)
      for (ASTSpan s : _spans) if (s.contains(a)) return true;
    if (_idxs != null)
      for (long l : _idxs) if (l == a) return true;
    return false;
  }

  boolean isColSelector() { return _isCol; }
  boolean isRowSelector() { return _isRow; }

  void setSlice(boolean row, boolean col) { _isRow = row; _isCol = col; }

  @Override
  void exec(Env e) {
    ValSeries v = new ValSeries(_idxs, _spans);
    v._order = _order;
    v.setSlice(_isRow, _isCol);
    e.push(v);
  }

  @Override String value() { return null; }
  @Override int type() { return Env.SERIES; }

  @Override
  public String toString() {
    String res = "c(";
    if (_spans != null) {
      for (ASTSpan s : _spans) {
        res += s.toString();
        res += ",";
      }
      if (_idxs == null) res = res.substring(0, res.length() - 1); // remove last comma?
    }
    if (_idxs != null) {
      for (long l : _idxs) {
        res += l;
        res += ",";
      }
      res = res.substring(0, res.length() - 1); // remove last comma.
    }
    res += ")";
    return res;
  }

  long[] toArray() {
    int res_length = 0;
    if (_spans != null) for (ASTSpan s : _spans) res_length += (int) s._max - (int) s._min + 1;
    if (_idxs != null) res_length += _idxs.length;
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
//  @Override public AutoBuffer write_impl(AutoBuffer ab) {
//    ab.putA8(_idxs);
//    ab.put1(_spans.length);
//    for (int i = 0; i < _spans.length; ++i) {
//      ab = _spans[i].write_impl(ab);
//    }
//    ab.putZ(_isCol);
//    ab.putA4(_order);
//    return ab;
//  }
//  @Override public ASTSeries read_impl(AutoBuffer ab) {
//    long[] idxs = ab.getA8();
//    int nspans = ab.get1();
//    ASTSpan[] spans = new ASTSpan[nspans];
//    for (int i = 0; i < nspans; ++i) {
//      spans[i] = ab.get(ASTSpan.class);
//    }
//    boolean isCol = ab.getZ();
//    int[] order = ab.getA4();
//    ASTSeries series = new ASTSeries(idxs, spans);
//    series._order = order;
//    series._isCol = isCol;
//    series._isRow = !isCol;
//    return series;
//  }
}

class ASTStatement extends AST {

  // must parse all statements: {(ast);(ast);(ast);...;(ast)}
  @Override ASTStatement parse_impl( Exec E ) {
    ArrayList<AST> ast_ary = new ArrayList<AST>();

    // an ASTStatement is an array of ASTs. May have ASTStatements within ASTStatements.
    while (E.hasNextStmnt()) {
      AST ast = E.skipWS().parse();
      ast_ary.add(ast);
      E.skipEOS();  // skip EOS == End of Statement
    }

    ASTStatement res = (ASTStatement) clone();
    res._asts = ast_ary.toArray(new AST[ast_ary.size()]);
    return res;
  }
  @Override void exec(Env env) {
    ArrayList<Frame> cleanup = new ArrayList<>();
    for( int i=0; i<_asts.length-1; i++ ) {
      if (_asts[i] instanceof ASTReturn) {
       _asts[i].treeWalk(env);
        return;
      }
      _asts[i].treeWalk(env);  // Execute the statements by walking the ast
      if (env.isAry()) cleanup.add(env.pop0Ary()); else env.pop();  // Pop all intermediate results; needed results will be looked up.
    }
    _asts[_asts.length-1].treeWalk(env); // Return final statement as result
    for (Frame f : cleanup) f.delete();
  }

  @Override String value() { return null; }
  @Override int type() {return 0; }

  @Override public String toString() { return toString(new StringBuilder(""), 0).toString() + ";;;"; }
  @Override public StringBuilder toString( StringBuilder sb, int d ) {
    for (int i = 0; i < _asts.length - 1; i++)
      _asts[i].toString(sb, d + 1).append(";\n");
    return _asts[_asts.length - 1].toString(sb, d + 1);
  }
}

class ASTReturn extends ASTStatement {
  protected AST _stmnt;
  ASTReturn() {}

  @Override ASTReturn parse_impl(Exec E) {
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    AST stmnt = E.skipWS().parse();
    ASTReturn res = (ASTReturn) clone();
    res._stmnt = stmnt;
    return res;
  }

  @Override void exec(Env e) { _stmnt.treeWalk(e); }
  @Override String value() { return null; }
  @Override int type() { return 0; }
}

class ASTIf extends ASTStatement {
  protected AST _pred;
  protected ASTElse _else = null;
  ASTIf() {}

  // (if pred body)
  @Override ASTIf parse_impl(Exec E) {
    // parse the predicate
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    AST pred = E.parse();
    ASTStatement statement = super.parse_impl(E.skipWS());
    ArrayList<AST> ast_list = new ArrayList<>();
    for (int i = 0; i < statement._asts.length; ++i) {
      if (statement._asts[i] instanceof ASTElse) _else = (ASTElse)statement._asts[i];
      else ast_list.add(statement._asts[i]);
    }
    ASTIf res = (ASTIf) clone();
    res._pred = pred;
    res._asts = ast_list.toArray(new AST[ast_list.size()]);
    return res;
  }

  @Override void exec(Env e) {
    Env captured = e.capture();
    captured = _pred.treeWalk(captured);
    if (captured.isAry()) throw new IllegalArgumentException("Frames not supported in the if's condition.");
    double v = captured.popDbl();
    captured.popScope();
    if (v == 0) if (_else == null) return; else _else.exec(e);
    else super.exec(e);  // run the statements
  }
  @Override String value() { return null; }
  @Override int type() { return 0; }
}

class ASTElse extends ASTStatement {
  // (else body)
  ASTElse() {}
  @Override ASTElse parse_impl(Exec E) {
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    ASTStatement statements = super.parse_impl(E.skipWS());
    ASTElse res = (ASTElse)clone();
    res._asts = statements._asts;
    return res;
  }
  @Override void exec(Env e) { super.exec(e); }
  @Override String value() { return null; }
  @Override int type() { return 0; }
}

class ASTFor extends ASTStatement {
  protected int _start;
  protected int _end;
//  protected Object[] _arr;

  // (for #start #end body)
  @Override ASTFor parse_impl(Exec E) {
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    int s = (int)((ASTNum)E.skipWS().parse())._d;
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    int e = (int)((ASTNum)E.skipWS().parse())._d;
    ASTStatement stmts = super.parse_impl(E);
    ASTFor res = (ASTFor)clone();
    res._asts = stmts._asts;
    res._start = s;
    res._end = e;
    return res;
  }

  @Override void exec(Env e) { for (int i = _start; i < _end; ++i) super.exec(e); }
  @Override String value() { return null; }
  @Override int type() { return 0; }
}

class ASTWhile extends ASTStatement {
//  protected AST _pred;

  // (while pred body)
  @Override
  ASTWhile parse_impl(Exec E) {
    throw H2O.unimpl("while loops are not supported.");
  }
}

//    AST pred = E.parse();
//    ASTStatement statement = super.parse_impl(E);
//    ASTWhile res = (ASTWhile) clone();
//    res._pred = pred;
//    res._asts = statement._asts;
//    return res;
//  }
//
//  @Override void exec(Env e) {
//    while(checkPred(e)) {
//
//    }
//  }
//
//  private boolean checkPred(Env e) {
//    Env captured = e.capture();
//    captured = _pred.treeWalk(captured);
//    double v = captured.popDbl();
//    captured.popScope();
//    if (v == 0) return false;
//    return true;
//  }
//
//  @Override String value() { return null; }
//  @Override int type() { return 0; }
//}

class ASTString extends AST {
  final String _s;
  final char _eq;
  ASTString(char eq, String s) { _eq = eq; _s = s; }
  ASTString parse_impl(Exec E) {
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    return new ASTString(_eq, E.parseString(_eq));
  }
  @Override public String toString() { return _s; }
  @Override void exec(Env e) { e.push(new ValStr(_s)); }
  @Override int type () { return Env.STR; }
  @Override String value() { return _s; }
}

class ASTNull extends AST {
  ASTNull() {}
  @Override void exec(Env e) { e.push(new ValNull());}
  @Override String value() { return null; }
  @Override int type() { return Env.NULL; }
}

/**
 *  The ASTAssign Class
 *
 *  Handle the four cases of assignment:
 *
 *     1. Whole frame assignment:  hexA = RHS
 *     2. Three flavors of slot assignment:
 *        A. hex[,col(s)]       = RHS       // column(s) re-assign
 *        B. hex[row(s),]       = RHS       // row(s) re-assign
 *        C. hex[row(s),col(s)] = RHS       // row(s) AND col(s) re-assign
 *
 *     NB: RHS is any arbitrary (but valid) AST
 *
 *     This also supports adding a new column.
 *     (e.g., hex$new_column &lt;- 10, creates "new_column" vector in hex with values set to 10)
 *
 *     The RHS is already on the stack, the LHS is not yet on the stack.
 *
 *     Note about Categorical/Non-Categorical Vecs:
 *
 *       If the vec is enum, then the RHS must also be enum (if enum is not in domain of LHS produce NAs).
 *       If the vec is numeric, then the RHS must also be numeric (if enum, then produce NAs or throw IAE).
 */
class ASTAssign extends AST {
  ASTAssign parse_impl(Exec E) {
    E.skipWS();
    AST l;
    if (E.isSpecial(E.peek())) {
      boolean putkv = E.peek() == '!';
      if (putkv) E._x++; // skip the !
      l = new ASTId(putkv ? '!' : '&', E.parseID()); // parse the ID on the left, or could be a column, or entire frame, or a row
    } else l = E.parse();
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    AST r = E.skipWS().parse();   // parse double, String, or Frame on the right
    ASTAssign res = (ASTAssign)clone();
    res._asts = new AST[]{l,r};
    return res;
  }

  @Override int type () { throw H2O.fail(); }
  @Override String value() { throw H2O.fail(); }
  private static boolean in(String s, String[] matches) { return Arrays.asList(matches).contains(s); }

  private static void replaceRow(Chunk[] chks, int row, double d0, String s0, long[] cols) {
    for (int c = 0; c < cols.length; ++c) {
      int col = (int)cols[c];
      // have an enum column
      if (chks[col].vec().isEnum()) {
        if (s0 == null) { chks[col].setNA0(row); continue; }
        String[] dom = chks[col].vec().domain();
        if (in(s0, dom)) { chks[col].set0(row, Arrays.asList(dom).indexOf(s0)); continue; }
        chks[col].setNA0(row); continue;

        // have a numeric column
      } else if (chks[col].vec().isNumeric()) {
        if (Double.isNaN(d0) || s0 != null) { chks[col].setNA0(row); continue; }
        chks[col].set(row, d0); continue;
      }
    }
  }

  // replace thing on the RHS -> LHS. set from cs to ary
  private static void replaceRow(Chunk[] cs, int row0, long row_id, long[] cols, Frame ary) {
    for (int c = 0; c < cols.length; ++c) {
      int col = (int)cols[c];
      // got an enum trying to set into enum
      // got an enum trying to set into something else -> NA
      if (cs[c].vec().isEnum()) {
        if (ary.vecs()[col].isEnum()) {
          ary.vecs()[col].set(row_id, cs[c].at0(row0));
        } else {
          ary.vecs()[col].set(row_id, Double.NaN);
        }
      }

      // got numeric trying to set into something non-numeric -> NA
      // got numeric trying to set into numeric
      if (cs[c].vec().isNumeric()) {
        if (ary.vecs()[col].isNumeric()) {
          ary.vecs()[col].set(row_id, cs[c].at0(row0));
        } else {
          ary.vecs()[col].set(row_id, Double.NaN);
        }
      }
    }
  }

  private static void assignRows(Env e, Object rows, final Frame lhs_ary, Object cols) {
    // For every col at the range of indexes, set the value to be the rhs, which is expected to be a scalar (or possibly a string).
    // If the rhs is a double or str, then fill with doubles or NA when type is Categorical.
    final long[] cols0 = cols == null ? new long[lhs_ary.numCols()] : (long[])cols;
    if (cols == null) for (int i = 0; i < lhs_ary.numCols(); ++i) cols0[i] = i;

    if (!e.isAry()) {

      String s = null;
      double d = Double.NaN;
      if (e.isStr()) s = e.popStr();
      else if (e.isNum()) d = e.popDbl();
      else throw new IllegalArgumentException("Did not get a single number or factor level on the RHS of the assignment.");
      final double d0 = d;
      final String s0 = s;

      // Case: Have a long[] of rows
      if (rows instanceof long[]) {
        final long[] rows0 = (long[]) rows;

        // MRTask over the lhs array
        new MRTask() {
          @Override public void map(Chunk[] chks) {
            for (int row = 0; row < chks[0]._len; ++row)
              if (Arrays.asList(rows0).contains(row)) replaceRow(chks, row, d0, s0, cols0);
          }
        }.doAll(lhs_ary);
        e.push0(new ValFrame(lhs_ary));
        return;

        // Case: rows is a Frame -- in this case it's expected to be a predicate vec
      } else if (rows instanceof Frame) {
        Frame rr = new Frame(lhs_ary).add((Frame) rows);
        if (rr.numCols() != lhs_ary.numCols() + 1)
          throw new IllegalArgumentException("Got multiple columns for row predicate.");

        // treat rows as a bit vec, nonzeros mean rows should be replaced with s0 or d0, 0s mean continue
        new MRTask() {
          @Override public void map(Chunk[] cs, NewChunk[] ncs) {
            Chunk pred = cs[cs.length - 1];
            int rows = cs[0]._len;
            for (int r = 0; r < rows; ++r) if (pred.at0(r) != 0) replaceRow(cs, r, d0, s0, cols0);
          }
        }.doAll(rr);
        e.cleanup(rr, (Frame) rows);
        e.push0(new ValFrame(lhs_ary));
        return;
      } else throw new IllegalArgumentException("Invalid row selection. (note: RHS was a constant)");

      // If the rhs is an array, then fail if `height` of the rhs != rows.length. Otherwise, fetch-n-fill! (expensive)
    } else {
      // RHS shape must match LHS shape...
      // Example: hex[1:50,] <- hex2[90:100,] will fail, but swap out 90:100 w/ 101:150 should pass
      // LHS.numCols() == RHS.numCols()
      // mismatch in types results in NA

      final Frame rhs_ary = e.pop0Ary();
      if ( ( cols == null && rhs_ary.numCols() != lhs_ary.numCols()) || (cols != null && rhs_ary.numCols() != ((long[]) cols).length ) )
        throw new IllegalArgumentException("Right-hand frame has does not match the number of columns required in the assignment to the left-hand side." );
      if (rhs_ary.numRows() > lhs_ary.numRows()) throw new IllegalArgumentException("Right-hand side frame has more rows than the left-hand side.");

      // case where rows is a long[]
      if (rows instanceof long[]) {
        final long[] rows0 = (long[])rows;
        if (rows0.length != rhs_ary.numRows()) throw new IllegalArgumentException("Right-hand side array does not match the number of rows selected in the left-hand side.");
        // rows0 will have access to the index of the row to grab, use this to get the correct row from the rhs ary

        // MRTask over the lhs array
        new MRTask() {
          @Override public void map(Chunk[] chks) {
            for (int row = 0; row < chks[0]._len; ++row) {
              if (Arrays.asList(rows0).contains(row)) {
                long row_id = (long)Arrays.asList(rows0).indexOf(row);
                for (int c = 0; c < cols0.length; ++c) {
                  int col = (int)cols0[c];
                  // vec is enum
                  if (chks[col].vec().isEnum())
                    if (!rhs_ary.vecs()[col].isEnum()) { chks[col].setNA0(row); continue; }
                    // else vec is numeric
                  else if (chks[col].vec().isNumeric())
                    if (!rhs_ary.vecs()[col].isNumeric()) { chks[col].setNA0(row); continue; }
                  chks[col].set0(row, rhs_ary.vecs()[col].at(row_id));
                }
              }
            }
          }
        }.doAll(lhs_ary);
        e.cleanup(rhs_ary);
        e.push0(new ValFrame(lhs_ary));
        return;

        // case where rows is a Frame
      } else if (rows instanceof Frame) {

        // MRTask over the lhs frame + rows predicate vec. the value of the predicate vec will be 1 + the row index
        // in the corresponding rhs frame

        // MRTask over the pred vec to collapse to a dense set of row IDs
        if (((Frame)rows).numCols() != 1) throw new IllegalArgumentException("Got multiple columns for row predicate.");
        Frame pred = new MRTask() {
          @Override public void map(Chunk c, NewChunk nc) {
            for (int r = 0; r < c._len; ++r) {
              double d = c.at0(r);
              if (d != 0) nc.addNum(d);
            }
          }
        }.doAll(1, (Frame)rows).outputFrame(null, null);

        if (pred.numRows() != rhs_ary.numRows())
          throw new IllegalArgumentException("Right-hand side array does not match the number of rows selected in the left-hand side.");

        Frame rr = new Frame(rhs_ary).add(pred);

        // MRTask over the RHS ary, pushing data out to the LHS ary based on the pred vec
        new MRTask() {
          @Override public void map(Chunk[] cs) {
            Chunk pred = cs[cs.length-1];
            int rows = cs[0]._len;
            for (int r=0; r<rows; ++r) {
              long row_id = (long)pred.at0(r) - 1;
              replaceRow(cs, r, row_id, cols0, lhs_ary);
            }
          }
        }.doAll(rr);

        e.cleanup(pred, rr, (Frame) rows);
        e.push0(new ValFrame(lhs_ary));
        return;
      } else throw new IllegalArgumentException("Invalid row selection. (note: RHS was Frame");
    }
  }

  @Override void exec(Env e) {

    // Case 1: Whole Frame assignment
    // Check if lhs is ID, update the symbol table; Otherwise it's a slice!
    if( this._asts[0] instanceof ASTId ) {
      ASTId id = (ASTId)this._asts[0];
      assert id.isGlobalSet() || id.isLocalSet() : "Expected to set result into the LHS!.";

      // RHS is a frame
      if (e.isAry()) {
        Frame f = e.pop0Ary();  // pop without lowering counts
        Key k = Key.make(id._id);
        Frame fr = new Frame(k, f.names(), f.vecs());
        if (id.isGlobalSet()) {
          Futures fs = new Futures();
          DKV.put(k, fr, fs);
          fs.blockForPending();
        }
        if (e._local_locked != null) {
          e._local_locked.add(fr._key);
          e._local_frames.add(fr._key);
        }
        else  {
          e._locked.add(fr._key);
          e._global_frames.add(fr._key);
        }
        e.push(new ValFrame(fr));
        e.put(id._id, Env.ARY, id._id);
      }

    // The other three cases of assignment follow
    } else {

      // Peel apart a slice assignment
      ASTSlice lhs_slice = (ASTSlice) this._asts[0];  // asts of ASTSlice => AST[]{hex, rows, cols}

      // push the slice onto the stack
      lhs_slice._asts[0].treeWalk(e);   // push hex
      lhs_slice._asts[1].treeWalk(e);   // push rows
      lhs_slice._asts[2].treeWalk(e);   // push cols

      // Case C: Simple case where we have a single row and a single column
      if (e.isNum() && e.peekTypeAt(-1) == Env.NUM) {
        int col = (int) e.popDbl();
        long row = (long) e.popDbl();
        Frame ary = e.pop0Ary();
        if (Math.abs(row) > ary.numRows())
          throw new IllegalArgumentException("New rows would leave holes after existing rows.");
        if (Math.abs(col) > ary.numCols())
          throw new IllegalArgumentException("New columns would leave holes after existing columns.");
        if (row < 0 && Math.abs(row) > ary.numRows()) throw new IllegalArgumentException("Cannot extend rows.");
        if (col < 0 && Math.abs(col) > ary.numCols()) throw new IllegalArgumentException("Cannot extend columns.");
        if (e.isNum()) {
          double d = e.popDbl();
          if (ary.vecs()[col].isEnum()) throw new IllegalArgumentException("Currently can only set numeric columns");
          ary.vecs()[col].set(row, d);
          e.push0(new ValFrame(ary));
          return;
        } else if (e.isStr()) {
          if (!ary.vecs()[col].isEnum())
            throw new IllegalArgumentException("Currently can only set categorical columns.");
          String s = e.popStr();
          String[] dom = ary.vecs()[col].domain();
          if (in(s, dom)) ary.vecs()[col].set(row, Arrays.asList(dom).indexOf(s));
          else ary.vecs()[col].set(row, Double.NaN);
          e.push0(new ValFrame(ary));
          return;
        } else
          throw new IllegalArgumentException("Did not get a single number or factor level on the RHS of the assignment.");
      }

      // Get the LHS slicing rows/cols. This is a more complex case than the simple 1x1 re-assign
      Val colSelect = e.pop();
      int rows_type = e.peekType();
      Val rowSelect = rows_type == Env.ARY ? e.pop0() : e.pop();

      Frame lhs_ary = e.peekAry(); // Now the stack looks like [ ..., RHS, LHS_FRAME]
      Object cols = ASTSlice.select(lhs_ary.numCols(), colSelect, e, true);
      Object rows = ASTSlice.select(lhs_ary.numRows(), rowSelect, e, false);
      lhs_ary = e.pop0Ary(); // Now the stack looks like [ ..., RHS]

      long[] cs1;
      long[] rs1;

      // Repeat of case C with a single col and row specified, but possibly packaged into ASTSeries objects.
      if (cols != null && rows != null
              && (cols instanceof long[]) && (rows instanceof long[])
              && (cs1 = (long[]) cols).length == 1 && (rs1 = (long[]) rows).length == 1) {
        long row = rs1[0];
        int col = (int) cs1[0];
        if (Math.abs(row) > lhs_ary.numRows())
          throw new IllegalArgumentException("New rows would leave holes after existing rows.");
        if (Math.abs(col) > lhs_ary.numCols())
          throw new IllegalArgumentException("New columnss would leave holes after existing columns.");
        if (row < 0 && Math.abs(row) > lhs_ary.numRows()) throw new IllegalArgumentException("Cannot extend rows.");
        if (col < 0 && Math.abs(col) > lhs_ary.numCols()) throw new IllegalArgumentException("Cannot extend columns.");
        if (e.isNum()) {
          if (lhs_ary.vecs()[col].isEnum())
            throw new IllegalArgumentException("Currently can only set numeric columns");
          lhs_ary.vecs()[col].set(row, e.popDbl());
          e.push0(new ValFrame(lhs_ary));
          return;
        } else if (e.isStr()) {
          if (!lhs_ary.vecs()[col].isEnum()) throw new IllegalArgumentException("Currently can only set categorical columns.");
          String s = e.popStr();
          String[] dom = lhs_ary.vecs()[col].domain();
          if (in(s, dom)) lhs_ary.vecs()[col].set(row, Arrays.asList(dom).indexOf(s));
          else lhs_ary.vecs()[col].set(row, Double.NaN);
          e.push0(new ValFrame(lhs_ary));
          return;
        } else throw new IllegalArgumentException("Did not get a single number or factor level on the RHS of the assignment.");
      }

      // Partial row assignment? Cases B and C
      if (rows != null) {

        // Only have partial row assignment, Case B
        if (cols == null) {
          assignRows(e, rows, lhs_ary, null);

          // Have partial row and col assignment? Case C
        } else {
          assignRows(e, rows, lhs_ary, cols);
        }

      // Case A, just cols
      } else if (cols != null) {
        Frame rhs_ary;
        // convert constant into a whole vec
        if (e.isNum()) rhs_ary = new Frame(lhs_ary.anyVec().makeCon(e.popDbl()));
        else if (e.isStr()) rhs_ary = new Frame(lhs_ary.anyVec().makeZero(new String[]{e.popStr()}));
        else if (e.isAry()) rhs_ary = e.pop0Ary();
        else throw new IllegalArgumentException("Bad RHS on the stack: " + e.peekType() + " : " + e.toString());

        long[] cs = (long[]) cols;
        if (rhs_ary.numCols() != 1 && rhs_ary.numCols() != cs.length)
          throw new IllegalArgumentException("Can only assign to a matching set of columns; trying to assign " + rhs_ary.numCols() + " cols over " + cs.length + " cols");

        // Replace the LHS cols with the RHS cols
        Vec rvecs[] = rhs_ary.vecs();
        Futures fs = new Futures();
        for (int i = 0; i < cs.length; i++) {
          boolean subit=true;
          int cidx = (int) cs[i];
          Vec rv = rvecs[rvecs.length == 1 ? 0 : i];
          e.addVec(rv);
          if (cidx == lhs_ary.numCols()) {
            if (!rv.group().equals(lhs_ary.anyVec().group())) {
              subit=false;
              e.subVec(rv);
              rv = lhs_ary.anyVec().align(rv);
              e.addVec(rv);
            }
            lhs_ary.add("C" + String.valueOf(cidx + 1), rv);     // New column name created with 1-based index
          } else {
            if (!(rv.group().equals(lhs_ary.anyVec().group())) && rv.length() == lhs_ary.anyVec().length()) {
              subit=false;
              e.subVec(rv);
              rv = lhs_ary.anyVec().align(rv);
              e.addVec(rv);
            }
            lhs_ary.replace(cidx, rv); // returns the new vec, but we don't care... (what happens to the old vec?)
          }
        }
        fs.blockForPending();
//        e.cleanup(rhs_ary);
        e.push0(new ValFrame(lhs_ary));
        return;
      } else throw new IllegalArgumentException("Invalid row/col selections.");
    }
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

// AST Slice
class ASTSlice extends AST {
  ASTSlice() {}

  ASTSlice parse_impl(Exec E) {
    AST hex = E.parse();
    AST rows = E.skipWS().parse();
    if (rows instanceof ASTString) rows = new ASTNull();
    if (rows instanceof ASTSpan) ((ASTSpan) rows).setSlice(true, false);
    if (rows instanceof ASTSeries) ((ASTSeries) rows).setSlice(true, false);
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    AST cols = E.skipWS().parse();
    cols = (cols instanceof ASTString && cols.value().equals("null")) ? new ASTNull() : cols;
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
    Val cols = env.pop();    int rows_type = env.peekType();
    Val rows = rows_type == Env.ARY ? env.pop0() : env.pop();
    if( cols_type == Env.STR ) {
      Frame ary = env.peekAry();
      int idx = ary.find(((ValStr)cols)._s);
      if( idx == -1 ) throw new IllegalArgumentException("Column name not in frame, "+cols);
      cols = new ValNum(idx);  cols_type = Env.NUM;
    }

    // Scalar load?  Throws AIIOOB if out-of-bounds
    if(cols_type == Env.NUM && rows_type == Env.NUM) {
      // Known that rows & cols are simple positive constants.
      // Use them directly, throwing a runtime error if OOB.
      long row = (long)((ValNum)rows)._d;
      int  col = (int )((ValNum)cols)._d;
      Frame ary=env.popAry();
      try {
        if (ary.vecs()[col].isEnum()) {
          env.push(new ValStr(ary.vecs()[col].domain()[(int) ary.vecs()[col].at(row)]));
        } else env.push(new ValNum(ary.vecs()[col].at(row)));
      } catch (ArrayIndexOutOfBoundsException e) {
        if (col < 0 || col >= ary.vecs().length) throw new IllegalArgumentException("Column index out of bounds: tried to select column 0<="+col+"<="+(ary.vecs().length-1)+".");
        if (row < 0 || row >= ary.vecs()[col].length()) throw new IllegalArgumentException("Row index out of bounds: tried to select row 0<="+row+"<="+(ary.vecs()[col].length()-1)+".");
      }
      env.cleanup(ary);
    } else {
      // Else It's A Big Copy.  Some Day look at proper memory sharing,
      // disallowing unless an active-temp is available, etc.
      // Eval cols before rows (R's eval order).
      Frame ary= env.peekAry(); // Get without popping
      Object colSelect = select(ary.numCols(), cols, env, true);
      Object rowSelect = select(ary.numRows(),rows,env, false);
      Frame fr2 = ary.deepSlice(rowSelect,colSelect);
      if (colSelect instanceof Frame) for (Vec v : ((Frame)colSelect).vecs()) Keyed.remove(v._key);
      if (rowSelect instanceof Frame) for (Vec v : ((Frame)rowSelect).vecs()) Keyed.remove(v._key);
      if( fr2 == null ) fr2 = new Frame(); // Replace the null frame with the zero-column frame
      env.cleanup(ary, env.pop0Ary(), rows_type == Env.ARY ? ((ValFrame)rows)._fr : null);
      env.push(new ValFrame(fr2));
    }
  }

  // Execute a col/row selection & return the selection.  NULL means "all".
  // Error to mix negatives & positive.  Negative list is sorted, with dups
  // removed.  Positive list can have dups (which replicates cols) and is
  // ordered.  numbers.  1-based numbering; 0 is ignored & removed.
  static Object select( long len, Val v, Env env, boolean isCol) {
    if( v.type() == Env.NULL ) return null; // Trivial "all"
    env.push(v);
    long cols[];
    if( env.isNum()) {
      int col = (int)env.popDbl(); // Pop double; Silent truncation (R semantics)
      if( col < 0 && col < -len ) col=0; // Ignore a non-existent column
      if (col < 0) {
        ValSeries s = new ValSeries(new long[]{col}, null);
        s.setSlice(!isCol, isCol);
        return select(len, s, env, isCol);
      }
      return new long[]{col};
    }
    if (env.isSeries()) {
      ValSeries a = env.popSeries();
      if (!a.isValid()) throw new IllegalArgumentException("Cannot mix negative and positive array selection.");
      // if selecting out columns, build a long[] cols and return that.
      if (a.isColSelector()) return a.toArray();

      // Check the case where we have c(1), e.g., a series of a single digit...
      if (a.isNum() && !a.all_neg()) return select(len, new ValNum(a.toNum()), env, isCol);

      // Otherwise, we have rows selected: Construct a compatible "predicate" vec
      Frame ary = env.peekAry();
      Vec v0 = a.all_neg() ? ary.anyVec().makeCon(1) : ary.anyVec().makeZero();
      final ValSeries a0 = a;

      Frame fr = a0.all_neg()
        ? new MRTask() {
            @Override public void map(Chunk cs) {
              for (long i = cs.start(); i < cs._len + cs.start(); ++i)
                if (a0.contains(-i)) cs.set0((int) (i - cs.start() - 1), 0); // -1 for indexing
            }
          }.doAll(v0).getResult()._fr
        : new MRTask() {
            @Override public void map(Chunk cs) {
              for (long i = cs.start(); i < cs._len + cs.start(); ++i)
                if (a0.contains(i)) cs.set0( (int)(i - cs.start()),i+1);
            }
          }.doAll(v0).getResult()._fr;
      return fr;
    }
    if (env.isSpan()) {
      ValSpan a = env.popSpan();
      if (!a.isValid()) throw new IllegalArgumentException("Cannot mix negative and positive array selection.");
      // if selecting out columns, build a long[] cols and return that.
      if (a.isColSelector()) return a.toArray();

      // Otherwise, we have rows selected: Construct a compatible "predicate" vec
      Frame ary = env.peekAry();
      final ValSpan a0 = a;
      Vec v0 = a.all_neg() ? ary.anyVec().makeCon(1) : ary.anyVec().makeZero();
      Frame fr = a0.all_neg()
        ? new MRTask() {
            @Override public void map(Chunk cs) {
              for (long i = cs.start(); i < cs._len + cs.start(); ++i)
                if (a0.contains(-i)) cs.set0((int) (i - cs.start() - 1), 0); // -1 for indexing
            }
          }.doAll(v0).getResult()._fr
        : new MRTask() {
            @Override public void map(Chunk cs) {
              for (long i = cs.start(); i < cs._len + cs.start(); ++i)
                if (a0.contains(i)) cs.set0( (int)(i - cs.start()),i+1);
              }
          }.doAll(v0).getResult()._fr;
      return fr;
    }
    // Got a frame/list of results.
    // Decide if we're a toss-out or toss-in list
    Frame ary = env.pop0Ary();  // get it off the stack!!!!
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
    if(  _asts[2]==null ) indent(sb,d+1).append("all\n");
    else _asts[2].toString(sb,d+1).append("\n");
    if(  _asts[1]==null ) indent(sb,d+1).append("all");
    else _asts[1].toString(sb,d+1);
    return sb;
  }
}

//-----------------------------------------------------------------------------
class ASTDelete extends AST {
  ASTDelete parse_impl(Exec E) {
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    AST cols = E.skipWS().parse();
    ASTDelete res = (ASTDelete) clone();
    res._asts = new AST[]{ary,cols};
    return res;
  }
  @Override String value() { return null; }
  @Override int type() { return 0; }
  @Override public String toString() { return "(del)"; }
  @Override void exec(Env env) {
    // stack looks like:  [....,hex,cols]
    Frame  ary = ((ASTFrame )_asts[0])._fr;
    String col = ((ASTString)_asts[1])._s;
    Vec vec = ary.remove(col);
    vec.remove();
    DKV.put(ary);
    env.push(new ValFrame(ary));
  }
}
