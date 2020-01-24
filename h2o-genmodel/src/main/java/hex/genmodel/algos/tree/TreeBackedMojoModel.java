package hex.genmodel.algos.tree;

public interface TreeBackedMojoModel extends SharedTreeGraphConverter {

    int getNTreeGroups();

    int getNTreesPerGroup();

    double getInitF();

}
