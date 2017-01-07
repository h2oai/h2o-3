package hex.tree;

import water.util.IcedBitSet;
import water.util.SB;

/** A tree code generator producing Java code representation of the tree:
 *
 *  - A generated class contains score0 method
 *  - if score0 method is too long, it redirects prediction to a new subclass's score0 method
 */
class TreeJCodeGen extends TreeVisitor<RuntimeException> {
  public static final int MAX_NODES = (1 << 12) / 4; // limit for a number decision nodes visited per generated class
  //public static final int MAX_NODES = 5; // limit for a number decision nodes
  private static final int MAX_DEPTH = 70;

  private static final int MAX_CONSTANT_POOL_SIZE = (1 << 16) - 4096; // Keep some space for method and string constants
  private static final int MAX_METHOD_SIZE = (1 << 16) - 4096;
  // FIXME: the dataset gbm_test/30k_cattest.csv produces trees ~ 100 depth
  //
  // Simulate stack since we need to preserve each info per generated class
  final SB    _sbs []  = new SB   [MAX_DEPTH];
  final int   _nodesCnt[]= new int[MAX_DEPTH];
  final SB    _grpSplits[] = new SB[MAX_DEPTH];
  final int   _grpSplitsCnt[] = new int[MAX_DEPTH];
  final int   _constantPool[] = new int[MAX_DEPTH];
  final int   _staticInit[] = new int[MAX_DEPTH];

  final String _javaClassName;
  final SharedTreeModel _tm;
  SB _sb;
  SB _csb;
  SB _grpsplit;

