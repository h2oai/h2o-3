"""
This module contains code for the lazy expression DAG.
"""

import sys
from math import sqrt, isnan, floor
import h2o
import frame
import tabulate

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

    self._op, self._data = (op, None) if isinstance(op, str) else ("rawdata", op)
    self._name = self._op  # Set an initial name, generally overwritten

    assert self._is_valid(), str(self._name) + str(self._data)

    self._left = left._expr if isinstance(left, frame.H2OVec) else left
    self._rite = rite._expr if isinstance(rite, frame.H2OVec) else rite

    assert self._left is None or self._is_valid(self._left), self.debug()
    assert self._rite is None or self._is_valid(self._rite), self.debug()

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

  def is_local(self): return isinstance(self._data, (list, int, float))

  def is_remote(self): return isinstance(self._data, unicode)

  def is_pending(self): return self._data is None

  def is_computed(self): return not self.is_pending()

  def is_slice(self): return isinstance(self._data, slice)

  def _is_valid(self, obj=None):
    if obj:  # not None'ness depends on short-circuiting `or`; see python docs
      return isinstance(obj, Expr) or isinstance(self._data, unicode)
    return self.is_local() or self.is_remote() or self.is_pending() or self.is_slice()

  def __len__(self):
    """
    The length of this H2OVec/H2OFrame (generally without triggering eager evaluation)
    :return: The number of columns/rows of the H2OFrame/H2OVec.
    """
    return self._len

  def debug(self):
    """
    :return: The structure of this object without evaluating.
    """
    return ("([" + self._name + "] = " +
            str(self._left._name if isinstance(self._left, Expr) else self._left) +
            " " + self._op + " " +
            str(self._rite._name if isinstance(self._rite, Expr) else self._rite) +
            " = " + str(type(self._data)) + ")")

  def show(self, noprint=False):
    """
    Evaluate and print.
    :return:
    """
    self.eager()
    if noprint:
      if isinstance(self._data, unicode):
        j = h2o.frame(self._data)
        data = j['frames'][0]['columns'][0]['data'][0:10]
        return data
      return self._data
    else:
      if isinstance(self._data, unicode):
        j = h2o.frame(self._data)
        data = j['frames'][0]['columns'][0]['data'][0:10]
      elif isinstance(self._data, int):
        print self._data
        return
      else:
        data = [self._data]
      header = self._vecname + " (first " + str(len(data)) + " row(s))"
      rows = range(1, len(data) + 1, 1)
      print tabulate.tabulate(zip(rows, data), headers=["Row ID", header])
      print

#  def __repr__(self):
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
    j = h2o.frame(self._data)
    self._summary = j['frames'][0]['columns'][0]
    return self._summary

  # Basic indexed or sliced lookup
  def __getitem__(self, i):
    x = self.eager()
    if self.is_local(): return x[i]
    if not isinstance(i, int): raise NotImplementedError  # need a bigdata slice here
    # ([ %vec #row #0)
    #j = H2OCONN.Rapids("([ %"+str(self._data)+" #"+str(i)+" #0)")
    #return j['scalar']
    raise NotImplementedError

  # Small-data add; result of a (lazy but small) Expr vs a plain int/float
  def __add__(self, i):
    return self.eager() + i

  def __radd__(self, i):
    return self + i  # Add is commutative

  def __del__(self):
    # Dead pending op or local data; nothing to delete
    if self.is_pending() or self.is_local(): return
    assert self.is_remote(), "Data wasn't remote. Hrm..."
    global __CMD__
    if __CMD__ is None:
      h2o.remove(self._data)
    else:
      s = " (del %" + self._data + " #0)"
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
    __TMPS__ = ""  # Begin gathering rapids commands
    self._do_it()   # Symbolically execute the command
    cmd = __CMD__
    tmps = __TMPS__  # Stop  gathering rapids commands
    __CMD__ = None
    __TMPS__ = None
    if self.is_local():  return self._data  # Local computation, all done

    # Remote computation - ship Rapids over wire, assigning key to result
    if tmps:
      cmd = "(, " + cmd + tmps + ")"
    j = h2o.rapids(cmd)
    if isinstance(self._data, unicode):
      pass  # Big Data Key is the result
    # Small data result pulled locally
    else:
      self._data = j['head'] if j['num_rows'] else j['scalar']
    return self._data

  def _do_child(self, child):
    assert child is None or isinstance(child, Expr)
    global __CMD__
    if child:
      if child.is_pending():
        child._do_it()
      elif isinstance(child._data, (int, float)):
        __CMD__ += "#" + str(child._data)
      elif isinstance(child._data, unicode):
        __CMD__ += "%" + str(child._data)
      elif isinstance(child._data, slice):
        __CMD__ += \
            "(: #" + str(child._data.start) + " #" + str(child._data.stop - 1) \
            + ")"
        child._data = None  # trigger GC now
      else:
        pass
    __CMD__ += " "
    return child

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
    # Magical count-of-4 is the depth of 4 interpreter stack
    py_tmp = cnt != 4 and self._len > 1 and not assign_vec

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

    if self._op in ["+", "&", "-", "*", "/", "==", "<", ">", ">=", "<="]:   # in self.BINARY_INFIX_OPS:
      #   num op num
      #   num op []
      if isinstance(left._data, (int, float)):
        if isinstance(rite._data, (int, float)):   self._data = eval("left " + self._op + " rite")
        elif rite.is_local():                      self._data = eval("[left "+ self._op + " x for x in rite._data]")
        else:                                      pass

      #   [] op num
      elif isinstance(rite._data, (int, float)):
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
      if left.is_local():    self._data = left._data[rite._data]
      #   all rows / columns ([ %fr_key "null" ()) / ([ %fr_key () "null")
      else:                  __CMD__ += ' "null"'

    elif self._op == "=":

      if left.is_local():   raise NotImplementedError
      else:
        if rite is None: __CMD__ += "#NaN"

    elif self._op == "floor":
      if left.is_local():   self._data = [floor(x) for x in left._data]
      else:                 pass

    elif self._op == "month":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op == "dayOfWeek":
      if left.is_local():   raise NotImplementedError
      else:                 pass

    elif self._op == "mean":
      if left.is_local():   self._data = sum(left._data) / len(left._data)
      else:                 __CMD__ += " #0 %TRUE"  # Rapids mean extra args (trim=0, rmNA=TRUE)

    elif self._op in ["as.factor", "h2o.runif"]:
      if left.is_local():   self._data = map(str, left._data)
      else:                 pass

    elif self._op == "quantile":
      if left.is_local():   raise NotImplementedError
      else:
        rapids_series = "{"+";".join([str(x) for x in rite._data])+"}"
        __CMD__ += rapids_series + " %FALSE #7"

    elif self._op == "mktime":
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
      if assign_vec._op != "rawdata":  # Need to roll-up nested exprs
        raise NotImplementedError
      self._left = assign_vec
      self._data = assign_vec._data
