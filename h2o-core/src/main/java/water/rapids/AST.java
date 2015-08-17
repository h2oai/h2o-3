package water.rapids;

import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.ValueString;
import water.util.IcedInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 *   Each node in the syntax tree knows how to parse a piece of text from the passed tree.
 */
abstract public class AST extends Iced {
  String[] _arg_names;
  AST[] _asts;
  AST parse_impl(Exec e) { throw H2O.fail("Missing parse_impl for "+this.getClass()); }
  abstract String opStr();
  abstract void exec(Env e);
  abstract String value();
  abstract int type();
  abstract AST make();
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
      } else if (this instanceof ASTLs || this instanceof ASTSetTimeZone || this instanceof ASTListTimeZones || this instanceof ASTGetTimeZone || this instanceof ASTStoreSize) {
        ((ASTOp) this).apply(e);
      } else if (this instanceof ASTFunc) {
        ((ASTFunc) this).apply(e);
      } else if (this instanceof ASTApply) {
        _asts[0].treeWalk(e);  // push the frame we're `apply`ing over
        ((ASTApply) this).apply(e);
      } else if(this instanceof ASTMMult) {
        _asts[1].treeWalk(e);
        _asts[0].treeWalk(e);
        ((ASTMMult)this).apply(e);
      } else if(this instanceof ASTTranspose) {
        _asts[0].treeWalk(e);
        ((ASTTranspose)this).apply(e);
      } else if (this instanceof ASTddply) {
        _asts[0].treeWalk(e);
        ((ASTddply)this).apply(e);
      } else if (this instanceof ASTMerge) {
        _asts[1].treeWalk(e);
        _asts[0].treeWalk(e);
        ((ASTMerge)this).apply(e);
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
        throw new H2OIllegalArgumentException("Got a bad identifier: '" + id.value() + "'. It has no type '!' or '$'.",
                "Got a bad identifier: '" + id.value() + "'. It has no type '!' or '$'." + " AST: " + this);
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
            this instanceof ASTFrame || this._asts[0] instanceof ASTFrame ||
            this instanceof ASTDelete )
      { this.exec(e); }

    else { throw H2O.fail("Unknown AST class: " + this.getClass());}
    return e;
  }

  protected StringBuilder indent( StringBuilder sb, int d ) {
    for( int i=0; i<d; i++ ) sb.append("  ");
    return sb.append(' ');
  }

  StringBuilder toString( StringBuilder sb, int d ) { return indent(sb,d).append(this); }
}

class ASTId extends AST {
  String opStr() {
    switch( _type ) {
      case '$': return "$";
      case '!': return "!";
      case '&': return "&";
      default:
        throw new IllegalArgumentException("No such type for ID: " + _type);
    }
  }
  final String _id;
  final char _type; // either '$' or '!' or '&'
  ASTId(char type, String id) { _type = type; _id = id; }
  AST parse_impl(Exec E) {
    String id = E.isQuoted(E.peek()) ? E.parseString(E.getQuote()) : E.parseID(); // allows for quoted ID here...
    AST ast = Env.staticLookup(new ASTId(_type, id));
    return ast;
  }
  @Override public String toString() { return _type+_id; }
  @Override void exec(Env e) { e.push(new ValId(_type, _id)); } // should this be H2O.fail() ??
  @Override int type() { return Env.ID; }
  @Override String value() { return _id; }
  boolean isLocalSet() { return _type == '&'; }
  boolean isGlobalSet() { return _type == '!'; }
  boolean isLookup() { return _type == '%'; }
  boolean isValid() { return isLocalSet() || isGlobalSet() || isLookup(); }
  @Override ASTId make() { return new ASTId(_type, ""); }
}

class ASTKey extends AST {
  String opStr() {throw H2O.unimpl("No such opStr for ASTKey."); }
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
  @Override ASTKey make() { return new ASTKey(""); }
}

class ASTFrame extends AST {
  String opStr() {return "%";}
  final String _key;
  final Frame _fr;
  boolean isFrame;
  boolean _g;
  ASTFrame(Frame fr) { _key = fr._key == null ? null : fr._key.toString(); _fr = fr; }
  ASTFrame(String key) {
    Key k = Key.make(key);
    Keyed val = DKV.getGet(k);
    if (val == null) throw new H2OKeyNotFoundArgumentException(key);
    _key = key;
    _fr = (isFrame=(val instanceof Frame)) ? (Frame)val : new Frame(Key.make(), null, new Vec[]{(Vec)val});
    if( !isFrame && _fr._key!=null && DKV.get(_fr._key)==null ) { DKV.put(_fr._key,_fr); }
    _g = true;
  }
  @Override public String toString() { return "Frame with key " + _key + ". Frame: :" +_fr.toString(); }
  @Override void exec(Env e) {
    if (_key != null && DKV.get(_key)!=null ) e.lock(_fr); // add to list of things that cannot be DKV removed.
    else                                      e.put(_key,_fr,isFrame); // _key not in the DKV, have transient Frame
    if( !isFrame ) e._tmpFrames.add(_fr);
    e.push(new ValFrame(_fr, !isFrame, _g));
  }
  @Override int type () { return Env.ARY; }
  @Override String value() { return _key; }
  boolean isGlobal() { return _g; }
  @Override ASTFrame make() { throw H2O.unimpl(); }
}

class ASTNum extends AST {
  final double _d;
  ASTNum(double d) { _d = d; }
  String opStr() {return "#";}
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
  @Override ASTNum make() { return new ASTNum(0); }
}

/**
 *  ASTSpan parses phrases like 1:10.
 */
