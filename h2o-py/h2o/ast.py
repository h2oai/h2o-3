#
# This is the new Expr class. TODO: Title will change


"""
This module contains code for the lazy expression DAG.
"""

import sys
import h2o


class ExprNode:
  """ Composable Expressions """

  # Magical count-of-5:   (get 2 more when looking at it in debug mode)
  #  2 for _do_it frame, 2 for _do_it local dictionary list, 1 for parent
  MAGIC_REF_COUNT = 5 if sys.gettrace() is None else 7  # M = debug ? 6 : 4

  def __init__(self,op,*args):
    self._rows = self._cols = self._id = None
    self._op=op                # unary/binary/prefix op
    self._children=[ExprNode._arg_to_expr(a) for a in args]  # a list of ExprNode instances; the children of "this" node; (e.g. (+ left rite)  self._children = [left,rite] )

  def _eager(self,sb=None):
    """
    The top-level call to evaluate an expression DAG.

    This call is mutually recusrive with ExprNode._do_it and H2OFrame._do_it

    First walk the expr DAG and build a rapids string.
    Second evaluate the rapids string and exit.
    Caller handles the results

    :return: sb
    """
    if sb is None: sb = []
    sb += ["("+self._op+" "]
    for child in self._children: ExprNode._do_it(child,sb)
    sb += [") "]
    return sb

  @staticmethod
  def _do_it(child,sb):
    if isinstance(child, h2o.H2OFrame): child._do_it(sb)
    elif isinstance(child, ExprNode):   child._eager(sb)
    else:                               sb+=[str(child)+" "]

  # debug printing
  def _debug_print(self,pprint=True):
    sb = self._to_string()
    if pprint: print "".join(sb)
    else:      print ExprNode._collapse_sb(sb)

  # companion method to _debug_print
  def _to_string(self,depth=0,sb=None):
    if sb is None: sb = []
    sb += ['\n']
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