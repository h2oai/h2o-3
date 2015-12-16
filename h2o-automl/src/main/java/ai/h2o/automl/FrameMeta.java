package ai.h2o.automl;

import ai.h2o.automl.collectors.MetaCollector;
import water.H2O;
import water.Iced;
import water.fvec.Frame;

import java.util.ArrayList;

/**
 * Cache common questions asked upon the frame.
 */
public class FrameMeta extends Iced {
  final Frame _fr;
  private final int _response;
  ColMeta[] _cols;

  // cached things
  private String[] _ignoredCols;

  public FrameMeta(Frame fr, int response) {
    _fr=fr;
    _response=response;
    _cols = new ColMeta[_fr.numCols()];
  }

  public String[] ignoredCols() {  // publishes private field
    if( _ignoredCols==null ) {
      ArrayList<Integer> cols = new ArrayList<>();
      for(ColMeta c: _cols)
        if( c._ignored ) cols.add(c._idx);
      _ignoredCols=new String[cols.size()];
      for(int i=0;i<cols.size();++i)
        _ignoredCols[i]=_fr.name(cols.get(i));
    }
    return _ignoredCols;
  }

  public ColMeta response() { return _cols[_response]; }

  // blocking call to compute 1st pass of column metadata
  public FrameMeta computeFrameMetaPass1() {
    MetaCollector.ColMetaTaskPass1[] tasks = new MetaCollector.ColMetaTaskPass1[_fr.numCols()];
    for(int i=0; i<tasks.length; ++i)
      tasks[i] = new MetaCollector.ColMetaTaskPass1(i==_response, _fr.name(i), i);

    MetaCollector.ParallelTasks metaCollector = new MetaCollector.ParallelTasks<>(_fr, tasks);
    H2O.submitTask(metaCollector).join();
    for(MetaCollector.ColMetaTaskPass1 cmt: tasks)
      _cols[cmt._colMeta._idx] = cmt._colMeta;
    return this;
  }
}
