package hex.deeplearning;

import hex.FrameTask;
import water.H2O;
import water.H2O.H2OCountedCompleter;
import water.Key;
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

  @Override protected boolean skipMissing() {
    return _output.get_params()._missing_values_handling == DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip;
  }

  public DeepLearningTask(Key jobKey, hex.deeplearning.DeepLearningModel.DeepLearningModelInfo input, float fraction){this(jobKey, input,fraction,null);}
  private DeepLearningTask(Key jobKey, hex.deeplearning.DeepLearningModel.DeepLearningModelInfo input, float fraction, H2OCountedCompleter cmp){
    super(jobKey,input.data_info(),cmp);
    _training=true;
    _input=input;
    _useFraction=fraction;
    _shuffle = _input.get_params()._shuffle_training_data;
    assert(_output == null);
  }

  // transfer ownership from input to output (which will be worked on)
  @Override protected void setupLocal(){
    super.setupLocal();
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
    if (model_info().get_params()._reproducible) {
      seed += model_info().get_processed_global(); //avoid periodicity
    } else {
      seed = new Random().nextLong();
    }
    ((Neurons.Input)_neurons[0]).setInput(seed, nums, numcats, cats);
    step(seed, _neurons, _output, _training, responses);
  }

  @Override protected void chunkDone(long n) {
    if (_training) _output.add_processed_local(n);
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
    if (other._output.unstable()) _output.set_unstable();
  }

  static long _lastWarn;
  static long _warnCount;
  @Override protected void postGlobal(){
    if (H2O.CLOUD.size() > 1 && !_output.get_params()._replicate_training_data) {
      long now = System.currentTimeMillis();
      if (_chunk_node_count < H2O.CLOUD.size() && (now - _lastWarn > 5000) && _warnCount < 3) {
//        Log.info("Synchronizing across " + _chunk_node_count + " H2O node(s).");
        Log.warn(H2O.CLOUD.size() - _chunk_node_count + " node(s) (out of " + H2O.CLOUD.size()
                + ") are not contributing to model updates. Consider setting replicate_training_data to true or using a larger training dataset (or fewer H2O nodes).");
        _lastWarn = now;
        _warnCount++;
      }
    }
    if (!_output.get_params()._replicate_training_data || H2O.CLOUD.size() == 1) {
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
    final DeepLearningModel.DeepLearningParameters params = minfo.get_params();
    final int[] h = params._hidden;
    Neurons[] neurons = new Neurons[h.length + 2]; // input + hidden + output
    // input
    neurons[0] = new Neurons.Input(dinfo.fullN(), dinfo);
    // hidden
    for( int i = 0; i < h.length + (params._autoencoder ? 1 : 0); i++ ) {
      int n = params._autoencoder && i == h.length ? minfo.units[0] : h[i];
      switch( params._activation ) {
        case Tanh:
          neurons[i+1] = new Neurons.Tanh(n);
          break;
        case TanhWithDropout:
          neurons[i+1] = params._autoencoder && i == h.length ? new Neurons.Tanh(n) : new Neurons.TanhDropout(n);
          break;
        case Rectifier:
          neurons[i+1] = new Neurons.Rectifier(n);
          break;
        case RectifierWithDropout:
          neurons[i+1] = params._autoencoder && i == h.length ? new Neurons.Rectifier(n) : new Neurons.RectifierDropout(n);
          break;
        case Maxout:
          neurons[i+1] = new Neurons.Maxout(n);
          break;
        case MaxoutWithDropout:
          neurons[i+1] = params._autoencoder && i == h.length ? new Neurons.Maxout(n) : new Neurons.MaxoutDropout(n);
          break;
      }
    }
    if(!params._autoencoder) {
      if (minfo._classification)
        neurons[neurons.length - 1] = new Neurons.Softmax(minfo.units[minfo.units.length - 1]);
      else
        neurons[neurons.length - 1] = new Neurons.Linear(1);
    }

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
      if (minfo.get_params()._autoencoder) {
        neurons[neurons.length - 1].fprop(seed, training);
        if (training) {
          for (int i=neurons.length-1; i>0; --i) {
            neurons[i].bprop();
          }
        }
      } else {
        if (minfo._classification) {
          ((Neurons.Softmax) neurons[neurons.length - 1]).fprop();
          if (training) {
            for (int i = 1; i < neurons.length - 1; i++)
              Arrays.fill(neurons[i]._e.raw(), 0);
            assert ((double) (int) responses[0] == responses[0]);
            final int target_label;
            if (Double.isNaN(responses[0])) { //missing response
              target_label = Neurons.missing_int_value;
            } else {
              assert ((double) (int) responses[0] == responses[0]); //classification -> integer labels expected
              target_label = (int) responses[0];
            }
            ((Neurons.Softmax) neurons[neurons.length - 1]).bprop(target_label);
          }
        } else {
          ((Neurons.Linear) neurons[neurons.length - 1]).fprop();
          if (training) {
            for (int i = 1; i < neurons.length - 1; i++)
              Arrays.fill(neurons[i]._e.raw(), 0);
            float target_value;
            if (Double.isNaN(responses[0])) { //missing response
              target_value = Neurons.missing_real_value;
            } else {
              target_value = (float) responses[0];
            }
            ((Neurons.Linear) neurons[neurons.length - 1]).bprop(target_value);
          }
        }
        if (training) {
          for (int i = neurons.length - 2; i > 0; --i)
            neurons[i].bprop();
        }
      }
    }
    catch(RuntimeException ex) {
      Log.warn(ex.getMessage());
      minfo.set_unstable();
      throw new RuntimeException("Canceling job due to numerical instability.");
    }
  }

}
