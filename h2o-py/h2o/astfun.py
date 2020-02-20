# -*- encoding: utf-8 -*-
"""
Disassembly support.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import dis
import inspect

from h2o.utils.compatibility import *
from .expr import ExprNode, ASTId
from . import h2o

#
# List of supported bytecode instructions: cf. https://docs.python.org/3/library/dis.html#python-bytecode-instructions
#
BYTECODE_INSTRS = {
    "BINARY_SUBSCR": "cols",  # column slice; could be row slice?
    "UNARY_POSITIVE": "+",
    "UNARY_NEGATIVE": "-",
    "UNARY_NOT": "!",
    "BINARY_POWER": "**",
    "BINARY_MULTIPLY": "*",
    "BINARY_FLOOR_DIVIDE": "//",
    "BINARY_TRUE_DIVIDE": "/",
    "BINARY_DIVIDE": "/",
    "BINARY_MODULO": "%",
    "BINARY_ADD": "+",
    "BINARY_SUBTRACT": "-",
    "BINARY_AND": "&",
    "BINARY_OR": "|",
    "COMPARE_OP": "",  # some cmp_op
    # from https://docs.python.org/3/library/dis.html#opcode-CALL_FUNCTION :
    # Calls a function. argc indicates the number of positional arguments. The positional arguments
    # are on the stack, with the right-most argument on top. Below the arguments, the function o
    # bject to call is on the stack. Pops all function arguments, and the function
    # itself off the stack, and pushes the return value.
    "CALL_FUNCTION": "",
    "CALL_FUNCTION_VAR": "",  # Py <= 3.5
    "CALL_FUNCTION_VAR_KW": "",  # Py <= 3.5
    # from https://docs.python.org/3/library/dis.html#opcode-CALL_FUNCTION_KW :
    # Calls a function. argc indicates the number of arguments (positional and keyword).
    # The top element on the stack contains a tuple of keyword argument names.
    # Below the tuple, keyword arguments are on the stack, in the order corresponding to the tuple.
    # Below the keyword arguments, the positional arguments are on the stack, with the
    # right-most parameter on top. Below the arguments, the function object to call is on the stack.
    # Pops all function arguments, and the function itself off the stack,
    # and pushes the return value.
    "CALL_FUNCTION_KW": "",
    # from https://docs.python.org/3/library/dis.html#opcode-CALL_FUNCTION_EX :
    # Calls a callable object with variable set of positional and keyword arguments.
    # If the lowest bit of flags is set, the top of the stack contains a mapping object
    # containing additional keyword arguments.
    # Below that is an iterable object containing positional arguments and a callable object to call.
    # BUILD_MAP_UNPACK_WITH_CALL and BUILD_TUPLE_UNPACK_WITH_CALL can be used
    # for merging multiple mapping objects and iterables containing arguments.
    # Before the callable is called, the mapping object and iterable object are each “unpacked”
    # and their contents passed in as keyword and positional arguments respectively.
    # CALL_FUNCTION_EX pops all arguments and the callable object off the stack,
    # calls the callable object with those arguments,
    # and pushes the return value returned by the callable object.
    "CALL_FUNCTION_EX": "",  # Py >= 3.6
    # from https://docs.python.org/3/library/dis.html#opcode-CALL_METHOD :
    # Calls a method. argc is the number of positional arguments.
    # Keyword arguments are not supported. This opcode is designed to be used with LOAD_METHOD.
    # Positional arguments are on top of the stack.
    # Below them, the two items described in LOAD_METHOD are on the stack
    # (either self and an unbound method object or NULL and an arbitrary callable).
    # All of them are popped and the return value is pushed.
    "CALL_METHOD": "",  # Py >= 3.7
}


def is_bytecode_instruction(instr):
    return instr in BYTECODE_INSTRS

def is_comp(instr):
    return "COMPARE" in instr

def is_binary(instr):
    return "BINARY" in instr

def is_unary(instr):
    return "UNARY" in instr

def is_func(instr):
    return "CALL_FUNCTION" == instr

def is_func_kw(instr):
    return "CALL_FUNCTION_KW" == instr

def is_func_ex(instr):  # Py >= 3.6
    return "CALL_FUNCTION_EX" == instr

def is_func_var(instr): # Py <= 3.5
    return "CALL_FUNCTION_VAR" == instr

def is_func_var_kw(instr): # Py <= 3.5
    return "CALL_FUNCTION_VAR_KW" == instr

def is_method_call(instr): # Py >= 3.7
    return "CALL_METHOD" == instr

def is_callable(instr):
    return is_func(instr) or is_func_kw(instr) or is_func_ex(instr) or is_method_call(instr)

def is_builder(instr):
    return instr.startswith('BUILD_')

def is_load_fast(instr):
    return "LOAD_FAST" == instr

def is_attr(instr):
    return "LOAD_ATTR" == instr

def is_method(instr):
    return "LOAD_METHOD" == instr or is_attr(instr)  # LOAD_METHOD available since 3.7, fallback to `isattr` for backwards compatibility

def is_load_global(instr):
    return "LOAD_GLOBAL" == instr

def is_load_deref(instr):
    return "LOAD_DEREF" == instr

def is_load_outer_scope(instr):
    return is_load_deref(instr) or is_load_global(instr)

def is_return(instr):
    return "RETURN_VALUE" == instr

try:
    # Python 3
    from dis import _unpack_opargs
except ImportError:
    # Reimplement from Python3 in Python2 syntax
    def _unpack_opargs(code):
        extended_arg = 0
        i = 0
        n = len(code)
        while i < n:
            op = ord(code[i]) if PY2 else code[i]
            pos = i
            i += 1
            if op >= dis.HAVE_ARGUMENT:
                if PY2:
                    arg = ord(code[i]) + ord(code[i+1])*256 + extended_arg
                else: # to support Python version (3,3.5)
                    arg = code[i] + code[i+1]*256 + extended_arg
                extended_arg = 0
                i += 2
                if op == dis.EXTENDED_ARG:
                    extended_arg = arg*65536
            else:
                arg = None
            yield (pos, op, arg)


def _disassemble_lambda(co):
    code = co.co_code
    ops = []
    for offset, op, arg in _unpack_opargs(code):
        args = []
        if arg is not None:
            if op in dis.hasconst:
                args.append(co.co_consts[arg])  # LOAD_CONST
            elif op in dis.hasname:
                args.append(co.co_names[arg])  # LOAD_CONST
            elif op in dis.hasjrel:
                raise ValueError("unimpl: op in hasjrel")
            elif op in dis.haslocal:
                args.append(co.co_varnames[arg])  # LOAD_FAST
            elif op in dis.hasfree:
                args.append(co.co_freevars[arg])  # LOAD_DEREF
            elif op in dis.hascompare:
                args.append(dis.cmp_op[arg])  # COMPARE_OP
            elif is_callable(dis.opname[op]):
                args.append(arg)  # oparg == nargs(fcn)
            else:
                args.append(arg)
        ops.append([dis.opname[op], args])
    return ops


def _get_instr(ops, idx=0, argpos=0):
    # returns tuple (instruction, op)
    instr, args = ops[idx][0], ops[idx][1]
    return instr, args[argpos] if args else None


def lambda_to_expr(fun):
    code = fun.__code__
    lambda_dis = _disassemble_lambda(code)
    return _lambda_bytecode_to_ast(code, lambda_dis)


def _lambda_bytecode_to_ast(co, ops):
    # have a stack of ops, read from R->L to get correct oops
    s = len(ops) - 1
    keys = [o[0] for o in ops]
    result = [ASTId("{")] + [ASTId(arg) for arg in co.co_varnames] + [ASTId(".")]
    instr = keys[s]
    if is_return(instr):
        s -= 1
        instr = keys[s]
    if is_bytecode_instruction(instr) or is_load_fast(instr) or is_load_outer_scope(instr):
        body, s = _opcode_read_arg(s, ops, keys)
    else:
        raise ValueError("unimpl bytecode instr: " + instr)
    if s > 0:
        print("Dumping disassembled code: ")
        for i in range(len(ops)):
            if i == s:
                print(i, " --> " + str(ops[i]))
            else:
                print(i, str(ops[i]).rjust(5))
        raise ValueError("Unexpected bytecode disassembly @ " + str(s))
    result += [body] + [ASTId("}")]
    return result


def _opcode_read_arg(start_index, ops, keys):
    instr, op = _get_instr(ops, start_index)
    return_idx = start_index - 1
    if is_bytecode_instruction(instr):
        # print(ops)
        if is_binary(instr):
            return _binop_bc(BYTECODE_INSTRS[instr], return_idx, ops, keys)
        elif is_comp(instr):
            return _binop_bc(op, return_idx, ops, keys)
        elif is_unary(instr):
            return _unop_bc(BYTECODE_INSTRS[instr], return_idx, ops, keys)
        elif is_func(instr):
            return _call_func_bc(op, return_idx, ops, keys)
        elif is_func_kw(instr):
            return _call_func_kw_bc(op, return_idx, ops, keys)
        elif is_func_ex(instr):
            return _call_func_ex_bc(op, return_idx, ops, keys)
        elif is_method_call(instr):
            return _call_method_bc(op, return_idx, ops, keys)
        elif is_func_var(instr):
            return _call_func_var_bc(op, return_idx, ops, keys)
        elif is_func_var_kw(instr):
            return _call_func_var_kw_bc(op, return_idx, ops, keys)
        else:
            raise ValueError("unimpl bytecode op: " + instr)
    elif is_load_fast(instr):
        return [_load_fast(op), return_idx]
    elif is_load_outer_scope(instr):
        return [_load_outer_scope(op), return_idx]
    return op, return_idx


def _binop_bc(op, idx, ops, keys):
    rite, idx = _opcode_read_arg(idx, ops, keys)
    left, idx = _opcode_read_arg(idx, ops, keys)
    return ExprNode(op, left, rite), idx


def _unop_bc(op, idx, ops, keys):
    arg, idx = _opcode_read_arg(idx, ops, keys)
    return ExprNode(op, arg), idx


def _call_func_bc(nargs, idx, ops, keys):
    """
    Implements transformation of CALL_FUNCTION bc inst to Rapids expression.
    The implementation follows definition of behavior defined in
    https://docs.python.org/3/library/dis.html#opcode-CALL_FUNCTION
    
    :param nargs: number of arguments including keyword and positional arguments
    :param idx: index of current instruction on the stack
    :param ops: stack of instructions
    :param keys:  names of instructions
    :return: ExprNode representing method call
    """
    kwargs, idx, nargs = _read_explicit_keyword_args(nargs, idx, ops, keys)
    args, idx, nargs = _read_explicit_positional_args(nargs, idx, ops, keys)
    return _to_rapids_expr(idx, ops, keys, *args, **kwargs)


def _call_func_kw_bc(nargs, idx, ops, keys):
    # Implements calling convention defined by CALL_FUNCTION_KW
    # https://docs.python.org/3/library/dis.html#opcode-CALL_FUNCTION_KW
    # Read keyword arguments
    _, keywords = _get_instr(ops, idx)
    if isinstance(keywords, tuple):  # Py >= 3.6
        # Skip the LOAD_CONST tuple
        idx -= 1
        # Load keyword arguments from stack
        kwargs = {}
        for key in keywords:
            val, idx = _opcode_read_arg(idx, ops, keys)
            kwargs[key] = val
            nargs -= 1
    else:  # Py <= 3.5
        kwargs, idx = _opcode_read_arg(idx, ops, keys)

    exp_kwargs, idx, nargs = _read_explicit_keyword_args(nargs, idx, ops, keys)
    kwargs.update(exp_kwargs)
    args, idx, nargs = _read_explicit_positional_args(nargs, idx, ops, keys)
    return _to_rapids_expr(idx, ops, keys, *args, **kwargs)


def _call_func_var_bc(nargs, idx, ops, keys):
    # Py <= 3.5 only
    var_args, idx = _opcode_read_arg(idx, ops, keys)
    args, idx, _ = _read_explicit_positional_args(nargs, idx, ops, keys)
    args.extend(var_args)
    return _to_rapids_expr(idx, ops, keys, *args)


def _call_func_var_kw_bc(nargs, idx, ops, keys):
    # Py <= 3.5 only
    kwargs, idx = _opcode_read_arg(idx, ops, keys)
    exp_kwargs, idx, nargs = _read_explicit_keyword_args(nargs, idx, ops, keys)
    kwargs.update(exp_kwargs)
    var_args, idx = _opcode_read_arg(idx, ops, keys)
    args, idx, _ = _read_explicit_positional_args(nargs, idx, ops, keys)
    args.extend(var_args)
    return _to_rapids_expr(idx, ops, keys, *args, **kwargs)


def _call_func_ex_bc(flags, idx, ops, keys):
    # https://docs.python.org/3/library/dis.html#opcode-CALL_FUNCTION_EX
    if flags & 1:
        instr, nargs = _get_instr(ops, idx)
        if is_builder(instr):  # first instr can be a map builder if we have to unpack kwargs, followed by normal keywords args
            idx -= 1
            # load **kwargs
            kwargs, idx = _opcode_read_arg(idx, ops, keys)
            # load other keywords args
            nargs -= 1
            if nargs > 0:
                instr, nargs = _get_instr(ops, idx)
                if is_builder(instr):  # BUILD_MAP identifies the start of explicit keyword args
                    idx -= 1
                    while nargs > 0:
                        val, idx = _opcode_read_arg(idx, ops, keys)
                        key, idx = _opcode_read_arg(idx, ops, keys)
                        kwargs[key] = val
                        nargs -= 1
        else:
            # load **kwargs
            kwargs, idx = _opcode_read_arg(idx, ops, keys)
    else:
        kwargs = {}

    instr, nargs = _get_instr(ops, idx)
    if is_builder(instr):  # if there are positional args, it will start with a BUILD_TUPLE instr
        idx -= 1
        args = []
        while nargs > 0:
            new_args, idx = _opcode_read_arg(idx, ops, keys)
            args.insert(0, *new_args)
            nargs -= 1
    else:
        args, idx = _opcode_read_arg(idx, ops, keys)
        args = [] if args is None else args

    return _to_rapids_expr(idx, ops, keys, *args, **kwargs)


def _call_method_bc(nargs, idx, ops, keys):
    # CALL_METHOD instr doesn't support keyword or unpacking arguments
    args, idx, _ = _read_explicit_positional_args(nargs, idx, ops, keys)
    return _to_rapids_expr(idx, ops, keys, *args)


def _read_explicit_keyword_args(nargs, idx, ops, keys):
    kwargs = {}
    while nargs >= 256:  # named args ( foo(50,True,x=10) ) read first  ( right -> left ): used for PY <= 3.5
        val, idx = _opcode_read_arg(idx, ops, keys)
        key, idx = _opcode_read_arg(idx, ops, keys)
        kwargs[key] = val
        nargs -= 256  # drop 256
    return kwargs, idx, nargs


def _read_explicit_positional_args(nargs, idx, ops, keys):
    args = []
    while nargs > 0:
        arg, idx = _opcode_read_arg(idx, ops, keys)
        args.append(arg)
        nargs -= 1
    args.reverse()
    return args, idx, nargs


def _to_rapids_expr(idx, ops, keys, *args, **kwargs):
    # LOAD_ATTR <method_name> or LOAD_METHOD <method_name>: Map call arguments to a call of method on H2OFrame class
    instr, op = _get_instr(ops, idx)
    rapids_args = _get_h2o_frame_method_args(op, *args, **kwargs) if is_method(instr) else []
    # Map function name to proper rapids name
    rapids_op = _get_func_name(op, rapids_args)
    # Go to next instruction
    idx -= 1
    instr, op = _get_instr(ops, idx)
    if is_bytecode_instruction(instr):
        arg, idx = _opcode_read_arg(idx, ops, keys)
        rapids_args.insert(0, arg)
    elif is_load_fast(instr):
        rapids_args.insert(0, _load_fast(op))
        idx -= 1
    return ExprNode(rapids_op, *rapids_args), idx


def _get_h2o_frame_method_args(op, *args, **kwargs):
    fr_cls = h2o.H2OFrame
    if not hasattr(fr_cls, op):
        raise ValueError("Unimplemented: op <%s> not bound in H2OFrame" % op)
    if PY2:
        argspec = inspect.getargspec(getattr(fr_cls, op))
        argnames = argspec.args[1:]
        argdefs = list(argspec.defaults or [])
    else:
        argnames = []
        argdefs = []
        for name, param in inspect.signature(getattr(fr_cls, op)).parameters.items():
            if name == "self": continue
            if param.kind == inspect._VAR_KEYWORD: continue
            argnames.append(name)
            argdefs.append(param.default)
    method_args = list(args) + argdefs[len(args):]
    for a in kwargs: method_args[argnames.index(a)] = kwargs[a]
    return method_args


def _get_func_name(op, args):
    if op == "ceil": op = "ceiling"
    if op == "sum" and len(args) > 0 and args[0]: op = "sumNA"
    if op == "min" and len(args) > 0 and args[0]: op = "minNA"
    if op == "max" and len(args) > 0 and args[0]: op = "maxNA"
    if op == "nacnt": op = "naCnt"
    return op


def _load_fast(x):
    return ASTId(x)


def _load_outer_scope(x):
    if x == 'True':
        return True
    elif x == 'False':
        return False
    stack = inspect.stack()
    for rec in stack:
        frame = rec[0]
        module = frame.f_globals.get('__name__', None)
        if module and module.startswith('h2o.'):
            continue
        scope = frame.f_locals
        if x in scope:
            return scope[x]
    return x
