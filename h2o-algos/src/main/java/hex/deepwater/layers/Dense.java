package hex.deepwater.layers;

import hex.deepwater.ParameterBuilder;
import static org.bytedeco.javacpp.tensorflow.*;

import org.python.core.PyObject;
import org.tensorflow.framework.*;
import org.tensorflow.framework.CostGraphDef;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by fmilo on 8/16/16.
 */
public class Dense implements Layer {

    final ParameterBuilder _params = new ParameterBuilder()
            .mustHaveInt("output_dim")
            .optional("activation", "linear")
            .optionalList("input_shape", 100L)
            .optional("init", "glorot_uniform");

    public List<Node> _inputs;
    public List<Node> _weights;
    public List<Node> _outputs;

    public Dense(PyObject[] args, String[] keywords){
       this(0);
        _params.parsePython(args, keywords);
    }

    public Dense(int output_dim){
        // check output_dim
        _params.set("output_dim", output_dim);

        _inputs = new ArrayList<Node>();
        _weights = new ArrayList<Node>();
        _outputs = new ArrayList<Node>();
    }

    public void build(GraphDefBuilder b){

       long[] inputShape = _params.getLongList("input_shape");


       GraphDef.newBuilder().addNodeBuilder();
       org.tensorflow.framework.NodeDef.newBuilder().setName("name").setOp("");

       Node weights = Variable( new TensorShape(inputShape), DT_FLOAT, b.opts().WithName("weights"));
       Node input = Variable( new TensorShape(inputShape), DT_FLOAT, b.opts().WithName("input"));
       Node bias = Variable( new TensorShape(inputShape), DT_FLOAT, b.opts().WithName("bias"));

        _inputs.add(input);

        _weights.add(weights);
        _weights.add(bias);


        _inputs.get(0);

       Node wx = MatMul(input, weights, b.opts());

       Node biasAdd = BiasAdd(wx, bias, b.opts());

       Node activation = Relu(biasAdd, b.opts());

        _outputs.add(activation);
    }

    @Override
    public List<Node> getInputs() {
        return _inputs;
    }

    @Override
    public List<Node> getWeights() {
        return _weights;
    }

    @Override
    public List<Node> getOutputs() {
        return _outputs;
    }


}
