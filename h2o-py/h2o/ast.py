#
# This is the new Expr class. TODO: Title will change


"""
This module contains code for the lazy expression DAG.
"""

import sys
from math import sqrt, isnan
import h2o


class H2OObj:
  # ops
  def __add__(self, i): return ExprNode("+",   self,i)
  def __sub__(self, i): return ExprNode("-",   self,i)
  def __mul__(self, i): return ExprNode("*",   self,i)
  def __div__(self, i): return ExprNode("/",   self,i)
  def __mod__(self, i): return ExprNode("mod", self,i)
  def __or__ (self, i): return ExprNode("|",   self,i)
  def __and__(self, i): return ExprNode("&",   self,i)
  def __ge__ (self, i): return ExprNode(">=",  self,i)
  def __gt__ (self, i): return ExprNode(">",   self,i)
  def __le__ (self, i): return ExprNode("<=",  self,i)
  def __lt__ (self, i): return ExprNode("<",   self,i)
  def __eq__ (self, i): return ExprNode("==",  self,i)
  def __ne__ (self, i): return ExprNode("!=",  self,i)
  def __pow__(self, i): return ExprNode("^",   self,i)

  # rops
  def __rmod__(self, i): return ExprNode("mod",i,self)
  def __radd__(self, i): return self.__add__(i)
  def __rsub__(self, i): return ExprNode("-",i,self)
  def __rand__(self, i): return self.__and__(i)
  def __ror__ (self, i): return self.__or__ (i)
  def __rdiv__(self, i): return ExprNode("/",i,self)
  def __rmul__(self, i): return self.__mul__(i)
  def __rpow__(self, i): return ExprNode("^",i,self)

  # unops
  def __abs__ (self): return ExprNode("abs",self)


class ExprNode(H2OObj):
  """ Composable Expressions """
  def __init__(self,op,*args):
    self._data=None    # a scalar, an H2OFrame (with a real-live ID in H2O), or None if pending
    self._op=op        # unary/binary/prefix op
    self._args=args    # arguments to the op
    self._children=[ExprNode._arg_to_expr(a) for a in self._args]  # a list of ExprNode instances; the children of "this" node; (e.g. (+ left rite)  self._children = [left,rite] )

  def is_pending(self): return self._data is None

  def visit(self):
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

  @staticmethod
  def _arg_to_expr(arg):
    if isinstance(arg, h2o.H2OFrame): return arg._id
    elif isinstance(arg, (int, float, ExprNode)): return arg
    elif isinstance(arg, str): return '"'+arg+'"'
    elif isinstance(arg, slice): return "[{}:{}]".format(arg.start,arg.stop)
    raise ValueError("Unexpected arg type: " + str(type(arg)))