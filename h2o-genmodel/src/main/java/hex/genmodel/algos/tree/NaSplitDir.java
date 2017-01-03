package hex.genmodel.algos.tree;

/**
 * Copy of `hex.tree.DHistogram.NASplitDir` in package `h2o-algos`.
 */
public enum NaSplitDir {
    //never saw NAs in training
    None(0),     //initial state - should not be present in a trained model

    // saw NAs in training
    NAvsREST(1), //split off non-NA (left) vs NA (right)
    NALeft(2),   //NA goes left
    NARight(3),  //NA goes right

    // never NAs in training, but have a way to deal with them in scoring
    Left(4),     //test time NA should go left
    Right(5);    //test time NA should go right

    private int value;
    NaSplitDir(int v) { this.value = v; }
    public int value() { return value; }
}
