package ai.h2o.automl.targetencoder.strategy;

import hex.Model;
import water.Iced;
import water.Key;

import java.util.Comparator;

public abstract class ModelParametersSelectionStrategy<MP extends Model.Parameters> extends Iced {

  public abstract Evaluated getBestParamsWithEvaluation();


  public static class Evaluated< M extends Model> extends Iced<Evaluated> {

    transient M _model;
    transient M.Parameters _params;
    private double _score;
    // One-based index of evaluation run
    private long _index;

    public Evaluated(M model, double score) {
      _model = model;
      _params = model == null ? null : model._parms;
      _score = score;
    }

    public void setAttemptIdx(long _index) {
      this._index = _index;
    }

    public M getModel() {
      return _model;
    }

    public M.Parameters getParams() {
      return _params;
    }

    public double getScore() {
      return _score;
    }
  }

  public static class EvaluatedComparator extends Iced<EvaluatedComparator> implements Comparator<Evaluated> {
    private boolean _theBiggerTheBetter;

    public EvaluatedComparator(boolean theBiggerTheBetter) {
      _theBiggerTheBetter = theBiggerTheBetter;
    }

    @Override
    public int compare(Evaluated o1, Evaluated o2) {
      int inverseTerm = _theBiggerTheBetter ? -1 : 1;
      return inverseTerm * Double.compare(o1.getScore(), o2.getScore());
    }
  }

}
