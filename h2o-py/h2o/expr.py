import h2o, frame, astfun
import math, collections, tabulate, urllib, gc, sys, copy


class ExprNode:
  """Composable Expressions: This module contains code for the lazy expression DAG.

  Execution Overview
  ------------------
    The job of ExprNode is to provide a layer of indirection to H2OFrame instances that
    are built of arbitrarily large, lazy expression DAGs. In order to do this job well,
    ExprNode must also track top-level entry points to the such DAGs, maintain a sane
    amount of state to know which H2OFrame instances are temporary (or not), and maintain
    a cache of H2OFrame properties (nrows, ncols, types, names, few rows of data).

    Top-Level Entry Points
    ----------------------
      An expression is declared top-level if it
        A) Computes and returns an H2OFrame to some on-demand call from somewhere
        B) An H2OFrame instance has more referrers than the 4 needed for the usual flow
           of python execution (see MAGIC_REF_COUNT below for more details).

    Sane Amount of State
    --------------------
      Instances of H2OFrame live and die by the state contained in the _ex field. The three
      pieces of state -- _op, _children, _cache -- are the fewest pieces of state (and no
      fewer) needed to unambiguously track temporary H2OFrame instances and prune
      them according to the usual scoping laws of python.

      If _cache._id is None, then this DAG has never been sent over to H2O, and there's
      nothing more to do when the object goes out of scope.

      If _cache._id is not None, then there has been some work done by H2O to compute the
      big data object sitting in H2O to which _id points. At the time that __del__ is
      called on this object, a determination to throw out the corresponding data in H2O or
      to keep that data is made by the None'ness of _children.

      tl;dr:
        If _cache._id is not None and _children is None, then do not delete in H2O cluster
        If _cache._id is not None and _children is not None, then do delete in H2O cluster

    H2OCache
    --------
      To prevent several unnecessary REST calls and unnecessary execution, a few of the
      oft-needed attributes of the H2OFrame are cached for convenience. The primary
      consumers of these cached values are __getitem__, __setitem__, and a few other
      H2OFrame ops that do argument validation or exchange (e.g. colnames for indices).
      There are more details available under the H2OCache class declaration.
  """

  # Magical count-of-5:   (get 2 more when looking at it in debug mode)
  #  2 for _do_it frame, 2 for _do_it local dictionary list, 1 for parent
  MAGIC_REF_COUNT = 5 if sys.gettrace() is None else 7  # M = debug ? 7 : 5

  def __init__(self, op="", *args):
    assert isinstance(op,str), op
    self._op        = op          # Base opcode string
    self._children  = tuple(a._ex if isinstance(a, frame.H2OFrame) else a for a in args)  # ast children; if not None and _cache._id is not None then tmp
    self._cache     = H2OCache()  # ncols, nrows, names, types

  def _eager_frame(self):  # returns H2OFrame instance
    if not self._cache.is_empty(): return self
    if self._cache._id is not None: return self  # Data already computed under ID, but not cached locally
    return self._eval_driver(True)

  def _eager_scalar(self):  # returns a scalar (or a list of scalars)
    if not self._cache.is_empty():
      assert self._cache.is_scalar()
      return self
    assert self._cache._id is None
    self._eval_driver(False)
    assert self._cache._id is None
    assert self._cache.is_scalar()
    return self._cache._data

  def _eval_driver(self,top):
    exec_str = self._do_it(top)
    res = h2o.rapids(exec_str)
    if 'scalar' in res:  self._cache._data = res['scalar']
    if 'string' in res:  self._cache._data = res['string']
    if 'funstr' in res:  raise NotImplementedError
    if 'key'    in res:
      self._cache.nrows = res['num_rows']
      self._cache.ncols = res['num_cols']
    return self

  # Recursively build a rapids execution string.  Any object with more than
  # MAGIC_REF_COUNT referrers will be cached as a temp until the next client GC
  # cycle - consuming memory.  Do Not Call This except when you need to do some
  # other cluster operation on the evaluated object.  Examples might be: lazy
  # dataset time parse vs changing the global timezone.  Global timezone change
  # is eager, so the time parse as to occur in the correct order relative to
  # the timezone change, so cannot be lazy.
  def _do_it(self,top):
    if not self._cache.is_empty():    # Data already computed and cached; could a "false-like" cached value
      return str(self._cache._data) if self._cache.is_scalar() else self._cache._id
    if self._cache._id is not None: return self._cache._id  # Data already computed under ID, but not cached
    # assert isinstance(self._children,tuple)
    exec_str = "({} {})".format(self._op," ".join([ExprNode._arg_to_expr(ast) for ast in self._children]))
    gc_ref_cnt = len(gc.get_referrers(self))
    if top or gc_ref_cnt >= ExprNode.MAGIC_REF_COUNT:
      self._cache._id = frame._py_tmp_key()
      exec_str = "(tmp= {} {})".format(self._cache._id, exec_str)
    return exec_str

  @staticmethod
  def _arg_to_expr(arg):
    if   isinstance(arg, ExprNode):               return arg._do_it(False)
    elif isinstance(arg, astfun.ASTId):           return str(arg)
    elif isinstance(arg, bool):                   return "{}".format("TRUE" if arg else "FALSE")
    elif isinstance(arg, (int, long, float)):     return "{}".format("NaN" if math.isnan(arg) else arg)
    elif isinstance(arg, basestring):             return '"'+arg+'"'
    elif isinstance(arg, slice):                  return "[{}:{}]".format(0 if arg.start is None else arg.start,"NaN" if (arg.stop is None or math.isnan(arg.stop)) else (arg.stop) if arg.start is None else (arg.stop-arg.start))
    elif isinstance(arg, list):                   return ("[\"" + "\" \"".join(arg) + "\"]") if all(isinstance(i, basestring) for i in arg) else ("[" + " ".join(["NaN" if i == 'NaN' or math.isnan(i) else str(i) for i in arg])+"]")
    elif arg is None:                             return "[]"  # empty list
    raise ValueError("Unexpected arg type: " + str(type(arg))+" "+str(arg.__class__)+" "+arg.__repr__())

  def __del__(self):
    if self._cache._id is not None and self._children is not None:
      h2o.rapids("(rm {})".format(self._cache._id))

  @staticmethod
  def _collapse_sb(sb): return ' '.join("".join(sb).replace("\n", "").split()).replace(" )", ")")
  def _debug_print(self,pprint=True): return "".join(self._2_string(sb=[])) if pprint else ExprNode._collapse_sb(self._2_string(sb=[]))
  def _to_string(self):
    return ' '.join(["("+self._op] + [ExprNode._arg_to_expr(a) for a in self._children] + [")"])
  def _2_string(self,depth=0,sb=None):
    sb += ['\n', " "*depth, "("+self._op, " "]
    if self._children is not None:
      for child in self._children:
        if isinstance(child, h2o.H2OFrame) and child._ex._cache._id is None:  child._ex._2_string(depth+2,sb)
        elif isinstance(child, h2o.H2OFrame):                                 sb+=['\n', ' '*(depth+2), child._ex._cache._id]
        elif isinstance(child, ExprNode):                                     child._2_string(depth+2,sb)
        else:                                                                 sb+=['\n', ' '*(depth+2), str(child)]
    sb+=['\n',' '*depth+") "] + ['\n'] * (depth==0)  # add a \n if depth == 0
    return sb

  def __repr__(self):
    return "Expr(op=%r,id=%r,ast=%r,is_scalar=%r)" % (self._op,self._cache._id,self._children,self._cache.is_scalar())

