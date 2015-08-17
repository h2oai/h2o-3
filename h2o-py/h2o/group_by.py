import h2o

class GroupBy:
  """
  A class that represents the group by operation on an H2OFrame.

  Sample usage:

         >>> my_frame = ...  # some existing H2OFrame
         >>> grouped = my_frame.group_by(by=["C1","C2"],order_by="C1")
         >>> grouped.sum(col="X1",na="all").mean(col="X5",na="all").max()

  Any number of aggregations may be chained together in this manner.

  If no arguments are given to the aggregation (e.g. "max" in the above example),
  then it is assumed that the aggregation should apply to all columns but the
  group by columns.

  The na parameter is one of ["all","ignore","rm"].
      "all"    - include NAs
      "rm"     - exclude NAs
      "ignore" - ignore NAs in aggregates, but count them (e.g. in denominators for mean, var, sd, etc.)
  """
  def __init__(self,fr,by,order_by=None):
    self._fr=fr                 # IN
    self._by=by                 # IN
    self._col_names=fr.names    # IN
    self._aggs={}               # IN
    self._order_by = order_by
    self._computed=False        #
    self._res=None              # OUT: resulting group by frame

    if isinstance(order_by,str):  # sanitize order_by if str
      idx = self._fr.index(order_by)  # must indirectly index into by columns
      try:               self._order_by=self._by.index(idx)
      except ValueError: print "Invalid order_by: Must be a group by column."

    if isinstance(by,str):            self._by = [self._fr.index(by)]
    elif isinstance(by,(tuple,list)): self._by = [self._fr.index(b) if isinstance(b, str) else b for b in by]
    else: self._by = [self._by]

  def min(  self,col=None,name="",na="all"): return self._add_agg("min",col,name,na)
  def max(  self,col=None,name="",na="all"): return self._add_agg("max",col,name,na)
  def mean( self,col=None,name="",na="all"): return self._add_agg("mean",col,name,na)
  def count(self,name="",na="all"):          return self._add_agg("count",None,name,na)
  def sum(  self,col=None,name="",na="all"): return self._add_agg("sum",col,name,na)
  def sd(   self,col=None,name="",na="all"): return self._add_agg("sd",col,name,na)
  def var(  self,col=None,name="",na="all"): return self._add_agg("var",col,name,na)
  def first(self,col=None,name="",na="all"): return self._add_agg("first",col,name,na)
  def last( self,col=None,name="",na="all"): return self._add_agg("last",col,name,na)
  def ss(self,col=None,name="",na="all"):    return self._add_agg("ss",col,name,na)

  def get_frame(self):
    """
    :return: the result of the group by
    """
    self._eager()
    return self._res

  def remove(self,name=None,regex=None):
    """
    Remove an aggregate with the specified name.

    :param name:  The name of the aggregate to remove.
    :param regex: If regex is not None, name is ignored and this regex is used.
    :return: this GroupBy object
    """
    if regex is not None:
      names = [n for n in self._aggs.keys() if regex in n]
      for n in names:
        del self._aggs[n]
    elif name is None:
      self._aggs={}
      return self
    else:
      if name not in self._aggs: raise ValueError("No such aggregate: " + str(name))
      del self._aggs[name]
    return self

  def _eager(self):
    if not self._computed:
      aggs=[]
      for k in self._aggs: aggs += (self._aggs[k] + [str(k)])
      aggs = h2o.ExprNode("agg", *aggs)
      self._res = h2o.H2OFrame(expr=h2o.ExprNode("GB", self._fr,self._by,aggs,self._order_by))._frame()
      self._computed=True

  def _add_agg(self,op,col,name,na):
    if op=="count":
      col=0
      name="count" if name=="" else name
    if col is None:
      for i in range(self._fr.ncol):
        if i not in self._by: self._add_agg(op,i,name,na)
      return self
    elif isinstance(col, (str,unicode)): cidx=self._fr.index(col)
    elif isinstance(col, int):         cidx=col
    elif isinstance(col, (tuple,list)):
      for i in col:
        self._add_agg(op,i,name,na)
      return self
    else:                              raise ValueError("col must be a column name or index.")
    if name=="": name = "{}_{}".format(op,self._fr.col_names[cidx])
    self._aggs[name]=[op,cidx,na]
    self._computed=False
    return self

  def __repr__(self):
    print "GroupBy: "
    print "  Frame: {}; by={}".format(self._fr._id,str(self._by))
    print "  Aggregates: {}".format(str(self._aggs.keys()))
    print "*** Use get_frame() to get groupby frame ***"
    return ""
