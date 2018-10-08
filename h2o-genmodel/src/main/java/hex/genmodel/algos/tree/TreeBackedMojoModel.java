package hex.genmodel.algos.tree;

import hex.genmodel.MojoModel;

public interface TreeBackedMojoModel {

    SharedTreeGraph convert(final int treeNumber, final int treeClass);
}