class ASTSpan extends AST {
  String opStr() { return ":"; }
  final long _min;       double _max;
  final ASTNum _ast_min; final ASTNum _ast_max;
  boolean _isCol; boolean _isRow;
  ASTSpan() {_min=0;_max=0;_ast_max=null;_ast_min=null;}
  ASTSpan(ASTNum min, ASTNum max) { _ast_min = min; _ast_max = max; _min = (long)min._d; _max = max._d;
    if( _min <= 0 && _max <= 0) {
      if (!Double.isNaN(_max) && _max > _min)
        throw new IllegalArgumentException("max>min: All negative, incorrect order.");
    } else {
        if (!Double.isNaN(_max) && _min > _max) throw new IllegalArgumentException("min > max: min <= max for `:` operator.");
    }
  }
  ASTSpan(long min, long max) { _ast_min = new ASTNum(min); _ast_max = new ASTNum(max); _min = min; _max = max;
    if( _min < 0 && _max < 0) {
      if (_max > _min)
        throw new IllegalArgumentException("max>min: All negative, incorrect order.");
    } else {
      if (_min > _max) throw new IllegalArgumentException("min > max: min <= max for `:` operator.");
    }
  }
  ASTSpan parse_impl(Exec E) {
    AST l = E.parse();
    AST r = E.parse();
    E.eatEnd(); // eat ending ')'
    return new ASTSpan((ASTNum)l, (ASTNum)r);
  }
  boolean contains(long a) {
    if (all_neg()) return _max <= a && a <= _min;
    return _min <= a && a <= _max;
  }
  boolean isColSelector() { return _isCol; }
  boolean isRowSelector() { return _isRow; }
  ASTSpan setSlice(boolean row, boolean col) { _isRow = row; _isCol = col; return this; }
  @Override void exec(Env e) { ValSpan v = new ValSpan(_ast_min, _ast_max); v.setSlice(_isRow, _isCol); e.push(v); }
  @Override String value() { return null; }
  @Override int type() { return Env.SPAN; }
  @Override public String toString() { return _min + ":" + _max; }
  long length() { return (long)_max - _min + 1; }

  long[] toArray() {
    long[] res = new long[(int)_max - (int)_min + 1];
    long min = _min;
    for (int i = 0; i < res.length; ++i) res[i] = min++;
    return res;
  }
  boolean all_neg() { return _min<0||_max<0; }
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
  @Override ASTSpan make() {return new ASTSpan(new ASTNum(0),new ASTNum(0)); }
}

class ASTSeries extends AST {
  String opStr() { return "{";}
  final long[] _idxs;
  final ASTSpan[] _spans;
  final double[] _d;
  boolean _isCol;
  boolean _isRow;
  int[] _order; // a sequence of 0s and 1s. 0 -> span; 1 -> index

