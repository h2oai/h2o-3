"""
This module contains code for the lazy expression DAG.
"""

import sys
from math import sqrt, isnan
import h2o
import frame

__CMD__ = None
__TMPS__ = None


class Expr(object):
  """
  Expr objects have a few different flavors:
      1. A pending to-be-computed BigData expression. Does _NOT_ have a Key
      2. An already computed BigData expression. Does have a key
      3. A small-data computation, pending or not.

  Pending computations point to other Expr objects in a DAG of pending computations.
  Pointed at by at most one H2OVec (during construction) and no others. If that H2OVec
  goes dead, this computation is known to be an internal temp, used only in building
  other Expr objects.
  """

  def __init__(self, op, left=None, rite=None, length=None):
    """
    Create a new Expr object.

    Constructor choices:
        ("op"   left rite): pending calc, awaits left & rite being computed
        ("op"   None None): precomputed local small data
        (fr_key #num name): precomputed remote Big Data

    :param op: An operation to perform
    :param left: An Expr to the "left"
    :param rite: An Expr to the "right"
    :param length: The length of the H2OVec/H2OFrame object.
    :return: A new Expr object.
    """

    # instance variables
    self._op      = None
    self._data    = None   # the "head" of a frame, or a float, or int
    self._left    = None
    self._rite    = None
    self._name    = None
    self._summary = None   # computed lazily
    self._len     = None
    self._vecname = ""     # the name of the Vec, if any
    self._isslice = False  # if a slice, then return a new H2OVec from show
    self._removed_by_frame_del = False  # True if H2OFrame.__del__ is called over this expr (i.e., expr is rep of some vec that got deleted in a H2OFrame.__del__)

    if isinstance(op, str):
      self._op, self._data = (op, None)
    elif not op and isinstance(left,str):
      self._op, self._data = ("rawdata", left)
    else:
      self._op, self._data = ("rawdata", op)
    self._name = self._op  # Set an initial name, generally overwritten

    assert self._is_valid(), str(self._name) + str(self._data)

    self._left = left._expr if isinstance(left, frame.H2OVec) else left
    self._rite = rite._expr if isinstance(rite, frame.H2OVec) else rite

    assert self._left is None or isinstance(self._left, str) or self._left._is_valid(), self._left.debug()
    assert self._rite is None or isinstance(self._rite, str) or self._rite._is_valid(), self._rite.debug()

    # Compute length eagerly
    if self.is_remote():   # Length must be provided for remote data
      assert length is not None
      self._len = length
    elif self.is_local():  # Local data, grab length by inspection
      self._len = len(self._data) if isinstance(self._data, list) else 1
    elif self.is_slice():
      self._len = self._data.stop - self._data.start
    else:
      self._len = length if length else len(self._left)
    assert self._len is not None

    if left and isinstance(left, frame.H2OVec):
      self._vecname = left._name

  def name(self): return self._name

  def op(self): return self._op

  def set_len(self, i): self._len = i

  def get_len(self): return self._len

  def data(self): return self._data

  def left(self): return self._left

  def rite(self): return self._rite

  def vecname(self): return self._vecname

  def is_local(self): return isinstance(self._data, (list, tuple, int, float, str))

  def is_remote(self): return isinstance(self._data, unicode)

  def is_pending(self): return self._data is None

  def removed_by_frame_del(self): return self._removed_by_frame_del

  def is_computed(self): return not self.is_pending()

  def is_slice(self): return isinstance(self._data, slice)

  def _is_valid(self):
    return self.is_local() or self.is_remote() or self.is_pending() or self.is_slice()

  def _is_key(self):
    has_key = self._data is not None and isinstance(self._data, unicode)
    if has_key:
      return "py" == self._data[0:2]
    return False

  def __len__(self):
    """
    The length of this H2OVec/H2OFrame (generally without triggering eager evaluation)

    :return: The number of columns/rows of the H2OFrame/H2OVec.
    """
    return self._len

  def dim(self):
    """
    Eagerly evaluate the Expr. If it's an H2OFrame, return the number of rows and columns.

    :return: The number of rows and columns in the H2OFrame as a list [rows, cols].
    """
    self.eager()
    if self.is_remote(): # potentially big data
      frame = h2o.frame(self._data)
      return [frame['frames'][0]['rows'], len(frame['frames'][0]['columns'])]
    elif self.is_local(): # small data
      return [1,1] if not hasattr(self._data, '__len__') else [1,len(self._data)]
    raise ValueError("data must be local or remote")

  def debug(self):
    """
    :return: The structure of this object without evaluating.
    """
    return ("(" + self._name + " <== " +
            str(self._left._name if isinstance(self._left, Expr) else self._left) +
            " " + self._op + " " +
            str(self._rite._name if isinstance(self._rite, Expr) else self._rite) +
            " ==> " + str(type(self._data)) + ")")

  def show(self, noprint=False):
    """
    Evaluate and print.

    :return: None
    """
    self.eager()
    if noprint:
      if isinstance(self._data, unicode):
        j = h2o.frame(self._data)
        data = [c['data'] if c['type']!="string" else c["string_data"] for c in j['frames'][0]['columns'][:]]
        domains  = [c['domain'] for c in j['frames'][0]['columns']]
        for i in range(len(data)):
          if domains[i] is not None:
            for j in range(len(data[i])):
              if data[i][j] == "NaN": continue
              data[i][j] = domains[i][int(data[i][j])]
        data = map(list, zip(*data))
        return data[0:min(10,len(data))]
      return self._data
    else:
      if isinstance(self._data, unicode):
        j = h2o.frame(self._data)
        data = [c['data'] for c in j['frames'][0]['columns'][:]]
      elif isinstance(self._data, (int, float, str, list)):
        print self._data
        print
        return
      else:
        data = [self._data]
      t_data = map(list, zip(*data))
      t_data = t_data[0:min(10,len(t_data))]
      for didx,d in enumerate(t_data): t_data[didx].insert(0,didx)
      headers = ["Row ID"]
      for i in range(len(t_data[0])): headers.append('')
      print "Displaying first " + str(len(t_data)) + " row(s)"
      h2o.H2ODisplay(t_data,headers)

  # def __repr__(self):
  #    self.show()
  #    return ""

  # Compute summary data
  def summary(self):
    self.eager()
    if self.is_local():
      x = self._data[0]
      t = 'int' if isinstance(x, int) else (
          'enum' if isinstance(x, str) else 'real')
      mins = [min(self._data)]
      maxs = [max(self._data)]
      n = len(self._data)
      mean = sum(self._data) / n if t != 'enum' else None
      ssq = 0
      zeros = 0
      missing = 0
      for x in self._data:
        delta = x - mean
        if t != 'enum': ssq += delta * delta
        if x == 0:  zeros += 1
        if x is None or (t != 'enum' and isnan(x)): missing += 1
      stddev = sqrt(ssq / (n - 1)) if t != 'enum' else None
      return {'type': t, 'mins': mins, 'maxs': maxs, 'mean': mean, 'sigma': stddev, 'zeros': zeros, 'missing': missing}
    if self._summary: return self._summary
    j = h2o.frame_summary(self._data)
    self._summary = j['frames'][0]['columns'][0]
    return self._summary

  # Basic indexed or sliced lookup
  def __getitem__(self, i):
    if   self.is_local():
      if    isinstance(i, int)  : return self.eager()[i]
      elif  isinstance(i, tuple): return self.eager()[i[0]][i[1]]
      else                      : raise ValueError("Integer and 2-tuple slicing supported only")
    elif self.is_remote() or self.is_pending():
      if    isinstance(i, int)  : return Expr("[", self, Expr(("()", i)))  # column slicing
      elif  isinstance(i, tuple): # row, column slicing
        res = Expr("[", self, Expr((i[0], i[1])))
        if isinstance(i[0],int) and isinstance(i[1],int): return res.eager() # small data
        return res # potentially big data
      else                      : raise ValueError("Integer and 2-tuple slicing supported only")
    raise NotImplementedError

  def _simple_expr_bin_op( self, i, op):
    if isinstance(i, h2o.H2OFrame):  return i._simple_frames_bin_rop(self,op)
    if isinstance(i, h2o.H2OVec  ):  return i._simple_vec_bin_rop(self,op)
    if isinstance(i, Expr)        :
      e = self.eager()
      return  Expr(op, Expr(e), i) if isinstance(e, (int,float)) else Expr(op, self, i)
    if isinstance(i, (int, float)):  return Expr(op, self, Expr(i))
    if isinstance(i, str)         :  return Expr(op, self, Expr(None,i))
    raise NotImplementedError

  def _simple_expr_bin_rop(self, i, op):
    if isinstance(i, (int, float)):  return Expr(op, Expr(i), self)
    raise NotImplementedError

  def logical_negation(self):  return Expr("not", self)

  def __add__(self, i):  return self._simple_expr_bin_op(i,"+" )
  def __mod__(self, i):  return self._simple_expr_bin_op(i, "mod")
  def __sub__(self, i):  return self._simple_expr_bin_op(i,"-" )
  def __and__(self, i):  return self._simple_expr_bin_op(i,"&" )
  def __or__ (self, i):  return self._simple_expr_bin_op(i,"|" )
  def __div__(self, i):  return self._simple_expr_bin_op(i,"/" )
  def __mul__(self, i):  return self._simple_expr_bin_op(i,"*" )
  def __eq__ (self, i):  return self._simple_expr_bin_op(i,"n")
  def __ne__ (self, i):  return self._simple_expr_bin_op(i,"N")
  def __pow__(self, i):  return self._simple_expr_bin_op(i,"^" )
  def __ge__ (self, i):  return self._simple_expr_bin_op(i,"G")
  def __gt__ (self, i):  return self._simple_expr_bin_op(i,"g" )
  def __le__ (self, i):  return self._simple_expr_bin_op(i,"L")
  def __lt__ (self, i):  return self._simple_expr_bin_op(i,"l" )

  def __radd__(self, i): return self.__add__(i)
  def __rmod__(self, i): return self._simple_expr_bin_rop(i,"mod")
  def __rsub__(self, i): return self._simple_expr_bin_rop(i,"-")
  def __rand__(self, i): return self.__and__(i)
  def __ror__ (self, i): return self.__or__ (i)
  def __rdiv__(self, i): return self._simple_expr_bin_rop(i,"/")
  def __rmul__(self, i): return self.__mul__(i)
  def __rpow__(self, i): return self._simple_expr_bin_rop(i,"^")

  def __abs__ (self): return h2o.abs(self)

  # generic reducers (min, max, sum, sd, var, mean, median)
  def min(self):
    """
    :return: A lazy Expr representing the standard deviation of this H2OVec.
    """
    return Expr("min", self).eager()

  def max(self):
    """
    :return: A lazy Expr representing the variance of this H2OVec.
    """
    return Expr("max", self).eager()

  def sum(self):
    """
    :return: A lazy Expr representing the variance of this H2OVec.
    """
    return Expr("sum", self).eager()

  def sd(self):
    """
    :return: A lazy Expr representing the standard deviation of this H2OVec.
    """
    return Expr("sd", self)

  def var(self):
    """
    :return: A lazy Expr representing the variance of this H2OVec.
    """
    return Expr("var", self)

  def mean(self):
    """
    :return: A lazy Expr representing the mean of this H2OVec.
    """
    return Expr("mean", self).eager()

  def median(self):
    """
    :return: A lazy Expr representing the median of this H2OVec.
    """
    return Expr("median", self).eager()

  def __del__(self):
    if self.removed_by_frame_del(): return   # see H2OFrame.__del__
    # Dead pending op or local data; nothing to delete
    if self.is_pending() or self.is_local(): return
    assert self.is_remote(), "Data wasn't remote. Hrm..."
    global __CMD__
    if __CMD__ is None:
      if h2o is not None:
        h2o.remove(self._data)
    else:
      # Hard/deep remove of a Vec, built into a rapids expression
      s = " (del '" + self._data + "')"
      global __TMPS__
      if __TMPS__ is None:
        print "Lost deletes: ", s
      else:
        __TMPS__ += s

  def eager(self):
    """
    This forces a top-level execution, as needed, and produces a top-level result
    locally. Frames are returned and truncated to the standard preview response
    provided by rapids - 100 rows X 200 cols.

    :return: A key pointing to the big data object
    """
    if self.is_computed(): return self._data
    # Gather the computation path for remote work, or doit locally for local work
    global __CMD__, __TMPS__
    assert not __CMD__ and not __TMPS__
    __CMD__ = ""
    __TMPS__ = ""    # Begin gathering rapids commands
    dummy = self     # Force extra refcnt so we get a top-level assignment in do_it
    self._do_it()    # Symbolically execute the command
    cmd = __CMD__
    tmps = __TMPS__  # Stop  gathering rapids commands
    __CMD__ = None
    __TMPS__ = None
    if self.is_local():  return self._data  # Local computation, all done

    # Remote computation - ship Rapids over wire, assigning key to result
    if tmps:
      cmd = "(, " + cmd + tmps + ")"
    j = h2o.rapids(cmd)
    if j['result_type'] == 0:
      pass  # Big Data Key is the result
    # Small data result pulled locally
    elif j['num_rows']:   # basically checks if num_rows is nonzero... sketchy.
      self._data = j['head']
    elif j['result'] in [u'TRUE', u'FALSE']:
      self._data = (j['result'] == u'TRUE')
    elif j['result_type'] in [1,2,3,4]:
      if isinstance(j['string'], str):
        self._data = j['string']
      if isinstance(j['string'], unicode):
        self._data = j['string'].encode('utf-8')
      else:
        if not hasattr(j['scalar'], '__len__'): self._data = j['scalar']

    if j['result_type'] in [3,4]:
      for key in j['vec_ids']:
        h2o.remove(key['name'])

    return self._data

  def _do_child(self, child):
    assert child is None or isinstance(child, Expr), " expected None or Expr but found: %r" % child
    global __CMD__
    if child:
      if child.is_pending():
        child._do_it()
      elif isinstance(child._data, (int, float)):
        __CMD__ += "#" + str(child._data)
      elif isinstance(child._data, (str,unicode)):
        __CMD__ += "'" + str(child._data) + "'"
      elif isinstance(child._data, slice):
        __CMD__ += "(: #"+str(child._data.start)+" #"+str(child._data.stop - 1)+")"
        child._data = None  # trigger GC now
      elif self._op == "[" and isinstance(self._rite._data, tuple): # multi-dimensional slice
        if not isinstance(child._data, tuple): return child         # doing left child.  just return.
        __CMD__ += self.multi_dim_slice_cmd(child)                  # doing right child. generate slice string
        return child
    __CMD__ += " "
    return child

  def multi_dim_slice_cmd(self, child):
    return self.multi_dim_slice_data_cmd(child) + ' ' + self.multi_dim_slice_rows_cmd(child) + ' ' + \
           self.multi_dim_slice_cols_cmd(child)

  def multi_dim_slice_data_cmd(self, child):
    if    isinstance(self._left._data,unicode): return ""
    elif  isinstance(self._left._data, list)  :
      c = child._data[1]
      if isinstance(c, int): return "'" + self._left._data[c] + "'"
      if isinstance(c, slice):
        cols = self._left._data[c]
        cmd = "(cbind %FALSE"
        for col in cols: cmd += " '" + str(col) + "'"
        cmd += ")"
        return cmd
      if c == "()":
        cmd = "(cbind %FALSE"
        for col in self._left._data: cmd += " '" + str(col) + "'"
        cmd += ")"
        return cmd
    raise NotImplementedError

  def multi_dim_slice_rows_cmd(self, child):
    r = child._data[0]
    if isinstance(r, int): return "#" + str(r)
    if isinstance(r, slice): return "(: #"+str(r.start)+" #"+str(r.stop)+")"
    if r == "()": return '()'
    raise NotImplementedError

  def multi_dim_slice_cols_cmd(self, child):
    if   isinstance(self._left._data, list): return '\"null\"' # TODO: there might be a bug here: replace null with ()
    elif isinstance(self._left._data,unicode):
      c = child._data[1]
      if isinstance(c, int): return "#" + str(c)
      if isinstance(c, slice): return "(: #"+str(c.start)+" #"+str(c.stop)+")"
      if c == "()": return '()'
    raise NotImplementedError

  def _do_it(self):
    """
    External API for eager; called by all top-level demanders (e.g. print)
    This may trigger (recursive) big-data evaluation.

    :return: None
    """
    if self.is_computed(): return

    # Slice assignments are a 2-nested deep structure, returning the left-left
    # vector.  Must fetch it now, before it goes dead after eval'ing the slice.
    # Shape: (= ([ %vec bool_slice_expr) vals_to_assign)
    # Need to fetch %vec out
    assign_vec = self._left._left if self._op == "=" and self._left._op == "[" else None

    # See if this is not a temp and not a scalar; if so it needs a name
    # Remove one count for the call to getrefcount itself
    cnt = sys.getrefcount(self) - 1

    # Magical count-of-5:
    #  1 for _do_it    frame, 1 for _do_it    local dictionary list
    #  1 for _do_child frame, 1 for _do_child local dictionary list
    #  1 for each parent.
    py_tmp = cnt != 5 and self._len > 1 and not assign_vec

    global __CMD__
    if py_tmp:
      self._data = frame.H2OFrame.py_tmp_key()  # Top-level key/name assignment
      __CMD__ += "(= !" + self._data + " "
    if self._op != ",":           # Comma ops curry in-place (just gather args)
      __CMD__ += "(" + self._op + " "

    left = self._do_child(self._left)  # down the left

    rite = self._do_child(self._rite)  # down the right

    # eventually will need to create dedicated objects each overriding a "set_data" method
    # live with this "switch" for now

    # Do not try/catch NotImplementedError - it blows the original stack trace
    # so then you can't see what's not implemented

    if self._op in ["+", "&", "|", "-", "*", "/", "^", "n", "N", "g", "G", "l", "L", "mod"]:   # in self.BINARY_INFIX_OPS:
      rapids_dict = {"+":"+", "&":"&", "|":"|", "-":"-", "*":"*", "/":"/", "^":"**", "n":"==", "N":"!=", "g":">", "G":">=", "l":"<",
                     "L":"<=", "mod":"mod"}
      #   num op num
      #   num op []
      if isinstance(left._data, (int, float,str)):
        if isinstance(rite._data, (int, float,str)):   self._data = eval("left " + rapids_dict[self._op] + " rite")
        elif rite.is_local():                      self._data = eval("[left "+ rapids_dict[self._op] +
                                                                     " x for x in rite._data]")
        else:                                      pass

      #   [] op num
      elif isinstance(rite._data, (int, float,str)):
        if left.is_local():   self._data = eval("[x" + self._op + " rite for x in left._data]")
        else:                 pass

      #   [] op []
      else:
        if left.is_local() and rite.is_local():             self._data = eval("[x " + self._op + " y for x, y in zip(left._data, rite._data)]")
        elif (left.is_remote() or left._data is None) and \
                (rite.is_remote() or rite._data is None):      pass
        else:                                               raise NotImplementedError

    elif self._op == "[":

      #   [] = []
      if isinstance(rite._data, tuple): pass #   multi-dimensional slice
      elif left.is_local():    self._data = left._data[rite._data]
      #   all rows / columns ([ %fr_key "null" ()) / ([ %fr_key () "null")
      else:                  __CMD__ += ' "null"'

    elif self._op == "=":

      if left.is_local():   raise NotImplementedError
      else:
        if rite is None: __CMD__ += "#NaN"

    elif self._op in ["floor", "abs"]:
      if left.is_local():   self._data = eval("[" + self._op +  "(x) for x in left._data]")
      else:                 pass

    elif self._op == "not":
      if left.is_local():   self._data = [not x for x in left._data]
      else:                 pass

    elif self._op == "sign":
      if left.is_local():   self._data = [cmp(x,0) for x in left._data]
      else:                 pass

    elif self._op in ["cos", "sin", "tan", "acos", "asin", "atan", "cosh", "sinh", "tanh", "acosh", "asinh", "atanh", \
                      "sqrt", "trunc", "log", "log10", "log1p", "exp", "expm1", "gamma", "lgamma"]:
      if left.is_local():   self._data = eval("[math." + self._op + "(x) for x in left._data]")
      else:                 pass

    elif self._op in ["cospi", "sinpi", "tanpi", "ceiling", "log2", "digamma", "trigamma"]:
      if left.is_local():
        if self._op   == "cospi"   : self._data = eval("[math.cos(math.pi*x) for x in left._data]")
        elif self._op == "sinpi"   : self._data = eval("[math.sin(math.pi*x) for x in left._data]")
        elif self._op == "tanpi"   : self._data = eval("[math.tan(math.pi*x) for x in left._data]")
        elif self._op == "ceiling" : self._data = eval("[math.ceil(x) for x in left._data]")
        elif self._op == "log2"    : self._data = eval("[math.log(x,2) for x in left._data]")
        elif self._op == "digamma" : self._data = eval("[scipy.special.polygamma(0,x) for x in left._data]")
        elif self._op == "trigamma": self._data = eval("[scipy.special.polygamma(1,x) for x in left._data]")
      else:                 pass

    elif self._op == "year":
      if left.is_local(): raise NotImplementedError
      else:               pass

    elif self._op == "month":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op == "dayOfWeek":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op == "day":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op == "week":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op == "hour":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op in ["min", "max", "sum", "median"]:
      if left.is_local():   raise NotImplementedError
      else:                 __CMD__ += "%FALSE"

    elif self._op == "cbind":
      if left.is_local():
        __CMD__ += " %FALSE "
        for v in left._data: __CMD__ += "'" + str(v._expr._data) + "'"
      else:                 pass

    elif self._op == "mean":
      if left.is_local():   self._data = sum(left._data) / len(left._data)
      else:                 __CMD__ += " #0 %TRUE"  # Rapids mean extra args (trim=0, rmNA=TRUE)

    elif self._op in ["var", "sd"]:
      if left.is_local():
        mean = sum(left._data) / len(left._data)
        sum_of_sq = sum((x-mean)**2 for x in left._data)
        num_obs = len(left._data)
        var = sum_of_sq / (num_obs - 1)
        self._data = var if self._op == "var" else var**0.5
      else:                 __CMD__ += " () %TRUE \"everything\"" if self._op == "var" else " %TRUE"

    elif self._op == "is.factor":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op in ["as.character", "as.factor", "h2o.runif", "is.na"]:
      if left.is_local():   self._data = map(str, left._data)
      else:                 pass

    elif self._op == "as.numeric":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op == "quantile":
      if left.is_local():   raise NotImplementedError
      else:
        rapids_series = "(dlist #"+" #".join([str(x) for x in rite._data])+")"
        __CMD__ += rapids_series + " "

    elif self._op == "mktime":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op == "t":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op == ",":
      pass

    else:
      raise NotImplementedError(self._op)

    # End of expression... wrap up parens
    if self._op != ",":
      __CMD__ += ")"
    if py_tmp:
      __CMD__ += ")"

    # Free children expressions; might flag some subexpresions as dead
    self._left = None  # Trigger GC/ref-cnt of temps
    self._rite = None

    # Keep LHS alive
    if assign_vec:
      #if assign_vec._op != "rawdata":  # Need to roll-up nested exprs
      #  print assign_vec.debug()
      #  print assign_vec._data,assign_vec._left,assign_vec._rite
      #  raise NotImplementedError
      self._left = assign_vec
      self._data = assign_vec._data
