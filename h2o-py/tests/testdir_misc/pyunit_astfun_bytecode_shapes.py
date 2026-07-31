#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
Bytecode-shape regression tests for ``astfun.py``.

The existing kwarg-order / kwarg-skip / apply tests exercise ``lambda_to_expr``
on whichever Python version happens to be running, but they do not assert
which opcode the disassembler actually saw. Two new CPython opcodes added in
the Py3.12-3.14 series silently change the bytecode layout:

  * ``CALL_KW`` (Py 3.13+) replaces the ``KW_NAMES + CALL`` pair for kwarg-bearing
    function calls. ``astfun._call_kw_bc`` is the new code path that must run
    when CALL_KW is present; if a future CPython renames or splits the opcode,
    a `lambda_to_expr` call could silently take the wrong branch.

  * ``BINARY_OP`` with arg ``NB_SUBSCR=26`` (Py 3.14+) replaces the standalone
    ``BINARY_SUBSCR`` opcode for ``x[k]`` subscription. ``astfun.BINARY_OPS[26]
    = "cols"`` is the new mapping; a future arg renumbering would silently
    rewrite ``frame[col]`` to a different Rapids op.

These tests use ``dis.get_instructions`` to verify the lambda's bytecode
actually contains the expected opcode on the running Python (skipping on
older versions where the opcode does not yet exist), then re-runs
``lambda_to_expr`` and asserts the produced Rapids ExprNode is correct.

No H2O server connection required.
"""
import dis
import sys

import h2o
from h2o.astfun import lambda_to_expr


def _opnames(lam):
    """Return the list of opnames in a lambda's bytecode."""
    return [i.opname for i in dis.get_instructions(lam)]


def _instructions_with_arg(lam):
    """Return a list of ``(opname, argval, arg)`` triples for the lambda."""
    return [(i.opname, i.argval, i.arg) for i in dis.get_instructions(lam)]


def _body_of(lam):
    """Strip the lambda-frame preamble from the result of ``lambda_to_expr``."""
    nodes = lambda_to_expr(lam)
    return nodes[3]


def _assert_scale_true_false(expr, label):
    """All matrix cases below call ``x.scale(center=True, scale=False)`` in some
    syntactic form, so the produced Rapids node must always be the same:
    ``(scale x True False)`` (child[0] is the frame, [1]=center, [2]=scale)."""
    assert expr._op == "scale", "%s: op=%r" % (label, expr._op)
    assert expr._children[1] is True and expr._children[2] is False, \
        "%s: *args/**kwargs misbound — children=%r" % (label, expr._children)


# Module-level (global-scope) iterables. Referenced inside a lambda these load
# via ``LOAD_GLOBAL`` which, on Py3.14, *folds* the CALL_FUNCTION_EX kwargs-NULL
# slot into the instruction (no standalone ``PUSH_NULL`` op). That is exactly the
# shape that breaks a naive "PUSH_NULL precedes CALL_FUNCTION_EX ⇒ no kwargs"
# heuristic (see C1c). Local/free vars do NOT fold the NULL, so the global path
# must be tested explicitly — the pre-existing tests only used locals.
_EX_GLOBAL_ARGS = (True, False)               # -> scale(True, False)
_EX_GLOBAL_KW = dict(center=True, scale=False)
_EX_GLOBAL_POS = (True,)                       # center=True positionally
_EX_GLOBAL_KWREST = dict(scale=False)          # scale=False via **kwargs
_EX_GLOBAL_KWCENTER = dict(center=True)        # center=True via **kwargs
_EX_GLOBAL_REST = (False,)                     # scale=False positionally


# ---------- C1 : CALL_KW (Py 3.13+) -----------------------------------------

