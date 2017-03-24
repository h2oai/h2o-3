package water.rapids.ast.prims.mungers;

import org.apache.commons.lang.ArrayUtils;
import water.*;
import water.fvec.*;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.vals.ValFrame;
import water.util.VecUtils;

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
    Frame fr2 = fr.sort(new int[]{0});
    final long[] classes = new VecUtils.CollectDomain().doAll(fr.vec(colIdx)).domain();
    final int nClass = fr.vec(colIdx).isNumeric() ? classes.length : fr.vec(colIdx).domain().length;
    String[] header = (String[]) ArrayUtils.addAll(new String[]{index}, fr.vec(colIdx).domain());
    Frame initialPassPreSort = new pivotTask(fr2,index,column,value)
      .doAll(nClass+1, Vec.T_NUM, fr2)
      .outputFrame(null, header, null);
    fr2.delete();
    Frame initialPass = initialPassPreSort.sort(new int[]{0});
    initialPassPreSort.delete();
    // Collapse identical index rows even when index crosses over chunk boundaries
    Frame secondPass = new pivotCleanup()
      .doAll(nClass+1,Vec.T_NUM,initialPass)
      .outputFrame(null,header,null);

    initialPass.delete();
    Frame result = new Frame(secondPass.vec(0).makeCopy(null,fr.vec(indexIdx).get_type()));
    result._key = Key.<Frame>make();
    result.setNames(new String[]{index});
    secondPass.remove(0);
    result.add(secondPass);
    return new ValFrame(result);
  }

  private class pivotCleanup extends MRTask<AstPivot.pivotCleanup>{
    pivotCleanup() {}
    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
      // skip past the first rows of the first index if we know that the previous chunk will run in here
      long firstIdx =  cs[0].at8(0);
      long globalIdx = cs[0].start();
      int start = 0;
      if (globalIdx > 0 && cs[0].vec().at8(globalIdx-1)==firstIdx){
        while(start < cs[0].len() && firstIdx == cs[0].at8(start)) start++;
      }
      for (int i=start; i<cs[0]._len; i++) {
        long currentIdx = cs[0].at8(i);
        if (currentIdx != cs[0].at8(i+1)) {
          nc[0].addNum(cs[0].at8(i));
          for (int j=1;j<nc.length;j++) nc[j].addNum(cs[j].atd(i));
          // were done here since we know the next row has a different index
          continue;
        }
        // here we know we have to search ahead
        int count = 1;
        double[] newRow = new double[nc.length-1];
        // start with a copy of the current row
        for (int j = 0; j < nc.length-1; j++) newRow[j] = cs[j+1].atd(i);

        while ( count + i < cs[0]._len && currentIdx == cs[0].at8(i + count) ) {
          // merge the forward row, the newRow and the existing row
          // here would be a good place to apply aggregating function
          // for now we are aggregating by "first"
          for (int j = 0; j < nc.length - 1; j++) {
            if (Double.isNaN(newRow[j]) && !Double.isNaN(cs[j + 1].atd(i + count))) {
              newRow[j] = cs[j + 1].atd(i + count);
            }
          }
          count++;
        }
        // need to look if we need to go to next chunk
        if (i + count == cs[0]._len && cs[0].nextChunk() != null) {
          Chunk nextChunk = cs[0].nextChunk(); // for the index
          Chunk[] nextChunkArr = new Chunk[nc.length - 1]; // for the rest of the columns
          for (int j = 0; j < nc.length - 1; j++) {
            nextChunkArr[j] = cs[j + 1].nextChunk();
          }
          int countNC = 0;
          // If we reach the end of the chunk, we'll update nextChunk and nextChunkArr
          while (nextChunk != null && countNC < nextChunk._len && currentIdx == nextChunk.at8(countNC)) {
            for (int j = 0; j < nc.length - 1; j++) {
              if (Double.isNaN(newRow[j]) && !Double.isNaN(nextChunkArr[j].atd(countNC))) {
                newRow[j] = nextChunkArr[j].atd(countNC);
              }
            }
            countNC++;
            if (countNC == nextChunk._len) { // go to the next chunk again
              nextChunk = nextChunk.nextChunk();
              for (int j = 0; j < nc.length - 1; j++) {
                nextChunkArr[j] = nextChunkArr[j].nextChunk();
              }
              countNC = 0;
            }
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
  private class pivotTask extends MRTask<AstPivot.pivotTask> {
    int _indexColIdx;
    int _colColIdx;
    int _valColIdx;
    pivotTask(Frame fr, String index, String column, String value ) {
      _indexColIdx = fr.find(index); _colColIdx = fr.find(column); _valColIdx = fr.find(value);
    }
    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
      for (int i = 0; i < cs[0]._len; i++) {
        // consolidate locally. we know its sorted by index
        // i will be jumping forward
        long currentIdx = cs[_indexColIdx].at8(i); // the row label
        int count = 0; // the secondary chunk row counter
        double[] newRow = new double[nc.length-1];   // the pivot accumulator
        Arrays.fill(newRow,Double.NaN);
        while (i+count < cs[0]._len && currentIdx == cs[_indexColIdx].at8(i+count)) {
          newRow[(int) cs[_colColIdx].at8(i+count)] = cs[_valColIdx].atd(i+count);
          count++;
        }
        i+=(count-1);
        // load the data into newChunk
        nc[0].addNum(currentIdx);
        for (int j = 1; j < nc.length; j++) {
          nc[j].addNum(newRow[j-1]);
        }


      }
    }

  }
}
