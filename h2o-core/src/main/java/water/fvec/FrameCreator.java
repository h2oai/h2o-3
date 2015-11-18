package water.fvec;

import hex.CreateFrame;
import jsr166y.CountedCompleter;
import water.*;
import water.util.ArrayUtils;
import water.util.FrameUtils;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import static water.fvec.Vec.makeCon;
import water.util.RandomUtils;

/**
 * Helper to make up a Frame from scratch, with random content
 */
public class FrameCreator extends H2O.H2OCountedCompleter {

  public FrameCreator(CreateFrame createFrame, Key job) {
    super(null);
    _job=job;
    _createFrame = createFrame;

    int[] idx = _createFrame.has_response ? ArrayUtils.seq(1, _createFrame.cols + 1) : ArrayUtils.seq(0, _createFrame.cols);
    int[] shuffled_idx = new int[idx.length];
    ArrayUtils.shuffleArray(idx, idx.length, shuffled_idx, _createFrame.seed, 0);

    int catcols = (int)(_createFrame.categorical_fraction * _createFrame.cols);
    int intcols = (int)(_createFrame.integer_fraction * _createFrame.cols);
    int bincols = (int)(_createFrame.binary_fraction * _createFrame.cols);
    int realcols = _createFrame.cols - catcols - intcols - bincols;

    assert(catcols >= 0);
    assert(intcols >= 0);
    assert(bincols >= 0);
    assert(realcols >= 0);

    _cat_cols  = Arrays.copyOfRange(shuffled_idx, 0,                        catcols);
    _int_cols  = Arrays.copyOfRange(shuffled_idx, catcols,                  catcols+intcols);
    _real_cols = Arrays.copyOfRange(shuffled_idx, catcols+intcols,          catcols+intcols+realcols);
    _bin_cols  = Arrays.copyOfRange(shuffled_idx, catcols+intcols+realcols, catcols+intcols+realcols+bincols);

    // create domains for categorical variables
    _domain = new String[_createFrame.cols + (_createFrame.has_response ? 1 : 0)][];
    if(createFrame.randomize) {
      if(_createFrame.has_response) {
        assert (_createFrame.response_factors >= 1);
        _domain[0] = _createFrame.response_factors == 1 ? null : new String[_createFrame.response_factors];
        if (_domain[0] != null) {
          for (int i = 0; i < _domain[0].length; ++i) {
            _domain[0][i] = new Integer(i).toString();
          }
        }
      }

      for (int c : _cat_cols) {
        _domain[c] = new String[_createFrame.factors];
        for (int i = 0; i < _createFrame.factors; ++i) {
          _domain[c][i] = UUID.randomUUID().toString().subSequence(0, 5).toString();
          // make sure that there's no pure number-labels
          while (_domain[c][i].matches("^\\d+$") || _domain[c][i].matches("^\\d+e\\d+$")) {
            _domain[c][i] = UUID.randomUUID().toString().subSequence(0, 5).toString();
          }
        }
      }
    }
    // All columns together fill one chunk
    final int log_rows_per_chunk = Math.max(1, FileVec.DFLT_LOG2_CHUNK_SIZE - (int)Math.floor(Math.log(_createFrame.cols)/Math.log(2.)));
    _v = makeCon(_createFrame.value, _createFrame.rows, log_rows_per_chunk,false);
  }

  transient Vec _v;
  public int nChunks() { return _v.nChunks(); }
  final private CreateFrame _createFrame;
  private int[] _cat_cols;
  private int[] _int_cols;
  private int[] _real_cols;
  private int[] _bin_cols;
  private String[][] _domain;
  private Frame _out;
  final private Key _job;

