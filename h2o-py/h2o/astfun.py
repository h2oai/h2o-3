import itertools, inspect
import expr,frame

classes = (
  "BinOp",
  "Compare",
  "Call",
  "Lambda",
  "Name",
  "Num",
  "Str",
  "List",
  "Tuple",
  "Subscript",
  "Index",
  "Attribute",
)

cmpops = {
  "Gt": ">",
  "GtE": ">=",
  "Lt": "<",
  "LtE": "<=",
  "Eq": "==",
  "NotEq": "!=",
}

binops = {
  "Add": "+",
  "Sub": "-",
  "Mult": "*",
  "Div": "/",
  "Mod": "%",
  "BitOr": "|",
  "BitAnd": "&",
  "Pow": "**",
  "FloorDiv":"//",
}

unop = {
  "Not": "!",
  "UAdd":"+",
  "USub":"-",
}

valid_node_types = tuple(itertools.chain(
  *[
    classes,
    cmpops.keys(),
    binops.keys(),
    unop.keys(),
]))


def _ast_deparse(node):
  """ Deparse ast.Node into Rapids compatible format """
  return ASTDeparser(node).deparse()


def _name(node):
  return node.__class__.__name__


class ASTDeparser:

  def __init__(self,node):
    node_type = _name(node)
    if node_type not in valid_node_types:
      raise ValueError("Node type " + node_type + " not supported.")
    self.node=node  # IN

  @staticmethod
  def _visit_BinOp(binop_node):
    op = binops[_name(binop_node.op)]
    left = ASTDeparser(binop_node.left).deparse()
    rite = ASTDeparser(binop_node.right).deparse()
    return expr.ExprNode(op,left,rite)

  @staticmethod
  def _visit_Call(call_node):
    op = call_node.func.attr
    args = [ASTDeparser(call_node.func.value).deparse()]
    if op == "ifelse" and args[0].name == "h2o":
      args = []
    args +=  [ASTDeparser(arg).deparse() for arg in call_node.args]
    args += [ASTDeparser(arg.value).deparse() for arg in call_node.keywords]
    if call_node.kwargs is not None:
      raise ValueError("unimpl: kwargs in _visit_Call")
    if call_node.starargs is not None:
      raise ValueError("unimpl: starargs in _visit_Call")
    return expr.ExprNode(op,*args)

  @staticmethod
  def _visit_Attribute(attr_node):
    op = attr_node.attr
    obj = ASTDeparser(attr_node.value).deparse()
    default_args = inspect.getargspec(getattr(frame.H2OFrame,op))[3]
    return expr.ExprNode(op,obj,*default_args)

  @staticmethod
  def _visit_Lambda(lambda_node):
    args = [ASTId("{")] + [ASTDeparser(arg).deparse() for arg in lambda_node.args.args] + [ASTId(".")]
    args += [ASTDeparser(lambda_node.body).deparse()] + [ASTId("}")]
    return args

  @staticmethod
  def _visit_Compare(compare_node):
    if len(compare_node.ops) > 1 or len(compare_node.comparators) > 1:
      raise ValueError("unimpl: more than 1 compare op in _visit_Compare")
    op = cmpops[_name(compare_node.ops[0])]
    left = ASTDeparser(compare_node.left).deparse()
    rite = ASTDeparser(compare_node.comparators[0]).deparse()
    return expr.ExprNode(op,left,rite)

  @staticmethod
  def _visit_Name(name_node):
    if name_node.id in ["True","False"]:
      return bool(name_node.id)
    return ASTId(name_node.id)

  @staticmethod
  def _visit_Num(num_node):
    return num_node.n

  @staticmethod
  def _visit_Str(str_node):
    return str_node.s

  @staticmethod
  def _visit_List(list_node):
    return [ASTDeparser(node).deparse() for node in list_node.elts]

  @staticmethod
  def _visit_Subscript(subscript_node):
    fr = ASTDeparser(subscript_node.value).deparse()
    cols = ASTDeparser(subscript_node.slice).deparse()
    return expr.ExprNode("cols", fr, cols)

  @staticmethod
  def _visit_Index(index_node):
    return ASTDeparser(index_node.value).deparse()

  def deparse(self):
    return getattr(self,"_visit_"+_name(self.node))(self.node)


class ASTId:
  def __init__(self, name=None):
    if name is None:
      raise ValueError("Attempted to make ASTId with no name.")
    self.name=name

  def __repr__(self):
    return self.name