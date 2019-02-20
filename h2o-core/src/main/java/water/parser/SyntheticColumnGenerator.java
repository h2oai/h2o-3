package water.parser;

import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

public class SyntheticColumnGenerator extends MRTask<SyntheticColumnGenerator> {
  
  public static Frame addSyntheticColumns(Job<Frame> job, Vec[] parsedVecs, ParseSetup setup, Key[] fkeys, int[] fileChunkOffsets) {
    if (setup._synthetic_column_names != null) {
      Vec[] withSynth = new Vec[parsedVecs.length + setup._synthetic_column_names.length];
      System.arraycopy(parsedVecs, 0, withSynth, 0, parsedVecs.length);
      for (int synthIdx = 0; synthIdx < setup._synthetic_column_names.length; synthIdx++) {
        withSynth[parsedVecs.length + synthIdx] = parsedVecs[0].makeCon(0, Vec.T_STR);
      }
      new SyntheticColumnGenerator(setup, fkeys, fileChunkOffsets).doAll(withSynth);
      return new Frame(job._result, mergeColumnNames(setup), withSynth);
    } else {
      return new Frame(job._result, setup._column_names, parsedVecs);
    }
  }
  
  private static String[] mergeColumnNames(ParseSetup parseSetup) {
    String[] names = new String[parseSetup._column_names.length + parseSetup._synthetic_column_names.length];
    System.arraycopy(parseSetup._column_names, 0, names, 0, parseSetup._column_names.length);
    System.arraycopy(parseSetup._synthetic_column_names, 0, names, parseSetup._column_names.length, parseSetup._synthetic_column_names.length);
    return names;
  }
  
  private final ParseSetup _setup;
  private final Key[] _fkeys;
  private final int[] _fileChunkOffsets;

  SyntheticColumnGenerator(ParseSetup setup, Key[] fkeys, int[] fileChunkOffsets) {
    _setup = setup;
    _fileChunkOffsets = fileChunkOffsets;
    _fkeys = fkeys;
  }

  @Override
  public void map(Chunk[] cs) {
    int synColCnt = _setup._synthetic_column_names.length;
    for (int colIdx = 0; colIdx < synColCnt; colIdx++) {
      String fkey = findFileForChunk(cs[0].cidx());
      String colValue = _setup._synthetic_column_values.get(fkey)[colIdx];
      for (int row = 0; row < cs[0]._len; row++) {
        cs[cs.length - synColCnt + colIdx].set(row, colValue);
      }
    }
  }

  private String findFileForChunk(int cidx) {
    for (int i = 0; i < _fileChunkOffsets.length; i++) {
      if (_fileChunkOffsets[i] <= cidx && (i+1 == _fileChunkOffsets.length || _fileChunkOffsets[i+1] > cidx)) {
        return _fkeys[i].toString() ;
      }
    }
    throw new RuntimeException("Failed to find file for chunk index " + cidx);
  }
}
