package water.rapids.ast.prims.advmath;

import water.DKV;
import water.MRTask;
import water.Value;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Merge;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.prims.mungers.AstRankWithinGroupBy;
import water.rapids.vals.ValNum;

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
    final Value value = DKV.get(asts[1].str());
    if (!value.isFrame()) {
      throw new IllegalArgumentException(String.format("The given key '%s' is not a frame.", asts[1]));
    }

    final Frame originalUnsortedFrame = value.get(Frame.class);
    final int vecIdX = originalUnsortedFrame.find(asts[2].exec(env).getStr());
    final int vecIdY = originalUnsortedFrame.find(asts[3].exec(env).getStr());

    final Frame unsortedFrameWithoutNAs = new Merge.RemoveNAsTask(vecIdX, vecIdY)
            .doAll(originalUnsortedFrame.types(), originalUnsortedFrame)
            .outputFrame(originalUnsortedFrame.names(), originalUnsortedFrame.domains());

    Frame sortedFrame = unsortedFrameWithoutNAs;
    
    if (!sortedFrame.vec(vecIdX).isCategorical()) {
          // Sort by X
      final AstRankWithinGroupBy.SortnGrouby sortTaskX = new AstRankWithinGroupBy.SortnGrouby(sortedFrame, new int[]{},
              new int[]{vecIdX}, new int[]{1}, "rankX");
      sortTaskX.doAll(sortTaskX._groupedSortedOut);

      AstRankWithinGroupBy.RankGroups rankTaskX = new AstRankWithinGroupBy.RankGroups(sortTaskX._groupedSortedOut, sortTaskX._groupbyCols,
              sortTaskX._sortCols, sortTaskX._chunkFirstG, sortTaskX._chunkLastG, sortTaskX._newRankCol)
              .doAll(sortTaskX._groupedSortedOut);
      sortedFrame = rankTaskX._finalResult;
    }

    if (!sortedFrame.vec(vecIdY).isCategorical()) {
      // Sort by Y
      final AstRankWithinGroupBy.SortnGrouby sortTaskY = new AstRankWithinGroupBy.SortnGrouby(sortedFrame, new int[]{},
              new int[]{vecIdY}, new int[]{1}, "rankY");
      sortTaskY.doAll(sortTaskY._groupedSortedOut);

      AstRankWithinGroupBy.RankGroups rankTaskY = new AstRankWithinGroupBy.RankGroups(sortTaskY._groupedSortedOut, sortTaskY._groupbyCols,
              sortTaskY._sortCols, sortTaskY._chunkFirstG, sortTaskY._chunkLastG, sortTaskY._newRankCol)
              .doAll(sortTaskY._groupedSortedOut);
      sortedFrame = rankTaskY._finalResult;
    }

    final Vec rankX;
    Vec rankY;
    
    if(!sortedFrame.vec(vecIdX).isCategorical()) {
      rankX = sortedFrame.vec("rankX");
    } else {
      rankX = sortedFrame.vec(vecIdX);
    }
    
    if(!sortedFrame.vec(vecIdY).isCategorical()) {
      rankY = sortedFrame.vec("rankY");
    } else {
      rankY = sortedFrame.vec(vecIdY);
    }

    final SpearmanCorrelationCoefficientTask spearman = new SpearmanCorrelationCoefficientTask(sortedFrame.numRows())
            .doAll(rankX, rankY);
    return new ValNum(spearman._spearmanCorrelationCoefficient);
  }


  @Override
  public String str() {
    return "spearman";
  }

  /**
   * A task to do calculate Pearson's correlation coefficient.
   * The intermediate calculations required for standard deviant of both columns could be calculated by existing code,
   * however the point is to perform the calculations by going through the data only once.
   * <p>
   * Note: If one of the observation's columns used to calculate Pearson's corr. coef. contains a NaN value,
   * the whole line is skipped.
   *
   * @see {@link water.rapids.ast.prims.advmath.AstVariance}
   */
  private static class SpearmanCorrelationCoefficientTask extends MRTask<SpearmanCorrelationCoefficientTask> {

    private final long _numRows;
    private double _xyDiff = 0;
    private double _spearmanCorrelationCoefficient;

    /**
     * @param numRows
     */
    private SpearmanCorrelationCoefficientTask(final long numRows) {
      _numRows = numRows;
    }

    @Override
    public void map(Chunk[] chunks) {
      assert chunks.length == 2; // Amount of linear correlation only calculated between two vectors at once
      final Chunk xChunk = chunks[0];
      final Chunk yChunk = chunks[1];

      for (int row = 0; row < chunks[0].len(); row++) {
        _xyDiff += Math.pow(xChunk.atd(row) - yChunk.atd(row), 2);
      }
    }


    @Override
    public void reduce(final SpearmanCorrelationCoefficientTask mrt) {
      _xyDiff += mrt._xyDiff;
    }

    @Override
    protected void postGlobal() {
      _spearmanCorrelationCoefficient =  1 - ((6 * _xyDiff) / (_numRows * (Math.pow(_numRows, 2) - 1)));
    }

    public double getSpearmanCorrelationCoefficient() {
      return _spearmanCorrelationCoefficient;
    }
  }
}
