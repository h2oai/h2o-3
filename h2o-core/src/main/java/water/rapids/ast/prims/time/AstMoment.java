package water.rapids.ast.prims.time;

import org.joda.time.Chronology;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.chrono.ISOChronology;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;

import java.util.ArrayList;


/**
 * Convert year, month, day, hour, minute, sec, msec to Unix epoch time
 * (in milliseconds).
 *
 * This is a replacement for {@code AstMktime} class.
 */
public class AstMoment extends AstBuiltin<AstMoment> {

  @Override public int nargs() {
    return 8;
  }

  public String[] args() {
    return new String[]{"yr", "mo", "dy", "hr", "mi", "se", "ms"};
  }

  @Override public String str() {
    return "moment";
  }


  @Override
  protected ValFrame exec(Val[] args) {

    // Parse the input arguments, verifying their validity.
    boolean naResult = false;
    long numRows = -1;
    int[] timeparts = new int[7];
    ArrayList<Integer> chunksmap = new ArrayList<>(7);
    ArrayList<Vec> timevecs = new ArrayList<>(7);
    for (int i = 0; i < 7; i++) {
      Val vi = args[i + 1];
      if (vi.isFrame()) {
        Frame fr = vi.getFrame();
        if (fr.numCols() != 1)
          throw new IllegalArgumentException("Argument " + i + " is a frame with " + fr.numCols() + " columns");
        if (!fr.vec(0).isNumeric())
          throw new IllegalArgumentException("Argument " + i + " is not a numeric column");
        if (fr.numRows() == 0)
          throw new IllegalArgumentException("Column " + i + " has 0 rows");
        if (fr.numRows() == 1) {
          double d = fr.vec(0).at(0);
          if (Double.isNaN(d))
            naResult = true;
          else
            timeparts[i] = (int) d;
        } else {
          if (numRows == -1)
            numRows = fr.numRows();
          if (fr.numRows() != numRows)
            throw new IllegalArgumentException("Incompatible vec " + i + " having " + fr.numRows() + " rows, whereas " +
                                               "other vecs have " + numRows + " rows.");
          timevecs.add(fr.vec(0));
          chunksmap.add(i);
        }
      } else if (vi.isNum()){
        double d = vi.getNum();
        if (Double.isNaN(d))
          naResult = true;
        else
          timeparts[i] = (int) d;
      } else {
        throw new IllegalArgumentException("Argument " + i + " is neither a number nor a frame");
      }
    }

    // If all arguments are scalars, return a 1x1 frame
    if (timevecs.isEmpty()) {
      double val = Double.NaN;
      if (!naResult) {
        try {
          val = ISOChronology.getInstanceUTC().getDateTimeMillis(timeparts[0], timeparts[1], timeparts[2],
              timeparts[3], timeparts[4], timeparts[5], timeparts[6]);
        } catch (IllegalFieldValueException ignored) {}
      }
      return make1x1Frame(val);
    }

    // If the result is all-NAs, make a constant NA vec
    if (naResult) {
      long n = timevecs.get(0).length();
      Vec v = Vec.makeCon(Double.NaN, n, Vec.T_TIME);
      Frame fr = new Frame(Key.<Frame>make(), new String[]{"time"}, new Vec[]{v});
      return new ValFrame(fr);
    }

    // Some arguments are vecs -- create a frame of the same size
    Vec[] vecs = timevecs.toArray(new Vec[timevecs.size()]);
    int[] cm = ArrayUtils.toPrimitive(chunksmap);
    Frame fr = new SetTimeTask(timeparts, cm)
        .doAll(Vec.T_TIME, vecs)
        .outputFrame(new String[]{"time"}, null);

    return new ValFrame(fr);
  }

  private ValFrame make1x1Frame(double val) {
    Vec v = Vec.makeTimeVec(new double[]{val}, null);
    Frame f = new Frame(new String[]{"time"}, new Vec[]{v});
    return new ValFrame(f);
  }


  private static class SetTimeTask extends MRTask<SetTimeTask> {
    private int[] tp;
    private int[] cm;

    /**
     * @param timeparts is the array of [year, month, day, hrs, mins, secs, ms]
     *                  for all constant parts of the date;
     * @param chunksmap is a mapping between chunks indices and the timeparts
     *                  array. For example, if {@code chunksmap = [1, 2]},
     *                  then the first chunk describes the "month" part of the
     *                  date, and the second chunk the "day" part.
     */
    public SetTimeTask(int[] timeparts, int[] chunksmap) {
      tp = timeparts;
      cm = chunksmap;
    }

    @Override public void map(Chunk[] chks, NewChunk nc) {
      int nVecs = cm.length;
      assert chks.length == nVecs;
      Chronology chronology = ISOChronology.getInstanceUTC();
      int nChunkRows = chks[0]._len;
      int[] tpl = new int[tp.length];
      System.arraycopy(tp, 0, tpl, 0, tp.length);

      BYROW:
      for (int i = 0; i < nChunkRows; i++) {
        for (int j = 0; j < nVecs; j++) {
          double d = chks[j].atd(i);
          if (Double.isNaN(d)) {
            nc.addNum(Double.NaN);
            continue BYROW;
          }
          tpl[cm[j]] = (int) d;
        }
        try {
          double millis = chronology.getDateTimeMillis(tpl[0], tpl[1], tpl[2], tpl[3], tpl[4], tpl[5], tpl[6]);
          nc.addNum(millis);
        } catch (IllegalFieldValueException e) {
          nc.addNum(Double.NaN);
        }
      }
    }
  }
}
