package water;

import jsr166y.CountedCompleter;

import java.util.concurrent.CancellationException;

/**
 * Created by tomas on 11/5/16.
 *
 * Generic Local MRTask utility. Will launch requested number of tasks (on local node!), organized in a binary tree fashion.
 *
 */
public class LocalMR<T extends MrFun<T>> extends H2O.H2OCountedCompleter<LocalMR> {
  private int _lo;
  private int _hi;
  MrFun _mrFun;
  volatile Throwable _t;
  protected volatile boolean  _cancelled;
  private LocalMR<T> _root;

  public LocalMR(MrFun mrt, int nthreads){this(mrt,nthreads,null);}
  public LocalMR(MrFun mrt, H2O.H2OCountedCompleter cc){this(mrt,H2O.NUMCPUS,cc);}
  public LocalMR(MrFun mrt, int nthreads, H2O.H2OCountedCompleter cc){
    super(cc);
    if(nthreads <= 0) throw new IllegalArgumentException("nthreads must be positive");
    _root = this;
    _mrFun = mrt;
    _lo = 0;
    _hi = nthreads;
    _prevTsk = null;
  }
  private LocalMR(LocalMR src, LocalMR prevTsk,int lo, int hi) {
    super(src);
    _root = src._root;
    _prevTsk = prevTsk;
    _lo = lo;
    _hi = hi;
    _cancelled = src._cancelled;
  }

  private LocalMR<T> _left;
  private LocalMR<T> _rite;
  private final LocalMR<T> _prevTsk; // right child will attempt to share MrFun with " left sibling" if the whole left subtree is done


  volatile boolean completed; // this task and all it's children completed
  volatile boolean started; // this task and all it's children completed
  public boolean isCancelRequested(){return _root._cancelled;}

  private int mid(){ return _lo + ((_hi - _lo) >> 1);}

  @Override
  public final void compute2() {
    started = true;
    if (!_root._cancelled) {
      try {
        int mid = mid();
        assert _hi > _lo;
        int pending = 1;
        if (_hi - _lo >= 2) {
          _left = new LocalMR(this, _prevTsk, _lo, mid);
          if (mid < _hi) {
            _rite = new LocalMR(this, _left, mid, _hi);
            pending++;
          }
          addToPendingCount(pending);
          if(_rite != null) _rite.fork();
          _left.fork();
        } else {

          if(_prevTsk != null && _prevTsk.completed){
            _mrFun = _prevTsk._mrFun;
            _prevTsk._mrFun = null;
          } else _mrFun = _root._mrFun.makeCopy();
          _mrFun.map(mid);
        }
      } catch (Throwable t) {
        t.printStackTrace();
        if (_root._t == null) {
          _root._t = t;
          _root._cancelled = true;
        }
      }
    }
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
    if (_left != null && _left._mrFun != null && _mrFun != _left._mrFun) {
      assert _left.completed;
      if(_mrFun == null)_mrFun = _left._mrFun;
      else _mrFun.reduce(_left._mrFun);
    }
    if (_rite != null && _mrFun != _rite._mrFun) {
      assert _rite.completed;
      if(_mrFun == null)_mrFun = _rite._mrFun;
      else _mrFun.reduce(_rite._mrFun);
    }
    _left = null;
    _rite = null;
    completed = true;
  }

}
