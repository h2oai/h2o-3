package hex.genmodel.algos.tree;

import hex.genmodel.MojoModel;

public interface TreeBackedMojoModel {

    SharedTreeGraph computeGraph(final int treeNumber, final int treeClass);
}
