package hex.glrm;

import hex.genmodel.algos.glrm.GlrmMojoModel;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.NewChunk;

/**
 * GLRMGenX will generate the coefficients (X matrix) of a GLRM model given the archetype
 * for a dataframe.
 */
public class GLRMGenX  extends MRTask<GLRMGenX> {
  final GLRMModel _m; // contains info to transfer to the glrm mojo model
  final int _k;       // store column size of X matrix
  GlrmMojoModel _gMojoModel;  // instantiate mojo model from GLRM model info

  public GLRMGenX(GLRMModel m, int k) {
    _m = m;
    _m._parms = m._parms;
    _k = k;
  }

  @Override
  protected void setupLocal() {
    _gMojoModel = new GlrmMojoModel(_m._output._names, _m._output._domains, null);
    _gMojoModel._allAlphas = GlrmMojoModel.initializeAlphas(_gMojoModel._numAlphaFactors);  // set _allAlphas array

    GLRM.Archetypes arch = _m._output._archetypes_raw;
    // fill out the mojo model, no need to fill out every field
    _gMojoModel._ncolA = _m._output._lossFunc.length;
    _gMojoModel._ncolY = arch.nfeatures();
    _gMojoModel._nrowY = arch.rank();
    _gMojoModel._ncolX = _m._parms._k;
    _gMojoModel._seed = _m._parms._seed;
    _gMojoModel._regx = _m._parms._regularization_x;
    _gMojoModel._gammax = _m._parms._gamma_x;
    _gMojoModel._init = _m._parms._init;

    _gMojoModel._ncats = _m._output._ncats;
    _gMojoModel._nnums = _m._output._nnums;
    _gMojoModel._normSub = _m._output._normSub;
    _gMojoModel._normMul = _m._output._normMul;
    _gMojoModel._permutation = _m._output._permutation;
    _gMojoModel._reverse_transform = _m._parms._impute_original;
    _gMojoModel._transposed = _m._output._archetypes_raw._transposed;

    // loss functions
    _gMojoModel._losses = _m._output._lossFunc;

    // archetypes
    _gMojoModel._numLevels = arch._numLevels;
    _gMojoModel._catOffsets = arch._catOffsets;
    _gMojoModel._archetypes = arch.getY(false);
  }

  public void map(Chunk[] chks, NewChunk[] preds) {
    int featureLen = chks.length;
    long rowStart = chks[0].start();
    long baseSeed = _gMojoModel._seed+rowStart;

    double[] rowdata = MemoryManager.malloc8d(chks.length);  // read in each row of data
    double[] pdimensions = MemoryManager.malloc8d(_k);
    for (int rid = 0; rid < chks[0]._len; ++rid) {
      for (int col = 0; col < featureLen; col++) {
        rowdata[col] = chks[col].atd(rid);
      }

      _gMojoModel.score0(rowdata, pdimensions, baseSeed+rid); // make prediction
      for (int c=0; c<_k; c++) {
        preds[c].addNum(pdimensions[c]);
      }
    }
  }

  public GlrmMojoWriter getMojo() {
    return new GlrmMojoWriter(_m);
  }
}
