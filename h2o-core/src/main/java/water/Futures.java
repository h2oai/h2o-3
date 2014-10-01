package water;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import water.util.Log;

/** A collection of Futures that can be extended, or blocked on the whole
 *  collection.  Undefined if you try to add Futures while blocking.
 *  <p>
 *  Used as a service to sub-tasks, collect pending-but-not-yet-done future
 *  tasks that need to complete prior to *this* task completing... or if the
 *  caller of this task is knowledgeable, pass these pending tasks along to him
 *  to block on before he completes. 
 *  <p>
 *  Highly efficient under a high load of short-completion-time Futures.  Safe
 *  to call with e.g. millions of Futures per second, as long as they all
 *  complete in roughly the same rate. */
public class Futures {
  // implemented as an exposed array mostly because ArrayList doesn't offer
  // synchronization and constant-time removal.
  Future[] _pending = new Future[1];
  int _pending_cnt;

  /** Some Future task which needs to complete before this Futures completes */
  synchronized public Futures add( Future f ) {
    if( f == null ) return this;
    if( f.isDone() ) return this;
    // NPE here if this Futures has already been added to some other Futures
    // list, and should be added to again.
    if( _pending_cnt == _pending.length ) {
      cleanCompleted();
      if( _pending_cnt == _pending.length )
        _pending = Arrays.copyOf(_pending,_pending_cnt<<1);
    }
    _pending[_pending_cnt++] = f;
    return this;
  }

  /** Merge pending-task lists (often as part of doing a 'reduce' step) */
  public void add( Futures fs ) {
    if( fs == null ) return;
    assert fs != this;          // No recursive death, please
    for( int i=0; i<fs._pending_cnt; i++ )
      add(fs._pending[i]); // NPE here if using a dead Future
    fs._pending = null;    // You are dead, should never be inserted into again
  }

  /** Clean out from the list any pending-tasks which are already done.  Note
   *  that this drops the algorithm from O(n) to O(1) in practice, since mostly
   *  things clean out as fast as new ones are added and the list never gets
   *  very large. */
  synchronized private void cleanCompleted() {
    for( int i=0; i<_pending_cnt; i++ )
      if( _pending[i].isDone() ) // Done?
        // Do cheap array compression to remove from list
        _pending[i--] = _pending[--_pending_cnt];
  }

  /** Block until all pending futures have completed or canceled.  */
  public final void blockForPending() {
    try {
      // Block until the last Future finishes.
      while( true ) {
        Future f;
        synchronized(this) {
          if( _pending_cnt == 0 ) return;
          f = _pending[--_pending_cnt];
        }
        try { f.get(); } 
        catch( CancellationException e ) { /*Ignore canceled tasks*/ }
      }
    } catch( ExecutionException e ) {
      // Replace ExEx with RunEx
      throw Log.throwErr(new RuntimeException(e.getCause()));
    } catch( InterruptedException e ) { throw Log.throwErr(e); }
  }
}
