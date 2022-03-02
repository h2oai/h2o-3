package hex.tree.isoforextended.isolationtree;

import water.AutoBuffer;
import water.Iced;

/**
 * Upper class for {@link CompressedNode} and {@link CompressedLeaf} used to access both types from array.
 */
public abstract class AbstractCompressedNode extends Iced<AbstractCompressedNode> {
    private final int _height;

    public AbstractCompressedNode(int height) {
        _height = height;
    }

    public int getHeight() {
        return _height;
    }
    
    public abstract void toBytes(AutoBuffer ab);
}