  ASTSeries(long[] idxs, double[] d, ASTSpan[] spans) {
    _idxs=idxs; _d=d; _spans=spans;
  }
  ASTSeries(long[] idxs, ASTSpan[] spans) {
    _idxs = idxs; _d=null;
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

  ASTSeries setSlice(boolean row, boolean col) { _isRow = row; _isCol = col; return this; }

  @Override
  void exec(Env e) {
    ValSeries v = new ValSeries(_idxs, _d, _spans);
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
  @Override ASTSeries make() { return new ASTSeries(null, null); }
}

class ASTStatement extends AST {
  @Override ASTStatement make() { return new ASTStatement(); }
  String opStr() { return ","; }
  // must parse all statements: {(ast);(ast);(ast);...;(ast)}
  @Override ASTStatement parse_impl( Exec E ) {
    ArrayList<AST> ast_ary = new ArrayList<AST>();

    // an ASTStatement is an array of ASTs. May have ASTStatements within ASTStatements.
    while( !E.isEnd() )
      ast_ary.add(E.parse());

    E.eatEnd(); // eat the ending ')'

    ASTStatement res = (ASTStatement) clone();
    res._asts = ast_ary.toArray(new AST[ast_ary.size()]);
    return res;
  }
  @Override void exec(Env env) {
    ArrayList<Frame> cleanup = new ArrayList<>();
    if( _asts.length==0 ) { env.push(new ValNull()); return; }
    for( int i=0; i<_asts.length-1; i++ ) {
      if (_asts[i] instanceof ASTReturn) {
       _asts[i].treeWalk(env);
        return;
      }
      _asts[i].treeWalk(env);  // Execute the statements by walking the ast
      if( !(_asts[i+1] instanceof ASTDelete || _asts[i+1] instanceof ASTRemoveFrame) ) env.pop();
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
    AST stmnt = E.parse();
    E.eatEnd(); // eat the ending ')'
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
    AST pred = E.parse();
    ASTStatement statement = super.parse_impl(E);
    ArrayList<AST> ast_list = new ArrayList<>();
    for (int i = 0; i < statement._asts.length; ++i) {
      if (statement._asts[i] instanceof ASTElse) _else = (ASTElse)statement._asts[i];
      else ast_list.add(statement._asts[i]);
    }
    E.eatEnd(); // eat the ending ')'
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
    ASTStatement statements = super.parse_impl(E);
    E.eatEnd(); // eat the ending ')'
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
//  protected Object[] confusion_matrix;

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
  @Override ASTString make() { return new ASTString(_eq,""); }
  String opStr() { return String.valueOf(_eq); }
  final String _s;
  final char _eq;
  ASTString(char eq, String s) { _eq = eq; _s = s; }
  AST parse_impl(Exec E) {
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    ASTString as = new ASTString(_eq, E.parseString(_eq));
    return Env.staticLookup(as);
  }
  @Override public String toString() { return _s; }
  @Override void exec(Env e) { e.push(new ValStr(_s)); }
  @Override int type () { return Env.STR; }
  @Override String value() { return _s; }
}

class ASTNull extends AST {
  @Override ASTNull make() { return new ASTNull(); }
  String opStr() { throw H2O.unimpl();}
  ASTNull() {}
  ASTNull parse_impl(Exec E) { return this; }
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
  @Override ASTAssign make() { return new ASTAssign(); }
  String opStr() { return "="; }
  ASTAssign parse_impl(Exec E) {
    AST l;

    // LHS parsing
    // LHS can be one of six things:
    //   !ID, &ID, "ID" / 'ID', %ID, ID, or (...)
    // ! means do a dkv put -- essentially keep the result global for all the world
    // & means do a local put -- do not keep result around
    // " and ' mean parse and do local put (semantically an '&')
    // nothing on the ID implies & semantics
    // % on the LHS is semantically equivalent to & -- does not overwrite the global with the same ID
    // ( implies that the LHS is a new AST -- result locality depends on the AST
    if (E.isSpecial(E.peek())) {  // LHS is NOT an AST... no slice assignment
      boolean putkv = E.peek() == '!';
      E._x++; // skip the special char...
      boolean skip1 = (E.peek() == '\'' || E.peek() == '\"');  // skip one more at the end if quoted ID...
      if( skip1 ) E._x++; // skip beginning quote
      l = new ASTId(putkv ? '!' : '&', E.parseID()); // parse the ID on the left, or could be a column, or entire frame, or a row
      if( skip1 ) E._x++; // skip ending quote
    } else {
      if( E.peek() == '(' ) l = E.parse();  // thing on the LHS is an AST => slot assign [<-
      else l = new ASTId('&', E.parseID()); // else got plain old ID, assume local put
    }

    // RHS parsing
    if (!E.hasNext()) throw new IllegalArgumentException("Missing RHS in ASTAssign.");
    AST r = E.parse();   // parse double, String, or Frame on the right

    E.eatEnd(); // eat ending ')'

    // clone and return
    ASTAssign res = (ASTAssign)clone();
    res._asts = new AST[]{l,r};
    return res;
  }

  @Override int type () { return -1; }
  @Override String value() { throw H2O.unimpl("No value() for ASTAssign."); }
  private static boolean in(String s, String[] matches) { return Arrays.asList(matches).contains(s); }

  private static void replaceRow(Chunk[] chks, int row, double d0, String s0, long[] cols) {
    for (int c = 0; c < cols.length; ++c) {
      int col = (int)cols[c];
      // have an enum column
      if (chks[col].vec().isEnum()) {
        if (s0 == null) { chks[col].setNA(row); continue; }
        String[] dom = chks[col].vec().domain();
        if (in(s0, dom)) { chks[col].set(row, Arrays.asList(dom).indexOf(s0)); continue; }
        chks[col].setNA(row); continue;

        // have a numeric column
      } else if (chks[col].vec().isNumeric()) {
        if (Double.isNaN(d0) || s0 != null) { chks[col].setNA(row); continue; }
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
          ary.vecs()[col].set(row_id, cs[c].atd(row0));
        } else {
          ary.vecs()[col].set(row_id, Double.NaN);
        }
      }

      // got numeric trying to set into something non-numeric -> NA
      // got numeric trying to set into numeric
      if (cs[c].vec().isNumeric()) {
        if (ary.vecs()[col].isNumeric()) {
          ary.vecs()[col].set(row_id, cs[c].atd(row0));
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
      else if (e.isNul()) d = Double.NaN;
      else throw new IllegalArgumentException("Did not get a single number or factor level on the RHS of the assignment. Got type #:" + Env.typeToString(e.peekType()));
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
        e.push(new ValFrame(lhs_ary));
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
            for (int r = 0; r < rows; ++r) if (pred.atd(r) != 0) replaceRow(cs, r, d0, s0, cols0);
          }
        }.doAll(rr);
        e.push(new ValFrame(lhs_ary));
        return;
      } else throw new IllegalArgumentException("Invalid row selection. (note: RHS was a constant)");

      // If the rhs is an array, then fail if `height` of the rhs != rows.length. Otherwise, fetch-n-fill! (expensive)
    } else {
      // RHS shape must match LHS shape...
      // Example: hex[1:50,] <- hex2[90:100,] will fail, but swap out 90:100 w/ 101:150 should pass
      // LHS.numCols() == RHS.numCols()
      // mismatch in types results in NA

      final Frame rhs_ary = e.popAry();
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
                    if (!rhs_ary.vecs()[col].isEnum()) { chks[col].setNA(row); continue; }
                    // else vec is numeric
                  else if (chks[col].vec().isNumeric())
                    if (!rhs_ary.vecs()[col].isNumeric()) { chks[col].setNA(row); continue; }
                  chks[col].set(row, rhs_ary.vecs()[col].at(row_id));
                }
              }
            }
          }
        }.doAll(lhs_ary);
        e.push(new ValFrame(lhs_ary));
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
              double d = c.atd(r);
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
              long row_id = (long)pred.atd(r) - 1;
              replaceRow(cs, r, row_id, cols0, lhs_ary);
            }
          }
        }.doAll(rr);
        e.push(new ValFrame(lhs_ary));
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

      if( (e.isNum() || e.isStr()) && id.isLocalSet() ) {
        if( e.isNum() ) e.put(id.value(),Env.NUM,String.valueOf(e.popDbl()));
        else            e.put(id.value(),Env.STR,e.popStr());
        return;
      }

      // RHS is a frame
      if (e.isAry() || (id.isGlobalSet() && (e.isNum() || e.isStr()))) {
        Vec tVec=null;
        Frame f=null;
        if(e.isAry()) f = e.popAry();
        else if( e.isNum() ) f = new Frame(null, new String[]{"C1"}, new Vec[]{tVec=Vec.makeCon(e.popDbl(),1)});
        else if( e.isStr() ) {
          String s = e.popStr();
          Vec v;
          if( s.equals("TRUE") || s.equals("FALSE") ) {
            v = Vec.makeCon(s.equals("TRUE")?1:0,1);
            v.setDomain(new String[]{"FALSE","TRUE"});
          } else {
            v = Vec.makeCon(0,1);
            v.setDomain(new String[]{s});
          }
          tVec = v;
          f = new Frame(null, new String[]{"C1"}, new Vec[]{tVec});;
        }
        Key k = Key.make(id._id);
        Vec[] vecs = f.vecs();
        if( id.isGlobalSet() ) vecs = f.deepCopy(null).vecs(); // for non-blocking put, see ASTGPut
        Frame fr = new Frame(k, f.names(), vecs);

        // if global set, then dkv put this frame under the Key k
        if( id.isGlobalSet() ) {
          DKV.put(k, fr);
          e.lock(fr);
          if( tVec != null ) tVec.remove();
        } else {
          // not a global set, push into transient set of Frames in the SymbolTable...
          e.put(k.toString(),fr);
        }
        e.push(new ValFrame(fr, id.isGlobalSet()));
      }

    // The other three cases of assignment follow
    } else {

      // Peel apart a slice assignment
      AST x = _asts[0];
      while( x instanceof ASTAssign ) x = x._asts[1];
      ASTSlice lhs_slice = (ASTSlice) x;  // asts of ASTSlice => AST[]{hex, rows, cols}

      // push the slice onto the stack
      lhs_slice._asts[0].treeWalk(e);   // push hex
      lhs_slice._asts[1].treeWalk(e);   // push rows
      lhs_slice._asts[2].treeWalk(e);   // push cols

      // Case C: Simple case where we have a single row and a single column
      if (e.isNum() && e.peekTypeAt(-1) == Env.NUM) {
        int col = (int) e.popDbl();
        long row = (long) e.popDbl();
        Frame ary = e.popAry();
        if (Math.abs(row) > ary.numRows())
          throw new IllegalArgumentException("New rows would leave holes after existing rows.");
        if (Math.abs(col) > ary.numCols())
          throw new IllegalArgumentException("New columns would leave holes after existing columns.");
        if (row < 0 && Math.abs(row) > ary.numRows()) throw new IllegalArgumentException("Cannot extend rows.");
        if (col < 0 && Math.abs(col) > ary.numCols()) throw new IllegalArgumentException("Cannot extend columns.");
        if (e.isNum()) {
          double d = e.popDbl();
          if (ary.vecs()[col].isEnum()) ary.vecs()[col].set(row, Double.NaN);
          else ary.vecs()[col].set(row, d);
          if (ary._key != null && DKV.get(ary._key) != null) DKV.put(ary);
          e.push(new ValFrame(ary));
          return;

        } else if (e.isAry()) {
          Frame one_by_one_ary = e.popAry();
          if (one_by_one_ary.numCols() != 1 && one_by_one_ary.numRows() != 1)
            throw new IllegalArgumentException("Expected RHS to be a 1x1 (one row, one column). Got: " + one_by_one_ary.numRows() + " rows " + one_by_one_ary.numCols() + " cols.");
          Vec theVec = one_by_one_ary.anyVec();

          // RHS is enum
          if (theVec.isEnum()) {
            String s = theVec.domain()[(int)theVec.at(0)];
            String[] dom = ary.vecs()[col].domain();
            if (in(s, dom)) ary.vecs()[col].set(row, Arrays.asList(dom).indexOf(s));
            else ary.vecs()[col].set(row, Double.NaN);
            if (ary._key != null && DKV.get(ary._key) != null) DKV.put(ary);
            e.push(new ValFrame(ary));
            return;

            // LHS is enum but RHS is not
          } else if (ary.vecs()[col].isEnum()) {
            ary.vecs()[col].set(row, Double.NaN);
            if (ary._key != null && DKV.get(ary._key) != null) DKV.put(ary);
            e.push(new ValFrame(ary));
            return;

          // LHS and RHS are both numeric
          } else {
            double d = theVec.at(0);
            ary.vecs()[col].set(row, d);
            if (ary._key != null && DKV.get(ary._key) != null) DKV.put(ary);
            e.push(new ValFrame(ary));
            return;
          }
        } else if (e.isStr()) {
          if (!ary.vecs()[col].isEnum())
            throw new IllegalArgumentException("Currently can only set categorical columns.");
          String s = e.popStr();
          String[] dom = ary.vecs()[col].domain();
          if (in(s, dom)) ary.vecs()[col].set(row, Arrays.asList(dom).indexOf(s));
          else ary.vecs()[col].set(row, Double.NaN);
          if (ary._key != null && DKV.get(ary._key) != null) DKV.put(ary);
          e.push(new ValFrame(ary));
          return;
        } else
          throw new IllegalArgumentException("Did not get a single number or factor level on the RHS of the assignment.");
      }

      // Get the LHS slicing rows/cols. This is a more complex case than the simple 1x1 re-assign
      Val colSelect = e.pop();
      Val rowSelect = e.pop();

      Frame lhs_ary = e.peekAry(); // Now the stack looks like [ ..., RHS, LHS_FRAME]
      Object cols = ASTSlice.select(lhs_ary.numCols(), colSelect, e, true);
      Object rows = ASTSlice.select(lhs_ary.numRows(), rowSelect, e, false);
      lhs_ary = e.popAry(); // Now the stack looks like [ ..., RHS]

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
          e.pushAry(lhs_ary);
          if (lhs_ary._key != null && DKV.get(lhs_ary._key) != null) DKV.put(lhs_ary);
          return;
        } else if (e.isStr()) {
          if (!lhs_ary.vecs()[col].isEnum()) throw new IllegalArgumentException("Currently can only set categorical columns.");
          String s = e.popStr();
          String[] dom = lhs_ary.vecs()[col].domain();
          if (in(s, dom)) lhs_ary.vecs()[col].set(row, Arrays.asList(dom).indexOf(s));
          else lhs_ary.vecs()[col].set(row, Double.NaN);
          if (lhs_ary._key != null && DKV.get(lhs_ary._key) != null) DKV.put(lhs_ary);
          e.pushAry(lhs_ary);
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
        Key tKey=null;
        // convert constant into a whole vec
        if (e.isNum()) rhs_ary = new Frame(lhs_ary.anyVec().makeCon(e.popDbl()));
        else if (e.isStr()) rhs_ary = new Frame(lhs_ary.anyVec().makeZero(new String[]{e.popStr()}));
        else if (e.isAry()) rhs_ary = e.popAry(); //.deepCopy(null);
        else throw new IllegalArgumentException("Bad RHS on the stack: " + e.peekType() + " : " + e.toString());

        long[] cs = (long[]) cols;
        if (rhs_ary.numCols() != 1 && rhs_ary.numCols() != cs.length)
          throw new IllegalArgumentException("Can only assign to a matching set of columns; trying to assign " + rhs_ary.numCols() + " cols over " + cs.length + " cols");

        // Replace the LHS cols with the RHS cols
        Vec rvecs[] = rhs_ary.deepCopy((tKey=Key.make()).toString()).vecs();
        Futures fs = new Futures();
        for (int i = 0; i < cs.length; i++) {
          int cidx = (int) cs[i];
          Vec rv = rvecs[i];
          e.addRef(rv);
          if (cidx == lhs_ary.numCols()) {
            if (!rv.group().equals(lhs_ary.anyVec().group())) {
              Vec rvOld = rv;
              rv = lhs_ary.anyVec().align(rv);
              e.subRef(rvOld);
              e.addRef(rv);
            }
            lhs_ary.add("C" + String.valueOf(cidx + 1), rv);     // New column name created with 1-based index
          } else {
            if (!(rv.group().equals(lhs_ary.anyVec().group())) && rv.length() == lhs_ary.anyVec().length()) {
              Vec rvOld = rv;
              rv = lhs_ary.anyVec().align(rv); // creates a new vec
              e.subRef(rvOld); // should delete it now...
              e.addRef(rv);
            }
            Vec vv = lhs_ary.replace(cidx, rv);
            e._locked.remove(vv._key);
            e.subRef(vv);
          }
        }
        fs.blockForPending();
        if (lhs_ary._key != null && DKV.get(lhs_ary._key) != null) DKV.put(lhs_ary);
        e.pushAry(lhs_ary);
        if( tKey!=null ) DKV.remove(tKey);  // shallow remove of tKey
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
  @Override ASTSlice make() { return new ASTSlice(); }
  String opStr() { return "["; }
  ASTSlice() {}

  ASTSlice parse_impl(Exec E) {
    // ([ %fr rows cols)

    // parse the frame, could be an AST...
    AST fr = E.parse();

    // parse the rows
    // Five possibilties: AST, Vec/Frame, String, Span, Series
    // AST and Vec/Frame must produce single column of booleans.
    // String -- Automatically assume it's null (could also be "wakka wakka", or any string, but mapped automatically to null)
    //           null means "all"
    // Span   -- A span is a contiguous range of whole numbers specified like this (: #lo #hi)
    // List   -- A list...
    AST rows = E.parse();
    switch( rows.type() ) {
      case Env.STR:    rows = new ASTNull();                              break;
      case Env.SPAN:   rows = ((ASTSpan) rows).setSlice(true, false);     break;
      case Env.SERIES: rows = ((ASTSeries) rows).setSlice(true, false);   break;
      case Env.LIST:   rows = new ASTSeries(((ASTLongList)rows)._l,null,((ASTLongList)rows)._spans); ((ASTSeries)rows).setSlice(true,false); break;
      case Env.NULL:   rows = new ASTNull(); break;
      default: //pass thru
    }

    if (!E.hasNext())
      throw new IllegalArgumentException("Slice expected 3 arguments (frame, rows, cols), but got 2");

    // parse the cols
    AST cols = E.parse();
    if( !(cols instanceof ASTStringList) ) {
      switch( cols.type() ) {
        case Env.STR:    cols = cols.value().equals("null") ? new ASTNull() : cols; break;
        case Env.SPAN:   cols = ((ASTSpan) cols).setSlice(false, true);             break;
        case Env.SERIES: cols = ((ASTSeries) cols).setSlice(false, true);           break;
        case Env.LIST:
          if( cols instanceof ASTLongList ) cols = new ASTSeries(((ASTLongList)cols)._l,null,((ASTLongList)cols)._spans);
          else {
            double[] d = ((ASTDoubleList)cols)._d;
            long  [] l = new long[d.length];
            int i=0;
            for(double dd:d) l[i++]=(long)dd;
            cols = new ASTSeries(l,null,((ASTDoubleList) cols)._spans);
          }
          ((ASTSeries)cols).setSlice(false,true);
          break;
        case Env.NULL:   cols = new ASTNull(); break;
        default: // pass thru
      }
    }

    E.eatEnd(); // eat ending ')'

    ASTSlice res = (ASTSlice) clone();
    res._asts = new AST[]{fr,rows,cols};
    return res;
  }

  @Override String value() { return null; }
  @Override int type() { return 0; }

  // Read-only execution.  Assignment to the left-hand-side is handled by
  // ASTAssign.  Here we do copy-on-write when possible.
  @Override void exec(Env env) {

    // stack looks like:  [....,hex,rows,cols], so pop, pop !
    int cols_type = env.peekType();
    Val cols = env.pop();    int rows_type = env.peekType();
    Val rows = env.pop();

    if( cols_type == Env.LIST ) {
      assert cols instanceof ValStringList : "Expected ValStringList. Got: " + cols.getClass();
      String[] colnames = ((ValStringList)cols)._s;
      long[] colz = new long[colnames.length];
      for( int i=0;i<colz.length;++i) colz[i] = env.peekAry().find(colnames[i]);
      cols = new ValSeries(colz,null);
      ((ValSeries)cols).setSlice(false,true);
    }

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
      Frame ary;
      if( env.isNum() ) ary=new Frame(Vec.makeCon(env.popDbl(),1));
      else ary = env.popAry();
      try {
        if (ary.vecs()[col].isEnum()) {
          env.push(new ValStr(ary.vecs()[col].domain()[(int) ary.vecs()[col].at(row)]));
        } else {
          if(ary.vecs()[col].isString()) env.push(new ValStr(ary.vecs()[col].atStr(new ValueString(),row).toString()));
          else                           env.push(new ValNum(ary.vecs()[col].at(row)));
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        if( col < 0 ) {
          int rm_col = -1*col - 1;  // 1 -> 0 idx...
          // really want to do all columns BUT this one... so not a single scalar result => recurse
          long[] columns = new long[ary.numCols()-1];
          int v=0;
          for(int i=0;i<columns.length;++i) {
            if (i == rm_col) v++;
            columns[i] = v++;
          }
          ValSeries vs = new ValSeries(columns,null);
          vs.setSlice(false,true);  // make it a column selector...
          env.pushAry(ary);
          env.push(rows);
          env.push(vs);
          this.exec(env);
          return;
        }
        if (row < 0 || row >= ary.vecs()[col].length()) throw new IllegalArgumentException("Row index out of bounds: tried to select row 0<="+row+"<="+(ary.vecs()[col].length()-1)+".");
      }
    } else {
      // Else It's A Big Copy.  Some Day look at proper memory sharing,
      // disallowing unless an active-temp is available, etc.
      // Eval cols before rows (R's eval order).
      Frame fr2,ary = env.peekAry(); // Get without popping
      if (rows_type == Env.ARY) env.addRef(((ValFrame)rows)._fr);
      Object colSelect = select(ary.numCols(),cols, env, true);
      Object rowSelect = select(ary.numRows(),rows,env, false);
      if( rowSelect == null && (colSelect==null || colSelect instanceof long[]) ) {
        if( colSelect == null ) {
          fr2 = ary;            // All of it
        } else {
          long[] cols2 = (long[])colSelect;
          if( cols2.length > 0 && cols2[0] < 0 ) { // Dropping cols
            int[] idxs = new int[cols2.length];
            fr2 = new Frame(ary);
            for( int i=0; i<cols2.length; i++ )
              idxs[i]=(int)-cols2[i]-1;
            fr2.remove(idxs);
          } else {              // Else selecting positive cols
            fr2 = new Frame();
            for (long aCols2 : cols2)
              fr2.add(ary._names[(int) aCols2], ary.vec((int) aCols2));
          }
        }
      } else {
        fr2 = ary.deepSlice(rowSelect,colSelect);
        if (colSelect instanceof Frame && cols_type != Env.ARY) for (Vec v : ((Frame)colSelect).vecs()) Keyed.remove(v._key);
        if (rowSelect instanceof Frame && rows_type != Env.ARY) for (Vec v : ((Frame)rowSelect).vecs()) Keyed.remove(v._key);
        if( fr2 == null ) fr2 = new Frame(); // Replace the null frame with the zero-column frame
        if (rows_type == Env.ARY) env.subRef(((ValFrame)rows)._fr);
      }
      env.poppush(1, new ValFrame(fr2));
    }
  }

  // Execute a col/row selection & return the selection.  NULL means "all".
  // Error to mix negatives & positive.  Negative list is sorted, with dups
  // removed.  Positive list can have dups (which replicates cols) and is
  // ordered.  numbers.  1-based numbering; 0 is ignored & removed.
  static Object select( final long len, Val v, Env env, boolean isCol) {
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
                if (a0.contains(-i)) cs.set((int) (i - cs.start()), 0);
            }
          }.doAll(v0).getResult()._fr
        : new MRTask() {
            @Override public void map(Chunk cs) {
              for (long i = cs.start(); i < cs._len + cs.start(); ++i)
                if (a0.contains(i)) cs.set((int) (i - cs.start()), i + 1);
            }
          }.doAll(v0).getResult()._fr;
      return fr;
    }
    if (env.isSpan()) {
      ValSpan a = env.popSpan();
      if( Double.isNaN(a._max) || a._max > len) a._max=Math.max(0,len-1);
//      else a._max = Math.min(len,a._max-1);
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
                if (a0.contains(-i)) cs.set((int) (i - cs.start() - 1), 0); // -1 for indexing
            }
          }.doAll(v0).getResult()._fr
        : new MRTask() {
            @Override public void map(Chunk cs) {
              for (long i = cs.start(); i < cs._len + cs.start(); ++i)
                if (a0.contains(i)) cs.set((int) (i - cs.start()), i + 1);
              }
          }.doAll(v0).getResult()._fr;
      return fr;
    }
    // Got a frame/list of results.
    // Decide if we're a toss-out or toss-in list
    Frame ary = env.popAry();
    if( ary.numCols() != 1 ) throw new IllegalArgumentException("Selector must be a single column: "+AtoS(ary.names()));
    Vec vec = ary.anyVec();

    // got a frame as a column selector... it must be a boolean selector.
    if( isCol ) {
      if( vec.min() != 0 && vec.max() != 1 && !vec.isInt() )
        throw new IllegalArgumentException("Vec selector must be a single columns of 1s and 0s.");
      // passed in len is ncols of the frame to slice
      final ASTGroupBy.IcedNBHS<IcedInt> hs = new ASTGroupBy.IcedNBHS();
      // MRTask to fill cols in parallel
      new MRTask() {
        @Override public void map(Chunk c) {
          int start = (int)c.start();
          for( int i=0;i<c._len;++i ) {
            if( c.at8(i)==1 && len>(i+start) ) hs.add(new IcedInt(start+i));
          }
        }
      }.doAll(ary);
      cols = new long[(int)Math.min(hs.size(), len)];
      Iterator<IcedInt> it = hs.iterator();
      int j=0;
      while( j<cols.length && it.hasNext() )
        cols[j++] = it.next()._val;
    } else {

      // Check for a matching column of bools.
      if (ary.numRows() == len && vec.min() >= 0 && vec.max() <= 1 && vec.isInt())
        return ary;    // Boolean vector selection.
      // Convert single vector to a list of longs selecting rows
      if (ary.numRows() > 10000000)
        throw H2O.fail("Unimplemented: Cannot explicitly select > 10000000 rows in slice.");
      cols = MemoryManager.malloc8((int) ary.numRows());
      for (int i = 0; i < cols.length; ++i) {
        if (vec.isNA(i)) throw new IllegalArgumentException("Can not use NA as index!");
        cols[i] = vec.at8(i);
      }
    }
    return cols;
  }

  private static String AtoS(String[] s) {
    StringBuilder sb = new StringBuilder();
    for (String ss : s) sb.append(ss).append(',');
    return sb.toString();
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
  @Override ASTDelete make() { return new ASTDelete(); }
  String opStr() { return "del"; }
  ASTDelete parse_impl(Exec E) {
    AST ary = E.parse();
    AST cols = null;
    if( !E.isEnd() ) cols = E.parse();
    E.eatEnd(); // eat ending ')'
    ASTDelete res = (ASTDelete) clone();
    res._asts = new AST[]{ary,cols};
    return res;
  }
  @Override String value() { return null; }
  @Override int type() { return 0; }
  @Override public String toString() { return "(del)"; }
  @Override void exec(Env env) {
    // stack looks like:  [....,hex,cols]
    _asts[0].exec(env);
    // Hard/deep delete of a Frame
    if( env.isAry() )  env.popAry().remove();
    else if( env.isStr() ) Keyed.remove(Key.make(env.popStr()));
    else throw H2O.unimpl(env.pop().getClass().toString());
  }
}


