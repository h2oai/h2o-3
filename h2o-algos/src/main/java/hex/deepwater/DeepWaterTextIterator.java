package hex.deepwater;

import water.*;
import water.fvec.C4FChunk;
import water.util.StringUtils;
import water.util.UnsafeUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import static water.util.StringUtils.PADDING_SYMBOL;


class DeepWaterTextIterator extends DeepWaterIterator {

  private static ConcurrentHashMap<String,Integer> dict;
  static private ConcurrentHashMap getDict() {
    if (dict == null) {
//      try {
        dict = new ConcurrentHashMap<>();
        int count = 0;
        dict.put(PADDING_SYMBOL, count++);
//        // if available, pre-fill the dict
//        FileInputStream is = new FileInputStream("/home/arno/top100kwords.txt");
//        BufferedReader br = new BufferedReader(new InputStreamReader(is));
//        String line;
//        while ((line = br.readLine()) != null)
//          dict.put(line, count++);
//        is.close();
//      } catch (IOException e) {
//        e.printStackTrace();
//      }
    }
    return dict;
  }

  DeepWaterTextIterator(ArrayList<String> txt_lst, ArrayList<Float> label_lst, int batch_size, int dictLen, boolean cache) throws IOException {
    super(batch_size, dictLen, cache);
    _wordsPerLine = dictLen;
    _start_index = 0;
    _txt_list = txt_lst;
    _label_lst = label_lst;
    _num_obs = txt_lst.size();
  }

  /**
   * Turn a line of text into an array of integers (each word has its unique ID)
   */
  static class TextConverter extends H2O.H2OCountedCompleter<TextConverter> {
    String _text;
    float _label;
    float[] _destData;
    int _wordsPerLine;
    float[] _destLabel;
    int _index; //within minibatch
    int _globalId; //row index
    boolean _cache;
    TextConverter(int index, int globalId, String text, float label, float[] destData, int wordsPerLine, float[] destLabel, boolean cache) {
      _index=index;
      _globalId=globalId;
      _text = text;
      _label=label;
      _destData=destData;
      _wordsPerLine = wordsPerLine;
      _destLabel=destLabel;
      _cache = cache;
    }

    synchronized
    @Override
    public void compute2() {
      _destLabel[_index] = _label;
      final int start=_index* _wordsPerLine;
      Key txtKey = Key.make("line_" + _globalId + DeepWaterModel.CACHE_MARKER);
      boolean status = false;
      if (_cache) { //try to get the data from cache first
        C4FChunk icedTxt = DKV.getGet(txtKey);
        if (icedTxt != null) {
          // place the cached txt into the right minibatch slot
          for (int i=0; i<icedTxt._len; ++i)
            _destData[start+i] = (float)icedTxt.atd(i);
          status = true;
        }
      }
      if (!status) {
        int[] data = StringUtils.tokensToArray(StringUtils.tokenize(_text), _wordsPerLine, DeepWaterTextIterator.getDict());
//        System.err.println(Arrays.toString(data));
        for (int i = 0; i< _wordsPerLine; ++i) {
          _destData[start + i] = (float)data[i];
        }
        if (_cache) {
          byte[] mem = new byte[_wordsPerLine *4];
          for (int i = 0; i< _wordsPerLine; ++i)
            UnsafeUtils.set4f(mem,i<<2, _destData[start + i]);
          Value v = new Value(txtKey,new C4FChunk(mem));
          DKV.put(txtKey, v);
          v.freeMem();
        }
      }
      tryComplete();
    }
  }

  public boolean Next(Futures fs) throws IOException {
    if (_start_index < _num_obs) {
      if (_start_index + _batch_size > _num_obs)
        _start_index = _num_obs - _batch_size;
      // Multi-Threaded data preparation
      for (int i = 0; i < _batch_size; i++)
        fs.add(H2O.submitTask(new TextConverter(i, _start_index + i, _txt_list.get(_start_index +i),
            _label_lst == null?Float.NaN : _label_lst.get(_start_index +i),_data[which()], _wordsPerLine, _label[which()], _cache)));
      fs.blockForPending();
      flip();
      _start_index = _start_index + _batch_size;
      return true;
    } else {
      return false;
    }
  }

  final private int _num_obs;
  final private int _wordsPerLine;
  final private ArrayList<String> _txt_list;
  final private ArrayList<Float> _label_lst;
}
