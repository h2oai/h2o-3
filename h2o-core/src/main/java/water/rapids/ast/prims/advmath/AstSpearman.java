package water.rapids.ast.prims.advmath;

import water.MRTask;
import water.Scope;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Merge;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValNum;
import water.util.FrameUtils;
import water.util.VecUtils;

import java.math.BigDecimal;
import java.math.MathContext;

public class AstSpearman extends AstPrimitive<AstSpearman> {
  @Override
  public int nargs() {
    return 1 + 3; // Frame ID and ID of two numerical vectors to calculate SCC on.
  }

  @Override
  public String[] args() {
    return new String[]{"frame", "first_column", "second_column"};
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    final Frame originalUnsortedFrame = stk.track(asts[1].exec(env).getFrame());
    final int vecIdX = originalUnsortedFrame.find(asts[2].exec(env).getStr());
    final int vecIdY = originalUnsortedFrame.find(asts[3].exec(env).getStr());

    try {
      Scope.enter();
      final SpearmanRankedVectors rankedVectors = rankedVectors(originalUnsortedFrame, vecIdX, vecIdY);
      // Means must be calculated separately - those are not calculated for categorical columns in rollup stats.
      final double[] means = calculateMeans(rankedVectors._x, rankedVectors._y);
      final SpearmanCorrelationCoefficientTask spearman = new SpearmanCorrelationCoefficientTask(means[0], means[1])
              .doAll(rankedVectors._x, rankedVectors._y);
      return new ValNum(spearman.getSpearmanCorrelationCoefficient());
    } finally {
      Scope.exit();
    }

  }

  /**
   * Sorts and ranks the vectors of which SCC is calculated. Original Frame is not modified.
   *
   * @param originalUnsortedFrame Original frame containing the vectors compared.
   * @param vecIdX                First compared vector
   * @param vecIdY                Second compared vector
   * @return An instance of {@link SpearmanRankedVectors}, holding two new vectors with row rank.
   */
  private SpearmanRankedVectors rankedVectors(final Frame originalUnsortedFrame, final int vecIdX, final int vecIdY) {

    Frame comparedVecsWithNas = new Frame(originalUnsortedFrame.vec(vecIdX).makeCopy(),
            originalUnsortedFrame.vec(vecIdY).makeCopy());
    Frame unsortedFrameWithoutNAs = new Merge.RemoveNAsTask(0, 1)
            .doAll(comparedVecsWithNas.types(), comparedVecsWithNas)
            .outputFrame(comparedVecsWithNas.names(), comparedVecsWithNas.domains());

    Frame sortedX = new Frame(unsortedFrameWithoutNAs.vec(0).makeCopy());
    Scope.track(sortedX);
    Frame sortedY = new Frame(unsortedFrameWithoutNAs.vec(1).makeCopy());
    Scope.track(sortedY);

    final boolean xIsOrdered = needsOrdering(sortedX.vec(0));
    final boolean yIsOrdered = needsOrdering(sortedY.vec(0));
    if (xIsOrdered) {
      FrameUtils.labelRows(sortedX, "label");
      sortedX = sortedX.sort(new int[]{0});
      Scope.track(sortedX);

    }

    if (yIsOrdered) {
      FrameUtils.labelRows(sortedY, "label");
      sortedY = sortedY.sort(new int[]{0});
      Scope.track(sortedY);
    }

    assert sortedX.numRows() == sortedY.numRows();
    final Vec orderX = needsOrdering(sortedX.vec(0)) ? Vec.makeZero(sortedX.numRows()) : originalUnsortedFrame.vec(vecIdX);
    final Vec orderY = needsOrdering(sortedY.vec(0)) ? Vec.makeZero(sortedY.numRows()) : originalUnsortedFrame.vec(vecIdY);

    final Vec xLabel = sortedX.vec("label") == null ? sortedX.vec(0) : sortedX.vec("label");
    final Vec xValue = sortedX.vec(0);
    final Vec yLabel = sortedY.vec("label") == null ? sortedY.vec(0) : sortedY.vec("label");
    final Vec yValue = sortedY.vec(0);
    Scope.track(xLabel);
    Scope.track(yLabel);

    final Vec.Writer orderXWriter = orderX.open();
    final Vec.Writer orderYWriter = orderY.open();
    final Vec.Reader xValueReader = xValue.new Reader();
    final Vec.Reader yValueReader = yValue.new Reader();
    final Vec.Reader xLabelReader = xLabel.new Reader();
    final Vec.Reader yLabelReader = yLabel.new Reader();

    // Put the actual rank into the vectors with ranks. Ensure equal values share the same rank.
    double lastX = Double.NaN;
    double lastY = Double.NaN;
    long skippedX = 0;
    long skippedY = 0;
    for (int i = 0; i < orderX.length(); i++) {
      if (xIsOrdered) {
        if (lastX == xValueReader.at(i)) {
          skippedX++;
        } else {
          skippedX = 0;
        }
        lastX = xValueReader.at(i);
        orderXWriter.set(xLabelReader.at8(i) - 1, i - skippedX);
      }
      if (yIsOrdered) {
        if (lastY == yValueReader.at(i)) {
          skippedY++;
        } else {
          skippedY = 0;
        }
        lastY = yValueReader.at(i);
        orderYWriter.set(yLabelReader.at8(i) - 1, i - skippedY);
      }
    }
    orderXWriter.close();
    orderYWriter.close();

    return new SpearmanRankedVectors(orderX, orderY);
  }

