package hex.tree.isoforextended;

import water.Iced;
import water.fvec.Frame;
import water.fvec.Vec;

public class Node extends Iced {
    Frame frame;
    Vec n;
    double [] nn;
    Vec p;

    int currentHeight;
    boolean external = false;
    long size;

    public Node(Frame frame, int currentHeight) {
        this.frame = frame;
        this.currentHeight = currentHeight;
        this.size = frame.numRows();
    }
}
