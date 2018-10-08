package hex.tree;

import hex.genmodel.algos.tree.SharedTreeGraph;

public interface TreeBackedModel {

    SharedTreeGraph convert(final int treeNumber, final String treeClassName);
}
