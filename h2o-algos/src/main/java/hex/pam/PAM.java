package hex.pam;

import hex.ClusteringModelBuilder;
import hex.ModelCategory;
import water.*;
import water.fvec.Chunk;
import water.util.ArrayUtils;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Partitioning Around Medoids (PAM)
 * Based on p. 102-104 of "Finding Groups in Data" by Kaufman and Rousseeuw
 * @author eric_eckstrand
 *
 */

public class PAM extends ClusteringModelBuilder<PAMModel,PAMModel.PAMParameters,PAMModel.PAMOutput> {
  @Override public ModelCategory[] can_build() { return new ModelCategory[]{ ModelCategory.Clustering, }; }
  public enum DissimilarityMeasure {EUCLIDEAN, MANHATTAN}
  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; }
  public PAM(PAMModel.PAMParameters parms ) { super(parms); init(false); }
  public PAM(boolean startup_once) { super(new PAMModel.PAMParameters(),startup_once); }
  @Override protected PAMDriver trainModelImpl() { return new PAMDriver(); }

  @Override public void init(boolean expensive) {
    super.init(expensive);
    if (_parms._initial_medoids != null && _parms._initial_medoids.length != _parms._k)
      error("_initial_medoids","must have _k rows");
  }

  // ----------------------
  private class PAMDriver extends Driver {
    @Override public void compute2() {
      PAMModel model = null;
      Frame ddreer = null;
      try {
        Scope.enter();
        _parms.read_lock_frames(_job);
        init(true);

        model = new PAMModel(_job._result, _parms, new PAMModel.PAMOutput(PAM.this));
        model.delete_and_lock(_job);

        Frame f = train();

        // Other implementations of PAM start by creating the n*(n-1)/2 pairwise dissimilarity matrix of observations.
        // The clear downside of creating the dissimilarity matrix is its O(n^2) memory consumption. Currently, the
        // dissimilarity matrix is never computed, but it might be worth doing so if it can reasonably fit in memory.
        //Frame diss = DMatrix.pairwiseDiss(f, DMatrix.transpose(f));

        // Create DDREER Frame to be used in the BUILD and SWAP phases of PAM. Where "D" stands for Dj, "DR" stands for
        // Dj's row, "E" stands for Ej, and "ER" stands for Ej's row. Definitions of Dj and Ej are provided below, as
        // well as in "Finding Groups in Data."
        Key ddreerKey = Key.make("DDREER");

        ddreer = new Frame(ddreerKey, new String[]{"D","DR", "E", "ER", "tempD", "tempDR", "tempE", "tempER", "bestD",
                "bestDR", "bestE", "bestER"},
                new Vec[]{f.anyVec().makeCon(Double.MAX_VALUE), f.anyVec().makeCon(-1),
                        f.anyVec().makeCon(Double.MAX_VALUE), f.anyVec().makeCon(-1),
                        f.anyVec().makeCon(Double.MAX_VALUE), f.anyVec().makeCon(-1),
                        f.anyVec().makeCon(Double.MAX_VALUE), f.anyVec().makeCon(-1),
                        f.anyVec().makeCon(Double.MAX_VALUE), f.anyVec().makeCon(-1),
                        f.anyVec().makeCon(Double.MAX_VALUE), f.anyVec().makeCon(-1)});
        DKV.put(ddreerKey,ddreer);

        model._output._medoids = new double[_parms._k][f.numCols()];
        model._output._medoid_rows = new long[_parms._k]; // parallel array to the above medoids array. holds the row numbers for the respective medoids.

        // BUILD phase - greedily pick the initial k medoids
        // if (_parms._initial_medoids == null)
        buildPhase(f, ddreer, _parms._k, model._output._medoids, model._output._medoid_rows, _parms._dissimilarity_measure);
        // TODO compute initial DDREER when _initial_medoids provided

        model._output._sum_of_dissimilarities = ddreer.vec(0).mean()*ddreer.numRows();
        model._output._swap_iterations = 0;
        model._output._model_summary = createModelSummaryTable(model._output);
        model.update(_job);
        _job.update(1);
        Log.info("Build phase complete!");
        Log.info(model._output._model_summary);

        // SWAP phase - swap medoids with other non-selected observations and see if it improves the configuration
        while (_parms._do_swap && (_parms._k > 1 || _parms._initial_medoids != null) && swapIteration(f, ddreer,
                _parms._k, model._output._medoids, model._output._medoid_rows, _parms._dissimilarity_measure)) {
          model._output._swap_iterations++;
          model._output._sum_of_dissimilarities = ddreer.vec(0).mean()*ddreer.numRows();
          model.update(_job);
          _job.update(1);
          model._output._model_summary = createModelSummaryTable(model._output);
          Log.info("Swap conducted!");
          Log.info(model._output._model_summary);
        }
      } finally {
        if (ddreer != null) DKV.remove(ddreer._key);
        if (model != null) model.unlock(_job);
        _parms.read_unlock_frames(_job);
        Scope.exit(model == null ? null : model._key);
      }
      tryComplete();
    }
  }

  private TwoDimTable createModelSummaryTable(PAMModel.PAMOutput output) {
    List<String> colHeaders = new ArrayList<>();
    List<String> colTypes = new ArrayList<>();
    List<String> colFormat = new ArrayList<>();
    colHeaders.add("Number of Swap Iterations"); colTypes.add("long"); colFormat.add("%d");
    colHeaders.add("Sum of Dissimilarites"); colTypes.add("double"); colFormat.add("%.5f");

    final int rows = 1;
    TwoDimTable table = new TwoDimTable(
            "Model Summary", null,
            new String[rows],
            colHeaders.toArray(new String[0]),
            colTypes.toArray(new String[0]),
            colFormat.toArray(new String[0]),
            "");
    int row = 0;
    int col = 0;
    table.set(row, col++, output._swap_iterations);
    table.set(row, col++, output._sum_of_dissimilarities);
    return table;
  }

  /* Initial medoid selection
   * - BUILD phase of Partitioning Around Medoids (PAM) - Chapter 2 of "Finding Groups in Data", p. 102, Kaufman and
   *   Rousseeuw
   *
   * - First, select the most centrally located observation. This is the observation, i, that minimizes the following:
   *                                   --
   *                                   \
   *                        minimizes  /   d(j,i), where d(j,i) is the dissimilarity (l1 distance) between observations
   *                          i        --          i and j
   *                                   j
   *
   * - The next medoid is found by choosing the observation, i, that "contributes" the most to the medoid configuration.
   *                                   --
   *                                   \
   *                        maximizes  /   Cji, where Cji = max(Dj - d(j,i),0) and Dj is the distance of observation j
   *                          i        --       to its current medoid
   *                                   j
   *   Continue selecting medoids in this fashion until k medoids have been chosen.
   *
   * - At the termiation of the buildPhase() method, Frame parameter `f` will remain unaltered, the first column of
   *   Frame parameter `ddreer` will hold Dj for each observation, and `medoids` and `medoidRows` parameters will be
   *   initialized to the BUILD phase medoids and rows, respectively. */
  private static void buildPhase(Frame f, Frame ddreer, int k, double[][] medoids, long[] medoidRows, DissimilarityMeasure dissimilarityMeasure) {
    Arrays.fill(medoidRows, -1);
    double[] i = new double[f.numCols()]; // observation i
    String[] ddreerColnames = new String[]{"D","DR", "E", "ER", "tempD", "tempDR", "tempE", "tempER", "bestD",
            "bestDR", "bestE", "bestER"};

    for (int m=0; m<k; m++) { // pick k initial medoids

      double bestScore = m == 0 ? Double.MAX_VALUE : -1; // minimize for first medoid, maximize for remaining medoids.

      // Instead of serially looping over each row, an MRTask, which calls BUILDScore for each row, could be
      // implemented. However, this approach would require 2 passes over the data because, at the end of the BUILD
      // phase, the vector of distances of each observation to it's medoid (Dj) is desired. The current implementation
      // only passes over the data once, but requires serial update of Dj vector. Testing needs to be conducted to
      // determine which approach is faster.
      for (long iRow=0; iRow<f.numRows(); iRow++) {
        if (!contains(medoidRows,iRow)) { // for each not-already-chosen observation
          // Retrieve observation i
          for (int c = 0; c < f.numCols(); c++)
            i[c] = f.vec(c).at(iRow);

          // If k==0, compute sum over all j of d(j,i), otherwise compute sum over all j of Cji.
          BUILDScore score = new BUILDScore(i, iRow, m, dissimilarityMeasure).doAll(ArrayUtils.append(f.vecs(), ddreer.vecs()));

          // Serial update
          if (m == 0 ? score._C_j_i < bestScore : score._C_j_i >= bestScore) {
            bestScore = score._C_j_i;
            medoidRows[m] = iRow;
            medoids[m] = ArrayUtils.copyAndFillOf(i, i.length, 0);
            ddreer.swap(4,8); // swap "tempD" and "bestD" columns. "bestD" column now holds the current best Dj
            ddreer.swap(5,9);
            ddreer.swap(6,10);
            ddreer.swap(7,11);
            ddreer.setNames(ddreerColnames.clone());
          }
        }
      }
      assert medoidRows[m] >= 0; // we better have chosen a medoid
      ddreer.swap(0,8); // swap "D" and "bestD" columns. "D" column now holds current best Dj.
      ddreer.swap(1,9);
      ddreer.swap(2,10);
      ddreer.swap(3,11);
      ddreer.setNames(ddreerColnames.clone());
    }
  }

  // If `iteration`==0, compute sum over all j of d(j,i), otherwise compute sum over all j of Cji.
  public static class BUILDScore extends MRTask<BUILDScore> {
    double[] _i; // candidate medoid, i
    long _iRow; // candidate medoid i's row
    double _C_j_i = 0; // collects the contributions of each j
    int _iteration;
    DissimilarityMeasure _dissimilarity_measure;
    BUILDScore(double[] candidateMedoid, long candidateMedoidRow, int iteration, DissimilarityMeasure dissimilarityMeasure) {
      _i = candidateMedoid;
      _iRow = candidateMedoidRow;
      _iteration = iteration;
      _dissimilarity_measure = dissimilarityMeasure;
    }

    @Override
    public void map(Chunk... chks) { // ..., D, DR, E, tempD, tempDR, tempE, bestD, bestDR, bestE
      Chunk dj = chks[chks.length-12];
      Chunk djRow = chks[chks.length-11];
      Chunk tempDj = chks[chks.length-8];
      Chunk tempDjRow = chks[chks.length-7];
      Chunk tempEj = chks[chks.length-6];
      Chunk tempEjRow = chks[chks.length-5];

      double d_j_i; // d(j,i)
      double dj_minus_d_j_i; // Dj - d(j,i)
      double[/*p*/] j = new double[chks.length-12]; // an observation, j
      for (int r=0; r<chks[0]._len; r++) {
        for (int c=0; c<j.length; c++) j[c] = chks[c].atd(r);

        // compute j's distance to the candidate medoid, d(j,i). If `iteration==0`, simply add this to `_C_j_i`.
        // Otherwise, compare d(j,i) to j's distance to its current medoid, Dj, by computing Dj - d(j,i). If this
        // value is positive (i.e. j is closer to i than it is to its current medoid), then add this to `_C_j_i`.
        d_j_i = distance(j, _i, _dissimilarity_measure);
        if (_iteration == 0) {
          tempDj.set(r, d_j_i);
          tempDjRow.set(r, _iRow);
          _C_j_i += d_j_i;
        } else {
          dj_minus_d_j_i =  dj.atd(r) - d_j_i;
          if (dj_minus_d_j_i>0) {
            _C_j_i += dj_minus_d_j_i;
            tempDj.set(r, d_j_i);
            tempDjRow.set(r, _iRow);
            tempEj.set(r, dj.atd(r));
            tempEjRow.set(r, djRow.atd(r));
          } else {
            tempDj.set(r, dj.atd(r));
            tempDjRow.set(r, djRow.atd(r));
            tempEj.set(r, d_j_i);
            tempEjRow.set(r, _iRow);
          }
        }
      }
    }

    @Override
    public void reduce(BUILDScore c) { _C_j_i += c._C_j_i; }
  }

  /* Cluster refinement
  * - SWAP interation - Partitioning Around Medoids (PAM) - Chapter 2 of "Finding Groups in Data", pp. 103-104, Kaufman
  *   and Rousseeuw
  * - For each possible swap, (i, h), where i is a selected observation and h is a non-selected observation, compute
  *   Tih, the contribution of the swap, as follows:
  *                              --
  *                              \
  *                      Tih  =  /   Cjih
  *                              --
  *                              j
  *   Note that there are k*(n-k) possible (i,h) swaps. Compute each non-selected observation's (j) contribution, Cjih,
  *   to the swap as follows:
  *   Case 1: d(j,i) != Dj (i.e. i is not j's representative object/medoid)
  *     Case 1a: d(j,h) < Dj (i.e. j is closer to h than to j's representative object/medoid) => Cjih = d(j,h) - Dj
  *     Case 1b: d(j,h) >= Dj (i.e. j is closer to its medoid than to h)                      => Cjih = 0
  *   Case 2: d(j,i) == Dj (i.e. i is j's medoid)
  *     Case 2a: d(j,h) < Ej (i.e. j is closer to h than to j's second closest medoid)        => Cjih = d(j,h) - Dj
  *     Case 2b: d(j,h) >= Ej (i.e. j is closer to its second closest medoid than h)          => Cjih = Dj - Ej
  *
  * - Record the (i,h) swap that achieved the lowest Tih. If the lowest Tih is >= 0, then terminate the swap phase
  *   by returning false. On the other hand, if the best Tih is < 0, then perform the swap and return true. */
  private static boolean swapIteration(Frame f, Frame ddreer, int k, double[][] medoids, long[] medoidRows,
                                      DissimilarityMeasure dissimilarityMeasure) {
    double tih = Double.MAX_VALUE;
    double[] hBest = new double[f.numCols()];
    long hRowBest = 0;
    long iRowBest = 0;
    String[] ddreerColnames = new String[]{"D","DR", "E", "ER", "tempD", "tempDR", "tempE", "tempER", "bestD",
            "bestDR", "bestE", "bestER"};

    double[] i;
    double[] h = new double[f.numCols()];
    for (int m = 0; m < medoids.length; m++) {
      i = medoids[m];
      long iRow = medoidRows[m];
      for (long hRow = 0; hRow < f.numRows(); hRow++) {
        if (!contains(medoidRows,hRow)) { // don't swap with already-selected medoids
          // retrieve h
          for (int c = 0; c < f.numCols(); c++)
            h[c] = f.vec(c).at(hRow);

          // compute Tih for swap (i,h)
          SWAPScore score = new SWAPScore(k, i, h, iRow, hRow, medoids, medoidRows, dissimilarityMeasure).doAll(
                  ArrayUtils.append(f.vecs(), ddreer.vecs()));

          // Serial Update
          if (score._T_i_h < 0 && score._T_i_h < tih) {
            tih = score._T_i_h;
            hRowBest = hRow;
            iRowBest = iRow;
            hBest = ArrayUtils.copyAndFillOf(h, h.length, 0);
            ddreer.swap(4,8); // swap "tempD" and "bestD" columns. "bestD" column now holds the current best Dj
            ddreer.swap(5,9);
            ddreer.swap(6,10);
            ddreer.swap(7,11);
            ddreer.setNames(ddreerColnames.clone());
          }
        }
      }
    }
    if (tih < 0) {
      // swap (i,h)
      for (int z=0; z<medoidRows.length; z++) {
        if (medoidRows[z] == iRowBest) {
          medoidRows[z] = hRowBest;
          medoids[z] = ArrayUtils.copyAndFillOf(hBest, hBest.length, 0);
          break;
        }
      }
      ddreer.swap(0,8); // swap "D" and "bestD" columns. "D" column now holds current best Dj.
      ddreer.swap(1,9);
      ddreer.swap(2,10);
      ddreer.swap(3,11);
      ddreer.setNames(ddreerColnames.clone());
      return true;
    }
    return false;
  }

  // compute Tih for swap (i,h)
  public static class SWAPScore extends MRTask<SWAPScore> {
    int _k; // number of medoids
    double[] _i; // selected object coordinates
    double[] _h; // non-selected object coordinates
    long _i_row; // selected object row
    long _h_row; // non-selected object row
    double[][] _medoids;
    long[] _medoid_rows;
    double _T_i_h = 0; // the total contribution of swap, Tih
    DissimilarityMeasure _dissimilarity_measure;
    SWAPScore(int k, double[] i, double[] h, long iRow, long hRow, double[][] medoids, long[] medoidRows,
              DissimilarityMeasure dissimilarityMeasure) {
      _k = k;
      _i = i;
      _h = h;
      _i_row = iRow;
      _h_row = hRow;
      _medoids = medoids;
      _medoid_rows = medoidRows;
      _dissimilarity_measure = dissimilarityMeasure;
    }

    @Override
    public void map(Chunk... chks) { // ..., D, DR, E, ER, tempD, tempDR, tempE, tempER, bestD, bestDR, bestE, bestER
      Chunk dj = chks[chks.length - 12];
      Chunk djRow = chks[chks.length - 11];
      Chunk ej = chks[chks.length - 10];
      Chunk ejRow = chks[chks.length - 9];
      Chunk tempDj = chks[chks.length - 8];
      Chunk tempDjRow = chks[chks.length - 7];
      Chunk tempEj = chks[chks.length - 6];
      Chunk tempEjRow = chks[chks.length - 5];

      double[/*p*/] j = new double[chks.length - 12];
      double[] secondClosestDist = new double[1];
      long[] secondClosestRow = new long[1];
      for (int r = 0; r < chks[0]._len; r++) {
        // retrieve j's row number
        long jRow = chks[0].start() + r;
        // retrieve j
        for (int c = 0; c < j.length; c++) j[c] = chks[c].atd(r);

        double d_j_h = distance(_h, j, _dissimilarity_measure);
        if (djRow.at8(r) != _i_row) { // d(j,i) != Dj (i.e. i is not j's selected observation)
          if (d_j_h < dj.atd(r)) {
            _T_i_h += d_j_h - dj.atd(r);
            tempDj.set(r, d_j_h);
            tempDjRow.set(r, _h_row);
            tempEj.set(r, dj.atd(r));
            tempEjRow.set(r, djRow.at8(r));
          } else {
            tempDj.set(r, dj.atd(r));
            tempDjRow.set(r, djRow.at8(r));
            distAndRow(j,_h,_medoids,_medoid_rows,_i_row,_h_row,secondClosestDist,secondClosestRow, _dissimilarity_measure);
            tempEj.set(r, secondClosestDist[0]);
            tempEjRow.set(r, secondClosestRow[0]);
          }
        } else { // i is j's selected observation
          if (d_j_h < ej.atd(r)) {
            _T_i_h += d_j_h - dj.atd(r);
            tempDj.set(r, d_j_h);
            tempDjRow.set(r, _h_row);
            tempEj.set(r, ej.atd(r));
            tempEjRow.set(r, ejRow.at8(r));
          } else {
            _T_i_h += ej.atd(r) - dj.atd(r);
            tempDj.set(r, ej.atd(r));
            tempDjRow.set(r, ejRow.at8(r));
            distAndRow(j,_h,_medoids,_medoid_rows,_i_row,_h_row,secondClosestDist,secondClosestRow, _dissimilarity_measure);
            tempEj.set(r, secondClosestDist[0]);
            tempEjRow.set(r, secondClosestRow[0]);
          }
        }
      }
    }

    @Override
    public void reduce(SWAPScore tih) { _T_i_h += tih._T_i_h; }
  }

  static double distance( double[] ds0, double[] ds1, DissimilarityMeasure dissimilarityMeasure) {
    double sum = 0;
    if (dissimilarityMeasure == DissimilarityMeasure.MANHATTAN) {
      for (int i = 0; i < ds0.length; i++) sum += Math.abs(ds0[i] - ds1[i]);
      return sum;
    } else { // EUCLIDEAN
      for (int i = 0; i < ds0.length; i++) sum += Math.pow(ds0[i] - ds1[i], 2);
      return Math.sqrt(sum);
    }
  }


  static boolean contains(long[] la, long ll) {
    for (long l : la)
      if (l == ll) return true;
    return false;
  }

  static void distAndRow(double[] j, double[] h, double[][] medoids, long[] medoidRows, long iRow, long hRow,
                         double[] secondClosestDist, long[] secondClosestRow, DissimilarityMeasure dissimilarityMeasure)
  {
    double[] firstClosestDist = new double[]{Double.MAX_VALUE};
    long[] firstClosestRow = new long[]{-1};
    secondClosestDist[0] = Double.MAX_VALUE;
    secondClosestRow[0] = -1;
    double potentialFirstClosestDist;
    long potentialFirstClosestRow;

    int idx = 0;
    for (long row : medoidRows) {
      if (row != iRow) {
        potentialFirstClosestDist = distance(j, medoids[idx], dissimilarityMeasure);
        potentialFirstClosestRow = row;
        if (potentialFirstClosestDist < firstClosestDist[0]) {
          secondClosestDist[0] = firstClosestDist[0];
          secondClosestRow[0] = firstClosestRow[0];
          firstClosestDist[0] = potentialFirstClosestDist;
          firstClosestRow[0] = potentialFirstClosestRow;
        } else if (potentialFirstClosestDist < secondClosestDist[0]) {
          secondClosestDist[0] = potentialFirstClosestDist;
          secondClosestRow[0] = potentialFirstClosestRow;
        }
      }
      idx++;
    }
    // check h
    potentialFirstClosestDist = distance(j, h, dissimilarityMeasure);
    if (potentialFirstClosestDist < firstClosestDist[0]) {
      secondClosestDist[0] = firstClosestDist[0];
      secondClosestRow[0] = firstClosestRow[0];
      firstClosestDist[0] = potentialFirstClosestDist;
      firstClosestRow[0] = hRow;
    } else if (potentialFirstClosestDist < secondClosestDist[0]) {
      secondClosestDist[0] = potentialFirstClosestDist;
      secondClosestRow[0] = hRow;
    }
  }
}
