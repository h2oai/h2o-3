#
# This is the new Expr class. TODO: Title will change


"""
This module contains code for the lazy expression DAG.
"""

import sys
import h2o
from h2oobject import H2OObject

class ExprNode(H2OObject):
  """ Composable Expressions """
  def __init__(self,op,*args):
    self._rows = self._cols = self._id = None
    self._data=None    # a scalar, an H2OFrame (with a real-live ID in H2O), or None if pending
    self._op=op        # unary/binary/prefix op
    self._args=args    # arguments to the op
    self._children=[ExprNode._arg_to_expr(a) for a in self._args]  # a list of ExprNode instances; the children of "this" node; (e.g. (+ left rite)  self._children = [left,rite] )

  def is_pending(self): return self._data is None
  def is_computed(self): return not self.is_pending()

  def _visit(self):
    sb = self._to_string()
    expr = ' '.join("".join(sb).replace("\n", "").split()).replace(" )", ")")
    print expr

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

  def _pprint(self):
    sb = self._to_string()
    print "".join(sb)

  def _eager(self):
    if self.is_computed(): return self._data

  def _do_it(self):
    if not self.is_computed():
      # self is really just a node in expression DAG
      # self may have referrers within the DAG and without!
      # if self has ___ referrers, then need to SAVE the result
      # of the execution of self
      cnt = sys.getrefcount(self) - 1
      pytmp = cnt != 5


  @staticmethod
  def _arg_to_expr(arg):
    if isinstance(arg, h2o.H2OFrame): return arg._id
    elif isinstance(arg, (int, float, ExprNode)): return arg
    elif isinstance(arg, str): return '"'+arg+'"'
    elif isinstance(arg, slice): return "[{}:{}]".format(arg.start,arg.stop)
    raise ValueError("Unexpected arg type: " + str(type(arg)))