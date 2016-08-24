package hex.deepwater;

import water.*;
import water.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


public class DeepWaterImageIterator {

  public DeepWaterImageIterator(ArrayList<String> img_lst, ArrayList<Float> lable_lst, float[] meanData, int batch_size, int width, int height, int channels, boolean cache) throws IOException {
    assert _label_lst ==null || img_lst.size() == lable_lst.size();
    _img_lst = img_lst;
    _label_lst = lable_lst;
    _meanData = meanData;
    _batch_size = batch_size;
    _val_num = img_lst.size();
    _start_index = 0;
    _width = width;
    _height = height;
    _channels = channels;
    _data = new float[2][];
    _data[0] = new float[batch_size * width * height * channels];
    _data[1] = new float[batch_size * width * height * channels];
    _label = new float[2][];
    _label[0] = new float[batch_size];
    _label[1] = new float[batch_size];
    _file = new String[2][];
    _file[0] = new String[batch_size];
    _file[1] = new String[batch_size];
    _cache = cache;
  }

  public static class Dimensions extends Iced<Dimensions> implements Comparable<Dimensions> {
    int _width;
    int _height;
    int _channels;
    public int len() { return _width * _height * _channels; }

    @Override
    public int compareTo(Dimensions o) {
      return o._width == _width && o._height == _height && o._channels == _channels ? 0 : (len() < o.len() ? -1 : 1);
    }
  }

  //Helper for image conversion
  //TODO: add cropping, distortion, rotation, etc.
  public static class Conversion {
    Conversion() { _dim = new Dimensions(); }
    Dimensions _dim;
    public int len() { return _dim.len(); }
  }

  public static class IcedImage extends Iced<IcedImage> {
    IcedImage(Dimensions dim, float[] data) { _dim = dim; _data = data; }
    Dimensions _dim;
    float[] _data;
  }

  static class ImageConverter extends H2O.H2OCountedCompleter<ImageConverter> {
    String _file;
    float _label;
    Conversion _conv;
    float[] _destData;
    float[] _meanData;
    float[] _destLabel;
    String[] _destFile;
    int _index;
    boolean _cache;
    public ImageConverter(int index, String file, float label, Conversion conv, float[] destData, float[] meanData, float[] destLabel, String[] destFile, boolean cache) {
      _index=index;
      _file=file;
      _label=label;
      _conv=conv;
      _destData=destData;
      _meanData=meanData;
      _destLabel=destLabel;
      _destFile=destFile;
      _cache = cache;
    }

    @Override
    public void compute2() {
      _destFile[_index] = _file;
      _destLabel[_index] = _label;
      try {
        final int start=_index*_conv.len();
        Key imgKey = Key.make(_file + DeepWaterModel.CACHE_MARKER);
        boolean status = false;
        if (_cache) {
          IcedImage icedIm = DKV.getGet(imgKey);
          if (icedIm != null && icedIm._dim.compareTo(_conv._dim)==0) {
            // place the cached image into the right minibatch slot
            for (int i=0; i<icedIm._data.length; ++i)
              _destData[start+i] = icedIm._data[i];
            status = true;
          }
        }
        if (!status) {
          // read the image into a float[], directly into the right place
          water.gpu.util.img2pixels(_file, _conv._dim._width, _conv._dim._height, _conv._dim._channels, _destData, start, _meanData);
          if (_cache)
            DKV.put(imgKey, new IcedImage(_conv._dim, Arrays.copyOfRange(_destData, start, start + _conv.len())));
        }
      } catch (IOException e) {
        Log.warn("unable to convert image " + _file + " to raw float array: " + e.getMessage());
        //e.printStackTrace();
      }
      tryComplete();
    }
  }

  public boolean Next(Futures fs) throws IOException {
    if (_start_index < _val_num) {
      if (_start_index + _batch_size > _val_num)
        _start_index = _val_num - _batch_size;
      // Multi-Threaded data preparation
      Conversion conv = new Conversion();
      conv._dim._height=this._height;
      conv._dim._width=this._width;
      conv._dim._channels=this._channels;
      for (int i = 0; i < _batch_size; i++)
        fs.add(H2O.submitTask(new ImageConverter(i, _img_lst.get(_start_index +i), _label_lst ==null?Float.NaN: _label_lst.get(_start_index +i),conv, _data[_which], _meanData, _label[_which], _file[_which], _cache)));
      fs.blockForPending();
      flip();
      _start_index = _start_index + _batch_size;
      return true;
    } else {
      return false;
    }
  }

  public void flip() { assert(_which ==0 || _which ==1); _which ^=1; }
  public String[] getFiles() { return _file[_which ^1]; }
  public float[] getData() { return _data[_which ^1]; }
  public float[] getLabel() { return _label[_which ^1]; }

  private int _which; //0 or 1
  private int _val_num;
  private int _start_index;
  private int _batch_size;
  private int _width, _height, _channels;
  private float[][] _data;
  private float[] _meanData; //mean image
  private float[][] _label;
  private String[][] _file;
  private ArrayList<String> _img_lst;
  private ArrayList<Float> _label_lst;
  private final boolean _cache;
}
