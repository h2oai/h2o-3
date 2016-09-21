package hex.deepwater;

import water.*;

import java.io.IOException;


abstract public class DeepWaterIterator {

  public DeepWaterIterator(int batch_size, int obsSize, boolean cache) throws IOException {
    _observationSize = obsSize;
    _batch_size = batch_size;
    _start_index = 0;
    _cache = cache;
    _data = new float[2][];
    _data[0] = new float[batch_size * _observationSize];
    _data[1] = new float[batch_size * _observationSize];
    _label = new float[2][];
    _label[0] = new float[batch_size];
    _label[1] = new float[batch_size];
  }

  abstract public boolean Next(Futures fs) throws IOException;
  public float[] getData() { return _data[_which ^ 1]; }
  public float[] getLabel() { return _label[_which ^ 1]; }

  protected void flip() { assert(_which == 0 || _which == 1); _which ^= 1; }
  protected int which() { return _which; }

  private int _which; //0 or 1
  protected int _start_index;
  final protected int _batch_size;
  final protected int _observationSize;
  final protected boolean _cache;
  final protected float[][] _data;
  final protected float[][] _label;
}