def test_call_kw_opcode_present_on_py313_plus():
    """A kwarg-bearing method-call lambda must emit CALL_KW on Py 3.13+."""
    if sys.version_info < (3, 13):
        print("SKIP: CALL_KW was added in Py 3.13; running on %s" % (sys.version,))
        return
    lam = lambda x: x.scale(center=True, scale=False)
    ops = _opnames(lam)
    assert "CALL_KW" in ops, (
        "Expected CALL_KW in the disassembly of a 2-kwarg method call on Py 3.13+, "
        "got opnames=%r. If CPython renamed/split the opcode, astfun.is_call_kw "
        "needs to be updated." % (ops,)
    )
    # KW_NAMES disappears in Py3.13+ in favor of a LOAD_CONST holding the
    # kwnames tuple immediately preceding CALL_KW.
    assert "KW_NAMES" not in ops, (
        "KW_NAMES should be gone on Py 3.13+; got opnames=%r" % (ops,)
    )


def test_call_kw_lambda_to_expr_binding():
    """End-to-end: lambda_to_expr must bind kwargs in the right order on Py 3.13+."""
    if sys.version_info < (3, 13):
        print("SKIP: CALL_KW only on Py 3.13+")
        return
    # Same fixture as pyunit_astfun_kwarg_order.test_two_distinct_kwargs but
    # gated to the CALL_KW path specifically. Distinct values ensure a kwarg
    # swap would be caught.
    expr = _body_of(lambda x: x.scale(center=True, scale=False))
    assert expr._op == "scale", "op=%r" % (expr._op,)
    assert expr._children[1] is True, \
        "center should be True on Py 3.13+; got %r (kwargs swapped?)" % (expr._children[1],)
    assert expr._children[2] is False, \
        "scale should be False on Py 3.13+; got %r" % (expr._children[2],)


def test_kwnames_tuple_argval_is_strings_not_indices():
    """Guard against a future CPython change to how kwnames are stored.

    ``_call_kw_bc`` reads the LOAD_CONST's ``argval`` as the kwnames tuple
    (a tuple of strings). If a future CPython stored it as a tuple of ints
    or a name-table index, the binding would silently misbind every kwarg.
    """
    if sys.version_info < (3, 13):
        print("SKIP: CALL_KW only on Py 3.13+")
        return
    lam = lambda x: x.scale(center=True, scale=False)
    insns = _instructions_with_arg(lam)
    # Find the LOAD_CONST immediately preceding CALL_KW; its argval must be
    # the literal tuple ('center', 'scale').
    kwnames = None
    for i, (opname, argval, _arg) in enumerate(insns):
        if opname == "CALL_KW" and i > 0:
            prev_op, prev_arg, _ = insns[i - 1]
            assert prev_op == "LOAD_CONST", \
                "Op preceding CALL_KW should be LOAD_CONST(kwnames); got %s" % prev_op
            kwnames = prev_arg
            break
    assert kwnames == ("center", "scale"), (
        "kwnames tuple shape changed: expected ('center', 'scale'), got %r. "
        "_call_kw_bc relies on this being a tuple of str." % (kwnames,)
    )


# ---------- C1b : CALL_FUNCTION_EX *args / **kwargs (all versions) -----------

def test_call_function_ex_star_args_kwargs_binding():
    """lambda_to_expr must bind *args / **kwargs correctly via CALL_FUNCTION_EX.

    This is the fast (server-free) guard for ``astfun._call_func_ex_bc``. On
    Py3.14 CALL_FUNCTION_EX lost its explicit ``flags`` arg, so astfun infers it
    from a preceding PUSH_NULL — the most speculative new bytecode logic. This
    test runs on every Python version (no skip): on <3.14 it exercises the
    explicit-flags path, on 3.14+ the inference path, so a regression in either
    is caught. Free vars (args/kwargs) are resolved by astfun._load_outer_scope
    from this frame's locals, so no H2O server is needed.

    Distinct True/False values ensure a positional swap or dropped kwarg is caught;
    children layout mirrors test_call_kw_lambda_to_expr_binding (child[0] is the
    frame, child[1]=center, child[2]=scale for H2OFrame.scale).
    """
    # *args only  -> scale(True, False)  (flags=0 on Py3.14: PUSH_NULL inference)
    args = (True, False)
    expr = _body_of(lambda x: x.scale(*args))
    assert expr._op == "scale", "op=%r" % (expr._op,)
    assert expr._children[1] is True and expr._children[2] is False, \
        "*args misbound (flags inference?): children=%r" % (expr._children,)

    # **kwargs only -> scale(center=True, scale=False)  (flags=1)
    kwargs = dict(center=True, scale=False)
    expr = _body_of(lambda x: x.scale(**kwargs))
    assert expr._op == "scale", "op=%r" % (expr._op,)
    assert expr._children[1] is True and expr._children[2] is False, \
        "**kwargs misbound: children=%r" % (expr._children,)

    # *args + **kwargs -> scale(center=True, scale=False)
    pos = (True,)
    kw = dict(scale=False)
    expr = _body_of(lambda x: x.scale(*pos, **kw))
    assert expr._op == "scale", "op=%r" % (expr._op,)
    assert expr._children[1] is True and expr._children[2] is False, \
        "*args+**kwargs misbound: children=%r" % (expr._children,)


