package water.rapids.ast.prims.mungers;

import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;
import water.rapids.vals.ValFrame;

import java.util.Arrays;


/** Given a dataframe, a list of groupby columns, a list of sort columns, a list of sort directions, a string
 * for the new name of the rank column, an integer sort_cols_order, this class
 * will sort the whole dataframe according to the columns and sort directions.  It will add the rank of the
 * row within the groupby groups based on the sorted order determined by the sort columns and sort directions.  Note
 * that rank starts with 1.
 *
 * If there is any NAs in the sorting columns, the rank of that row will be NA as well.
 *
 * If there is any NAs in the groupby columns, they will be counted as a group and will be given a rank.  The user
 * can choose to ignore the ranks of groupby groups with NAs in them.
 *
 * If sort_cols_order is 1, the returned frame will be sorted according to the sort columns and the sort directions
 * specified earlier.  However, to get a small speed up, the user can set it to 0.  In this case, the returned
 * frame will be sorted according to the groupby columns followed by the sort columns.  This will save you one
 * sort action on the final frame.
 *
 */
public class AstRankWithinGroupBy extends AstPrimitive {

  @Override public String[] args() {
    return new String[]{"frame", "groupby_cols", "sort_cols", "sort_orders", "new_colname", "sort_cols_order"};
  }

  @Override public String str(){ return "rank_within_groupby";}
  @Override public int nargs() { return 1+6; } // (rank_within_groupby frame groupby_cols sort_cols sort_orders new_colname)

  @Override public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame(); // first argument is dataframe
    int[] groupbycols = ((AstParameter)asts[2]).columns(fr.names());
    int[] sortcols =((AstParameter)asts[3]).columns(fr.names());  // sort columns

    int[] sortAsc;
    if (asts[4] instanceof AstNumList)
      sortAsc = ((AstNumList) asts[4]).expand4();
    else
      sortAsc = new int[]{(int) ((AstNum) asts[4]).getNum()};  // R client can send 1 element for some reason
    String newcolname = asts[5].str();
    Boolean sortColsOrder = ((AstNum) asts[6]).getNum()==1;
    
    assert sortAsc.length==sortcols.length;
    SortnGrouby sortgroupbyrank = new SortnGrouby(fr, groupbycols, sortcols, sortAsc, newcolname);
    sortgroupbyrank.doAll(sortgroupbyrank._groupedSortedOut);  // sort and add rank column
    RankGroups rankgroups = new RankGroups(sortgroupbyrank._groupedSortedOut, groupbycols,
            sortcols, sortgroupbyrank._chunkFirstG, sortgroupbyrank._chunkLastG,
            sortgroupbyrank._newRankCol).doAll(sortgroupbyrank._groupedSortedOut);

