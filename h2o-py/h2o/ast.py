#
# This is the new Expr class. TODO: Title will change


"""
This module contains code for the lazy expression DAG.
"""

import sys
import h2o

import gc

class ExprNode:
  """ Composable Expressions """

  # Magical count-of-4:   (get 2 more when looking at it in debug mode)
  #  2 for _do_it frame, 2 for _do_it local dictionary list
  MAGIC_REF_COUNT = 4 if sys.gettrace() is None else 6  # M = debug ? 5 : 4

  def __init__(self,op,*args):
    self._rows = self._cols = self._id = None
    self._data=None            # a scalar, an H2OFrame (with a real-live ID in H2O), or None if pending
    self._op=op                # unary/binary/prefix op
    self._children=[ExprNode._arg_to_expr(a) for a in args]  # a list of ExprNode instances; the children of "this" node; (e.g. (+ left rite)  self._children = [left,rite] )

  def is_pending(self):  return self._data is None


  def _eager(self,sb=None):
    """
    The top-level call to evaluate an expression DAG.

    First walk the expr DAG and build a rapids string.
    Second evaluate the rapids string and exit.
    Caller handles the results

    :return: None
    """
    assert self.is_pending()
    if sb is None: sb = []

    sb += ["("+self._op+" "]
    for child in self._children:
      if isinstance(child, (h2o.H2OFrame,ExprNode)): child._do_it(sb)
      else:                                          sb+=[str(child)+" "]
    sb += [") "]

    return sb

  def _do_it(self,sb): self._eager(sb)

  def __del__(self):
    # Dead pending op or local data; nothing to delete
    if self.is_pending(): return
    h2o.remove(self._data)

  # debug printing
  def _debug_print(self,pprint=True):
    sb = self._to_string()
    if pprint: print "".join(sb)
    else:      print ExprNode._collapse_sb(sb)

  # companion method to _debug_print
  def _to_string(self,depth=0,sb=None):
    if sb is None: sb = []
    sb += ['\n']
    if self.is_pending():
      sb += [" "*depth + "("+self._op, " "]   # the ',' gives a space and no newline
      for i,child in enumerate(self._children):
        if isinstance(child, ExprNode): child._to_string(depth+2,sb)
        else:
          if depth > 0:
            sb += ["\n"]
            sb += [" "*(depth+2) + str(child)]
          else:
            if i==(len(self._children)-1): sb +=[str(child)]
            else:                        sb += [str(child) + " "]
        if i==(len(self._children)-1): sb += ['\n'+' '*depth+") "]
    else:
      sb += [self._data]
    if depth==0: sb += ["\n"]
    return sb

  @staticmethod
  def _arg_to_expr(arg):
    if isinstance(arg, (int, float, ExprNode, h2o.H2OFrame)): return arg
    elif isinstance(arg, str): return '"'+arg+'"'
    elif isinstance(arg, slice): return "[{}:{}]".format(arg.start,arg.stop)
    raise ValueError("Unexpected arg type: " + str(type(arg)))

  @staticmethod
  def _collapse_sb(sb):
    return ' '.join("".join(sb).replace("\n", "").split()).replace(" )", ")")