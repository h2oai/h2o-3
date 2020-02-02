package hex;

import hex.genmodel.utils.DistributionFamily;
import jsr166y.CountedCompleter;
import water.*;
import water.api.FSIOException;
import water.api.HDFSIOException;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.rapids.ast.prims.advmath.AstKFold;
import water.udf.CFuncRef;
import water.util.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 *  tbd
 */
public class ModelPipelineBuilder<M extends Model<M,P,O>, P extends Model.Parameters, O extends Model.Output> extends ModelBuilder<M, P, O> {

  private ModelBuilder<M, P, O> _primaryBuilder;

  public List<Key<Model>> getPreprocessingModels() {
    return _preprocessingModels;
  }

  private List<Key<Model>> _preprocessingModels = new ArrayList<>();


  public ModelPipelineBuilder(ModelBuilder<M, P, O> primaryBuilder) {
    super(primaryBuilder._parms);
    _primaryBuilder = primaryBuilder;
  }

  public void addPreprocessorModel(Key<Model> modelKey) {
    _preprocessingModels.add(modelKey);
  }

  @Override
  protected Driver trainModelImpl() {
    _preprocessingModels.forEach(preprocessingModelKey -> {
      Model preprocessingModel = DKV.getGet(preprocessingModelKey);
      _parms.setTrain(preprocessingModel.score(_parms.train())._key);
      if( _parms.valid() != null) _parms.setValid(preprocessingModel.score(_parms.valid())._key);
      // leaderboard frame should be available when we add model to the Leaderboard
    });
    return _primaryBuilder.trainModelImpl();
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{ // TODO check which categories we support
            ModelCategory.Regression,
            ModelCategory.Binomial,
            ModelCategory.Multinomial,
    };
  }

  @Override
  public boolean isSupervised() {
    return true;
  }
}
