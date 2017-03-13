package water.rapids.ast.prims.mungers;

import org.apache.commons.lang.ArrayUtils;
import water.*;
import water.fvec.*;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.util.VecUtils;

public class AstPivot extends AstPrimitive {
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
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    // Distributed parallelized mrtask pivot
    // Limitations: a single index value cant have more than chunk size * chunk size number of rows
    //  or if all rows of a single index value cant fit on a single node (due to the sort call)
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    String index = stk.track(asts[2].exec(env)).getStr();
    String column = stk.track(asts[3].exec(env)).getStr();
    String value = stk.track(asts[4].exec(env)).getStr();
    int indexIdx = fr.find(index);
    int colIdx = fr.find(column);
    // This is the sort then MRTask method.
    // Create the target Frame
    // Now sort on the index key, result is that unique keys will be localized
    fr = fr.sort(new int[]{0});
    final long[] classes = new VecUtils.CollectDomain().doAll(fr.vec(colIdx)).domain();
    final int nClass = fr.vec(colIdx).isNumeric() ? classes.length : fr.vec(colIdx).domain().length;
    String[] header = (String[]) ArrayUtils.addAll(new String[]{index}, fr.vec(colIdx).domain());
    Frame initialPass = new pivotTask(fr,index,column,value).doAll(nClass+1, Vec.T_NUM, fr).outputFrame(null, header, null);
    initialPass = initialPass.sort(new int[]{0});
    // Collapse identical index rows even when index crosses over chunk boundaries
    Frame secondPass = new pivotCleanup().doAll(nClass+1,Vec.T_NUM,initialPass).outputFrame(Key.<Frame>make(),header,null);
    Vec origTypeVec = secondPass.vec(0).makeCopy(null,fr.vec(indexIdx).get_type());
    secondPass.replace(0,origTypeVec);
    initialPass.delete();
    return new ValFrame(secondPass);
  }

  protected class pivotCleanup extends MRTask<AstPivot.pivotCleanup>{
    pivotCleanup() {}
    @Override
    public void map(Chunk[] cs, NewChunk[] nc) {
      // skip past the first rows of the first index if we know that the previous chunk will run in here
      long firstIdx =  cs[0].at8(0);
      long globalIdx = cs[0].start();
      int start = 0;
      if (globalIdx > 0 && cs[0].vec().at8(globalIdx-1)==firstIdx){
        while(firstIdx == cs[0].at8(start)) start++;
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
          // need to look if we need to go to next chunk
          if (i + count == cs[0]._len - 1) {
            Chunk nextChunk = cs[0].nextChunk();
            if (nextChunk != null && currentIdx == nextChunk.at8(0)) {
              Chunk[] nextChunkArr = new Chunk[]{};
              for (int j = 0; j < nc.length - 1; j++) {
                nextChunkArr[j] = cs[j + 1].nextChunk();
              }
              int countNC = 0;
              while (currentIdx == nextChunk.at8(countNC)) {
                for (int j = 0; j < nc.length - 1; j++) {
                  if (Double.isNaN(newRow[j]) && !Double.isNaN(nextChunkArr[j].atd(countNC))) {
                    newRow[j] = nextChunkArr[j].atd(countNC);
                  }
                }
                countNC++;
              }
            }
          }
          count++;
        }
        nc[0].addNum(currentIdx);
        for (int j = 1; j < nc.length; j++) {
          nc[j].addNum(newRow[j - 1]);
        }
        i += (count - 1);
      }
    }
  }
  protected class pivotTask extends MRTask<AstPivot.pivotTask> {
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
        for (int j = 0; j < nc.length-1; j++) newRow[j] = Double.NaN;
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