  /**
   * Ranked vectors prepared to calculate Spearman's correlation coefficient
   */
  private static class SpearmanRankedVectors {
    private final Vec _x;
    private final Vec _y;

    public SpearmanRankedVectors(Vec x, Vec y) {
      this._x = x;
      this._y = y;
    }
  }

  private boolean needsOrdering(final Vec vec) {
    return !vec.isCategorical();
  }


  @Override
  public String str() {
    return "spearman";
  }

  /**
   * A task to do calculate Spearman's correlation coefficient. Not using the "approximation equation", but the
   * fully-fledged equation resistant against noise from repeated values.
   * The intermediate calculations required for standard deviation of both columns could be calculated by existing code,
   * however the point is to perform the calculations by going through the data only once.
   *
   * @see {@link water.rapids.ast.prims.advmath.AstVariance}
   */
  private static class SpearmanCorrelationCoefficientTask extends MRTask<SpearmanCorrelationCoefficientTask> {
    // Arguments obtained externally
    private final double _xMean;
    private final double _yMean;

    private double spearmanCorrelationCoefficient;

    // Required to later finish calculation of standard deviation
    private double _xDiffSquared = 0;
    private double _yDiffSquared = 0;
    private double _xyMul = 0;
    // If at least one of the vectors contains NaN, such line is skipped
    private long _linesVisited;
    
    /**
     * @param xMean Mean value of the first 'x' vector, with NaNs skipped
     * @param yMean Mean value of the second 'y' vector, with NaNs skipped
     */
    private SpearmanCorrelationCoefficientTask(final double xMean, final double yMean) {
      this._xMean = xMean;
      this._yMean = yMean;
    }

    @Override
    public void map(Chunk[] chunks) {
      assert chunks.length == 2; // Amount of linear correlation only calculated between two vectors at once
      final Chunk xChunk = chunks[0];
      final Chunk yChunk = chunks[1];

      for (int row = 0; row < chunks[0].len(); row++) {
        final double x = xChunk.atd(row);
        final double y = yChunk.atd(row);
        _linesVisited++;

        _xyMul += x * y;
        
        final double xDiffFromMean = x - _xMean;
        final double yDiffFromMean = y - _yMean;
        _xDiffSquared += Math.pow(xDiffFromMean, 2);
        _yDiffSquared += Math.pow(yDiffFromMean, 2);
      }
    }


