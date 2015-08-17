import h2o
import math


class ExprNode:
  """ Composable Expressions: This module contains code for the lazy expression DAG. """

  def __init__(self,op,*args):
    self._op=op                # unary/binary/prefix op
    self._children=[ExprNode._arg_to_expr(a) for a in args]  # a list of ExprNode instances; the children of "this" node; (e.g. (+ left rite)  self._children = [left,rite] )

  def _eager(self,sb=None):
    """ This call is mutually recursive with ExprNode._do_it and H2OFrame._do_it """
    if sb is None: sb = []
    sb += ["(",self._op," "]
    for child in self._children: ExprNode._do_it(child,sb)
    sb += [") "]
    return sb

  @staticmethod
  def _do_it(child,sb):
    if isinstance(child, h2o.H2OFrame): child._do_it(sb)
    elif isinstance(child, ExprNode):   child._eager(sb)
    else:                               sb+=[str(child)+" "]

  @staticmethod
  def _arg_to_expr(arg):
    if isinstance(arg, (ExprNode, h2o.H2OFrame)): return arg
    elif isinstance(arg, bool):                   return "%{}".format("TRUE" if arg else "FALSE")
    elif isinstance(arg, (int, float)):           return "#{}".format("NaN" if math.isnan(arg) else arg)
    elif isinstance(arg, (unicode,str)):          return '"'+arg+'"'
    elif isinstance(arg, slice):                  return "(: #{} #{})".format(0 if arg.start is None else arg.start,"NaN" if (arg.stop is None or math.isnan(arg.stop)) else arg.stop-1)
    elif isinstance(arg, list):                   return ("(slist \"" + "\" \"".join(arg) + "\")") if isinstance(arg[0], (str,unicode)) else ("(dlist #" + " #".join([str(i) for i in arg])+")")
    elif arg is None:                             return "()"
    raise ValueError("Unexpected arg type: " + str(type(arg)))

  @staticmethod
  def _collapse_sb(sb): return ' '.join("".join(sb).replace("\n", "").split()).replace(" )", ")")

  def _debug_print(self,pprint=True): return "".join(self._to_string(sb=[])) if pprint else ExprNode._collapse_sb(self._to_string(sb=[]))

  def _to_string(self,depth=0,sb=None):
    sb += ['\n', " "*depth, "("+self._op, " "]
    for child in self._children:
      if isinstance(child, h2o.H2OFrame) and not child._computed: child._ast._to_string(depth+2,sb)
      elif isinstance(child, ExprNode):                           child._to_string(depth+2,sb)
      else:                                                       sb+=['\n', ' '*(depth+2), str(child)]
    sb+=['\n',' '*depth+") "] + ['\n'] * (depth==0)  # add a \n if depth == 0
    return sb