package hex.deepwater;

import water.*;

import java.io.IOException;


abstract class DeepWaterIterator {

  DeepWaterIterator(int batch_size, int obsSize, boolean cache) throws IOException {
    _batch_size = batch_size;
    _start_index = 0;
    _cache = cache;
    _data = new float[2][];
    _data[0] = new float[_batch_size * obsSize];
    _data[1] = new float[_batch_size * obsSize];
    _label = new float[2][];
    _label[0] = new float[_batch_size];
    _label[1] = new float[_batch_size];
  }

  abstract public boolean Next(Futures fs) throws IOException;
  public float[] getData() { return _data[_which ^ 1]; }
  public float[] getLabel() { return _label[_which ^ 1]; }

  void flip() { assert(_which == 0 || _which == 1); _which ^= 1; }
  int which() { return _which; }

  private int _which; //0 or 1
  int _start_index;
  final int _batch_size;
  final boolean _cache;
  final float[][] _data;
  final float[][] _label;
}
