package hex.deepwater.models;

import hex.deepwater.ParameterBuilder;
import hex.deepwater.TensorFrame;
import hex.deepwater.layers.Layer;

import static org.bytedeco.javacpp.tensorflow.*;

import org.bytedeco.javacpp.annotation.Const;
import org.python.core.PyObject;
import water.fvec.Frame;
import water.util.Pair;

import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Created by fmilo on 8/16/16.
 */

public class Sequential {

    static Tensor asTensor(Frame frame) {
        Tensor t = new Tensor(DT_FLOAT, new TensorShape(frame.numRows(), frame.numCols()));
        FloatBuffer fb = t.createBuffer();
        FloatBuffer ffb = frame.createBuffer();
        fb.put(ffb);
        return t;
    }

    public static void checkStatus(Status status) {

        if (!status.ok()){
            throw new InternalError(status.error_message().getString());
        }
    }

    GraphDefBuilder _builder;
    GraphDef _graphDef;

    public Sequential(){
        _builder = new GraphDefBuilder();
    }

    public void compile(PyObject[]args, String []kwds){
        compile(new ParameterBuilder().mustHaveString("optimizer").mustHaveString("loss").optional("sample_weight_mode", 0));
    }
/*
    Node Multi(Graph g, String name, Node...args ){

        NodeBuilder nb = new NodeBuilder(g.NewName("n"), name);
        for(Node arg: args) {
           nb.Input(arg);
        }

    }
*/
    static public String[] getNames(TensorVector v){
       return new String[]{};
    }

    public void compile(ParameterBuilder pm){
        Boolean isFirst = false;
        String [] inputNames ;
        for (int i = 0; i < _layers.size(); i++) {
            Layer l = _layers.get(i);
            l.build(_builder);
        }


        Layer firstLayer = _layers.get(0);
        Layer lastLayer = _layers.get(_layers.size()-1);


//        Node input_placeholder = Variable(input.input_type(0), _builder.opts().WithName("x"));

 //       Assign(input, input_placeholder, _builder.opts());

 //       Node output_placeholder = Variable( , _builder.opts().WithName("y"));

  //      Node output = _layers.get(_layers.size()-1).getOutputs().get(0);

   //     Assign(output, output_placeholder, _builder.opts());

//        ApplyGradientDescent(loss_function, learning_rate, epoch, _builder.opts());

//        Graph g = new Graph(OpRegistry.Global());


       _graphDef = new GraphDef();
      checkStatus(_builder.ToGraphDef(_graphDef));
    }

    /*
    public History fit(PyObject[]args, String []kwds){
        return fit(new ParameterBuilder().mustHaveString("optimizer").mustHaveString("loss").optional("sample_weight_mode"));
    }*/

    public History fit(TensorFrame x, TensorFrame y, int batch_size, int nb_epoch){
        SessionOptions opt = new SessionOptions();
        Session session = new Session(opt);

        checkStatus(session.Create(_graphDef));

        TensorVector outputs = new TensorVector();

        History h = new History();

        String [] outNames = new String[]{ "y:0"};
        StringVector outputTensors = new StringVector(outNames);
        session.Run( new StringTensorPairVector(
                new String[]{"x:0"},
                new Tensor[]{x}
        ), outputTensors, new StringVector(), outputs);

        for (int i = 0; i < outputTensors.size(); i++) {
            String name = outNames[i];
            Tensor value = outputs.get(i);
            if (value !=  null) {
                h.recordEvent(name, value);
            } else {
                System.out.println(name + ": does not have a proper value");
            }
        }



        return h;
    }

    public void evaluate(Frame x, Frame y, int batch_size ){}

    public void predict(){}

    public void predict_classes(){}

    public void predict_proba(){}

    public void train_on_batch(){}

    public void test_on_batch(){}

    public void predict_on_batch(){}

    // fit_generator

    // evaluate_generator

    ArrayList<Layer> _layers = new ArrayList<>();

    public Sequential add(Layer l) {
        _layers.add(l);
        return this;
    }
}
