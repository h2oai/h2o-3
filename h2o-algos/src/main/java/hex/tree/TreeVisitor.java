package hex.tree;

import water.AutoBuffer;
import water.util.IcedBitSet;


/** Abstract visitor class for serialized trees.*/
public abstract class TreeVisitor<T extends Exception> {
  protected static final int DhnasdNaVsRest = DHistogram.NASplitDir.NAvsREST.value();
  protected static final int DhnasdNaLeft = DHistogram.NASplitDir.NALeft.value();
  protected static final int DhnasdLeft = DHistogram.NASplitDir.Left.value();

  // Override these methods to get walker behavior.
  protected void pre (int col, float fcmp, IcedBitSet gcmp, int equal, int naSplitDirInt) throws T { }
  protected void mid ( int col, float fcmp, int equal ) throws T { }
  protected void post( int col, float fcmp, int equal ) throws T { }
  protected void leaf( float pred )                     throws T { }
  long  result( ) { return 0; } // Override to return simple results

  protected final CompressedTree _ct;
  private final AutoBuffer _ts;
  private final IcedBitSet _gcmp; // Large-count categorical bit-set splits
  protected int _depth; // actual depth
  protected int _nodes; // number of visited nodes
  public TreeVisitor( CompressedTree ct ) {
    _ts = new AutoBuffer((_ct=ct)._bits);
    _gcmp = new IcedBitSet(0);
  }

  // Call either the single-class leaf or the full-prediction leaf
  private void leaf2( int mask ) throws T {
    assert (mask==0 || ( (mask&16)==16 && (mask&32)==32) ) : "Unknown mask: " + mask;   // Is a leaf or a special leaf on the top of tree
    leaf(_ts.get4f());
  }

  public final void visit() throws T {
    int nodeType = _ts.get1();
    int col = _ts.get2();
    if( col==65535 ) { leaf2(nodeType); return; }
    int equal = (nodeType&12) >> 2;
    int naSplitDirInt = _ts.get1();

    float fcmp = -1;
    if (naSplitDirInt != DhnasdNaVsRest) {
      // Extract value or group to split on
      if (equal == 0 || equal == 1)
        fcmp = _ts.get4f();
      else {
        if (equal == 2) _gcmp.fill2(_ct._bits, _ts);
        else _gcmp.fill3(_ct._bits, _ts);
      }
    }
    // Compute the amount to skip.
    int lmask =  nodeType & 0x33;
    int rmask = (nodeType & 0xC0) >> 2;
    int skip = 0;
    switch(lmask) {
    case 0:  skip = _ts.get1();  break;
    case 1:  skip = _ts.get2();  break;
    case 2:  skip = _ts.get3();  break;
    case 3:  skip = _ts.get4();  break;
    case 48: skip =  4;  break; // skip is always 4 for direct leaves (see DecidedNode.size() and LeafNode.size() methods)
    default: assert false:"illegal lmask value " + lmask;
    }
    pre(col, fcmp, _gcmp, equal, naSplitDirInt); // Pre-walk
    _depth++;
    if( (lmask & 0x10)==16 ) leaf2(lmask);  else  visit();
    mid(col, fcmp, equal);   // Mid-walk
    if( (rmask & 0x10)==16 ) leaf2(rmask);  else  visit();
    _depth--;
    post(col, fcmp, equal);
    _nodes++;
  }
}

