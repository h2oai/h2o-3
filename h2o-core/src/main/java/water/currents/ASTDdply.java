package water.currents;

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
  @Override int nargs() { return 4; } // (ddply data [group-by-cols] fcn )
  @Override public String str() { return "h2o.ddply"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int ncols = fr.numCols();

    ASTNumList groupby = ASTGroup.check(ncols, asts[2]);
    int[] gbCols = groupby.expand4();

    AST fun = asts[3].exec(env).getFun();

    // Pass 1: Find all the groups (and count rows-per-group)
    IcedHashMap<ASTGroup.G,String> gss = ASTGroup.doGroups(fr,gbCols,ASTGroup.aggNRows());

    // Uniquely number the groups
    int gnum=0;
    for( ASTGroup.G g : gss.keySet() )
      g._dss[0][0] = gnum++;

    // Pass 2: Build all the groups, building 1 Vec per-group, with exactly the
    // same Chunk layout, except each Chunk will be the filter rows numbers; a
    // list of the Chunk-relative row-numbers for that group in an original
    // data Chunk.  Each Vec will have a *different* number of rows.
    Vec[] vgrps = new BuildGroup(gbCols,gss).doAll(gss.size(),fr).close();

    // Pass 3: For each group, build a full frame for the group, run the
    // function on it and tear the frame down.
    RemoteRapids[] remoteTasks = new RemoteRapids[vgrps.length]; // gather up the remote tasks...
    Futures fs = new Futures();
    for( int i=0; i<vgrps.length; i++ )
      fs.add(RPC.call(vgrps[i]._key.home_node(), remoteTasks[i] = new RemoteRapids(vgrps[i]._key, fun)));
    fs.blockForPending();


    // Final result frame name
    String fname = (fr._key == null ? Key.rand() : fr._key.toString()) + "_ddply";
    
    throw H2O.unimpl();
  }

  // --------------------------------------------------------------------------
  // Build all the groups, building 1 Vec per-group, with exactly the same
  // Chunk layout, except each Chunk will be the filter rows numbers; a list
  // of the Chunk-relative row-numbers for that group in an original data Chunk.
  private static class BuildGroup extends MRTask<BuildGroup> {
    final IcedHashMap<ASTGroup.G,String> _gss;
    final int[] _gbCols;
    BuildGroup( int[] gbCols, IcedHashMap<ASTGroup.G,String> gss ) { _gbCols = gbCols; _gss = gss; }
    @Override public void map( Chunk[] cs, NewChunk[] ncs ) {
      ASTGroup.G gWork = new ASTGroup.G(_gbCols.length,null); // Working Group
      for( int row=0; row<cs[0]._len; row++ ) {
        gWork.fill(row,cs,_gbCols); // Fill the worker Group for the hashtable lookup
        int gnum = (int)_gss.getk(gWork)._dss[0][0]; // Existing group number
        ncs[gnum].addNum(row);  // gather row-numbers per-chunk per-group
      }
    }
    // Gather all the output Vecs.  Note that each Vec has a *different* number
    // of rows, and taken together they do NOT make a valid Frame.
    Vec[] close() {
      Futures fs = new Futures();
      Vec[] vgrps = new Vec[_gbCols.length];
      for( int i = 0; i < vgrps.length; i++ )
        vgrps[i] = _appendables[i].close(fs);
      fs.blockForPending();
      return vgrps;
    }
  }

  // --------------------------------------------------------------------------
  private static class RemoteRapids extends DTask<RemoteRapids> {
    private final Key<Vec> _vKey; // the group to process...
    private final AST _FUN;       // the ast to execute on the group
    private double[] _result;     // result is 1 row per group!

    RemoteRapids( Key<Vec> vKey, AST FUN) {
      _vKey=vKey; _FUN=FUN;
      // Always 1 higher priority than calling thread... because the caller will
      // block & burn a thread waiting for this MRTask to complete.
      Thread cThr = Thread.currentThread();
      _priority = (byte)((cThr instanceof H2O.FJWThr) ? ((H2O.FJWThr)cThr)._priority+1 : super.priority());
    }

    final private byte _priority;
    @Override public byte priority() { return _priority; }
    @Override public void compute2() {
      assert _vKey.home();
      // Make a group Frame, using wrapped Vecs wrapping the original data
      // frame with the filtered Vec passed in.  Run the function, getting a
      // scalar or a 1-row Frame back out.  Delete the group Frame.  Return the
      // 1-row Frame as a double[] of results for this group.


      throw H2O.unimpl();
      //groupFrame.delete();
      //tryComplete();
    }
  }
}
