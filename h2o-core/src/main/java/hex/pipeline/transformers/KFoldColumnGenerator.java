package hex.pipeline.transformers;

import hex.Model.Parameters.FoldAssignmentScheme;
import hex.pipeline.DataTransformer;
import hex.pipeline.PipelineContext;
import water.DKV;
import water.KeyGen;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.ast.prims.advmath.AstKFold;

public class KFoldColumnGenerator extends DataTransformer<KFoldColumnGenerator> {
  
  private static final int DEFAULT_NFOLDS = 5;
  
  static String FOLD_COLUMN_PREFIX = "__fold__";
  
  private String _fold_column;
  private FoldAssignmentScheme _scheme;
  
  private int _nfolds;
  private long _seed;
  
  private String _response_column;
  
  private final KeyGen _trainWFoldKeyGen = new KeyGen.PatternKeyGen("{0}_wfoldc");

  public KFoldColumnGenerator() {
    this(null);
  }

  public KFoldColumnGenerator(String foldColumn) {
    this(foldColumn, null, -1, -1);
  }

  public KFoldColumnGenerator(String foldColumn, FoldAssignmentScheme scheme, int nfolds, long seed) {
    super();
    _fold_column = foldColumn;
    _scheme = scheme;
    _nfolds = nfolds;
    _seed = seed;
  }

  @Override
  protected void doPrepare(PipelineContext context) {
    assert context != null;
    assert context._params != null;
    if (_fold_column == null) _fold_column = context._params._fold_column;
    if (_fold_column == null) _fold_column = FOLD_COLUMN_PREFIX+context._params._response_column;
    
    if (_scheme == null) _scheme = context._params._fold_assignment;
    if (_scheme == null) _scheme = FoldAssignmentScheme.AUTO;
    
    if (_nfolds <= 0) _nfolds = context._params._nfolds;
    if (_nfolds <= 0) _nfolds = DEFAULT_NFOLDS;
    
    if (_seed < 0) _seed = context._params.getOrMakeRealSeed();
    
    if (_response_column == null) _response_column = context._params._response_column;
    assert !(_response_column == null && _scheme == FoldAssignmentScheme.Stratified);
    
    if (context.getTrain() != null && context.getTrain().find(_fold_column) < 0) {
      Frame withFoldC = doTransform(context.getTrain(), FrameType.Training, context);
      withFoldC._key = _trainWFoldKeyGen.make(context.getTrain()._key);
      DKV.put(withFoldC);
      Scope.track(withFoldC);
      context.setTrain(withFoldC);
    }
    // now that we have a fold column, reassign cv params to avoid confusion
    context._params._fold_column = _fold_column;
    context._params._nfolds = 0;
    context._params._fold_assignment = FoldAssignmentScheme.AUTO;
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    if (type == FrameType.Training && fr.find(_fold_column) < 0) {
      Vec foldColumn = createFoldColumn(
              fr,
              _scheme,
              _nfolds,
              _response_column,
              _seed
      );
      Frame withFoldc = new Frame(fr);
      withFoldc.add(_fold_column,  foldColumn);
      return withFoldc;
    }
    return fr;
  }

  static Vec createFoldColumn(Frame fr,
                              FoldAssignmentScheme fold_assignment,
                              int nfolds,
                              String responseColumn,
                              long seed) {
    Vec foldColumn;
    switch (fold_assignment) {
      default:
      case AUTO:
      case Random:
        foldColumn = AstKFold.kfoldColumn(fr.anyVec().makeZero(), nfolds, seed);
        break;
      case Modulo:
        foldColumn = AstKFold.moduloKfoldColumn(fr.anyVec().makeZero(), nfolds);
        break;
      case Stratified:
        foldColumn = AstKFold.stratifiedKFoldColumn(fr.vec(responseColumn), nfolds, seed);
        break;
    }
    return foldColumn;
  }

}
