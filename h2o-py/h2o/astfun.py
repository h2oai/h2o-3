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
    # "CALL_FUNCTION_EX": "",
    # from https://docs.python.org/3/library/dis.html#opcode-CALL_METHOD :
    # Calls a method. argc is the number of positional arguments.
    # Keyword arguments are not supported. This opcode is designed to be used with LOAD_METHOD.
    # Positional arguments are on top of the stack.
    # Below them, the two items described in LOAD_METHOD are on the stack
    # (either self and an unbound method object or NULL and an arbitrary callable).
    # All of them are popped and the return value is pushed.
    "CALL_METHOD": "",
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

def is_func_ex(instr):  # since 3.6
    return "CALL_FUNCTION_EX" == instr

def is_method_call(instr):
    return "CALL_METHOD" == instr

def is_callable(instr):
    return is_func(instr) or is_func_kw(instr) or is_func_ex(instr) or is_method_call(instr)

def is_load_fast(instr):
    return "LOAD_FAST" == instr

def is_attr(instr):
    return "LOAD_ATTR" == instr

def is_method(instr):
    return "LOAD_METHOD" == instr or is_attr(instr)  # LOAD_METHOD available since 3.7, fallback to `isattr` for backwards compatibility

def is_load_global(instr):
    return "LOAD_GLOBAL" == instr

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
            elif op in dis.hascompare:
                args.append(dis.cmp_op[arg])  # COMPARE_OP
            elif is_callable(dis.opname[op]):
                args.append(arg)  # oparg == nargs(fcn)
        ops.append([dis.opname[op], args])

    return ops


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
    if is_bytecode_instruction(instr) or is_load_fast(instr) or is_load_global(instr):
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
    instr = keys[start_index]
    return_idx = start_index - 1
    if is_bytecode_instruction(instr):
        if is_binary(instr):
            return _binop_bc(BYTECODE_INSTRS[instr], return_idx, ops, keys)
        elif is_comp(instr):
            return _binop_bc(ops[start_index][1][0], return_idx, ops, keys)
        elif is_unary(instr):
            return _unop_bc(BYTECODE_INSTRS[instr], return_idx, ops, keys)
        elif is_func(instr):
            return _call_func_bc(ops[start_index][1][0], return_idx, ops, keys)
        elif is_func_kw(instr):
            return _call_func_kw_bc(ops[start_index][1][0], return_idx, ops, keys)
        # elif is_func_ex(instr):  # since 3.6
            # return _call_func_ex_bc(ops[start_index][1][0], return_idx, ops, keys)
        elif is_method_call(instr):  # since 3.7
            return _call_method_bc(ops[start_index][1][0], return_idx, ops, keys)
        else:
            raise ValueError("unimpl bytecode op: " + instr)
    elif is_load_fast(instr):
        return [_load_fast(ops[start_index][1][0]), return_idx]
    elif is_load_global(instr):
        return [_load_global(ops[start_index][1][0]), return_idx]
    return [ops[start_index][1][0], return_idx]


def _binop_bc(op, idx, ops, keys):
    rite, idx = _opcode_read_arg(idx, ops, keys)
    left, idx = _opcode_read_arg(idx, ops, keys)
    return [ExprNode(op, left, rite), idx]


def _unop_bc(op, idx, ops, keys):
    arg, idx = _opcode_read_arg(idx, ops, keys)
    return [ExprNode(op, arg), idx]


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
    named_args = {}
    unnamed_args = []
    args = []
    # Extract arguments based on calling convention for CALL_FUNCTION_KW
    while nargs > 0:
        if nargs >= 256:  # named args ( foo(50,True,x=10) ) read first  ( right -> left )
            arg, idx = _opcode_read_arg(idx, ops, keys)
            named_args[ops[idx][1][0]] = arg
            idx -= 1  # skip the LOAD_CONST for the named args
            nargs -= 256  # drop 256
        else:
            arg, idx = _opcode_read_arg(idx, ops, keys)
            unnamed_args.insert(0, arg)
            nargs -= 1
    # LOAD_ATTR <method_name>: Map call arguments to a call of method on H2OFrame class
    op = ops[idx][1][0]
    args = _get_h2o_frame_method_args(op, named_args, unnamed_args) if is_method(ops[idx][0]) else []
    # Map function name to proper rapids name
    op = _get_func_name(op, args)
    # Go to next instruction
    idx -= 1
    if is_bytecode_instruction(ops[idx][0]):
        arg, idx = _opcode_read_arg(idx, ops, keys)
        args.insert(0, arg)
    elif is_load_fast(ops[idx][0]):
        args.insert(0, _load_fast(ops[idx][1][0]))
        idx -= 1
    return [ExprNode(op, *args), idx]


def _call_func_kw_bc(nargs, idx, ops, keys):
    named_args = {}
    unnamed_args = []
    # Implements calling convention defined by CALL_FUNCTION_KW
    # https://docs.python.org/3/library/dis.html#opcode-CALL_FUNCTION_KW
    # Read tuple of keyword arguments
    keyword_args = ops[idx][1][0]
    # Skip the LOAD_CONST tuple
    idx -= 1
    # Load keyword arguments from stack
    for keyword_arg in keyword_args:
        arg, idx = _opcode_read_arg(idx, ops, keys)
        named_args[keyword_arg] = arg
        nargs -= 1
    # Load positional arguments from stack
    while nargs > 0:
        arg, idx = _opcode_read_arg(idx, ops, keys)
        unnamed_args.insert(0, arg)
        nargs -= 1
    # LOAD_ATTR <method_name>: Map call arguments to a call of method on H2OFrame class
    op = ops[idx][1][0]
    args = _get_h2o_frame_method_args(op, named_args, unnamed_args) if is_method(ops[idx][0]) else []
    # Map function name to proper rapids name
    op = _get_func_name(op, args)
    # Go to next instruction
    idx -= 1
    if is_bytecode_instruction(ops[idx][0]):
        arg, idx = _opcode_read_arg(idx, ops, keys)
        args.insert(0, arg)
    elif is_load_fast(ops[idx][0]):
        args.insert(0, _load_fast(ops[idx][1][0]))
        idx -= 1
    return [ExprNode(op, *args), idx]


# def _call_func_ex_bc(nargs, idx, ops, keys):
    # Also try to improve code reuse between this and _call_func_kw_bc and _call_func_bc
    # return _call_func_kw_bc(nargs, idx, ops, keys)


def _call_method_bc(nargs, idx, ops, keys):
    # still need to verify it handles all use-cases:
    # delegating to basic func call will work most of the time though in the context we're using this (H2OFrame.apply).
    return _call_func_bc(nargs, idx, ops, keys)


def _get_h2o_frame_method_args(op, named_args, unnamed_args):
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
    args = unnamed_args + argdefs[len(unnamed_args):]
    for a in named_args: args[argnames.index(a)] = named_args[a]
    return args


def _get_func_name(op, args):
    if op == "ceil": op = "ceiling"
    if op == "sum" and len(args) > 0 and args[0]: op = "sumNA"
    if op == "min" and len(args) > 0 and args[0]: op = "minNA"
    if op == "max" and len(args) > 0 and args[0]: op = "maxNA"
    if op == "nacnt": op = "naCnt"
    return op


def _load_fast(x):
    return ASTId(x)


def _load_global(x):
    if x == 'True':
        return True
    elif x == 'False':
        return False
    return x
