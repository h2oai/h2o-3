package water.rapids.ast.prims.mungers;

import water.MRTask;
import water.fvec.*;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.vals.ValFrame;
import water.util.ArrayUtils;

public class AstMelt extends AstBuiltin<AstPivot> {

  @Override
  public String[] args() {
    return new String[]{"frame", "id_vars", "value_vars", "var_name", "value_name", "skip_na"};
  }

  @Override
  public int nargs() {
    return 1 + 6;
  } // (melt frame, id_vars value_vars var_name value_name skip_na)

  @Override
  public String str() {
    return "melt";
  }

  @Override
  public ValFrame exec(Val[] args) {
    Frame fr = args[1].getFrame();
    String[] idVars = args[2].getStrs();
    String[] valueVars = args[3].isEmpty() ? null : args[3].getStrs();
    String varName = args[4].getStr();
    String valueName = args[5].getStr();
    boolean skipNA = args[6].getBool();

    if (idVars.length == 0) {
      throw new IllegalArgumentException("Empty list of id_vars provided, id_vars needs to have at least one column name.");
    }
    final Frame idFrame = fr.subframe(idVars);
    
    if (valueVars == null) {
      valueVars = ArrayUtils.difference(fr.names(), idFrame.names());
    }
    if (valueVars.length == 0) {
      throw new IllegalArgumentException("Empty list of value_vars provided, value_vars needs to have at least one column name.");
    }
    final Frame valueFrame = fr.subframe(valueVars);

    for (Vec v : valueFrame.vecs()) {
      if (! v.isNumeric()) {
        throw new UnsupportedOperationException("You can only use `melt` with numerical columns. Categorical (and other) columns are not supported.");
      }
    }
    
    byte[] outputTypes = ArrayUtils.append(idFrame.types(), new byte[]{Vec.T_CAT, Vec.T_NUM});
    String[][] outputDomains = ArrayUtils.append(idFrame.domains(), valueVars, null);
    String[] outputNames = ArrayUtils.append(idFrame.names(), varName, valueName);

    Frame result = new MeltTask(idFrame.numCols(), skipNA)
            .doAll(outputTypes, ArrayUtils.append(idFrame.vecs(), valueFrame.vecs()))
            .outputFrame(null, outputNames, outputDomains);
    
    return new ValFrame(result);
  }

  private static class MeltTask extends MRTask<MeltTask> {
    private final int _id_vars_cnt;
    private final boolean _skip_na;

    MeltTask(int id_vars_cnt, boolean skip_na) {
      _id_vars_cnt = id_vars_cnt;
      _skip_na = skip_na;
    }

    @Override
    public void map(Chunk[] cs, NewChunk[] ncs) {
      ChunkVisitor.NewChunkVisitor[] id_ncs = new ChunkVisitor.NewChunkVisitor[_id_vars_cnt];
      for (int i = 0; i < _id_vars_cnt; i++) {
        id_ncs[i] = new ChunkVisitor.NewChunkVisitor(ncs[i]);
      }
      NewChunk var_ncs = ncs[_id_vars_cnt];
      ChunkVisitor.NewChunkVisitor value_ncs = new ChunkVisitor.NewChunkVisitor(ncs[_id_vars_cnt + 1]);
      for (int i = 0; i < cs[0]._len; i++) {
        for (int c = _id_vars_cnt; c < cs.length; c++) {
          if (_skip_na && cs[c].isNA(i))
            continue;
          // copy id vars
          for (int j = 0; j < _id_vars_cnt; j++)
            cs[j].processRows(id_ncs[j], i, i + 1);
          // add var name
          var_ncs.addNum(c - _id_vars_cnt, 0);
          // add value
          cs[c].processRows(value_ncs, i, i + 1);
        }
      }
    }
  }

}