# ---------- C1c : CALL_FUNCTION_EX with module-GLOBAL unpack (Py3.14 regression) ----

def test_call_function_ex_global_args_null_fold_shape_py314():
    """Anchor the Py3.14 bytecode shape that defeats the naive flags heuristic.

    On Py3.14 ``CALL_FUNCTION_EX`` lost its explicit ``flags`` operand, so astfun
    infers it. For ``*GLOBAL_ITERABLE`` the iterable loads via ``LOAD_GLOBAL``,
    which *itself* pushes the kwargs-NULL slot — there is NO standalone
    ``PUSH_NULL`` before ``CALL_FUNCTION_EX``. A heuristic keyed on a preceding
    ``PUSH_NULL`` therefore wrongly concludes "has kwargs" (flags=1). This test
    pins that shape so a future CPython change is caught loudly.
    """
    if sys.version_info < (3, 14):
        print("SKIP: CALL_FUNCTION_EX kept an explicit flags arg before Py3.14; running on %s"
              % (sys.version,))
        return
    ops = _opnames(lambda x: x.scale(*_EX_GLOBAL_ARGS))
    i = ops.index("CALL_FUNCTION_EX")
    assert ops[i - 1] == "LOAD_GLOBAL", (
        "Expected the global args load (LOAD_GLOBAL, kwargs-NULL folded in) "
        "immediately before CALL_FUNCTION_EX on Py3.14; got %r (ops=%r)"
        % (ops[i - 1], ops))
    assert ops[i - 1] != "PUSH_NULL", (
        "If the global-args path ever grows a standalone PUSH_NULL the naive "
        "heuristic would start working by accident; ops=%r" % (ops,))
    flags_arg = [arg for op, _argval, arg in _instructions_with_arg(lambda x: x.scale(*_EX_GLOBAL_ARGS))
                 if op == "CALL_FUNCTION_EX"][0]
    assert flags_arg is None, \
        "CALL_FUNCTION_EX should carry no flags arg on Py3.14; got %r" % (flags_arg,)


def test_call_function_ex_global_star_args():
    """REGRESSION (GH-16147): ``lambda x: x.scale(*GLOBAL_TUPLE)`` must bind on Py3.14.

    Crashed with ``TypeError: argument after ** must be a mapping`` on Py3.14
    only: the kwargs-NULL folded into LOAD_GLOBAL made the flags-inference set
    flags=1, so the args tuple was read as ``**kwargs``. Passed on 3.7-3.13
    (explicit flags). Server-free.
    """
    _assert_scale_true_false(_body_of(lambda x: x.scale(*_EX_GLOBAL_ARGS)), "*GLOBAL_ARGS")


def test_call_function_ex_global_star_kwargs():
    """``**GLOBAL_DICT`` (kwargs-only, global) must bind. prev op is DICT_MERGE
    on Py3.14 — flags must be inferred as 1."""
    _assert_scale_true_false(_body_of(lambda x: x.scale(**_EX_GLOBAL_KW)), "**GLOBAL_KW")


