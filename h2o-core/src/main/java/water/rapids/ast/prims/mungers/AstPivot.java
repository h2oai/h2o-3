package water.rapids.ast.prims.mungers;

import org.apache.commons.lang.ArrayUtils;
import water.*;
import water.fvec.*;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.vals.ValFrame;
import water.util.VecUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import java.util.Arrays;

public class AstPivot extends AstBuiltin<AstPivot> {
  @Override
  public String[] args() {
    return new String[]{"ary", "index", "column", "value"}; //the array and name of columns
  }

  @Override
  public int nargs() {
    return 1 + 4;
  } // (pivot ary index column value)

  @Override
  public String str() {
    return "pivot";
  }

  @Override
  public ValFrame exec(Val[] args) {
    // Distributed parallelized mrtask pivot
    // Limitations: a single index value cant have more than chunk size * chunk size number of rows
    //  or if all rows of a single index value cant fit on a single node (due to the sort call)
    Frame fr = args[1].getFrame();
    String index = args[2].getStr();
    String column = args[3].getStr();
    String value = args[4].getStr();
    int indexIdx = fr.find(index);
    int colIdx = fr.find(column);
    if(fr.vec(column).isConst())
      throw new IllegalArgumentException("Column: '" + column + "'is constant. Perhaps use transpose?" );
    if(fr.vec(index).naCnt() > 0)
      throw new IllegalArgumentException("Index column '" + index + "' has > 0 NAs");
    // This is the sort then MRTask method.
    // Create the target Frame
    // Now sort on the index key, result is that unique keys will be localized
    Frame fr2 = fr.sort(new int[]{indexIdx});
    final long[] classes = new VecUtils.CollectIntegerDomain().doAll(fr.vec(colIdx)).domain();
    final int nClass = (fr.vec(colIdx).isNumeric() || fr.vec(colIdx).isTime()) ? classes.length : fr.vec(colIdx).domain().length;
    String[] header = null;
    if (fr.vec(colIdx).isNumeric()) {
      header = (String[]) ArrayUtils.addAll(new String[]{index}, Arrays.toString(classes).split("[\\[\\]]")[1].split(", "));
    } else if (fr.vec(colIdx).isTime()) {
      header = new String[nClass];
      for (int i=0;i<nClass;i++) header[i] = (new DateTime(classes[i], DateTimeZone.UTC)).toString();
    } else {
      header = (String[]) ArrayUtils.addAll(new String[]{index}, fr.vec(colIdx).domain());
    }

    Frame initialPass = new pivotTask(fr2.find(index),fr2.find(column),fr2.find(value),classes)
      .doAll(nClass+1, Vec.T_NUM, fr2)
      .outputFrame(null, header, null);
    fr2.delete();
    Frame result = new Frame(initialPass.vec(0).makeCopy(fr.vec(indexIdx).domain(),fr.vec(indexIdx).get_type()));
    result._key = Key.<Frame>make();
    result.setNames(new String[]{index});
    initialPass.remove(0);
    result.add(initialPass);
    return new ValFrame(result);
  }

  private class pivotTask extends MRTask<AstPivot.pivotTask>{
    int _indexColIdx;
    int _colColIdx;
    int _valColIdx;
    long[] _classes;
    pivotTask(int indexColIdx, int colColIdx, int valColIdx, long[] classes) {
      _indexColIdx = indexColIdx; _colColIdx = colColIdx; _valColIdx = valColIdx; _classes=classes;
    }
    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
      // skip past the first rows of the first index if we know that the previous chunk will run in here
      long firstIdx =  cs[_indexColIdx].at8(0);
      long globalIdx = cs[_indexColIdx].start();
      int start = 0;
      if (globalIdx > 0 && cs[_indexColIdx].vec().at8(globalIdx-1)==firstIdx){
        while(start < cs[_indexColIdx].len() && firstIdx == cs[_indexColIdx].at8(start)) start++;
      }
      for (int i=start; i<cs[_indexColIdx]._len; i++) {
        long currentIdx = cs[_indexColIdx].at8(i);
        // start with a copy of the current row
        double[] newRow = new double[nc.length-1];
        Arrays.fill(newRow,Double.NaN);
        if (((i == cs[_indexColIdx]._len -1) &&
              (cs[_indexColIdx].nextChunk() == null || cs[_indexColIdx].nextChunk() != null && currentIdx != cs[_indexColIdx].nextChunk().at8(0)))
          || (i < cs[_indexColIdx]._len -1 && currentIdx != cs[_indexColIdx].at8(i+1))) {

          newRow[ArrayUtils.indexOf(_classes,cs[_colColIdx].at8(i))] = cs[_valColIdx].atd(i);
          nc[0].addNum(cs[_indexColIdx].at8(i));
          for (int j = 1; j < nc.length; j++)  nc[j].addNum(newRow[j - 1]);
          // were done here since we know the next row has a different index
          continue;
        }
        // here we know we have to search ahead
        int count = 1;
        newRow[ArrayUtils.indexOf(_classes,cs[_colColIdx].at8(i))] = cs[_valColIdx].atd(i);

        while ( count + i < cs[_indexColIdx]._len && currentIdx == cs[_indexColIdx].at8(i + count) ) {
          // merge the forward row, the newRow and the existing row
          // here would be a good place to apply aggregating function
          // for now we are aggregating by "first"
          if (Double.isNaN(newRow[ArrayUtils.indexOf(_classes,cs[_colColIdx].at8(i + count))]))  {
            newRow[ArrayUtils.indexOf(_classes,cs[_colColIdx].at8(i + count))] = cs[_valColIdx].atd(i + count);
          }
          count++;
        }
        // need to look if we need to go to next chunk
        if (i + count == cs[_indexColIdx]._len && cs[_indexColIdx].nextChunk() != null) {
          Chunk indexNC = cs[_indexColIdx].nextChunk(); // for the index
          Chunk colNC = cs[_colColIdx].nextChunk(); // for the rest of the columns
          Chunk valNC = cs[_valColIdx].nextChunk(); // for the rest of the columns
          int countNC = 0;
          // If we reach the end of the chunk, we'll update nextChunk and nextChunkArr
          while (indexNC != null && countNC < indexNC._len && currentIdx == indexNC.at8(countNC)) {
              if (Double.isNaN(newRow[ArrayUtils.indexOf(_classes, colNC.at8(countNC))])) {
                newRow[(int) colNC.atd(countNC)] = valNC.atd(countNC);
              }
            }
            countNC++;
            if (countNC == indexNC._len) { // go to the next chunk again
              indexNC = indexNC.nextChunk();
              colNC = colNC.nextChunk();
              valNC = valNC.nextChunk();
              countNC = 0;
            }
        }
        nc[0].addNum(currentIdx);
        for (int j = 1; j < nc.length; j++) {
          nc[j].addNum(newRow[j - 1]);
        }
        i += (count - 1);
      }
    }
  }
}
