package hex.deepwater;

import hex.genmodel.GenModel;
import water.*;
import water.util.Log;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;


class DeepWaterImageIterator extends DeepWaterIterator {

  public DeepWaterImageIterator(ArrayList<String> img_lst, ArrayList<Float> lable_lst, float[] meanData, int batch_size, int width, int height, int channels, boolean cache) throws IOException {
    super(batch_size, width*height*channels, cache);
    _img_lst = img_lst;
    _label_lst = lable_lst;
    _meanData = meanData;
    _num_obs = img_lst.size();
    _width = width;
    _height = height;
    _channels = channels;
    _file = new String[2][];
    _file[0] = new String[batch_size];
    _file[1] = new String[batch_size];
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
  private static class Conversion {
    Conversion() { _dim = new Dimensions(); }
    Dimensions _dim;
    public int len() { return _dim.len(); }
  }

  static class IcedImage extends Keyed<IcedImage> {
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
      _cache = cache;
    }

    @Override
    public void compute2() {
      _destLabel[_index] = _label;
      File file = new File(_file);
      try {
        final int start=_index*_conv.len();
        Key<IcedImage> imgKey = Key.make(_file + DeepWaterModel.CACHE_MARKER);
        boolean status = false;
        if (_cache) { //try to get the data from cache first
          IcedImage icedIm = DKV.getGet(imgKey);
          if (icedIm != null && icedIm._dim.compareTo(_conv._dim)==0) {
            // place the cached image into the right minibatch slot
            for (int i=0; i<icedIm._data.length; ++i)
              _destData[start+i] = icedIm._data[i];
            status = true;
          }
        }
        if (!status) {
          boolean isURL = _file.startsWith("http") && !file.exists();
          BufferedImage img;
          if (isURL) img = ImageIO.read(new URL(_file.trim()));
          else       img = ImageIO.read(new File(_file.trim()));
          GenModel.img2pixels(img, _conv._dim._width, _conv._dim._height, _conv._dim._channels, _destData, start, _meanData);
          if (_cache)
            DKV.put(imgKey, new IcedImage(_conv._dim, Arrays.copyOfRange(_destData, start, start + _conv.len())));
        }
      } catch (IOException e) {
        Log.warn(e.getMessage());
      }
      tryComplete();
    }
  }

  public boolean Next(Futures fs) throws IOException {
    if (_start_index < _num_obs) {
      if (_start_index + _batch_size > _num_obs)
        _start_index = _num_obs - _batch_size;
      // Multi-Threaded data preparation
      Conversion conv = new Conversion();
      conv._dim._height=this._height;
      conv._dim._width=this._width;
      conv._dim._channels=this._channels;
      for (int i = 0; i < _batch_size; i++)
        fs.add(H2O.submitTask(new ImageConverter(i, _img_lst.get(_start_index +i), _label_lst ==null?Float.NaN: _label_lst.get(_start_index +i),conv, _data[which()], _meanData, _label[which()], _file[which()], _cache)));
      fs.blockForPending();
      flip();
      _start_index = _start_index + _batch_size;
      return true;
    } else {
      return false;
    }
  }

  public String[] getFiles() { return _file[which() ^1]; }

  final private int _num_obs;
  private int _start_index;
  final private int _width, _height, _channels;
  final private float[] _meanData; //mean image
  final private String[][] _file;
  final private ArrayList<String> _img_lst;
  final private ArrayList<Float> _label_lst;
}