class H2OCache(object):
  def __init__(self):
    self._id    = None
    self._nrows = -1
    self._ncols = -1
    self._types = None  # col types
    self._names = None  # col names
    self._data  = None  # ordered dict of cached rows, or a scalar
    self._l     = 0     # nrows cached

  @property
  def nrows(self): return self._nrows
  @nrows.setter
  def nrows(self, value): self._nrows = value
  def nrows_valid(self): return self._nrows >= 0
  @property
  def ncols(self): return self._ncols
  @ncols.setter
  def ncols(self, value): self._ncols = value
  def ncols_valid(self): return self._ncols >= 0
  @property
  def names(self): return self._names
  @names.setter
  def names(self, value): self._names = value
  def names_valid(self): return self._names is not None
  @property
  def types(self): return self._types
  @types.setter
  def types(self, value): self._types = value
  def types_valid(self): return self._types is not None
  @property
  def scalar(self): return self._data if self.is_scalar() else None
  @scalar.setter
  def scalar(self, value): self._data = value

  def __len__(self):   return self._l
  def is_empty(self):  return self._data is None
  def is_scalar(self): return not isinstance(self._data, dict)
  def is_valid(self):
    return self._id is not None and \
           not self.is_empty()  and \
           self.nrows_valid()   and \
           self.ncols_valid()   and \
           self.names_valid()   and \
           self.types_valid()

  def fill(self, rows=10):
    assert self._id is not None
    if self._data is not None:
      if rows <= len(self):
        return
    res = h2o.H2OConnection.get_json("Frames/"+urllib.quote(self._id), row_count=rows)["frames"][0]
    self._l     = rows
    self._nrows = res["rows"]
    self._ncols = res["total_column_count"]
    self._names = [c["label"] for c in res["columns"]]
    self._types = dict(zip(self._names,[c["type"] for c in res["columns"]]))
    self._fill_data(res)

  def _fill_data(self, json):
    self._data = collections.OrderedDict()
    for c in json["columns"]:
      c.pop('__meta')              # Redundant description ColV3
      c.pop('domain_cardinality')  # Same as len(c['domain'])
      sdata = c.pop('string_data')
      if sdata: c['data'] = sdata  # Only use data field; may contain either [str] or [real]
      # Data (not string) columns should not have a string in them.  However,
      # our NaNs are encoded as string literals "NaN" as opposed to the bare
      # token NaN, so the default python json decoder does not convert them
      # to math.nan.  Do that now.
      else: c['data'] = [float('nan') if x=="NaN" else x for x in c['data']]
      self._data[c.pop('label')] = c # Label used as the Key
    return self

  #### pretty printing ####

  def _tabulate(self,tablefmt,rollups):
    """Pretty tabulated string of all the cached data, and column names"""
    if not self.is_valid(): self.fill()
    # Pretty print cached data
    d = collections.OrderedDict()
    # If also printing the rollup stats, build a full row-header
    if rollups:
      col = self._data.itervalues().next() # Get a sample column
      lrows = len(col['data'])  # Cached rows being displayed
      d[""] = ["type", "mins", "mean", "maxs", "sigma", "zeros", "missing"]+map(str,range(lrows))
    # For all columns...
    for k,v in self._data.iteritems():
      x = v['data']          # Data to display
      domain = v['domain']   # Map to cat strings as needed
      if domain:
        x = ["" if math.isnan(idx) else domain[int(idx)] for idx in x]
      if rollups:            # Rollups, if requested
        mins = v['mins'][0] if v['mins'] else None
        maxs = v['maxs'][0] if v['maxs'] else None
        x = [v['type'],mins,v['mean'],maxs,v['sigma'],v['zero_count'],v['missing_count']]+x
      d[k] = x               # Insert into ordered-dict
    return tabulate.tabulate(d,headers="keys",tablefmt=tablefmt)

  def flush(self):  # flush everything but the frame_id
    fr_id = self._id
    self.__dict__ = H2OCache().__dict__.copy()
    self._id = fr_id
    return self

  def fill_from(self, cache):
    assert isinstance(cache, H2OCache)
    cur_id = self._id
    self.__dict__ = copy.deepcopy(cache.__dict__)
    self._data=None
    self._id = cur_id

  def dummy_fill(self):
    self._id = "dummy"
    self._nrows=0
    self._ncols=0
    self._names=[]
    self._types={}
    self._data={}