package hex.pipeline;

import hex.Model.Parameters.FoldAssignmentScheme;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.ast.prims.advmath.AstKFold;

public class KFoldColumnGenerator extends DataTransformer<KFoldColumnGenerator>{
  
  private String _fold_column;
  private final FoldAssignmentScheme _scheme;

  public KFoldColumnGenerator() {
    this(null);
  }

  public KFoldColumnGenerator(String foldColumn) {
    this(foldColumn, FoldAssignmentScheme.AUTO);
  }

  public KFoldColumnGenerator(String foldColumn, FoldAssignmentScheme scheme) {
    super();
    _fold_column = foldColumn;
    _scheme = scheme;
  }

  @Override
  protected void doPrepare(PipelineContext context) {
    assert context != null;
    assert context._params != null;
    if (_fold_column == null) _fold_column = context._params._fold_column;
    Frame withFoldC = transform(context.getTrain(), FrameType.Training, context);
    context.setTrain(withFoldC);
  }

  @Override
  protected Frame doTransform(Frame fr, FrameType type, PipelineContext context) {
    if (type == FrameType.Training && fr.find(_fold_column) < 0) {
      assert context != null;
      assert context._params != null;
      long seed = context._params.getOrMakeRealSeed();
      Vec foldColumn = createFoldColumn(
              fr,
              _scheme,
              context._params._nfolds,
              context._params._response_column,
              seed
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
