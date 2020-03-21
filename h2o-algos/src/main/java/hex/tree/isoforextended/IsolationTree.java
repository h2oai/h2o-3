package hex.tree.isoforextended;

import hex.psvm.psvm.MatrixUtils;
import water.DKV;
import water.Iced;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FrameUtils;
import water.util.MathUtils;
import water.util.VecUtils;

import javax.swing.*;
import java.util.Arrays;

public class IsolationTree extends Iced {
    private Node[] nodes;

    private Key<Frame> frameKey;
    private int heightLimit;
    private long seed;
    private int extensionLevel;

    public IsolationTree(Key<Frame> frame, int heightLimit, long seed, int extensionLevel) {
        this.frameKey = frame;
        this.heightLimit = heightLimit;
        this.seed = seed;
        this.extensionLevel = extensionLevel;

        this.nodes = new Node[(int) Math.pow(2, heightLimit) - 1];
    }

    public void buildTree() {
        Frame frame = DKV.get(frameKey).get();
        nodes[0] = new Node(frame._key, frame.numRows(), 0);
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];
            if (node == null || node.external)
                continue;
            Frame nodeFrame = node.getFrame();
            int currentHeight = node.height;
            if (node.height >= heightLimit || nodeFrame.numRows() <= 1) {
                node.external = true;
                node.size = nodeFrame.numRows();
                node.height = currentHeight;
            } else {
                currentHeight++;

                node.p = VecUtils.uniformDistrFromFrameMR(nodeFrame, seed + i);
                node.n = VecUtils.makeGaussianVec(nodeFrame.numCols(), nodeFrame.numCols() - extensionLevel - 1, seed + i);
                node.nn = FrameUtils.asDoubles(node.n);
                Frame sub = MatrixUtils.subtractionMtv(nodeFrame, node.p);
                Vec mul = MatrixUtils.productMtv2(sub, node.n);
                Frame left = new FilterLtTask(mul, 0).doAll(nodeFrame.types(), nodeFrame).outputFrame(Key.make(), null, null);
                Frame right = new FilterGteRightTask(mul, 0).doAll(nodeFrame.types(), nodeFrame).outputFrame(Key.make(), null, null);
                DKV.put(left);
                DKV.put(right);

                if ((2 * i + 1) < nodes.length) {
                    nodes[2 * i + 1] = new Node(left._key, left.numRows(), currentHeight);
                    nodes[2 * i + 2] = new Node(right._key, right.numRows(), currentHeight);
                }
            }
        }
    }

    public void print() {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == null)
                System.out.print(". ");
            else
                System.out.print(nodes[i].size + " ");
        }
        System.out.println("");
    }

    public void printHeight() {
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == null)
                System.out.print(". ");
            else
                System.out.print(nodes[i].height + " ");
        }
        System.out.println("");
    }

    public double computePathLength(Vec row) {
        int position = 0;
        Node node = nodes[0];
        double score = 0;
        while (!node.external) {
            Vec sub = MatrixUtils.subtractionVtv(row, node.p);
            double mul = MatrixUtils.productVtV(sub, Vec.makeVec(node.nn, Vec.newKey()));
            if (mul <= 0) {
                position = 2 * position + 1;
            } else {
                position = 2 * position + 2;
            }
            if (position < nodes.length)
                node = nodes[position];
            else
                break;
        }
        score += node.height + averagePathLengthOfUnsuccesfullSearch(node.size);
        return score;
    }

    private class Node extends Iced {
        private Key<Frame> frameKey;
        private Vec n;
        private double[] nn;
        private Vec p;

        int height;
        boolean external = false;
        long size;

        public Node(Key<Frame> frameKey, long size, int currentHeight) {
            this.frameKey = frameKey;
            this.height = currentHeight;
            this.size = size;
        }

        Frame getFrame() {
            return DKV.get(frameKey).get();
        }
    }

    /**
     * Gives the average path length of unsuccessful search in BST
     *
     * @param n number of elements
     */
    public static double averagePathLengthOfUnsuccesfullSearch(long n) {
        if (n <= 0)
            return 0;
        return 2 * MathUtils.harmonicNumberEstimation(n - 1) - (2.0 * (n - 1.0)) / n;
    }
}