  @Override public void compute2() {
    int totcols = _createFrame.cols + (_createFrame.has_response ? 1 : 0);
    Vec[] vecs = new Vec[totcols];
    if(_createFrame.randomize) {
      for (int i = 0; i < vecs.length; ++i)
        vecs[i] = _v.makeZero(_domain[i]);
    } else {
      for (int i = 0; i < vecs.length; ++i)
        vecs[i] = _v.makeCon(_createFrame.value);
    }
    _v.remove();
    _v=null;
    String[] names = new String[vecs.length];
    if(_createFrame.has_response) {
      names[0] = "response";
      for (int i = 1; i < vecs.length; i++) names[i] = "C" + i;
    } else {
      for (int i = 0; i < vecs.length; i++) names[i] = "C" + (i+1);
    }

    _out = new Frame(_createFrame._dest, names, vecs);
    assert _out.numRows() == _createFrame.rows;
    assert _out.numCols() == totcols;
    _out.delete_and_lock(_job);

    // fill with random values
    new FrameRandomizer(_createFrame, _cat_cols, _int_cols, _real_cols, _bin_cols).doAll(_out);

    //overwrite a fraction with N/A
    FrameUtils.MissingInserter mi = new FrameUtils.MissingInserter(_createFrame._dest, _createFrame.seed, _createFrame.missing_fraction);
    mi.execImpl();
    mi.get();
    mi.remove();

    tryComplete();
  }

  @Override public void onCompletion(CountedCompleter caller){
    _out.update(_job);
    _out.unlock(_job);
    ((Job)DKV.getGet(_job)).done();
  }

  private static class FrameRandomizer extends MRTask<FrameRandomizer> {
    final private CreateFrame _createFrame;
    final private int[] _cat_cols;
    final private int[] _int_cols;
    final private int[] _real_cols;
    final private int[] _bin_cols;

    public FrameRandomizer(CreateFrame createFrame, int[] cat_cols, int[] int_cols, int[] real_cols, int[] bin_cols){
      _createFrame = createFrame;
      _cat_cols = cat_cols;
      _int_cols = int_cols;
      _real_cols = real_cols;
      _bin_cols = bin_cols;
    }

    //row+col-dependent RNG for reproducibility with different number of VMs, chunks, etc.
    void setSeed(Random rng, int col, long row) {
      rng.setSeed(_createFrame.seed + _createFrame.cols * row + col);
      rng.setSeed(rng.nextLong());
    }

    @Override
    public void map (Chunk[]cs){
      if (_createFrame.isCancelledOrCrashed()) return;
      if (!_createFrame.randomize) return;
      final Random rng = RandomUtils.getRNG(new Random().nextLong());

      // response
      if(_createFrame.has_response) {
        for (int r = 0; r < cs[0]._len; r++) {
          setSeed(rng, 0, cs[0]._start + r);
          if (_createFrame.response_factors > 1)
            cs[0].set(r, (int) (rng.nextDouble() * _createFrame.response_factors)); //classification
          else if (_createFrame.positive_response)
            cs[0].set(r, _createFrame.real_range * rng.nextDouble()); //regression with positive response
          else
            cs[0].set(r, _createFrame.real_range * (1 - 2 * rng.nextDouble())); //regression
        }
      }
      _createFrame.update(1);
      for (int c : _cat_cols) {
        for (int r = 0; r < cs[c]._len; r++) {
          setSeed(rng, c, cs[c]._start + r);
          cs[c].set(r, (int)(rng.nextDouble() * _createFrame.factors));
        }
      }
      _createFrame.update(1);
      for (int c : _int_cols) {
        for (int r = 0; r < cs[c]._len; r++) {
          setSeed(rng, c, cs[c]._start + r);
          cs[c].set(r, -_createFrame.integer_range + (long)(rng.nextDouble()*(2*_createFrame.integer_range+1)));
        }
      }
      _createFrame.update(1);
      for (int c : _real_cols) {
        for (int r = 0; r < cs[c]._len; r++) {
          setSeed(rng, c, cs[c]._start + r);
          cs[c].set(r, _createFrame.real_range * (1 - 2 * rng.nextDouble()));
        }
      }
      _createFrame.update(1);
      for (int c : _bin_cols) {
        for (int r = 0; r < cs[c]._len; r++) {
          setSeed(rng, c, cs[c]._start + r);
          cs[c].set(r, rng.nextFloat() > _createFrame.binary_ones_fraction ? 0 : 1);
        }
      }
      _createFrame.update(1);
    }
  }
}
