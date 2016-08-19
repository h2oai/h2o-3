package hex.deepwater.layers;

import java.util.List;

import static org.bytedeco.javacpp.tensorflow.Node;

import static org.bytedeco.javacpp.tensorflow.GraphDefBuilder;

public interface Layer {

    void build(GraphDefBuilder b);

    List<Node> getInputs();
    List<Node> getWeights();
    List<Node> getOutputs();
}
