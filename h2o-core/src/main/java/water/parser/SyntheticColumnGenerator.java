package water.parser;

import water.Job;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;

public class SyntheticColumnGenerator extends ParseFinalizer {

  @Override
  public Frame finalize(Job<Frame> job, Vec[] parsedVecs, ParseSetup setup, int[] fileChunkOffsets) {
    Vec[] withSynth = new Vec[parsedVecs.length + setup._synthetic_column_names.length];
    System.arraycopy(parsedVecs, 0, withSynth, 0, parsedVecs.length);
    for (int synthIdx = 0; synthIdx < setup._synthetic_column_names.length; synthIdx++) {
      withSynth[parsedVecs.length + synthIdx] = parsedVecs[0].makeCon(Vec.T_STR);
    }
    new SyntheticColumnGeneratorTask(setup, fileChunkOffsets).doAll(withSynth);

    if (Vec.T_CAT == setup._synthetic_column_type) {
      for (int synthIdx = 0; synthIdx < setup._synthetic_column_names.length; synthIdx++) {
        Vec originalSyntheticVec = withSynth[parsedVecs.length + synthIdx];
        withSynth[parsedVecs.length + synthIdx] = withSynth[parsedVecs.length + synthIdx].toCategoricalVec();
        originalSyntheticVec.remove();
      }
    }
    return new Frame(job._result, mergeColumnNames(setup), withSynth);
  }

  private String[] mergeColumnNames(ParseSetup parseSetup) {
    String[] names = new String[parseSetup._column_names.length + parseSetup._synthetic_column_names.length];
    System.arraycopy(parseSetup._column_names, 0, names, 0, parseSetup._column_names.length);
    System.arraycopy(parseSetup._synthetic_column_names, 0, names, parseSetup._column_names.length, parseSetup._synthetic_column_names.length);
    return names;
  }

  static class SyntheticColumnGeneratorTask extends MRTask<SyntheticColumnGeneratorTask> {

    private final ParseSetup _setup;
    private final int[] _fileChunkOffsets;

    SyntheticColumnGeneratorTask(ParseSetup setup, int[] fileChunkOffsets) {
      _setup = setup;
      _fileChunkOffsets = fileChunkOffsets;
    }

    @Override
    public void map(Chunk[] cs) {
      int synColCnt = _setup._synthetic_column_names.length;
      for (int colIdx = 0; colIdx < synColCnt; colIdx++) {
        int fileIdx = findFileIndexForChunk(cs[0].cidx());
        String colValue = _setup._synthetic_column_values[fileIdx][colIdx];
        for (int row = 0; row < cs[0]._len; row++) {
          cs[cs.length - synColCnt + colIdx].set(row, colValue);
        }
      }
    }

    private int findFileIndexForChunk(int cidx) {
      for (int i = 0; i < _fileChunkOffsets.length; i++) {
        if (_fileChunkOffsets[i] <= cidx && (i+1 == _fileChunkOffsets.length || _fileChunkOffsets[i+1] > cidx)) {
          return i;
        }
      }
      throw new RuntimeException("Failed to find file for chunk index " + cidx);
    }
    
  }
  
}
