package hex.tree.dt;

import water.Iced;


public abstract class AbstractCompressedNode extends Iced<AbstractCompressedNode> {

    public AbstractCompressedNode() {
    }
    
    public abstract String toString();

//    /**
//     * Serialize Node to the byte buffer
//     */
//    public abstract void toBytes(AutoBuffer ab);
}
