package hex.deepwater.backends.cudnn;

import org.tensorflow.framework.DataType;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.List;

public class Operation {

    private final NodeDef node;
    Graph graph;
    List<Tensor> _inputs;
    List<DataType> _input_types;
    List<DataType> _outputs_types;
    // TODO: ControlInputs
    List<Tensor> _outputs;
    private final List<DataType> output_types;

    public Operation(NodeDef node, Graph g, List<Tensor> inputs, List<DataType> output_types){
        this.node = node;
        graph = g;
        _inputs = inputs;

        _outputs = new ArrayList<>(output_types.size());
        this.output_types = output_types;
        for (int i = 0; i < output_types.size(); i++) {
           _outputs.add( new Tensor(this, i, output_types.get(i)));
        }

        // input types
        _input_types = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
           _input_types.add(inputs.get(i).getDataType());
        }
    }

    public String getDevice(){
        return node.getDevice();
    }

    public String getName() {
        return node.getName();
    }
}
