import expr
from opcode import *
import frame
import inspect

BYTECODE_INSTRS = {
  "BINARY_SUBSCR"      : "cols",  # column slice; could be row slice?
  "UNARY_POSITIVE"     : "+",
  "UNARY_NEGATIVE"     : "-",
  "UNARY_NOT"          : "!",
  "BINARY_POWER"       : "**",
  "BINARY_MULTIPLY"    : "*",
  "BINARY_FLOOR_DIVIDE": "//",
  "BINARY_TRUE_DIVIDE" : "/",
  "BINARY_DIVIDE"      : "/",
  "BINARY_MODULO"      : "%",
  "BINARY_ADD"         : "+",
  "BINARY_SUBTRACT"    : "-",
  "BINARY_AND"         : "&",
  "BINARY_OR"          : "|",
  "COMPARE_OP"         : "",  # some cmp_op
  "CALL_FUNCTION"      : "",  # some function call, have nargs in ops list...
}

def is_bytecode_instruction(instr): return instr in BYTECODE_INSTRS
def is_comp(instr):                 return "COMPARE" in instr
def is_binary(instr):               return "BINARY" in instr
def is_unary(instr):                return "UNARY" in instr
def is_func(instr):                 return "CALL_FUNCTION" == instr
def is_load_fast(instr):            return "LOAD_FAST" == instr
def is_attr(instr):                 return "LOAD_ATTR" == instr
def is_load_global(instr):          return "LOAD_GLOBAL" == instr
def is_return(instr):               return "RETURN_VALUE" == instr


class ASTId:
  def __init__(self, name=None):
    if name is None:
      raise ValueError("Attempted to make ASTId with no name.")
    self.name=name

  def __repr__(self):
    return self.name


def _bytecode_decompile_lambda(co):
  code = co.co_code
  n = len(code)
  i = 0
  ops = []
  while i < n:
    c = code[i]
    op = ord(c)
    args = []
    i += 1
    if op >= HAVE_ARGUMENT:
      oparg = ord(code[i]) + ord(code[i + 1]) * 256
      i += 2
      if op in hasconst:        args.append(co.co_consts[oparg]) # LOAD_CONST
      elif op in hasname:       args.append(co.co_names[oparg])  # LOAD_CONST
      elif op in hasjrel:       raise ValueError("unimpl: op in hasjrel")
      elif op in haslocal:      args.append(co.co_varnames[oparg])  # LOAD_FAST
      elif op in hascompare:    args.append(cmp_op[oparg])  # COMPARE_OP
      elif is_func(opname[op]): args.append(oparg)  # oparg == nargs(fcn)
    ops.append( [opname[op], args] )
  return _lambda_bytecode_to_ast(co,ops)

def _lambda_bytecode_to_ast(co,ops):
  # have a stack of ops, read from R->L to get correct oops
  s = len(ops) - 1
  keys = [o[0] for o in ops]
  result = [ASTId("{")] + [ASTId(arg) for arg in co.co_varnames] + [ASTId(".")]
  instr = keys[s]
  if is_return(instr):
    s-=1
    instr = keys[s]
  if is_bytecode_instruction(instr):
    body,s = _opcode_read_arg(s,ops,keys)
  else:
    raise ValueError("unimpl bytecode instr: " + instr)
  if s > 0:
    print "Dumping disassembled code: "
    for i in range(len(ops)):
      if i == s: print i, " --> " + str(ops[i])
      else:      print i, str(ops[i]).rjust(5)
    raise ValueError("Unexpected bytecode disassembly @ " + str(s) )
  result += [body] + [ASTId("}")]
  return result

def _opcode_read_arg(start_index,ops,keys):
  instr = keys[start_index]
  return_idx = start_index-1
  if is_bytecode_instruction(instr):
    if is_binary(instr):  return _binop_bc(BYTECODE_INSTRS[instr],return_idx,ops,keys)
    elif is_comp(instr):  return _binop_bc(ops[start_index][1][0],return_idx,ops,keys)
    elif is_unary(instr): return _unop_bc(BYTECODE_INSTRS[instr],return_idx,ops,keys)
    elif is_func(instr):  return _func_bc(ops[start_index][1][0],return_idx,ops,keys)
    else:                 raise ValueError("unimpl bytecode op: " + instr)
  elif is_load_fast(instr):   return [_load_fast(ops[start_index][1][0]), return_idx]
  elif is_load_global(instr): return [_load_global(ops[start_index][1][0]), return_idx]
  return [ops[start_index][1][0], return_idx]

def _binop_bc(op,idx,ops,keys):
  rite,idx= _opcode_read_arg(idx,ops,keys)
  left,idx= _opcode_read_arg(idx,ops,keys)
  return [expr.ExprNode(op,left,rite),idx]

def _unop_bc(op,idx,ops,keys):
  arg,idx= _opcode_read_arg(idx,ops,keys)
  return [expr.ExprNode(op,arg),idx]

def _func_bc(nargs,idx,ops,keys):
  named_args = {}
  unnamed_args = []
  while nargs > 0:
    if nargs >=256:  # named args ( foo(50,True,x=10) ) read first  ( right -> left )
      arg,idx=_opcode_read_arg(idx,ops,keys)
      named_args[ops[idx][1][0]] = arg
      idx-=1      # skip the LOAD_CONST for the named args
      nargs-=256  # drop 256
    else:
      arg,idx=_opcode_read_arg(idx,ops,keys)
      unnamed_args.insert(0, arg)
      nargs-=1
  op = ops[idx][1][0]
  if op not in dir(frame.H2OFrame):
    raise ValueError("Unimplemented: op not bound in H2OFrame")
  if is_attr(ops[idx][0]):
    argspec = inspect.getargspec(getattr(frame.H2OFrame, op))
    argnames = argspec.args[1:]
    argdefs  = argspec.defaults or ()
    args = unnamed_args + list(argdefs[len(unnamed_args):])
    for a in named_args: args[argnames.index(a)] = named_args[a]
  if op=="ceil": op = "ceiling"
  if op=="sum" and len(args) > 0 and args[0]: op="sumNA"
  if op=="min" and len(args) > 0 and args[0]: op="minNA"
  if op=="max" and len(args) > 0 and args[0]: op="maxNA"
  idx-=1
  if is_bytecode_instruction(ops[idx][0]):
    arg,idx = _opcode_read_arg(idx,ops,keys)
    args.insert(0, arg)
  elif is_load_fast(ops[idx][0]):
    args.insert(0, _load_fast(ops[idx][1][0]))
    idx-=1
  return [expr.ExprNode(op,*args),idx]

def _load_fast(x):
  return ASTId(x)

def _load_global(x):
  if x == 'True': return True
  elif x == 'False': return False
  return x
