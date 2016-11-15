package water;

import jsr166y.CountedCompleter;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by tomas on 11/5/16.
 *
 * Generic Local MRTask utility. Will launch requested number of tasks (on local node!), organized in a binary tree fashion.
 * Tasks are launched via H2O.submit task rather than fork (assuming different usage pattern than MRT - use fewer longer running tasks -> push into global FJQ).
 *
 * User provides a MrFun function object with map/reduce/makeCopy functions.
 *
 * Here is a sample tree and reduce pairs for LocalMR creating 8 tasks
 *          4
 *        /  \
 *      /     \
 *     2      6
 *    / \    / \
 *   1   3  5  7
 *   |
 *   0
 *
 *   Reduce will be called for pairs (1,0), (2,1), (2,3), (4,2), (6,5), (6,7), (4,6)
 *
 */
public class LocalMR<T extends MrFun<T>> extends H2O.H2OCountedCompleter<LocalMR> {
  private int _lo;
  private int _hi;
  final MrFun _mrFun;
  volatile Throwable _t;
  protected volatile boolean  _cancelled;
  private LocalMR<T> _root;

  public LocalMR(MrFun mrt){this(mrt,H2O.NUMCPUS);}
  public LocalMR(MrFun mrt, int nthreads){this(mrt,nthreads,null);}
  public LocalMR(MrFun mrt, H2O.H2OCountedCompleter cc){this(mrt,H2O.NUMCPUS,cc);}
  public LocalMR(MrFun mrt, int nthreads, H2O.H2OCountedCompleter cc){
    super(cc);
    if(nthreads <= 0) throw new IllegalArgumentException("nthreads must be positive");
    _root = this;
    _mrFun = mrt;
    _lo = 0;
    _hi = nthreads;
  }
  public LocalMR(MrFun mrt, int nthreads, H2O.H2OCountedCompleter cc, byte priority) {
    super(cc,priority);
    if(nthreads <= 0) throw new IllegalArgumentException("nthreads must be positive");
    _root = this;
    _mrFun = mrt;
    _lo = 0;
    _hi = nthreads;
  }

  private LocalMR(LocalMR src, int lo, int hi) {
    super(src);
    _root = src._root;
    _mrFun = src._mrFun.makeCopy();
    _lo = lo;
    _hi = hi;
    _cancelled = src._cancelled;
  }

  private LocalMR<T> _left;
  private LocalMR<T> _rite;

  volatile boolean completed;

  public boolean isCancelRequested(){return _root._cancelled;}

  private int mid(){ return _lo + ((_hi - _lo) >> 1);}

  @Override
  public final void compute2() {
    if (!_root._cancelled) {
      try {
        int mid = mid();
        assert _hi > _lo;
        int pending = 0;
        if (_hi - _lo >= 2) {
          _left = new LocalMR(this, _lo, mid);
          pending++;
          if ((mid + 1) < _hi) {
            _rite = new LocalMR(this, mid + 1, _hi);
            pending++;
          }
          addToPendingCount(pending);
          H2O.submitTask(_left);
          if(_rite != null) H2O.submitTask(_rite);
        }
        _mrFun.map(mid);
      } catch (Throwable t) {
        t.printStackTrace();
        if (_root._t == null) {
          _root._t = t;
          _root._cancelled = true;
        }
      }
    }
    completed = true;
    tryComplete();
  }

  @Override
  public final void onCompletion(CountedCompleter cc) {
    if(_cancelled){
      assert this == _root;
      completeExceptionally(_t == null?new CancellationException():_t); // instead of throw
      return;
    }
    if(_root._cancelled) return;
    assert cc == this || cc == _left || cc == _rite;
    if (_left != null) {
      assert _left.completed;
      _mrFun.reduce(_left._mrFun);
      _left = null;
    }
    if (_rite != null) {
      assert _rite.completed;
      _mrFun.reduce(_rite._mrFun);
      _rite = null;
    }
  }

}