    @Override
    public void reduce(final SpearmanCorrelationCoefficientTask mrt) {
      // The intermediate results are addable. The final calculations are done afterwards.
      this._xDiffSquared += mrt._xDiffSquared;
      this._yDiffSquared += mrt._yDiffSquared;
      this._linesVisited += mrt._linesVisited;
      this._xyMul += mrt._xyMul;
    }

    @Override
    protected void postGlobal() {
      final double xStdDev = Math.sqrt(_xDiffSquared / _linesVisited);
      final double yStdDev = Math.sqrt(_yDiffSquared / _linesVisited);

      spearmanCorrelationCoefficient = (_xyMul - (_linesVisited * _xMean * _yMean))
              / (_linesVisited * xStdDev * yStdDev);
    }

    public double getSpearmanCorrelationCoefficient() {
      return spearmanCorrelationCoefficient;
    }
  }

  /**
   * Calculates means of given numerical Vectors. Provided there is a NaN on a row in any of the give vectors,
   * the row is skipped and involved in the mean calculation.
   *
   * @param vecs An array of {@link Vec}, must not be empty, nor null. All vectors must be of same length.
   * @return An array of doubles with means for given vectors in the order they were given as arguments.
   * @throws IllegalArgumentException Zero vectors provided,
   */
  private static double[] calculateMeans(final Vec... vecs) throws IllegalArgumentException {
    if (vecs.length < 1) {
      throw new IllegalArgumentException("There are no vectors to calculate means from.");
    }

    final long referenceVectorLength = vecs[0].length();

    for (int i = 0; i < vecs.length; i++) {
      if (!vecs[i].isCategorical() && !vecs[i].isNumeric()) {
        throw new IllegalArgumentException(String.format("Given vector '%s' is not numerical or categorical.",
                vecs[i]._key.toString()));
      }
      if (referenceVectorLength != vecs[i].length()) {
        throw new IllegalArgumentException("Vectors to calculate means from do not have the same length." +
                String.format(" Vector '%s' is of length '%d'", vecs[i]._key.toString(), vecs[i].length()));
      }
    }

    return new MeanTask()
            .doAll(vecs)._means;
  }

  /**
   * Calculates means of given numerical Vectors. Provided there is a NaN on a row in any of the give vectors,
   * the row is skipped and involved in the mean calculation.
   */
  private static class MeanTask extends MRTask<MeanTask> {

    private double[] _means;
    private long _linesVisited = 0;

    @Override
    public void map(Chunk[] cs) {
      // Sums might get big, BigDecimal ensures local accuracy and no overflow
      final BigDecimal[] averages = new BigDecimal[cs.length];
      for (int i = 0; i < averages.length; i++) {
        averages[i] = new BigDecimal(0, MathContext.DECIMAL128);
      }

      row:
      for (int row = 0; row < cs[0].len(); row++) {
        final double[] values = new double[cs.length];
        for (int col = 0; col < cs.length; col++) {
          values[col] = cs[col].atd(row);
          if (Double.isNaN(values[col])) break row; // If a NaN is detected in any of the columns, just skip the row
        }
        _linesVisited++;
        for (int i = 0; i < values.length; i++) {
          averages[i] = averages[i].add(new BigDecimal(values[i], MathContext.DECIMAL128), MathContext.DECIMAL128);
        }
      }

      this._means = new double[cs.length];
      for (int i = 0; i < averages.length; i++) {
        this._means[i] = averages[i].divide(new BigDecimal(_linesVisited), MathContext.DECIMAL64).doubleValue();
      }
    }

    @Override
    public void reduce(MeanTask mrt) {
      final int numChunks = _means.length;
      for (int i = 0; i < numChunks; i++) {
        _means[i] = (_means[i] * _linesVisited + mrt._means[i] * mrt._linesVisited) / (_linesVisited + mrt._linesVisited);
      }
      _linesVisited += mrt._linesVisited;
    }
  }
}