// typed lists
// lists look like this
// (list a1 a2 a3 ...)

abstract class ASTList extends AST {
  @Override void exec(Env e) { throw H2O.unimpl("Illegal: cannot do anything with " + getClass()); }
  @Override String value() { return null; }
  @Override int type() { return Env.LIST; }
  ASTSpan[] _spans;
  ArrayList<ASTSpan> spans;
  ASTList() { spans=new ArrayList<>(); }
}

class ASTAry extends ASTList {
  AST[] _a;
  @Override String opStr() { return "list"; }
  @Override ASTDoubleList make() { return new ASTDoubleList(); }
  ASTAry parse_impl(Exec E) {
    ArrayList<AST> asts = new ArrayList<>();
    while( !E.isEnd() ) asts.add(E.parse());
    E.eatEnd();
    _a = asts.toArray(new AST[asts.size()]);
    ASTAry res = (ASTAry) clone();
    res._a = _a;
    return res;
  }
}

class ASTDoubleList extends ASTList {
  ASTDoubleList() {super();}
  double[] _d;
  @Override String opStr() { return "dlist"; }
  @Override ASTDoubleList make() { return new ASTDoubleList(); }
  ASTDoubleList parse_impl(Exec E) {
    ArrayList<Double> dbls = new ArrayList<>();
    while( !E.isEnd() ) {
      AST a = E.parse();
      if( a instanceof ASTNum ) dbls.add(((ASTNum)a)._d);
      else if( a instanceof ASTSpan ) spans.add((ASTSpan)a);
    }
    E.eatEnd();
    _d = new double[dbls.size()];
    int i=0;
    for( double d:dbls ) _d[i++] = d;
    if( spans.size()!=0 ) _spans = spans.toArray(new ASTSpan[spans.size()]);

    ASTDoubleList res = (ASTDoubleList) clone();
    res._d = _d; //probably useless
    res._spans = _spans;
    return res;
  }
  @Override public Env treeWalk(Env e) { e.push(new ValDoubleList(_d,_spans)); return e; }
}