    if (sortColsOrder)
      return new ValFrame(rankgroups._finalResult.sort(sortcols, sortAsc));
    else
      return new ValFrame(rankgroups._finalResult);
  }

  public boolean foundNAs(Chunk[] chunks, int rind, int[] sortCols, int sortLen) {
    for (int colInd = 0; colInd < sortLen; colInd++) { // check sort columns for NAs
      if (Double.isNaN(chunks[sortCols[colInd]].atd(rind))) {
        return true;
      }
    }
    return false;
  }

  public class RankGroups extends MRTask<RankGroups> {
    final int _newRankCol;
    final int _groupbyLen;
    final int[] _sortCols;
    final int _sortLen;
    final int[] _groupbyCols;
    final GInfoPC[] _chunkFirstG;  // store first Groupby group info per chunk
    final GInfoPC[] _chunkLastG;   // store last Groupby group info per chunk
    Frame _finalResult;

    private RankGroups(Frame inputFrame, int[] groupbycols, int[] sortCols, GInfoPC[] chunkFirstG,
                       GInfoPC[] chunkLastG, int newRankCol) {
      _newRankCol = newRankCol;
      _groupbyCols = groupbycols;
      _groupbyLen = groupbycols.length;
      _sortCols = sortCols;
      _sortLen = sortCols.length;
      _chunkFirstG= chunkFirstG; // store starting rank for next chunk
      _chunkLastG = chunkLastG;
      _finalResult = inputFrame;
    }
    @Override
    public void map(Chunk[] chunks) {
      int cidx = chunks[0].cidx();  // get current chunk id
      long rankOffset = setStartRank(cidx);
      GInfoPC previousKey = _chunkFirstG[cidx]==null ? new GInfoPC(_groupbyLen, 1) : _chunkFirstG[cidx]; // copy over first group info
      GInfoPC rowKey = new GInfoPC(_groupbyLen, 1);

      for (int rind = 0; rind < chunks[0]._len; rind++) {
        if (!Double.isNaN(chunks[_newRankCol].atd(rind)) || !foundNAs(chunks, rind, _sortCols, _sortLen)) { // only rank when sorting columns contains no NAs
          rowKey.fill(rind, chunks, _groupbyCols);
          if (previousKey.equals(rowKey)) {
            rankOffset += 1;
          } else {   // new key
            previousKey.fill(rowKey._gs, 1);  // only key value matter, _gs.
            rankOffset = 1;
          }
          chunks[_newRankCol].set(rind, rankOffset);
        }
      }
    }

    public long setStartRank(int cidx) {
      if (_chunkFirstG[cidx] != null) {
        return _chunkFirstG[cidx]._val;
      } else
        return 0;
    }
  }

  public class SortnGrouby extends MRTask<SortnGrouby> {
    final int[] _sortCols;
    final int[] _groupbyCols;
    final int[] _sortOrders;
    final String _newColname;
    Frame _groupedSortedOut;  // store final result
    GInfoPC[] _chunkFirstG;  // store first groupby class per chunk
    GInfoPC[] _chunkLastG;  // store first groupby class per chunk
    final int _groupbyLen;
    final int _sortLen;
    final int _newRankCol;
    final int _numChunks;

    private SortnGrouby(Frame original, int[] groupbycols, int[] sortCols, int[] sortasc, String newcolname) {
      _sortCols = sortCols;
      _groupbyCols = groupbycols;
      _groupbyLen = _groupbyCols.length;
      _sortLen = sortCols.length;
      _sortOrders = sortasc;
      _newColname = newcolname;

      int[] allSorts = new int[_groupbyLen+_sortLen];
      int[] allSortDirs = new int[allSorts.length];
      System.arraycopy(_groupbyCols, 0, allSorts, 0, _groupbyLen);
      System.arraycopy(_sortCols, 0, allSorts, _groupbyLen, _sortLen);
      Arrays.fill(allSortDirs, 1);
      System.arraycopy(_sortOrders, 0, allSortDirs, _groupbyLen, _sortLen);
      _groupedSortedOut = original.sort(allSorts, allSortDirs); // sort frame

      Vec newrank = original.anyVec().makeCon(Double.NaN);
      _groupedSortedOut.add(_newColname, newrank);  // add new rank column of invalid rank, NAs
      _numChunks = _groupedSortedOut.vec(0).nChunks();
      _chunkFirstG = new GInfoPC[_numChunks];
      _chunkLastG = new GInfoPC[_numChunks];
      _newRankCol = _groupedSortedOut.numCols() - 1;
    }

    /**
     * I will first go from row 0 towards end of chunk to collect info on first group of the chunk.
     * Next, I will go from bottom of chunk towards 0 to collect info on last group of the chunk.
     * It is possible that this chunk may contain only one chunk.  That is okay.  In this case, chunkFirstG
     * and chunkLastG will contain the same information.
     */
    @Override
    public void map(Chunk[] chunks) {
      int cidx = chunks[0].cidx();  // grab chunk id
      int chunkLen = chunks[0].len();
      GInfoPC gWork = new GInfoPC(_groupbyLen, 1);
      int nextGRind = 0;  // row where a new group is found
      int rind = 0;
      for (; rind < chunkLen; rind++) { // go through each row and try to find first groupby group
        if (!foundNAs(chunks, rind, _sortCols, _sortLen)) { // no NA in sort columns
          chunks[_newRankCol].set(rind, 0); // set new rank to 0 from NA
          gWork.fill(rind, chunks, _groupbyCols);
          if (_chunkFirstG[cidx] == null) { // has not found a group yet
            _chunkFirstG[cidx] = new GInfoPC(_groupbyLen, 1);
            _chunkFirstG[cidx].fill(gWork._gs, 1);
          } else {  // found a group already, still the same group?
            if (_chunkFirstG[cidx].equals(gWork)) {
              _chunkFirstG[cidx]._val += 1;
            } else {  // found new group
              nextGRind = rind;
              break;  // found new group
            }
          }
        }
      }

      // short cut to discover if there is only one group or no eligible group in this chunk
      if (nextGRind == 0) { // only one group is found or no group is found (nothing needs to be done for no group case)
        if (_chunkFirstG[cidx] != null) { // one big group in this chunk, lastG will contain the same info.
          _chunkLastG[cidx] = new GInfoPC(_groupbyLen, _chunkFirstG[cidx]._val);
          _chunkLastG[cidx].fill(_chunkFirstG[cidx]._gs, _chunkFirstG[cidx]._val);
        }
      } else {  // has two groups at least, find the last group
        for (int rowIndex = chunks[0]._len - 1; rowIndex >= rind; rowIndex--) {
          if (!foundNAs(chunks, rowIndex, _sortCols, _sortLen)) { // only process eligible rows
            chunks[_newRankCol].set(rowIndex, 0); // set new rank to 0 from NA
            gWork.fill(rowIndex, chunks, _groupbyCols);

            if (_chunkLastG[cidx] == null) { // has not found a group yet
              _chunkLastG[cidx] = new GInfoPC(_groupbyLen, 1);
              _chunkLastG[cidx].fill(gWork._gs, 1);
            } else {  // found a group already, still the same group?
              if (_chunkLastG[cidx].equals(gWork)) {
                _chunkLastG[cidx]._val += 1;
              } else {  // found new group
                break;
              }
            }
          }
        }
      }
    }

    @Override
    public void reduce(SortnGrouby git) {  // copy over the information from one chunk to the final
      copyGroupInfo(_chunkFirstG, git._chunkFirstG);  // copy over first group
      copyGroupInfo(_chunkLastG, git._chunkLastG);    // copy over last group info
    }

    public void copyGroupInfo(GInfoPC[] currentChunk, GInfoPC[] otherChunk) {
      int numChunks = currentChunk.length;
      for (int ind = 0; ind < numChunks; ind++) {
        if (currentChunk[ind] == null) {  // copy over first group info
          if (otherChunk[ind] != null) {
            currentChunk[ind] = new GInfoPC(_groupbyLen, 1);
            currentChunk[ind].fill(otherChunk[ind]._gs, otherChunk[ind]._val);
          }
        }
      }
    }

    @Override
    public void postGlobal() {  // change counts per group per chunk to be cumulative and assign the rank offset
      for (int cInd = 1; cInd < _numChunks; cInd++) {
        if (_chunkLastG[cInd - 1] != null) {
          if (_chunkFirstG[cInd] != null) {
            GInfoPC gPrevious = _chunkLastG[cInd - 1];
            GInfoPC gNext = _chunkFirstG[cInd];

            if (gNext.equals(gPrevious)) {  // same group, need to update rank offset
              gNext._val += gPrevious._val;
              GInfoPC gLast = _chunkLastG[cInd];
              if (gLast.equals(gNext)) {  // chunk contains one big group, update the last group info as well
                gLast._val += gPrevious._val; // one big group in this chunk, last group needs to update to reflect new rank offset
              }
            } else {
              gNext._val = 0; // no rank offset is needed, different groups
            }
          }
        }
      }
      _chunkFirstG[0]._val = 0; // first chunk, there is no offset
    }
  }

  /**
   * Store rank info for each chunk.
   */
  public class GInfoPC extends Iced {
    public final double[] _gs;  // Group Key: Array is final; contents change with the "fill"
    int _hash;
    long _val; // store count of the groupby key inside the chunk

    public GInfoPC(int ncols, long val) {
      _gs = new double[ncols];  // denote a groupby group
      _val = val;               //number of rows belonging to the groupby group
    }

    public GInfoPC fill(int row, Chunk chks[], int cols[]) {
      for (int c = 0; c < cols.length; c++) {// For all selection cols
        _gs[c] = chks[cols[c]].atd(row); // Load into working array
      }
      _val = 1;
      _hash = hash();
      return this;
    }

    public GInfoPC fill(double cols[], long val) {
      for (int c = 0; c < cols.length; c++) {// For all selection cols
        _gs[c] = cols[c]; // Load into working array
      }
      _val = val;
      _hash = hash();
      return this;
    }

    protected int hash() {
      long h = 0;                 // hash is sum of field bits
      for (double d : _gs) h += Double.doubleToRawLongBits(d);
      // Doubles are lousy hashes; mix up the bits some
      h ^= (h >>> 20) ^ (h >>> 12);
      h ^= (h >>> 7) ^ (h >>> 4);
      return (int) ((h ^ (h >> 32)) & 0x7FFFFFFF);
    }

    @Override
    public boolean equals(Object o) { // count keys as equal if they have the same key values.
      return o instanceof GInfoPC && Arrays.equals(_gs, ((GInfoPC) o)._gs); // && _val==((GInfoPC) o)._val;
    }

    @Override
    public int hashCode() {
      return _hash;
    }

    @Override
    public String toString() {
      return Arrays.toString(_gs);
    }
  }
}
