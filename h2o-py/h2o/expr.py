import h2o, frame, astfun
import math, collections, tabulate, urllib, gc, sys


class ExprNode:
  """ Composable Expressions: This module contains code for the lazy expression DAG. """

  def __init__(self,op,*ast):
    """
    Standard set-all-fields internal-use-only constructor.
    Caveat Emptor
    :return: An instance of an H2OFrame object.
    """
    assert isinstance(op,str), op
    self._op        = op  # Base opcode string
    # "ast" - True; computed temp; has ID, finalizer will remove
    #       - False; computed non-temp; has user ID, user must explicitly remove
    #       - tuple of Exprs; further: ID is one of
    #       -  - None: Expr is lazy, never evaluated
    #       -  - ""; Expr has been executed once, but no temp ID was made
    #       -  - String: this Expr is mid-execution, with the given temp ID.  
    #            Once execution has completed the ast field will be set to TRUE
    self._ast       = tuple(child._ex if isinstance(child,frame.H2OFrame) else child for child in ast)
    self._id        = None  # "id"  - See above; sometimes None, "", or a "py_tmp" string, or a user string
    # Cached small result; typically known and returned in all REST calls.
    # Calling _eager() is guaranteed to set these.
    self._nrows     = None
    self._ncols     = None
    # Cached info of the evaluated result; this can be large if e.g. the column
    # count is large and so the cache is lazily filled it.  The _data field
    # holds all of any scalar result (including strings and numerics), or a
    # dictionary of Vecs; each Vec holds a fixed set of locally cached rows.
    # Calling _fetch_data(N) will guarantee at least N rows are cached.
    self._data      = None  # any cached data, including scalars or prefixes of data frames

  def _set_rows(self,n):
    if self._nrows: assert self._nrows==n
    else: self._nrows = n

  def _set_cols(self,n):
    if self._ncols: assert self._ncols==n
    else: self._ncols = n

  def _fill_rows(self,raw_json):
    # Fill in the cache for column names and data and types from a raw JSON
    # /Frames response.  Remove a few redundant fields.  'string_data' and
    # 'data' are mutually exclusive; move 'string_data', if any, over 'data'.
    # Returns the self Frame.
    self._set_rows(raw_json["rows"])
    self._set_cols(len(raw_json["columns"]))
    if self._data:
      col = self._data.itervalues().next() # Get a sample column
      if raw_json["row_offset"]+raw_json["row_count"] <= len(col['data']):
        return self # Already have the same set of rows
      print(raw_json)
      print(self._data)
      raise NotImplementedError # Merge
    else:
      assert self._ncols == len(raw_json["columns"])
      self._data = collections.OrderedDict()
      for c in raw_json["columns"]:
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

  def _fetch_data(self,nrows):
    nrows = max(nrows,10)
    # Return the data cache, guaranteeing it covers the requested range of
    # rows.  If called with a large off or nrows, this will load large data and
    # possibly cause an OOM death.
    if self._data:
      col = self._data.itervalues().next() # Get a sample column
      if nrows <= len(col['data']): # Cache covers the desired range of rows
        return self._data
    xid = self._id or self._eager()._id # Force eval as needed
    res = h2o.H2OConnection.get_json("Frames/"+urllib.quote(xid)+"?row_count="+str(nrows))["frames"][0]
    return self._fill_rows(res)._data


  # Internal call, eagerly execute and return a frame.
  # Fully caching, includes fast-path cutout
  def _eager(self):
    if self._data is not None: return self
    if self._id: return self  # Data already computed under ID, but not cached locally
    return self._eval_driver(True)

  # Internal call, eagerly execute and return a scalar
  # Fully caching, includes fast-path cutout
  def _eager_scalar(self):
    if self._data is not None: return self
    assert self._id is None
    self._eval_driver(False)
    assert not self._id , self.__repr__()
    assert not isinstance(self._data,ExprNode)
    return self._data

  def _eval_driver(self,top):
    exec_str = self._do_it(top)
    res = h2o.rapids(exec_str)
    if 'scalar' in res:  self._data = res['scalar']
    if 'string' in res:  self._data = res['string']
    if 'funstr' in res:  raise NotImplementedError
    if 'key'    in res:
      self._set_rows(res['num_rows'])
      self._set_cols(res['num_cols'])
    # Now clear all internal DAG nodes, allowing GC to reclaim them
    self._clear_impl()
    # Enable this GC to trigger rapid R GC cycles, and rapid R clearing of
    # temps... to help debug GC issues.
    gc.collect()
    return self
    
  # Magical count-of-5:   (get 2 more when looking at it in debug mode)
  #  2 for _do_it frame, 2 for _do_it local dictionary list, 1 for parent
  MAGIC_REF_COUNT = 5 if sys.gettrace() is None else 7  # M = debug ? 7 : 5

  # Recursively build a rapids execution string.  Any object with more than
  # MAGIC_REF_COUNT referrers will be cached as a temp until the next client GC
  # cycle - consuming memory.  Do Not Call This except when you need to do some
  # other cluster operation on the evaluated object.  Examples might be: lazy
  # dataset time parse vs changing the global timezone.  Global timezone change
  # is eager, so the time parse as to occur in the correct order relative to
  # the timezone change, so cannot be lazy.
  def _do_it(self,top):
    if self._data is not None:    # Data already computed and cached; could a "false-like" cached value 
      return self._id if isinstance(self._data,dict) else str(self._data) 
    if self._id: return self._id  # Data already computed under ID, but not cached
    # Here self._id is either None or ""
    # Build the eval expression
    assert isinstance(self._ast,tuple)
    exec_str = "("+self._op+" "+" ".join([ExprNode._arg_to_expr(ast) for ast in self._ast])+")"
    gc_ref_cnt = len(gc.get_referrers(self))
    #print(gc_ref_cnt,self._op)
    if top or gc_ref_cnt >= ExprNode.MAGIC_REF_COUNT:
      self._id = frame._py_tmp_key()
      exec_str = "(tmp= "+self._id+" "+exec_str+")"
    return exec_str

  @staticmethod
  def _arg_to_expr(arg):
    if   isinstance(arg, ExprNode):               return arg._do_it(False)
    elif isinstance(arg, astfun.ASTId):           return str(arg)
    elif isinstance(arg, bool):                   return "{}".format("TRUE" if arg else "FALSE")
    elif isinstance(arg, (int, float)):           return "{}".format("NaN" if math.isnan(arg) else arg)
    elif isinstance(arg, basestring):             return '"'+arg+'"'
    elif isinstance(arg, slice):                  return "[{}:{}]".format(0 if arg.start is None else arg.start,"NaN" if (arg.stop is None or math.isnan(arg.stop)) else (arg.stop) if arg.start is None else (arg.stop-arg.start) )
    elif isinstance(arg, list):                   return ("[\"" + "\" \"".join(arg) + "\"]") if isinstance(arg[0], basestring) else ("[" + " ".join(["NaN" if math.isnan(i) else str(i) for i in arg])+"]")
    elif arg is None:                             return "[]"  # empty list
    raise ValueError("Unexpected arg type: " + str(type(arg))+" "+str(arg.__class__)+" "+arg.__repr__())

  def _clear_impl(self):
    if not isinstance(self._ast,tuple): return
    for ast in self._ast:
      if isinstance(ast,ExprNode): 
        ast._clear_impl()
    if self._id: self._ast = True  # Local pytmp


  def __del__(self):
    if( isinstance(self._ast,bool) and self._ast ):
      h2o.H2OConnection.delete("DKV/"+self._id)

  def _tabulate(self,tablefmt,rollups):
    """
    Pretty tabulated string of all the cached data, and column names
    """    
    if not isinstance(self._fetch_data(10),dict):  return str(self._data) # Scalars print normally
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

  def __repr__(self):
    """
    String representation of all the fields; hopefully possible to
    directly invert back to a H2OFrame from the string
    """
    return "Expr(op=%r,id=%r,ast=%r,nrows=%r,ncols=%r,data=%r)" % (self._op,self._id,self._ast,self._nrows,self._ncols,type(self._data))

  def __str__(self): return self._tabulate("simple",False)

  def col_names(self):
    """
    :return: A list of column names.
    """
    return self._fetch_data(0).keys()

  def show(self):
    """
    Used by the H2OFrame.__repr__ method to print or display a snippet of the data frame.
    If called from IPython, displays an html'ized result
    Else prints a tabulate'd result
    :return: None
    """
    if h2o.H2ODisplay._in_ipy():
      import IPython.display
      IPython.display.display_html(self._tabulate("html",False),raw=True)
    else:
      print(self)


  def summary(self):
    """
    Summary: show(), plus includes min/mean/max/sigma and other rollup data
    :return: None
    """
    if h2o.H2ODisplay._in_ipy():
      import IPython.display
      IPython.display.display_html(self._tabulate("html",True),raw=True)
    else:
      print(self._tabulate("simple",True))

  def describe(self):
    """
    Generate an in-depth description of this H2OFrame.
    Everything in summary(), plus the data layout
    :return: None (print to stdout)
    """
    # Force a fetch of 10 rows; the chunk & distribution summaries are not
    # cached, so must be pulled.  While we're at it, go ahead and fill in
    # the default caches if they are not already filled in
    xid = self._id or self._eager()._id # Force eval as needed
    res = h2o.H2OConnection.get_json("Frames/"+urllib.quote(xid)+"?row_count="+str(10))["frames"][0]
    self._fill_rows(res)
    print "Rows:{:,}".format(self._nrows), "Cols:{:,}".format(self._ncols)
    res["chunk_summary"].show()
    res["distribution_summary"].show()
    print("\n")
    self.summary()

  def __getitem__(self, item):
    """
    Frame slicing.
    Supports R-like row and column slicing.

    Examples:
      fr[2]              # All rows, column 2
      fr[-2]             # All rows, 2nd column from end
      fr[:,-1]           # All rows, last column
      fr[0:5,:]          # first 5 rows, all columns
      fr[fr[0] > 1, :]   # all rows greater than 1 in the first column, all columns
      fr[[1,5,6]]        # columns 1, 5, and 6
      fr[0:50, [1,2,3]]  # first 50 rows, columns 1,2, and 3

    :param item: A tuple, a list, a string, or an int.
                 If a tuple, then this indicates both row and column selection. The tuple
                 must be exactly length 2.
                 If a list, then this indicates column selection.
                 If a int, the this indicates a single column to be retrieved at the index.
                 If a string, then slice on the column with this name.
    :return: An H2OFrame.
    """
    if isinstance(item, (basestring,list)): return ExprNode("cols",self,item)  # just columns
    if isinstance(item, int): return ExprNode("cols",self,item if item >= 0 else self._ncols+item)  # just columns
    if isinstance(item, slice):
      item = slice(item.start,min(self._ncols,item.stop))
      return ExprNode("cols",self,item)
    if isinstance(item, frame.H2OFrame): item = item._ex
    if isinstance(item, ExprNode): return ExprNode("rows",self,item)  # just rows
    if isinstance(item, tuple):
      rows, cols = item
      allrows = allcols = False
      if isinstance(cols, slice):  allcols = all([a is None for a in [cols.start,cols.step,cols.stop]])
      if isinstance(rows, slice):  allrows = all([a is None for a in [rows.start,rows.step,rows.stop]])

      if allrows and allcols: return self            # fr[:,:]    -> all rows and columns.. return self
      if allrows: return ExprNode("cols",self,cols)  # fr[:,cols] -> really just a column slice
      if allcols: return ExprNode("rows",self,rows)  # fr[rows,:] -> really just a row slices

      res = ExprNode("rows", ExprNode("cols",self,cols),rows)
      # Pythonic: if the row & col selector turn into ints (or a single col
      # name), then extract the single element out of the Frame.  Otherwise
      # return a Frame, EVEN IF the selectors are e.g. slices-of-1-value.
      if isinstance(rows, int) and isinstance(cols,(basestring,int)):
        res = ExprNode("flatten",res) # Overwrite res to preserve gc referrer count
        return res._eager_scalar()
      return res
    raise ValueError("Unexpected __getitem__ selector: "+str(type(item))+" "+str(item.__class__))

  def _setitem(self, b, c):
    """
    Replace a column in an Expr

    :param b: A 0-based index or a column name.
    :param c: The vector that 'b' is replaced with.
    :return: Returns a new modifed Expr
    """
    col_expr=None
    row_expr=None
    colname=None  # When set, we are doing an append

    if isinstance(b, basestring):  # String column name, could be new or old
      if b in self.col_names(): col_expr = self.col_names().index(b)  # Old, update
      else:                     col_expr = self._ncols ; colname = b  # New, append
    elif isinstance(b, int):    col_expr = b # Column by number
    elif isinstance(b, tuple):     # Both row and col specifiers
      row_expr = b[0]
      col_expr = b[1]
      if isinstance(col_expr, basestring):   # Col by name
        if col_expr not in self.col_names(): # Append
          colname = col_expr
          col_expr = self._ncols
      elif isinstance(col_expr, slice):    # Col by slice
        if col_expr.start is None and col_expr.stop is None:
          col_expr = slice(0,self.ncol)    # Slice of all
    elif isinstance(b, ExprNode): row_expr = b # Row slicing

    src = c if isinstance(c,ExprNode) else (float("nan") if c is None else c)
    return (ExprNode(":="    ,self,src,col_expr,row_expr) if colname is None
       else ExprNode("append",self,src,colname))
