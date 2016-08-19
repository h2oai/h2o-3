package hex.deeplearning;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.presets.tensorflow;

/**
 * Created by fmilo on 8/18/16.
 */
public class Callback extends tensorflow.NodeLabelFunction {
    static {
        Loader.load(); }
    public Callback() { allocate(); }
    private native void allocate();

    @Override
    public BytePointer call(org.bytedeco.javacpp.tensorflow.Node node) {
        System.out.println(node.name());
        return null;
    }

}
