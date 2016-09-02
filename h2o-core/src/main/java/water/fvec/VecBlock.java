package water.fvec;

import water.*;
import water.util.IcedBitSet;

import java.util.Arrays;

/**
 * Created by tomas on 7/8/16.
 * Generalized Vec. Can hold multiple columns.
 */
public class VecBlock extends AVec<ChunkBlock> {
  int _nVecs;
  IcedBitSet _removed = new IcedBitSet(0);
  String [][] _domains;

  public VecBlock(Key<AVec> key, int rowLayout, int nCols, String[][] domains, byte[] types) {
    super(key,rowLayout);
    _domains = domains;
    _types = types;
  }

  public String[] domain(int i){return _domains == null?null:_domains[i];}

  @Override
  public byte type(int colId) {
    return 0;
  }

//  public boolean hasVec(int id) {
//    if(_nVecs < id || id < 0) return false;
//    return _removedVecs == null || Arrays.binarySearch(_removedVecs,id) < 0;
//  }


  @Override
  public int numCols(){return _nVecs - (_removedVecs == null?0:_removedVecs.length);}

//  @Override
//  public boolean hasCol(int id) {
//    if(numCols() < id || id < 0) return false;
//    return _removedVecs == null || Arrays.binarySearch(_removedVecs,id) < 0;
//  }

  public RollupStats getRollups(int vecId, boolean histo) {
//    if(!hasVec(vecId)) throw new NullPointerException("vec has been removed");
    throw H2O.unimpl(); // TODO
  }

  @Override
  public synchronized void setDomain(int vec, String[] domain) {
    if(_domains == null)
      _domains = new String[numCols()][];
    _domains[vec] = domain;
  }

  @Override
  public void setType(int i, byte t) {_types[i] = t;}

  @Override
  public Futures removeVecs(final int[] ids, Futures fs) {
    if(ids.length == numCols()) return remove(fs);
    Arrays.sort(ids);
    for(int i = 1; i < ids.length; ++i)
      if(ids[i-1] == ids[i]) throw new IllegalArgumentException("removing duplicate column " + ids[i]);
    fs.add(new TAtomic<VecBlock>() {
      @Override
      protected VecBlock atomic(VecBlock old) {
        for(int i:ids) old._removed.set(i);
        return old;
      }
      @Override public void onSuccess(VecBlock old) {
        if(old.numCols() == 0)
          old.remove(new Futures()); // do not wait for delete to finish
      }
    }.fork(_key));

    new MRTask() {
      @Override public void setupLocal(){
        final int N = 1000;
        for(int i = 0; i < nChunks(); i += N) {
          final int fi = i;
          addToPendingCount(1);
          new H2O.H2OCountedCompleter(this) {
            @Override
            public void compute2() {
              int n = Math.min(fi+N,nChunks());
              Key k = Key.make(_key._kb.clone());
              Futures fs = new Futures();
              for(int i = fi; i < n; ++i) {
                AVec.setChunkId(k,i);
                if(k.home()) {
                  Value v = DKV.get(k);
                  if(v != null) {
                    ChunkBlock cb = v.get();
                    if (cb != null) {
                      for (int j : ids) cb._chunks[j] = null;
                      DKV.DputIfMatch(k, v, new Value(k,cb), fs);
                    }
                  }
                }
              }
              fs.blockForPending();
            }
          }.fork();
        }
      }
    }.doAllNodes();
    return fs;
  }

  @Override
  public void setBad(int colId) {
    throw H2O.unimpl();
  }

  // Vec internal type: one of T_BAD, T_UUID, T_STR, T_NUM, T_CAT, T_TIME
  byte [] _types;                   // Vec Type


  private transient Key _rollupStatsKey;

  private static class SetMutating extends TAtomic<RollupStatsAry> {
    final int _N;
    final int [] _ids;

    SetMutating(int N, int... ids) {_ids = ids; _N = N;}

    @Override
    protected RollupStatsAry atomic(RollupStatsAry old) {
      if(old == null) {
        RollupStats [] rs = new RollupStats[_N];
        for(int i:_ids) rs[i] = RollupStats.makeMutating();
        return new RollupStatsAry(rs);
      }
      for(int i:_ids) old._rs[i] = RollupStats.makeMutating();
      return old;
    }
  }

  public Key rollupStatsKey() {
    if( _rollupStatsKey==null ) _rollupStatsKey=chunkKey(-2);
    return _rollupStatsKey;
  }

  @Override
  public void preWriting(int... colIds) {
    RollupStatsAry rbs = DKV.getGet(rollupStatsKey());
    boolean allreadyLocked = true;
    if(rbs != null){
      for(int i:colIds) {
        if (rbs._rs[i] == null || !rbs._rs[i].isMutating()) {
          allreadyLocked = false;
          break; // Vector already locked against rollups
        }
      }
    }
    if(!allreadyLocked)
      new SetMutating(numCols(),colIds).invoke(rollupStatsKey());
  }

  @Override
  public Futures postWrite(Futures fs) {
    throw H2O.unimpl();
  }

//  @Override
//  public Futures closeChunk(int cidx, AChunk ac, Futures fs) {
//    ChunkBlock cb = (ChunkBlock) ac;
//    boolean modified = false;
//    for(int i = 0; i < cb._chunks[i]._len; ++i) {
//      Chunk c = cb._chunks[i];
//      if(c._chk2 != null) {
//        modified = true;
//        if(c._chk2 instanceof NewChunk)
//          cb._chunks[i] = ((NewChunk) c._chk2).compress();
//      }
//    }
//    if(modified) DKV.put(chunkKey(cidx),cb,fs);
//    return fs;
//  }


  public long byteSize() { return 0; }



  public void close() {
    throw H2O.unimpl(); // TODO
  }

  public void setDomains(String[][] domains) {
    /** Set the categorical/factor names.  No range-checking on the actual
     *  underlying numeric domain; user is responsible for maintaining a mapping
     *  which is coherent with the Vec contents. */
    _domains = domains;
    for(int i = 0; i < domains.length; ++i)
      if( domains[i] != null ) assert _types[i] == Vec.T_CAT;
  }

  @Override
  public VecBlock doCopy(){
    final VecBlock v = new VecBlock(group().addVec(),_rowLayout,_nVecs,_domains,_types);
    new MRTask(){
      @Override public void map(Chunk [] chks){
        chks = chks.clone();
        for(int i = 0; i < chks.length; ++i)
          chks[i] = chks[i].deepCopy();
        DKV.put(v.chunkKey(chks[0].cidx()), new ChunkBlock(chks), _fs);
      }
    }.doAll(new VecAry(this));
    return v;
  }

}