def test_call_function_ex_global_args_and_kwargs():
    """Mixed ``*GLOBAL_POS, **GLOBAL_KWREST`` must bind (flags=1, both global)."""
    _assert_scale_true_false(
        _body_of(lambda x: x.scale(*_EX_GLOBAL_POS, **_EX_GLOBAL_KWREST)),
        "*GLOBAL_POS,**GLOBAL_KWREST")


def test_call_function_ex_local_unpack_still_binds():
    """Control: the local/free-var unpack path (standalone PUSH_NULL) keeps
    working — the fix must not regress it."""
    l_args = (True, False)
    l_kw = dict(center=True, scale=False)
    l_pos = (True,)
    l_kwrest = dict(scale=False)
    _assert_scale_true_false(_body_of(lambda x: x.scale(*l_args)), "*local_args")
    _assert_scale_true_false(_body_of(lambda x: x.scale(**l_kw)), "**local_kw")
    _assert_scale_true_false(_body_of(lambda x: x.scale(*l_pos, **l_kwrest)), "*local_pos,**local_kw")


def test_call_function_ex_local_global_parity():
    """The Rapids expression must be byte-identical whether the unpacked iterable
    is a local/free var or a module global — the asymmetry the NULL-fold caused
    on Py3.14. This is the cross-scope regression guard."""
    l_args = (True, False)
    l_kw = dict(center=True, scale=False)
    l_pos = (True,)
    l_kwrest = dict(scale=False)
    pairs = [
        (lambda x: x.scale(*l_args),               lambda x: x.scale(*_EX_GLOBAL_ARGS)),
        (lambda x: x.scale(**l_kw),                lambda x: x.scale(**_EX_GLOBAL_KW)),
        (lambda x: x.scale(*l_pos, **l_kwrest),    lambda x: x.scale(*_EX_GLOBAL_POS, **_EX_GLOBAL_KWREST)),
    ]
    for loc_lam, glob_lam in pairs:
        loc = _body_of(loc_lam)._get_ast_str()
        glob = _body_of(glob_lam)._get_ast_str()
        assert loc == glob, \
            "local vs global unpack diverged: local=%r global=%r" % (loc, glob)


# ---------- C1d : *args + explicit kwarg, and inline-iterable unpack --------
# These CALL_FUNCTION_EX shapes failed on EVERY supported Python (3.9-3.14) until
# GH-16147: the consumption logic mis-read an explicit-kwarg BUILD_MAP as a bare
# scalar, and spread a BUILD_LIST element with ``insert(0, *elem)``. They are
# independent of the Py3.14 flags-inference (they reproduced under the explicit-
# flags path too), so they run on all versions with no skip.

def test_call_function_ex_star_args_with_explicit_kwarg_global():
    """``x.scale(*GLOBAL_POS, scale=False)`` — ``*`` unpack + an explicit kwarg.

    The kwarg compiles to a BUILD_MAP on top of the stack; the old code skipped
    the builder and read a single value, so ``**kwargs`` received a scalar and
    raised ``TypeError: argument after ** must be a mapping``.
    """
    _assert_scale_true_false(
        _body_of(lambda x: x.scale(*_EX_GLOBAL_POS, scale=False)),
        "*GLOBAL_POS,scale=False")


def test_call_function_ex_star_args_with_explicit_kwarg_local():
    """Same shape with a local/free-var iterable (control for the global case)."""
    l_pos = (True,)
    _assert_scale_true_false(
        _body_of(lambda x: x.scale(*l_pos, scale=False)),
        "*local_pos,scale=False")


def test_call_function_ex_inline_list_unpack():
    """``x.scale(*[True, False])`` — inline list literal in unpack position.

    The args iterable is a BUILD_LIST; the old ``args.insert(0, *new_args)``
    spread a scalar element and raised ``TypeError: ... must be an iterable``.
    """
    _assert_scale_true_false(_body_of(lambda x: x.scale(*[True, False])), "*[True,False]")


def test_call_function_ex_inline_tuple_unpack():
    """``x.scale(*(True, False))`` (const tuple) and ``*(a, b)`` (BUILD_TUPLE)."""
    _assert_scale_true_false(_body_of(lambda x: x.scale(*(True, False))), "*(True,False)")
    a, b = True, False
    _assert_scale_true_false(_body_of(lambda x: x.scale(*(a, b))), "*(a,b)")


