package hex.tree.isoforextended;

import hex.psvm.psvm.MatrixUtils;
import water.Iced;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FrameUtils;
import water.util.MathUtils;
import water.util.VecUtils;

import java.util.Arrays;

public class IsolationTree extends Iced {
    private Node[] nodes;

    private Frame frame;
    private int heightLimit;
    private long seed;
    private int extensionLevel;

    public IsolationTree(Frame frame, int heightLimit, long seed, int extensionLevel) {
        this.frame = frame;
        this.heightLimit = heightLimit;
        this.seed = seed;
        this.extensionLevel = extensionLevel;

        this.nodes = new Node[(int) Math.pow(2, heightLimit) - 1];
    }

    public void buildTree() {
        nodes[0] = new Node(frame, 0);
        for (int i = 0; i < nodes.length; i++) {
            Node node = nodes[i];
            if (node == null || node.external)
                continue;
            int currentHeight = node.height;
            if (node.height >= heightLimit || node.frame.numRows() <= 1) {
                node.external = true;
                node.size = node.frame.numRows();
                node.height = currentHeight;
            } else {
                currentHeight++;

                node.p = VecUtils.uniformDistrFromFrameMR(node.frame, seed + i);
                node.n = VecUtils.makeGaussianVec(node.frame.numCols(), node.frame.numCols() - extensionLevel - 1, seed + i);
                node.nn = FrameUtils.asDoubles(node.n);
                Frame sub = MatrixUtils.subtractionMtv(node.frame, node.p);
                Vec mul = MatrixUtils.productMtv2(sub, node.n);
                Frame left = new FilterLtTask(mul, 0).doAll(node.frame.types(), node.frame).outputFrame();
                Frame right = new FilterGteRightTask(mul, 0).doAll(node.frame.types(), node.frame).outputFrame();
                
                if ((2 * i + 1) < nodes.length) {
                    nodes[2 * i + 1] = new Node(left, currentHeight);
                    nodes[2 * i + 2] = new Node(right, currentHeight);
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
        private Frame frame;
        private Vec n;
        private double [] nn;
        private Vec p;

        int height;
        boolean external = false;
        long size;

        public Node(Frame frame, int currentHeight) {
            this.frame = frame;
            this.height = currentHeight;
            this.size = frame.numRows();
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
