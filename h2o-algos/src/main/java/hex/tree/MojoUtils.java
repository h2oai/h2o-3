package hex.tree;

import hex.genmodel.algos.tree.SharedTreeMojoModel;

public abstract class MojoUtils {

    public static CompressedTree[][] extractCompressedTrees(SharedTreeMojoModel mojo) {
        final int ntrees = mojo.getNTreeGroups();
        final int ntreesPerGroup = mojo.getNTreesPerGroup();
        final int nclasses = mojo.nclasses();
        CompressedTree[][] trees = new CompressedTree[ntrees][];
        for (int t = 0; t < ntrees; t++) {
            CompressedTree[] tc = new CompressedTree[nclasses];
            for (int c = 0; c < ntreesPerGroup; c++) {
                tc[c] = new CompressedTree(mojo.treeBytes(t, c), -1L, t, c);
            }
            trees[t] = tc;
        }
        return trees;
    }

    public static boolean isUsingBinomialOpt(SharedTreeMojoModel mojo, CompressedTree[][] trees) {
        if (mojo.nclasses() != 2) {
            return false;
        }
        for (CompressedTree[] group : trees) {
            if (group.length != 2 || group[1] != null)
                return false;
        }
        return true;
    }

}