def test_call_function_ex_double_star_unpack():
    """Two ``*`` unpacks (``x.scale(*a, *b)``) — assembled via BUILD_LIST + chained
    LIST_EXTEND + LIST_TO_TUPLE. The old code read a LIST_EXTEND op as an argument
    and raised TypeError; now each source iterable's elements are spliced in order.
    Covers local/free vars, module globals, and a global + inline-literal mix.
    """
    p1, p2 = (True,), (False,)
    _assert_scale_true_false(_body_of(lambda x: x.scale(*p1, *p2)), "*p1,*p2 (local)")
    _assert_scale_true_false(_body_of(lambda x: x.scale(*_EX_GLOBAL_POS, *(False,))),
                             "*GLOBAL_POS,*(False,)")
    _assert_scale_true_false(_body_of(lambda x: x.scale(*_EX_GLOBAL_POS, *[False])),
                             "*GLOBAL_POS,*[False]")


def test_call_function_ex_explicit_positional_with_star():
    """Explicit leading positional plus a ``*`` unpack (``x.scale(True, *rest)``).
    The leading positional lives in the base BUILD_LIST that the LIST_EXTEND chain
    bottoms out on, and must be prepended ahead of the spliced elements."""
    rest = (False,)
    _assert_scale_true_false(_body_of(lambda x: x.scale(True, *rest)), "True,*rest (local)")
    _assert_scale_true_false(_body_of(lambda x: x.scale(True, *_EX_GLOBAL_REST)),
                             "True,*GLOBAL_REST")


def test_call_function_ex_explicit_positional_after_star():
    """Explicit *trailing* positional after a ``*`` unpack (``x.scale(*first, False)``).

    On Py 3.9+ the trailing positional is emitted as ``LIST_APPEND`` (one element),
    distinct from ``LIST_EXTEND`` (spliced iterable). The old code matched neither
    branch, read the LIST_APPEND op's oparg as an argument, and raised TypeError
    (GH-16147). The element must be appended after the spliced elements, in order."""
    first = (True,)
    _assert_scale_true_false(_body_of(lambda x: x.scale(*first, False)), "*first,False (local)")
    _assert_scale_true_false(_body_of(lambda x: x.scale(*[True], False)), "*[True],False (inline)")
    # leading explicit + star + trailing explicit, all in one call
    mid = ()
    expr = _body_of(lambda x: x.scale(True, *mid, False))
    _assert_scale_true_false(expr, "True,*mid,False")


# ---------- C1e : multi-source **kwargs merge (BUILD_MAP + DICT_MERGE chain) -
# On Py 3.9+ multiple ``**`` unpacks (and ``**`` mixed with explicit kwargs) build
# the kwargs map as a base BUILD_MAP followed by one DICT_MERGE per source. The
# old consumption loop assumed a *single* DICT_MERGE (``while nargs + 1 > 0``) and
# fed a DICT_MERGE oparg into ``dict.update`` on any 2-source merge, raising
# ``TypeError: 'int' object is not iterable``. Two-plus explicit kwargs alongside a
# ``*`` unpack additionally use BUILD_CONST_KEY_MAP, which the old code never
# handled (it read the oparg as a bare ``**`` mapping). All reproduce on every
# Python 3.9-3.14, so no version skip (GH-16147).

def test_call_function_ex_double_kwargs_unpack():
    """Two ``**`` unpacks (``x.scale(**a, **b)``) — two DICT_MERGE ops. The old
    fixed-count merge loop crashed here with ``'int' object is not iterable``."""
    l_center = dict(center=True)
    l_rest = dict(scale=False)
    _assert_scale_true_false(_body_of(lambda x: x.scale(**l_center, **l_rest)),
                             "**local_center,**local_rest")
    _assert_scale_true_false(
        _body_of(lambda x: x.scale(**_EX_GLOBAL_KWCENTER, **_EX_GLOBAL_KWREST)),
        "**GLOBAL_center,**GLOBAL_rest")


