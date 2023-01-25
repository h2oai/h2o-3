package biz.k11i.xgboost.tree;

import biz.k11i.xgboost.util.ModelReader;

import java.io.IOException;

/**
 * This class exposes some package-private APIs of RegTreeImpl.Node and provides additional helper methods.
 * These methods can eventually be folded back into the original class.
 */
public class NodeHelper {

    public static RegTreeNode read(ModelReader reader) throws IOException {
        return new RegTreeImpl.Node(reader);
    }

    public static boolean isEqual(RegTreeNode left, RegTreeNode right) {
        return left == right || (   // also covers null case
                left.getParentIndex() == right.getParentIndex() 
                && left.getLeftChildIndex() == right.getLeftChildIndex() 
                && left.getRightChildIndex() == right.getRightChildIndex()
                && left.getSplitIndex() == right.getSplitIndex()
                && Float.compare(left.getLeafValue(), right.getLeafValue()) == 0 
                && Float.compare(left.getSplitCondition(), right.getSplitCondition()) == 0
                && left.default_left() == right.default_left()
        );
    }

}
