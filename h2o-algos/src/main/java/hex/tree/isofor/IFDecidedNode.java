package hex.tree.isofor;

import hex.tree.DHistogram;
import hex.tree.DTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IFDecidedNode extends DTree.DecidedNode {

  public IFDecidedNode(DTree.UndecidedNode n, DHistogram[] hs) {
    super(n, hs);
  }

  @Override
  public DTree.Split bestCol(DTree.UndecidedNode u, DHistogram hs[]) {
    if( hs == null ) return null;
    final int maxCols = u._scoreCols == null /* all cols */ ? hs.length : u._scoreCols.length;
    List<FindSplits> findSplits = new ArrayList<>();
    for (int i=0; i<maxCols; i++) {
      int col = u._scoreCols == null ? i : u._scoreCols[i];
      if( hs[col]==null || hs[col].nbins() <= 1 ) continue;
      findSplits.add(new FindSplits(hs, col, u));
    }
    Collections.shuffle(findSplits);
    for (FindSplits fs : findSplits) {
      DTree.Split s = fs.computeSplit();
      if (s != null) {
        return s;
      }
    }
    return null;
  }


}