  int _subtrees = 0;
  int _grpCnt = 0;
  int _constantPoolSize = 0;
  int _staticInitSize = 0;

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
    sb.ip("}").p(" // constant pool size = ").p(_constantPoolSize).p("B, number of visited nodes = ").p(_nodes).p(", static init size = ").p(_staticInitSize).p("B");
    sb.nl(); // close the method
    // Append actual group splits
    _sb.p(_grpsplit);
    sb.di(1).ip("}").nl().nl(); // close the class
  }

  @Override protected void pre(int col, float fcmp, IcedBitSet gcmp, int equal, int naSplitDirInt) {
    // Check for method size and number of constants generated in constant pool
    if (_nodes > MAX_NODES || _constantPoolSize > MAX_CONSTANT_POOL_SIZE || _staticInitSize > MAX_METHOD_SIZE ) {
      _sb.p(_javaClassName).p('_').p(_subtrees).p(".score0").p("(data)");
      _nodesCnt[_depth] = _nodes;
      _sbs[_depth] = _sb;
      _grpSplits[_depth] = _grpsplit;
      _grpSplitsCnt[_depth] = _grpCnt;
      _constantPool[_depth] = _constantPoolSize;
      _staticInit[_depth] = _staticInitSize;
      _sb = new SB();
      _nodes = 0;
      _grpsplit = new SB();
      _grpCnt = 0;
      _constantPoolSize = 0;
      _staticInitSize = 0;
      preamble(_sb, _subtrees);
      _subtrees++;
    }
    // Generates array for group splits
    if(equal == 2 || equal == 3 && gcmp != null) {
      _grpsplit.i(1).p("// ").p(gcmp.toString()).nl();
      _grpsplit.i(1).p("public static final byte[] GRPSPLIT").p(_grpCnt).p(" = new byte[] ").p(gcmp.toStrArray()).p(";").nl();
      _constantPoolSize += gcmp.numBytes() + 3; // Each byte stored in split (NOT TRUE) and field reference and field name (Utf8) and NameAndType
      _staticInitSize += 6 + gcmp.numBytes() * 6; // byte size of instructions to create an array and load all byte values (upper bound = dup, bipush, bipush, bastore = 5bytes)
    }
    // Generates decision
    _sb.ip(" (");

    // Generate column names only if necessary
    String colName = _verboseCode ? " /* " + _tm._output._names[col] + " */" : "";

    if(equal == 0 || equal == 1) {
      String[][] domains = _tm._output._domains;
      int limit = (domains != null && domains[col] != null) ? domains[col].length : Integer.MAX_VALUE;

      if (naSplitDirInt == DhnasdNaVsRest) {
        _sb.p("!Double.isNaN(data[").p(col).p("])");
        if (limit != Integer.MAX_VALUE)
          _sb.p(" && (data[").p(col).p("] < " + limit + ") ");
      }
      else if (naSplitDirInt == DhnasdNaLeft || naSplitDirInt == DhnasdLeft) {
        _sb.p("Double.isNaN(data[").p(col).p("]) ");
        if (limit != Integer.MAX_VALUE)
          _sb.p("|| (data[").p(col).p("] >= " + limit + ") ");
        _sb.p("|| ");
      }
      else if (equal==1) {
        _sb.p("!Double.isNaN(data[").p(col).p("]) && ");
        if (limit != Integer.MAX_VALUE)
          _sb.p("(data[").p(col).p("] < " + limit + ") && ");
      }
      if (naSplitDirInt != DhnasdNaVsRest) {
        _sb.p("data[").p(col);
        _sb.p(colName);
        _sb.p("] ").p(equal == 1 ? "!= " : "<").pj(fcmp); // then left and then right (left is !=)
        _constantPoolSize += 2; // * bytes for generated float which is represented as double because of cast (Double occupies 2 slots in constant pool)
      }
    } else {
      boolean naVsRest = naSplitDirInt == DhnasdNaVsRest;
      boolean leftward = naSplitDirInt == DhnasdNaLeft || naSplitDirInt == DhnasdLeft;
      if (naVsRest) {
        _sb.p("!Double.isNaN(data[").p(col).p(colName).p("]) && "); //no need to store group split, all we need to know is NA or not (and in range)
        gcmp.toJavaRangeCheck(_sb, "GRPSPLIT" + _grpCnt, col);
      }
      else {
        if (leftward) {
          _sb.p("Double.isNaN(data[").p(col).p(colName).p("]) || !"); //NAs (or out of range) go left
          gcmp.toJavaRangeCheck(_sb, "GRPSPLIT" + _grpCnt, col);
          _sb.p(" || ");
        } else {
          _sb.p("!Double.isNaN(data[").p(col).p(colName).p("]) && ");
        }
        _sb.p("(");
        gcmp.toJavaRangeCheck(_sb, "GRPSPLIT" + _grpCnt, col);
        _sb.p(" && ");
        gcmp.toJava(_sb, "GRPSPLIT" + _grpCnt, col);
        _sb.p(")");
      }
      _grpCnt++;
    }
    _sb.p(" ? ").ii(2).nl();
  }
  @Override protected void leaf( float pred  ) {
    _sb.i().pj(pred);
    // We are generating float which occupies single slot in constant pool, however
    // left side of final expression is double, hence javac directly stores double in constant pool (2places)
    _constantPoolSize += 2;
  }

  @Override
  protected void mid(int col, float fcmp, int equal) throws RuntimeException {
    _sb.p(" : ").nl();
  }

  @Override protected void post(int col, float fcmp, int equal ) {
    _sb.p(')').di(2);
    if (_sbs[_depth]!=null) { // Top of stack  - finalize the class generate into _sb
      closure(_sb);
      _csb.p(_sb);
      _sb = _sbs[_depth];
      _nodes = _nodesCnt[_depth];
      _sbs[_depth] = null;
      _grpsplit = _grpSplits[_depth];
      _grpCnt = _grpSplitsCnt[_depth];
      _grpSplits[_depth] = null;
      _constantPoolSize = _constantPool[_depth];
      _staticInitSize = _staticInit[_depth];
    }
  }
  public void generate() {
    preamble(_sb, _subtrees++);   // TODO: Need to pass along group split BitSet
    visit();
    closure(_sb);
    _sb.p(_csb);
  }
}

