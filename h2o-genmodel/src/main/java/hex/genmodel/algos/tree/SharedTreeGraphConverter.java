package hex.genmodel.algos.tree;

/**
 * Implementors of this interface are able to convert internal tree representation to a shared representation.
 */
public interface SharedTreeGraphConverter {

    /**
     * Converts internal tree representation to a shared representation.
     *
     * @param treeNumber Number of the tree in the model to convert
     * @param treeClass  Tree's class. If not specified, all the classes form a forest in the resulting {@link SharedTreeGraph}
     * @return An instance of {@link SharedTreeGraph} containing a single tree or a forest of multiple trees.
     */
    SharedTreeGraph convert(final int treeNumber, final String treeClass);
}
