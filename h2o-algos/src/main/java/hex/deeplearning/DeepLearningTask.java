package hex.deeplearning;

import hex.FrameTask;
import water.H2O;
import water.H2O.H2OCountedCompleter;
import water.Job;
import static water.Job.JobCancelledException;
import water.util.Log;

import java.util.Arrays;
import java.util.Random;

public class DeepLearningTask extends FrameTask<DeepLearningTask> {
  final private boolean _training;
  private hex.deeplearning.DeepLearningModel.DeepLearningModelInfo _input;
  hex.deeplearning.DeepLearningModel.DeepLearningModelInfo _output;
  final public hex.deeplearning.DeepLearningModel.DeepLearningModelInfo model_info() { return _output; }

  transient Neurons[] _neurons;

  int _chunk_node_count = 1;

  public DeepLearningTask(hex.deeplearning.DeepLearningModel.DeepLearningModelInfo input, float fraction){this(input,fraction,null);}
  private DeepLearningTask(hex.deeplearning.DeepLearningModel.DeepLearningModelInfo input, float fraction, H2OCountedCompleter cmp){
    super(input.get_params(),input.data_info(),cmp);
    _training=true;
    _input=input;
    _useFraction=fraction;
    _shuffle = _input.get_params().shuffle_training_data;
    assert(_output == null);
  }

  // transfer ownership from input to output (which will be worked on)
  @Override protected void setupLocal(){
    _output = _input; //faster, good enough in this case (since the input was freshly deserialized by the Weaver)
    _input = null;
    _output.set_processed_local(0l);
  }

  // create local workspace (neurons)
  // and link them to shared weights
  @Override protected void chunkInit(){
    _neurons = makeNeuronsForTraining(_output);
  }

  @Override public final void processRow(long seed, final double [] nums, final int numcats, final int [] cats, double [] responses){
    if(_output.get_params().self() != null && !Job.isRunning(_output.get_params().self())) throw new JobCancelledException();
    if (H2O.CLOUD.size()==1) {
      seed += model_info().get_processed_global(); //avoid periodicity
    } else {
      seed = new Random().nextLong(); //multi-node: no point in being reproducible - better to be "good" at being random
    }
    ((Neurons.Input)_neurons[0]).setInput(seed, nums, numcats, cats);
    step(seed, _neurons, _output, _training, responses);
  }

  @Override public void reduce(DeepLearningTask other){
    if (other._output.get_processed_local() > 0 //other NNTask was active (its model_info should be used for averaging)
            && other._output != _output) //other NNTask worked on a different model_info
    {
      // avoid adding remote model info to unprocessed local data, still random
      // (this can happen if we have no chunks on the master node)
      if (_output.get_processed_local() == 0) {
        _output = other._output;
        _chunk_node_count = other._chunk_node_count;
      } else {
        _output.add(other._output);
        _chunk_node_count += other._chunk_node_count;
      }
    }
  }

  static long _lastWarn;
  static long _warnCount;
  @Override protected void postGlobal(){
    if (H2O.CLOUD.size() > 1 && !_output.get_params().replicate_training_data) {
      long now = System.currentTimeMillis();
      if (_chunk_node_count < H2O.CLOUD.size() && (now - _lastWarn > 5000) && _warnCount < 3) {
//        Log.info("Synchronizing across " + _chunk_node_count + " H2O node(s).");
        Log.warn(H2O.CLOUD.size() - _chunk_node_count + " node(s) (out of " + H2O.CLOUD.size()
                + ") are not contributing to model updates. Consider setting replicate_training_data to true or using a larger training dataset (or fewer H2O nodes).");
        _lastWarn = now;
        _warnCount++;
      }
    }
    if (!_output.get_params().replicate_training_data || H2O.CLOUD.size() == 1) {
      _output.div(_chunk_node_count);
      _output.add_processed_global(_output.get_processed_local());
      _output.set_processed_local(0l);
    }
    assert(_input == null);
  }

  public static Neurons[] makeNeuronsForTraining(final DeepLearningModel.DeepLearningModelInfo minfo) {
    return makeNeurons(minfo, true);
  }
  public static Neurons[] makeNeuronsForTesting(final DeepLearningModel.DeepLearningModelInfo minfo) {
    return makeNeurons(minfo, false);
  }

  // Helper
  private static Neurons[] makeNeurons(final DeepLearningModel.DeepLearningModelInfo minfo, boolean training) {
    DataInfo dinfo = minfo.data_info();
    final DeepLearning params = minfo.get_params();
    final int[] h = params.hidden;
    Neurons[] neurons = new Neurons[h.length + 2]; // input + hidden + output
    // input
    neurons[0] = new Neurons.Input(dinfo.fullN(), dinfo);
    // hidden
    for( int i = 0; i < h.length; i++ ) {
      switch( params.activation ) {
        case Tanh:
          neurons[i+1] = new Neurons.Tanh(h[i]);
          break;
        case TanhWithDropout:
          neurons[i+1] = new Neurons.TanhDropout(h[i]);
          break;
        case Rectifier:
          neurons[i+1] = new Neurons.Rectifier(h[i]);
          break;
        case RectifierWithDropout:
          neurons[i+1] = new Neurons.RectifierDropout(h[i]);
          break;
        case Maxout:
          neurons[i+1] = new Neurons.Maxout(h[i]);
          break;
        case MaxoutWithDropout:
          neurons[i+1] = new Neurons.MaxoutDropout(h[i]);
          break;
      }
    }
    // output
    if(params.classification)
      neurons[neurons.length - 1] = new Neurons.Softmax(minfo.units[minfo.units.length-1]);
    else
      neurons[neurons.length - 1] = new Neurons.Linear(1);

    //copy parameters from NN, and set previous/input layer links
    for( int i = 0; i < neurons.length; i++ )
      neurons[i].init(neurons, i, params, minfo, training);

//    // debugging
//    for (Neurons n : neurons) Log.info(n.toString());
    return neurons;
  }

  // forward/backward propagation
  // assumption: layer 0 has _a filled with (horizontalized categoricals) double values
  public static void step(long seed, Neurons[] neurons, DeepLearningModel.DeepLearningModelInfo minfo, boolean training, double[] responses) {
    try {
      for (int i=1; i<neurons.length-1; ++i) {
        neurons[i].fprop(seed, training);
      }
      if (minfo.get_params().classification) {
        ((Neurons.Softmax)neurons[neurons.length-1]).fprop();
        if (training) {
          for( int i = 1; i < neurons.length - 1; i++ )
            Arrays.fill(neurons[i]._e.raw(), 0);
          assert((double)(int)responses[0] == responses[0]);
          final int target_label = (int)responses[0];
          ((Neurons.Softmax)neurons[neurons.length-1]).bprop(target_label);
        }
      }
      else {
        ((Neurons.Linear)neurons[neurons.length-1]).fprop();
        if (training) {
          for( int i = 1; i < neurons.length - 1; i++ )
            Arrays.fill(neurons[i]._e.raw(), 0);
          final float target_value = (float)responses[0];
          ((Neurons.Linear)neurons[neurons.length-1]).bprop(target_value);
        }
      }
      if (training) {
        for (int i=neurons.length-2; i>0; --i)
          neurons[i].bprop();

        /**
         * Let neurons know the real-time number of processed rows -> for accurate learning rate decay, etc.
         */
        minfo.add_processed_local(1);
      }
    }
    catch(RuntimeException ex) {
      Log.warn(ex.getMessage());
      minfo.set_unstable();
      throw new JobCancelledException("Canceling job due to numerical instability.");
    }
  }

}
