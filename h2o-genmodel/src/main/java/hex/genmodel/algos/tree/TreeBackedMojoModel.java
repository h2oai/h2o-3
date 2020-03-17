package hex.genmodel.algos.tree;

public interface TreeBackedMojoModel extends SharedTreeGraphConverter {

    int getNTreeGroups();

    int getNTreesPerGroup();

    double getInitF();

    String[] getDecisionPath(final double[] row);

    SharedTreeMojoModel.LeafNodeAssignments getLeafNodeAssignments(final double[] row);

}
