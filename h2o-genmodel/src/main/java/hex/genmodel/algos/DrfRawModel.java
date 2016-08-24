package hex.genmodel.algos;

import hex.genmodel.RawModel;
import hex.genmodel.utils.ByteBufferWrapper;
import hex.genmodel.utils.BastardIcedBitSet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * "Distributed Random Forest" RawModel
 */
public class DrfRawModel extends RawModel {
    private int _ntrees;

    public DrfRawModel(ContentReader cr, Map<String, Object> info, String[] columns, String[][] domains) {
        super(cr, info, columns, domains);
        _ntrees = (int) info.get("n_trees");
    }


    // Pass in data in a double[], pre-aligned to the Model's requirements.
    // Jam predictions into the preds[] array; preds[0] is reserved for the
    // main prediction (class for classifiers or value for regression),
    // and remaining columns hold a probability distribution for classifiers.
    public double[] score0(double[] data, double[] preds) {
        java.util.Arrays.fill(preds, 0);
        for (int i = 0; i < _nclasses; i++) {
            for (int j = 0; j < _ntrees; j++) {
                try {
                    byte[] tree = _reader.getBinaryFile(String.format("trees/t%02d_%03d.bin", i, j));
                    preds[i] += scoreTree(tree, data);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return preds;
    }

    /**
     * This code is copied from `CompressedTree.score`
     */
    private double scoreTree(byte[] _bits, double[] data) {
        return scoreTree(_bits, data, false);
    }
    private double scoreTree(byte[] _bits, double[] data, boolean computeLeafAssignment) {
        ByteBufferWrapper ab = new ByteBufferWrapper(_bits);
        BastardIcedBitSet ibs = null;  // Lazily set on hitting first group test
        long bitsRight = 0;
        int level = 0;
        while (true) {
            int nodeType = ab.get1U();
            int colId = ab.get2();
            if (colId == 65535) return ab.get4f();
            DHistogram.NASplitDir naSplitDir = DHistogram.NASplitDir.values()[ab.get1U()];
            final boolean NAvsREST = naSplitDir == DHistogram.NASplitDir.NAvsREST;
            final boolean NALeft = naSplitDir == DHistogram.NASplitDir.NALeft;
            final boolean Left = naSplitDir == DHistogram.NASplitDir.Left;
            int equal = (nodeType&12) >> 2;
            assert (equal >= 0 && equal <= 3) :
                    "illegal equal value " + equal + " at " + ab + " in bitpile " + Arrays.toString(_bits);

            float splitVal = -1;
            if (!NAvsREST) {
                // Extract value or group to split on
                if (equal == 0 || equal == 1) {
                    // Standard float-compare test (either < or ==)
                    splitVal = ab.get4f(); // Get the float to compare
                } else {
                    // Bitset test
                    if (ibs == null) ibs = new BastardIcedBitSet(0);
                    if (equal == 2) ibs.fill2(_bits, ab);
                    else ibs.fill3(_bits, ab);
                }
            }

            // Compute the amount to skip.
            int lmask = nodeType & 0x33;
            int rmask = (nodeType & 0xC0) >> 2;
            int skip = 0;
            switch(lmask) {
                case 0:  skip = ab.get1U();  break;
                case 1:  skip = ab.get2();  break;
                case 2:  skip = ab.get3();  break;
                case 3:  skip = ab.get4();  break;
                case 16: skip = _nclasses < 256? 1 : 2;  break; // Small leaf
                case 48: skip = 4;  break; // skip the prediction
                default:
                    assert false : "illegal lmask value " + lmask + " in bitpile " + Arrays.toString(_bits);
            }

            assert equal != 1;  // no longer supported
            double d = data[colId];
            if (Double.isNaN(d) && !NALeft || // NA goes right
                !NAvsREST && equal == 0 && d >= splitVal ||  // greater or equals goes right
                !NAvsREST && (equal == 2 || equal == 3) && ibs.contains((int)d)  // if contained in bitset, go right
                ) {
                // RIGHT
                if (!(Double.isNaN(d) && (NALeft || Left))) { //missing value with NALeft or Left goes LEFT as well
                    ab.skip(skip);        // Skip to the right subtree
                    if (computeLeafAssignment && level < 64) bitsRight |= 1 << level;
                    lmask = rmask;        // And set the leaf bits into common place
                }
            } else {
                // LEFT
                assert !Double.isNaN(d) || NALeft || Left;
            }
            level++;
            if ((lmask&16) == 16) {
                if (computeLeafAssignment) {
                    bitsRight |= 1 << level; //mark the end of the tree
                    return Double.longBitsToDouble(bitsRight);
                }
                return ab.get4f();
            }
        }
    }

}
