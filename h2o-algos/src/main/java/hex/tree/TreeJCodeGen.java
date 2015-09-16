package hex.tree;

import java.util.Arrays;

import water.util.IcedBitSet;
import water.util.SB;

/** A tree code generator producing Java code representation of the tree:
 *
 *  - A generated class contains score0 method
 *  - if score0 method is too long,
 *  - too long methods are
 */
class TreeJCodeGen extends TreeVisitor<RuntimeException> {
  public static final int MAX_NODES = (1 << 12) / 4; // limit for a number decision nodes
  //public static final int MAX_NODES = 5; // limit for a number decision nodes
  private static final int MAX_DEPTH = 70;
  final byte  _bits[]  = new byte [MAX_DEPTH];
  final float _fs  []  = new float[MAX_DEPTH];
  final SB    _sbs []  = new SB   [MAX_DEPTH];
  final int   _nodesCnt[]= new int[MAX_DEPTH];
  final SB    _grpSplits[] = new SB[MAX_DEPTH];
  final int   _grpSplitsCnt[] = new int[MAX_DEPTH];
  final String _javaClassName;
  final SharedTreeModel _tm;
  SB _sb;
  SB _csb;
  SB _grpsplit;

  int _subtrees = 0;
  int _grpCnt = 0;

  final private boolean _verboseCode;

  public TreeJCodeGen(SharedTreeModel tm, CompressedTree ct, SB sb, String javaClassName, boolean verboseCode) {
    super(ct);
    _tm = tm;
    _sb = sb;
    _csb = new SB();
    _grpsplit = new SB();
    _verboseCode = verboseCode;
    _javaClassName = javaClassName;
  }

  // code preamble
  protected void preamble(SB sb, int subtree) throws RuntimeException {
    String subt = subtree > 0 ? "_" + String.valueOf(subtree) : "";
    sb.p("class ").p(_javaClassName).p(subt).p(" {").nl().ii(1);
    sb.ip("static final double score0").p("(double[] data) {").nl().ii(1); // predict method for one tree
    sb.ip("double pred = ");
  }

  // close the code
  protected void closure(SB sb) throws RuntimeException {
    sb.p(";").nl();
    sb.ip("return pred;").nl().di(1);
    sb.ip("}").nl(); // close the method
    // Append actual group splits
    _sb.p(_grpsplit);
    sb.di(1).ip("}").nl().nl(); // close the class
  }

  @Override protected void pre( int col, float fcmp, IcedBitSet gcmp, int equal ) {
    if( _depth > 0 ) {
      int b = _bits[_depth-1];
      assert b > 0 : Arrays.toString(_bits)+"\n"+_sb.toString();
      if( b==1         ) _bits[_depth-1]=3;
      if( b==1 || b==2 ) _sb.p('\n').i(_depth).p("?");
      if( b==2         ) _sb.p(' ').pj(_fs[_depth-1]); // Dump the leaf containing float value
      if( b==2 || b==3 ) _sb.p('\n').i(_depth).p(":");
    }
    // Switch to a new generator
    if (_nodes>MAX_NODES) {
      _sb.p(_javaClassName).p('_').p(_subtrees).p(".score0").p("(data)");
      _nodesCnt[_depth] = _nodes;
      _sbs[_depth] = _sb;
      _grpSplits[_depth] = _grpsplit;
      _grpSplitsCnt[_depth] = _grpCnt;
      _sb = new SB();
      _nodes = 0;
      _grpsplit = new SB();
      _grpCnt = 0;
      preamble(_sb, _subtrees);
      _subtrees++;
    }
    // Generates array for group splits
    if(equal == 2 || equal == 3 && gcmp != null) {
      _grpsplit.i(1).p("// ").p(gcmp.toString()).nl();
      _grpsplit.i(1).p("public static final byte[] GRPSPLIT").p(_grpCnt).p(" = new byte[] ").p(gcmp.toStrArray()).p(";").nl();
    }
    // Generates decision
    _sb.p(" (");
    if(equal == 0 || equal == 1) {
      _sb.p("data[").p(col);
      // Generate column names only if necessary
      if (_verboseCode) {
        _sb.p(" /* ").p(_tm._output._names[col]).p(" */");
      }
      _sb.p("] ").p(equal == 1 ? "!= " : "<").pj(fcmp); // then left and then right (left is !=)
    } else {
      gcmp.toJava(_sb, "GRPSPLIT"+_grpCnt,col,_tm._output._names[col]);
      _grpCnt++;
    }
    assert _bits[_depth]==0;
    _bits[_depth]=1;
  }
  @Override protected void leaf( float pred  ) {
    assert _depth==0 || _bits[_depth-1] > 0 : Arrays.toString(_bits); // it can be degenerated tree
    if( _depth==0) { // it is de-generated tree
      _sb.pj(pred);
    } else if( _bits[_depth-1] == 1 ) { // No prior leaf; just memorize this leaf
      _bits[_depth-1]=2; _fs[_depth-1]=pred;
    } else {          // Else==2 (prior leaf) or 3 (prior tree)
      if( _bits[_depth-1] == 2 ) _sb.p(" ? ").pj(_fs[_depth-1]).p(" ");
      else                       _sb.p('\n').i(_depth);
      _sb.p(": ").pj(pred);
    }
  }
  @Override protected void post( int col, float fcmp, int equal ) {
    _sb.p(')');
    _bits[_depth]=0;
    if (_sbs[_depth]!=null) {
      closure(_sb);
      _csb.p(_sb);
      _sb = _sbs[_depth];
      _nodes = _nodesCnt[_depth];
      _sbs[_depth] = null;
      _grpsplit = _grpSplits[_depth];
      _grpCnt = _grpSplitsCnt[_depth];
      _grpSplits[_depth] = null;
    }
  }
  public void generate() {
    preamble(_sb, _subtrees++);   // TODO: Need to pass along group split BitSet
    visit();
    closure(_sb);
    _sb.p(_csb);
    System.err.print(_csb.toString());
  }
}