class ASTLongList extends ASTList {
  ASTLongList() {super();}
  long[] _l;
  @Override String opStr() { return "llist"; }
  @Override ASTLongList make() { return new ASTLongList(); }
  ASTLongList parse_impl(Exec E) {
    ArrayList<Long> longs = new ArrayList<>();
    while( !E.isEnd() ) {
      AST a = E.parse();
      if( a instanceof ASTNum ) longs.add((long)((ASTNum)a)._d);
      else if( a instanceof ASTSpan ) spans.add((ASTSpan)a);
    }
    E.eatEnd();
    _l = new long[longs.size()];
    int i=0;
    for( long l:longs ) _l[i++] = l;
    if( spans.size()!=0 ) _spans = spans.toArray(new ASTSpan[spans.size()]);

    ASTLongList res = (ASTLongList) clone();
    res._l = _l; //probably useless
    res._spans = _spans;
    return res;
  }
  @Override public Env treeWalk(Env e) { e.push(new ValLongList(_l,_spans)); return e; }
}

class ASTStringList extends ASTList {
  String[] _s;
  @Override String opStr() { return "slist"; }
  @Override ASTStringList make() { return new ASTStringList(); }
  ASTStringList parse_impl(Exec E) {
    ArrayList<String> strs = new ArrayList<>();
    while( !E.isEnd() ) {// read until we hit a ")"
      AST a = E.parse();
      if( a instanceof ASTNull ) strs.add(null);
      else if( a instanceof ASTString ) strs.add(((ASTString) a)._s);
      else if( a instanceof ASTFrame  ) strs.add(((ASTFrame)a)._key);  // got screwed by the st00pid aststring hack for keys w/ spaces
    }
    E.eatEnd();
    _s = new String[strs.size()];
    int i=0;
    for( String s:strs ) _s[i++] = s;

    ASTStringList res = (ASTStringList) clone();
    res._s = _s; //probably useless
    return res;
  }
  @Override public Env treeWalk(Env e) { e.push(new ValStringList(_s)); return e; }
}