def test_call_function_ex_kwargs_unpack_with_explicit_kwarg():
    """``**`` unpack mixed with a single explicit kwarg, in both orders
    (``x.scale(**a, k=v)`` and ``x.scale(k=v, **a)``). The explicit kwarg is its
    own BUILD_MAP that is DICT_MERGEd onto the base — a second merge op."""
    l_center = dict(center=True)
    l_rest = dict(scale=False)
    _assert_scale_true_false(_body_of(lambda x: x.scale(**l_center, scale=False)),
                             "**local_center,scale=False")
    _assert_scale_true_false(_body_of(lambda x: x.scale(center=True, **l_rest)),
                             "center=True,**local_rest")
    _assert_scale_true_false(
        _body_of(lambda x: x.scale(**_EX_GLOBAL_KWCENTER, scale=False)),
        "**GLOBAL_center,scale=False")


def test_call_function_ex_star_args_with_two_explicit_kwargs():
    """``*`` unpack with 2+ explicit kwargs (``x.scale(*p, center=True, scale=False)``)
    builds the kwargs via BUILD_CONST_KEY_MAP (not BUILD_MAP). The old code read the
    BUILD_CONST_KEY_MAP oparg as a bare ``**`` mapping and raised TypeError."""
    empty = ()
    _assert_scale_true_false(
        _body_of(lambda x: x.scale(*empty, center=True, scale=False)),
        "*empty,center=True,scale=False")
    _assert_scale_true_false(
        _body_of(lambda x: x.scale(*_EX_GLOBAL_POS, scale=False, center=True)),
        "*GLOBAL_POS,scale=False,center=True")


def test_call_function_ex_kwargs_unpack_with_two_explicit_kwargs():
    """``**`` unpack with 2+ explicit kwargs (``x.scale(**a, center=True, scale=False)``
    and the reverse order). The explicit pair is a BUILD_CONST_KEY_MAP merged onto
    the base map — exercises BUILD_CONST_KEY_MAP inside the DICT_MERGE chain."""
    l_empty = dict()
    _assert_scale_true_false(
        _body_of(lambda x: x.scale(**l_empty, center=True, scale=False)),
        "**empty,center=True,scale=False")
    _assert_scale_true_false(
        _body_of(lambda x: x.scale(center=True, scale=False, **l_empty)),
        "center=True,scale=False,**empty")


# ---------- C2 : BINARY_OP NB_SUBSCR=26 (Py 3.14+) --------------------------

# CPython's NB_SUBSCR slot. Defined here to make a future renumbering loud
# (the BINARY_OPS table in astfun.py uses the same constant; a divergence
# means one was bumped without the other).
_NB_SUBSCR = 26


def test_binary_op_nb_subscr_present_on_py314_plus():
    """``x[i]`` must emit BINARY_OP with arg=NB_SUBSCR=26 on Py 3.14+."""
    if sys.version_info < (3, 14):
        print("SKIP: NB_SUBSCR was folded into BINARY_OP in Py 3.14; running on %s"
              % (sys.version,))
        return
    lam = lambda x: x[0]
    insns = _instructions_with_arg(lam)
    matched = [(op, arg) for op, _argval, arg in insns
               if op == "BINARY_OP" and arg == _NB_SUBSCR]
    assert matched, (
        "Expected BINARY_OP with arg=%d (NB_SUBSCR) in `lambda x: x[0]` on Py 3.14+; "
        "got instructions=%r. If CPython renumbered the NB_SUBSCR slot, "
        "astfun.BINARY_OPS must be updated to match." % (_NB_SUBSCR, insns)
    )
    # BINARY_SUBSCR should be gone in Py 3.14+ — it was folded into BINARY_OP.
    assert "BINARY_SUBSCR" not in [op for op, _, _ in insns], (
        "BINARY_SUBSCR should be removed in Py 3.14+; got insns=%r" % (insns,)
    )


