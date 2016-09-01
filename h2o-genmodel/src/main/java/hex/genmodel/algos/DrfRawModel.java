package hex.genmodel.algos;

import hex.genmodel.GenModel;
import hex.genmodel.utils.GenmodelBitSet;
import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.NaSplitDir;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * "Distributed Random Forest" RawModel
 */
public final class DrfRawModel extends TreeBasedModel {
    private int _effective_n_classes;
    private boolean _binomial_double_trees;


    public DrfRawModel(ContentReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
        super(cr, info, columns, domains);
        _binomial_double_trees = (boolean) info.get("binomial_double_trees");
        _effective_n_classes = _nclasses == 2 && !_binomial_double_trees? 1 : _nclasses;
        _compressed_trees = new byte[_ntrees * _effective_n_classes][];
    }

    public final double[] score0(double[] data) {
        double[] preds = new double[_nclasses == 1? 1 : _nclasses + 1];
        return score0(data, preds);
    }

    // Pass in data in a double[], pre-aligned to the Model's requirements.
    // Jam predictions into the preds[] array; preds[0] is reserved for the
    // main prediction (class for classifiers or value for regression),
    // and remaining columns hold a probability distribution for classifiers.
    public final double[] score0(double[] data, double[] preds) {
        java.util.Arrays.fill(preds, 0);
        for (int i = 0; i < _effective_n_classes; i++) {
            int k = _nclasses == 1? 0 : i + 1;
            for (int j = 0; j < _ntrees; j++) {
                try {
                    int itree = i * _ntrees + j;
                    byte[] tree = _compressed_trees[itree];
                    if (tree == null) {
                        tree = _reader.getBinaryFile(String.format("trees/t%02d_%03d.bin", i, j));
                        _compressed_trees[itree] = tree;
                    }
                    preds[k] += scoreTree(tree, data, _nclasses);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    System.out.println("Exception " + e + " when scoring tree " + i + ", " + j);
                    throw e;
                }
            }
        }
        // Correct the predictions -- see `DRFModel.toJavaUnifyPreds`
        boolean isBinomialModel = _nclasses == 2 && !_binomial_double_trees;
        if (_nclasses == 1) {
            // Regression
            preds[0] /= _ntrees;
        } else {
            // Classification
            if (isBinomialModel) {
                preds[1] /= _ntrees;
                preds[2] = 1.0 - preds[1];
            } else {
                double sum = 0;
                for (int i = 1; i <= _nclasses; i++) { sum += preds[i]; }
                if (sum > 0)
                    for (int i = 1; i <= _nclasses; i++) { preds[i] /= sum; }
            }
            if (_balanceClasses)
                GenModel.correctProbabilities(preds, _priorClassDistrib, _modelClassDistrib);
            preds[0] = GenModel.getPrediction(preds, _priorClassDistrib, data, _defaultThreshold);
        }
        return preds;
    }



}
