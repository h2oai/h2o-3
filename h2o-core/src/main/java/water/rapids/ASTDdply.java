package water.rapids;

import water.*;
import water.fvec.*;
import water.util.*;

import java.util.Arrays;

/** Ddply
 *  Group the rows of 'data' by unique combinations of '[group-by-cols]'.
 *  Apply any function 'fcn' to a group Frame, which must accept a Frame (and
 *  any "extra" arguments) and return a single scalar value.
 *
 *  Returns a set of grouping columns, with the single answer column, with one
 *  row per unique group.
 *  
 */
class ASTDdply extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "groupByCols", "fun"}; }
  @Override int nargs() { return 1+3; } // (ddply data [group-by-cols] fcn )
  @Override public String str() { return "ddply"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int ncols = fr.numCols();
    ASTNumList groupby = ASTGroup.check(fr, asts[2]);
    AST fun = asts[3].exec(env).getFun();
    ASTFun scope = env._scope;  // Current execution scope; needed to lookup variables
    // Frame of group keys
    Frame fr_keys = ASTGroup.gbFrame(fr,groupby.expand4());
    final int ngbCols = fr_keys.numCols();

    // Pass 1: Find all the groups (and count rows-per-group)
    IcedHashMap<ASTGroup.GKX,String> gs = ASTGroup.findGroups(fr_keys);
    final ASTGroup.GK0[] grps = ASTGroup.sortGroups(gs);

    // Pass 2: Build all the groups, building 1 Vec per-group, with exactly the
    // same Chunk layout, except each Chunk will be the filter rows numbers; a
    // list of the Chunk-relative row-numbers for that group in an original
    // data Chunk.  Each Vec will have a *different* number of rows.
    Vec[] vgrps = new BuildGroup(gs).doAll(gs.size(), Vec.T_NUM, fr).close();

    // Pass 3: For each group, build a full frame for the group, run the
    // function on it and tear the frame down.
    final RemoteRapids[] remoteTasks = new RemoteRapids[gs.size()]; // gather up the remote tasks...
    Futures fs = new Futures();
    for( int i=0; i<remoteTasks.length; i++ )
      fs.add(RPC.call(vgrps[i]._key.home_node(), remoteTasks[i] = new RemoteRapids(fr, vgrps[i]._key, fun, scope)));
    fs.blockForPending();
    
    // Build the output!
    final int res_len = remoteTasks[0]._result.length; // Sample output length
    String[] fcnames = new String[res_len];
    for( int i=0; i<res_len; i++ )
      fcnames[i] = "ddply_C"+(i+1);

    MRTask mrfill = new MRTask() {
      @Override public void map(Chunk[] c, NewChunk[] ncs) {
        final int start = (int)c[0].start();
        final int len = c[0]._len;
        for( int i=0; i<len; i++ ) {
          int gnum = i+start;
          ASTGroup.GKX gkx = grps[gnum]; // One Group per row
          gkx.setkey(ncs);      // The Group Key in the first cols
          double[] res = remoteTasks[i+start]._result;
          for( int a=0; a<res.length; a++ )
            ncs[a+ngbCols].addNum(res[a]);
        }
      }
      };
    
    Frame f = ASTGroup.buildOutput(fr_keys, res_len, fr, fcnames, gs.size(), mrfill);
    return new ValFrame(f);
  }

  // --------------------------------------------------------------------------
  // Build all the groups, building 1 Vec per-group, with exactly the same
  // Chunk layout, except each Chunk will be the filter rows numbers; a list
  // of the Chunk-relative row-numbers for that group in an original data Chunk.
  private static class BuildGroup extends MRTask<BuildGroup> {
    final IcedHashMap<ASTGroup.GKX,String> _gs;
    BuildGroup( IcedHashMap<ASTGroup.GKX,String> gs ) { _gs = gs; }
    @Override public void map( Chunk[] cs, NewChunk[] ncs ) {
      ASTGroup.GKX g = ASTGroup.GKX.init(cs.length);
      final int len = cs[0]._len;
      for( int row=0; row<len; row++ ) {
        int gnum = _gs.getk(g.fill(cs,row,0))._gnum;
        ncs[gnum].addNum(row);  // gather row-numbers per-chunk per-group
      }
    }
    // Gather all the output Vecs.  Note that each Vec has a *different* number
    // of rows, and taken together they do NOT make a valid Frame.
    Vec[] close() {
      Futures fs = new Futures();
      Vec[] vgrps = new Vec[_gs.size()];
      for( int i = 0; i < vgrps.length; i++ )
        vgrps[i] = _appendables[i].close(_appendables[i].compute_rowLayout(),fs);
      fs.blockForPending();
      return vgrps;
    }
  }

  // --------------------------------------------------------------------------
  private static class RemoteRapids extends DTask<RemoteRapids> {
    private Frame _data;        // Data frame
    private Key<Vec> _vKey;     // the group to process...
    private AST _fun;           // the ast to execute on the group
    private ASTFun _scope;      // Execution environment
    private double[] _result;   // result is 1 row per group!

    RemoteRapids( Frame data, Key<Vec> vKey, AST fun, ASTFun scope) {
      _data = data; _vKey=vKey; _fun=fun; _scope = scope;
      // Always 1 higher priority than calling thread... because the caller will
      // block & burn a thread waiting for this MRTask to complete.
      Thread cThr = Thread.currentThread();
      _priority = (byte)((cThr instanceof H2O.FJWThr) ? ((H2O.FJWThr)cThr)._priority+1 : super.priority());
    }

    final private byte _priority;
    @Override public byte priority() { return _priority; }
    @Override public void compute2() {
      assert _vKey.home();
      final Vec gvec = DKV.getGet(_vKey);
      assert gvec.group().equals(_data.anyVec().group());

      // Make a group Frame, using wrapped Vecs wrapping the original data
      // frame with the filtered Vec passed in.  Run the function, getting a
      // scalar or a 1-row Frame back out.  Delete the group Frame.  Return the
      // 1-row Frame as a double[] of results for this group.

      // Make the subset Frame Vecs, no chunks yet
      Key<Vec>[] groupKeys = gvec.group().addVecs(_data.numCols());
      final Vec[] groupVecs = new Vec[_data.numCols()];
      Futures fs = new Futures();
      for( int i=0; i<_data.numCols(); i++ )
        DKV.put(groupVecs[i] = new Vec(groupKeys[i], gvec._rowLayout, gvec.domain(), gvec.get_type()), fs);
      fs.blockForPending();
      // Fill in the chunks
      new MRTask() {
        @Override public void setupLocal() {
          Vec[] data_vecs = _data.vecs();
          for( int i=0; i<gvec.nChunks(); i++ )
            if( data_vecs[0].chunkKey(i).home() ) {
              Chunk rowchk = gvec.chunkForChunkIdx(i);
              for( int col=0; col<data_vecs.length; col++ )
                DKV.put( Vec.chunkKey(groupVecs[col]._key,i), new SubsetChunk(data_vecs[col].chunkForChunkIdx(i),rowchk,groupVecs[col]), _fs);
            }
        }
      }.doAllNodes();
      Frame groupFrame = new Frame(_data._names,groupVecs);

      // Now run the function on the group frame
      Env env = new Env();
      env._scope = _scope;      // Build an environment with proper lookup scope
      Val val = new ASTExec( new AST[]{_fun,new ASTFrame(groupFrame)}).exec(env);
      assert env.sp()==0;

      // Result into a double[]
      if( val.isFrame() ) {
        Frame res = val.getFrame();
        if( res.numRows() != 1 )
          throw new IllegalArgumentException("ddply must return a 1-row (many column) frame, found "+res.numRows());
        _result = new double[res.numCols()];
        for( int i=0; i<res.numCols(); i++ )
          _result[i] = res.vec(i).at(0);
      } else if( val.isNum() ) {
        _result = new double[]{val.getNum()};
      } else throw new IllegalArgumentException("ddply must return either a number or a frame, not a "+val);


      // Cleanup
      groupFrame.delete();      // Delete the Frame holding WrappedVecs over SubsetChunks
      gvec.remove();            // Delete the group-defining Vec
      _data = null;             // Nuke to avoid returning (not for GC)
      _vKey = null;             // Nuke to avoid returning (not for GC)
      _fun = null;              // Nuke to avoid returning (not for GC)
      _scope = null;            // Nuke to avoid returning (not for GC)
      // And done!
      tryComplete();
    }
  }

}
