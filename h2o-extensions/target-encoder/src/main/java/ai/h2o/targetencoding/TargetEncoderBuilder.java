package ai.h2o.targetencoding;

import hex.ModelBuilder;
import hex.ModelCategory;
import water.Scope;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.IcedHashMapGeneric;
import water.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TargetEncoderBuilder extends ModelBuilder<TargetEncoderModel, TargetEncoderModel.TargetEncoderParameters, TargetEncoderModel.TargetEncoderOutput> {

  public TargetEncoderModel getTargetEncoderModel() {
    assert _targetEncoderModel != null : "Training phase of the TargetEncoderBuilder did not take place yet. TargetEncoderModel is not available.";
    return _targetEncoderModel;
  }

  private TargetEncoderModel _targetEncoderModel;
  
  public TargetEncoderBuilder(TargetEncoderModel.TargetEncoderParameters parms) {
    super(parms);
    super.init(false);
  }

  public TargetEncoderBuilder(final boolean startupOnce) {
    super(new TargetEncoderModel.TargetEncoderParameters(), startupOnce);
  }

  @Override
  public void init(boolean expensive) {
    super.init(expensive);
  }

  private class TargetEncoderDriver extends Driver {
    @Override
    public void computeImpl() {
      final String[] encodedColumns = encodedColumnsFromIgnored(_parms.train()._names, _parms._ignored_columns,
              _parms._response_column, _parms._fold_column);
      TargetEncoder tec = new TargetEncoder(encodedColumns);

      Scope.untrack(train().keys());

      IcedHashMapGeneric<String, Frame> _targetEncodingMap = tec.prepareEncodingMap(train(), _parms._response_column, _parms._fold_column);

      // Mean could be computed from any encoding map as response column is shared
      double priorMean = tec.calculatePriorMean(_targetEncodingMap.entrySet().iterator().next().getValue());

      for(Map.Entry<String, Frame> entry: _targetEncodingMap.entrySet()) {
        Frame frameWithEncodingMap = entry.getValue();
        Scope.untrack(frameWithEncodingMap.keys());
      }

      disableIgnoreConstColsFeature();
      TargetEncoderModel.TargetEncoderOutput output = new TargetEncoderModel.TargetEncoderOutput(TargetEncoderBuilder.this, _targetEncodingMap, priorMean);
      _targetEncoderModel = new TargetEncoderModel(_job._result, _parms, output, tec);

      _targetEncoderModel.write_lock(_job);
      _targetEncoderModel.unlock(_job);
    }


    private String[] encodedColumnsFromIgnored(final String[] minued, final String[] subtrahend, final String responseName,
                                                      final String foldColumnName) {
      final Set<String> subtrahendSet = new HashSet(subtrahend.length + 1);
      for (String name : subtrahend) {
        subtrahendSet.add(name);
      }

      if (responseName != null) {
        subtrahendSet.add(responseName);
      }

      if (foldColumnName != null) {
        subtrahendSet.add(foldColumnName);
      }

      final Set<String> results = new HashSet<>();
      for (String name : minued) {
        if (!subtrahendSet.contains(name)) {
          results.add(name);
        }
      }
      return results.toArray(new String[0]);
    }

    private void disableIgnoreConstColsFeature() {
      _parms._ignore_const_cols = false;
      Log.info("We don't want to ignore any columns during target encoding transformation therefore `_ignore_const_cols` parameter was set to `false`");
    }
  }

  /**
   * Never do traditional cross-validation for Target Encoder Model. The {@link TargetEncoder} class handles
   * fold column on it's own.
   *
   * @return Always false
   */
  @Override
  public boolean nFoldCV() {
    return false;
  }

  @Override
  protected Driver trainModelImpl() {
    // We can use Model.Parameters to configure Target Encoder
    return new TargetEncoderDriver();
  }
  
  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{ ModelCategory.TargetEncoder};
  }

  @Override
  public boolean isSupervised() {
    return true;
  }

  @Override
  public BuilderVisibility builderVisibility() {
    return BuilderVisibility.Stable;
  }

  @Override
  public boolean haveMojo() {
    return true;
  }

  @Override
  public String getName() {
    return "targetencoder";
  }
}