class ASTShortList extends ASTList {
  short[] _s;
  @Override String opStr() { return "shortlist"; }
  @Override ASTShortList make() { return new ASTShortList(); }
  ASTShortList parse_impl(Exec E) {
    ArrayList<Short> shorts = new ArrayList<>();
    while( !E.isEnd() ) // read until we hit a ")"
      shorts.add((short)E.nextDbl());
    E.eatEnd();
    _s = new short[shorts.size()];
    int i=0;
    for( short s:shorts ) _s[i++] = s;

    ASTShortList res = (ASTShortList) clone();
    res._s = _s; //probably useless
    return res;
  }
}

class ASTFloatList extends ASTList {
  float[] _f;
  @Override String opStr() { return "flist"; }
  @Override ASTFloatList make() { return new ASTFloatList(); }
  ASTFloatList parse_impl(Exec E) {
    ArrayList<Float> flts = new ArrayList<>();
    while( !E.isEnd() ) // read until we hit a ")"
      flts.add((float)E.nextDbl());
    E.eatEnd();
    _f = new float[flts.size()];
    int i=0;
    for( float f:flts ) _f[i++] = f;

    ASTFloatList res = (ASTFloatList) clone();
    res._f = _f; //probably useless
    return res;
  }
}

class ASTIntList extends ASTList {
  int[] _i;
  @Override String opStr() { return "ilist"; }
  @Override ASTIntList make() { return new ASTIntList(); }
  ASTIntList parse_impl(Exec E) {
    ArrayList<Integer> ints = new ArrayList<>();
    while( !E.isEnd() ) // read until we hit a ")"
      ints.add((int)E.nextDbl());
    E.eatEnd();
    _i = new int[ints.size()];
    int j=0;
    for( int i:ints ) _i[j++] = i;

    ASTIntList res = (ASTIntList) clone();
    res._i = _i; //probably useless
    return res;
  }
}

