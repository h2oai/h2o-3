package hex.tree.isoforfaircut.isolationtree;

import water.AutoBuffer;

import java.util.Arrays;

import static hex.genmodel.algos.isoforfaircut.FairCutForestMojoModel.NODE;

/**
 * IsolationTree Node with better memory performance. Store only the data that are needed for scoring.
 */
public class CompressedNode extends AbstractCompressedNode {
    
    private final double[] _normalVector;
    private final double _intercept;
    protected double[] _means;
    protected double[] _stds;

    public CompressedNode(IsolationTree.Node node) {
        this(node.getNormalVector(), node.getIntercept(), node.getMeans(), node.getStds(), node.getHeight());
    }

    public CompressedNode(double[] normalVector, double intercept, double[] means, double[] stds, int currentHeight) {
        super(currentHeight);
        this._normalVector = normalVector == null ? null : Arrays.copyOf(normalVector, normalVector.length);
        this._intercept = intercept;

        this._means = means == null ? null : Arrays.copyOf(means, means.length);
        this._stds = stds == null ? null : Arrays.copyOf(stds, stds.length);
    }

    public double[] getNormalVector() {
        return _normalVector;
    }

    public double getIntercept() {
        return _intercept;
    }

    /**
     * The structure of the bytes is:
     *
     * |identifierOfTheNodeType|normalvectorvalues|interceptvalue|meanvalues|stdvalues
     */
    @Override
    public void toBytes(AutoBuffer ab) {
        ab.put1(NODE); // identifier of this node type
        for (double v : _normalVector) {
            ab.put8d(v);
        }
        ab.put8d(_intercept);
        for (double v : _means) {
            ab.put8d(v);
        }
        for (double v : _stds) {
            ab.put8d(v);
        }
    }
}