def test_binary_op_nb_subscr_maps_to_cols():
    """End-to-end: lambda x: x[0] must produce Rapids ``(cols x 0)``."""
    # This works on every Python because BINARY_SUBSCR (Py<=3.13) and
    # BINARY_OP/NB_SUBSCR (Py>=3.14) both map to "cols" in astfun.
    expr = _body_of(lambda x: x[0])
    assert expr._op == "cols", \
        "Subscript should map to Rapids 'cols' op; got %r" % (expr._op,)
    assert expr._children[1] == 0, \
        "Index 0 should be the second child; got %r" % (expr._children[1],)


def test_subscript_cross_version_consistency():
    """The Rapids expression for ``x[0]`` must be identical across Python versions.

    This is the regression guard the user-asks-for: a Py 3.14 NB_SUBSCR refactor
    that changes the produced expression silently (e.g. emits ``[0]`` instead of
    ``(cols ...)``) would slip through opcode-shape tests if they only check
    bytecode. Anchor the expected Rapids string here.
    """
    expr = _body_of(lambda x: x[0])
    ast_str = expr._get_ast_str()
    # H2O Rapids prints "(cols x 0)" with the LOAD_FAST variable name preserved.
    assert ast_str.startswith("(cols "), \
        "Rapids form for x[0] should start with '(cols '; got %r" % (ast_str,)
    assert " 0)" in ast_str, \
        "Rapids form should end with the index 0; got %r" % (ast_str,)


# ---------- C3 : unary ops across opcode reshuffles -------------------------

def test_unary_not_maps_to_rapids_not():
    """``lambda col: not col`` must produce ``(! col)`` on every Python.

    Py 3.13+ inserts an explicit TO_BOOL before UNARY_NOT; the disassembler
    must skip it or the leftover-ops check raises.
    """
    expr = _body_of(lambda col: not col)
    assert expr._op == "!", "not should map to Rapids '!'; got %r" % (expr._op,)


def test_unary_positive_maps_to_rapids_plus():
    """``lambda col: +col`` must produce ``(+ col)`` on every Python.

    Py 3.12+ removed UNARY_POSITIVE in favor of CALL_INTRINSIC_1 with
    INTRINSIC_UNARY_POSITIVE; the disassembler must normalize it back.
    """
    expr = _body_of(lambda col: +col)
    assert expr._op == "+", "+col should map to Rapids '+'; got %r" % (expr._op,)


def test_unary_negative_maps_to_rapids_minus():
    """``lambda col: -col`` must produce ``(- col)`` on every Python."""
    expr = _body_of(lambda col: -col)
    assert expr._op == "-", "-col should map to Rapids '-'; got %r" % (expr._op,)


def test_unary_not_of_comparison():
    """``not (col > 2)`` exercises TO_BOOL on a non-trivial operand."""
    expr = _body_of(lambda col: not (col > 2))
    assert expr._op == "!", "op=%r" % (expr._op,)
    inner = expr._children[0]
    assert inner._op == ">", "inner op should be '>'; got %r" % (inner._op,)


def test_binary_ops_table_nb_subscr_constant():
    """``astfun.BINARY_OPS`` must map slot 26 to ``"cols"``.

    Catches a future drift where someone renumbers BINARY_OPS without realising
    NB_SUBSCR is the CPython-fixed slot.
    """
    from h2o.astfun import BINARY_OPS
    assert _NB_SUBSCR in BINARY_OPS, \
        "BINARY_OPS missing NB_SUBSCR slot %d; current keys=%r" \
        % (_NB_SUBSCR, sorted(BINARY_OPS.keys()))
    assert BINARY_OPS[_NB_SUBSCR] == "cols", \
        "BINARY_OPS[%d] should be 'cols' (matches BINARY_SUBSCR mapping); got %r" \
        % (_NB_SUBSCR, BINARY_OPS[_NB_SUBSCR])


if __name__ == "__main__":
    failed = []
    for name, fn in list(globals().items()):
        if not name.startswith("test_") or not callable(fn):
            continue
        try:
            fn()
            print("PASS:", name)
        except AssertionError as exc:
            failed.append((name, exc))
            print("FAIL:", name, "—", exc)
    if failed:
        sys.exit(1)
    print("All tests passed.")