class ASTBoolList extends ASTList {
  boolean[] _b;
  @Override String opStr() { return "blist"; }
  @Override ASTBoolList make() { return new ASTBoolList(); }
  ASTBoolList parse_impl(Exec E) {
    ArrayList<Boolean> bools = new ArrayList<>();
    while( !E.isEnd() ) // read until we hit a ")"
      bools.add((int)E.nextDbl()==1?true:false);
    E.eatEnd();
    _b = new boolean[bools.size()];
    int j=0;
    for( boolean b:bools ) _b[j++] = b;

    ASTBoolList res = (ASTBoolList) clone();
    res._b = _b; //probably useless
    return res;
  }
}

class ASTByteList extends ASTList {
  byte[] _b;
  @Override String opStr() { return "bytelist"; }
  @Override ASTByteList make() { return new ASTByteList(); }
  ASTByteList parse_impl(Exec E) {
    ArrayList<Byte> bytes = new ArrayList<>();
    while( !E.isEnd() ) // read until we hit a ")"
      bytes.add((byte)E.nextDbl());
    E.eatEnd();
    _b = new byte[bytes.size()];
    int j=0;
    for( byte b:bytes ) _b[j++] = b;

    ASTByteList res = (ASTByteList) clone();
    res._b = _b; //probably useless
    return res;
  }
}

class ASTCharList extends ASTList {
  char[] _c;
  @Override String opStr() { return "clist"; }
  @Override ASTCharList make() { return new ASTCharList(); }
  ASTCharList parse_impl(Exec E) {
    ArrayList<Character> chars = new ArrayList<>();
    while( !E.isEnd() ) // read until we hit a ")"
      chars.add(E.nextStr().charAt(0));
    E.eatEnd();
    _c = new char[chars.size()];
    int j=0;
    for( char c:chars ) _c[j++] = c;

    ASTCharList res = (ASTCharList) clone();
    res._c = _c; //probably useless
    return res;
  }
}
