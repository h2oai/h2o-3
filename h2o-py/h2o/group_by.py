import h2o, expr


class GroupBy:
  """
  A class that represents the group by operation on an H2OFrame.

  Sample usage:

         >>> my_frame = ...  # some existing H2OFrame
         >>> grouped = my_frame.group_by(by=["C1","C2"],order_by="C1")
         >>> grouped.sum(col="X1",na="all").mean(col="X5",na="all").max()
         >>> grouped.get_frame

  Any number of aggregations may be chained together in this manner.

  If no arguments are given to the aggregation (e.g. "max" in the above example),
  then it is assumed that the aggregation should apply to all columns but the
  group by columns.

  The na parameter is one of ["all","ignore","rm"].
      "all"    - include NAs
      "rm"     - exclude NAs

  Variance (var) and standard deviation (sd) are the sample (not population) statistics.
  """
  def __init__(self,fr,by,order_by=None):
    self._fr=fr                 # IN
    self._by=by                 # IN
    self._order_by = order_by   # IN
    self._aggs={}               # IN
    self._res=None              # OUT

    if isinstance(order_by,basestring):  # sanitize order_by if str
      idx = fr.names.index(order_by)     # must indirectly index into by columns
      try: self._order_by = by.index(idx)
      except ValueError: print "Invalid order_by: Must be a group by column."

    if isinstance(by,basestring):     self._by = [self._fr.names.index(by)]
    elif isinstance(by,(tuple,list)): self._by = [self._fr.names.index(b) if isinstance(b, basestring) else b for b in by]
    else: self._by = [self._by]

  def min(  self,col=None,na="all"): return self._add_agg("min",col,na)
  def max(  self,col=None,na="all"): return self._add_agg("max",col,na)
  def mean( self,col=None,na="all"): return self._add_agg("mean",col,na)
  def count(self,na="all"):          return self._add_agg("nrow",None,na)
  def sum(  self,col=None,na="all"): return self._add_agg("sum",col,na)
  def sd(   self,col=None,na="all"): return self._add_agg("sdev",col,na)
  def var(  self,col=None,na="all"): return self._add_agg("var",col,na)
  # def first(self,col=None,na="all"): return self._add_agg("first",col,na)
  # def last( self,col=None,na="all"): return self._add_agg("last",col,na)
  def ss(self,col=None,na="all"):    return self._add_agg("sumSquares",col,na)
  def mode(self,col=None,na="all"):  return self._add_agg("mode",col,na)


  @property
  def frame(self):
    """
    :return: the result of the group by
    """
    return self.get_frame()

  def get_frame(self):
    """
    :return: the result of the group by
    """
    if not self._res:
      aggs=[]
      for k in self._aggs: aggs += (self._aggs[k])
      self._res = h2o.H2OFrame._expr(expr=expr.ExprNode("GB", self._fr,self._by,self._order_by, *aggs))
    return self._res

  def _add_agg(self,op,col,na):
    if op=="nrow": col=0
    if col is None:
      for i in range(self._fr.ncol):
        if i not in self._by: self._add_agg(op,i,na)
      return self
    elif isinstance(col, basestring):  cidx=self._fr.names.index(col)
    elif isinstance(col, int):         cidx=col
    elif isinstance(col, (tuple,list)):
      for i in col:
        self._add_agg(op,i,na)
      return self
    else:                              raise ValueError("col must be a column name or index.")
    name = "{}_{}".format(op,self._fr.names[cidx])
    self._aggs[name]=[op,cidx,na]
    return self

  def __repr__(self):
    print "GroupBy: "
    print "  Frame: {}; by={}".format(self._fr.frame_id,str(self._by))
    print "  Aggregates: {}".format(str(self._aggs.keys()))
    print "*** Use get_frame() to get groupby frame ***"
    return ""
