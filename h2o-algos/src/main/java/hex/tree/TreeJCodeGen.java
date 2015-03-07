package hex.tree;

import java.util.Arrays;
import water.H2O;
import water.util.SB;
import water.util.IcedBitSet;

class TreeJCodeGen extends TreeVisitor<RuntimeException> {
  public static final int MAX_NODES = (1 << 12) / 4; // limit for a number decision nodes
  final byte  _bits[]  = new byte [100];
  final float _fs  []  = new float[100];
  final SB    _sbs []  = new SB   [100];
  final int   _nodesCnt[]= new int[100];
  final SharedTreeModel _tm;
  SB _sb;
  SB _csb;
  SB _grpsplit;

  int _subtrees = 0;
  int _grpcnt = 0;

  public TreeJCodeGen(SharedTreeModel tm, CompressedTree ct, SB sb) {
    super(ct);
    _tm = tm;
    _sb = sb;
    _csb = new SB();
    _grpsplit = new SB();
  }

  // code preamble
  protected void preamble(SB sb, int subtree) throws RuntimeException {
    String subt = subtree>0?String.valueOf(subtree):"";
    sb.ip("static final float score0").p(subt).p("(float[] data) {").nl().ii(1); // predict method for one tree
    sb.ip("float pred = ");
  }

  // close the code
  protected void closure(SB sb) throws RuntimeException {
    sb.p(";").nl();
    sb.ip("return pred;").nl().di(1);
    sb.ip("}").nl();
  }

  @Override protected void pre( int col, float fcmp, IcedBitSet gcmp, int equal ) {
    if(equal == 2 || equal == 3 && gcmp != null) {
      _grpsplit.i(1).p("// ").p(gcmp.toString()).nl();
      _grpsplit.i(1).p("public static final byte[] GRPSPLIT").p(_grpcnt).p(" = new byte[] ").p(gcmp.toStrArray()).p(";").nl();
    }

    if( _depth > 0 ) {
      int b = _bits[_depth-1];
      assert b > 0 : Arrays.toString(_bits)+"\n"+_sb.toString();
      if( b==1         ) _bits[_depth-1]=3;
      if( b==1 || b==2 ) _sb.p('\n').i(_depth).p("?");
      if( b==2         ) _sb.p(' ').pj(_fs[_depth-1]); // Dump the leaf containing float value
      if( b==2 || b==3 ) _sb.p('\n').i(_depth).p(":");
    }
    if (_nodes>MAX_NODES) {
      _sb.p("score0").p(_subtrees).p("(data)");
      _nodesCnt[_depth] = _nodes;
      _sbs[_depth] = _sb;
      _sb = new SB();
      _nodes = 0;
      preamble(_sb, _subtrees);
      _subtrees++;
    }
    _sb.p(" (");
    if(equal == 0 || equal == 1) {
      _sb.p("data[").p(col).p(" /* ").p(_tm._output._names[col]).p(" */").p("] ").p(equal == 1 ? "!= " : "<").pj(fcmp); // then left and then right (left is !=)
    } else {
      gcmp.toJava(_sb,"GRPSPLIT"+_grpcnt,col,_tm._output._names[col]);
      _grpcnt++;
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
    }
  }
  public void generate() {
    preamble(_sb, _subtrees++);   // TODO: Need to pass along group split BitSet
    visit();
    closure(_sb);
    _sb.p(_grpsplit);
    _sb.p(_csb);
  }
}
