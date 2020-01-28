package ai.h2o.automl.targetencoder.strategy;

import hex.Model;
import hex.ModelBuilder;
import water.Iced;

import java.util.Comparator;

public abstract class TEParamsSelectionStrategy<MP extends Model.Parameters> extends Iced {

  public abstract MP getBestParams(ModelBuilder modelBuilder);


  public static class Evaluated<P> extends Iced<Evaluated<P>> {
    public P getItem() {
      return _item;
    }

    public double getScore() {
      return _score;
    }

    transient P _item;
    private double _score;

    // One-based index of evaluation run
    private long _index;

    public Evaluated(P item, double score, long index) {
      _item = item;
      _score = score;
      _index = index;
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
