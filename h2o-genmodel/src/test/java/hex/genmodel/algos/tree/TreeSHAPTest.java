package hex.genmodel.algos.tree;

import ai.h2o.algos.tree.INode;
import ai.h2o.algos.tree.INodeStat;
import org.junit.Test;

import static org.junit.Assert.*;

public class TreeSHAPTest {

    @Test
    public void calculateContributionsWithAlmostZeroWeights_modelApp() {
        checkContributionsBehaveSmoothlyWithZeroWeights(new Model_App_TreeBuilder());
    }

    @Test
    public void calculateContributionsWithAlmostZeroWeights_modelAppp() {
        checkContributionsBehaveSmoothlyWithZeroWeights(new Model_Appp_TreeBuilder());
    }

    private void checkContributionsBehaveSmoothlyWithZeroWeights(TreeBuilder tb) {
        final float closeToZeroWeight = 0.000001f;
        TreeSHAP<String, Node, Node> zeroTreeSHAP = new TreeSHAP<>(tb.buildTree(0));
        TreeSHAP<String, Node, Node> closeToZeroTreeSHAP = new TreeSHAP<>(tb.buildTree(closeToZeroWeight));
        final int nContribs = 3;
        String[] paths = {"LL", "LR", "RL", "RR"};

        for (String path : paths) {
            float[] expectedContribs = closeToZeroTreeSHAP.calculateContributions(path, new float[nContribs]);
            float[] actualContribs   = zeroTreeSHAP.calculateContributions(path, new float[nContribs]);
            assertArrayEquals("Contributions for path '" + path + "' should be similar",
                    expectedContribs, actualContribs, 1e-2f);
        }
    }

    private static class Model_App_TreeBuilder implements TreeBuilder { // Model A'' from support ticket #99203 (private)
        @Override
        public Node[] buildTree(float almostZeroWeight) {
            float regularWeight = 0.5f - almostZeroWeight;
            return new Node[]{
                    new Node(1, 2, 1f, 0, 40),
                    new Node(3, 4, 1f - (2 * almostZeroWeight), 1, 0),
                    new Node(5, 6, almostZeroWeight * 2, 1, 40),
                    new Node(regularWeight, 0),
                    new Node(regularWeight, 0),
                    new Node(almostZeroWeight, 0),
                    new Node(almostZeroWeight, 80)
            };
        }
    }

    private static class Model_Appp_TreeBuilder implements TreeBuilder { // Model A''' from support ticket #99203 (private)
        @Override
        public Node[] buildTree(float almostZeroWeight) {
            float regularWeight = 0.5f - almostZeroWeight;
            return new Node[]{
                    new Node(1, 2, 1f, 0, 40),
                    new Node(3, 4, almostZeroWeight * 2, 1, 0),
                    new Node(5, 6, 1f - (2 * almostZeroWeight), 1, 40),
                    new Node(almostZeroWeight, 0),
                    new Node(almostZeroWeight, 0),
                    new Node(regularWeight, 0),
                    new Node(regularWeight, 80)
            };
        }
    }

    private interface TreeBuilder {
        Node[] buildTree(float almostZeroWeight);
    }
    
    private static class Node implements INode<String>, INodeStat {
        final int left;
        final int right;
        final float weight;
        final int featureId;
        final float value;

        public Node(float weight, float value) {
            this.left = -1;
            this.right = -1;
            this.weight = weight;
            this.featureId = -1;
            this.value = value;
        }

        public Node(int left, int right, float weight, int featureId, float value) {
            this.left = left;
            this.right = right;
            this.weight = weight;
            this.featureId = featureId;
            this.value = value;
        }

        @Override
        public boolean isLeaf() {
            return left == -1 && right == -1;
        }

        @Override
        public float getLeafValue() {
            return value;
        }

        @Override
        public int getSplitIndex() {
            return featureId;
        }

        @Override
        public int next(String value) {
            return value.charAt(getSplitIndex()) == 'R' ? right : left;
        }

        @Override
        public int getLeftChildIndex() {
            return left;
        }

        @Override
        public int getRightChildIndex() {
            return right;
        }

        @Override
        public float getWeight() {
            return weight;
        }
    } 

}
