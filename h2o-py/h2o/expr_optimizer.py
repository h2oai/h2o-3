# -*- encoding: utf-8 -*-
"""
Rapids expression optimizer.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import h2o.expr


class ExprOptimization(object):
    """
    A generic Rapids expression optimizer

    """

    def __init__(self, supported_ops):
        self._supported_ops = supported_ops

    def supports(self, op):
        """
        A quick check if this optimization supports given operator.
        """
        return op in self._supported_ops

    def is_applicable(self, expr):
        """
        Is this optimization applicable for given operator
        This is expensive check and can results in traversal
        of Rapids expression tree.
        """
        return False

    def get_optimizer(self, expr):
        """
        Return a function is transform given expression and context to ExprNode.
        The function always expects that it is applied in applicable context.

        :param expr:  expression to optimize
        :return:  a function from context to ExprNode
        """
        return id(expr)


class FoldExprOptimization(ExprOptimization):
    """
    Fold optimization: support operators which
    accepts array of parameters (e.g., append, cbind):

    For example: append dst (src col_name)+
      (append (append dst srcX col_name_Y) src_A col_name_B) is transformed to
      (append dst src_X col_name_Y src_A col_name_B)

    Objective:
      - the folding save a temporary variable during evaluation
    """

    def __init__(self):
        super(self.__class__, self).__init__(["append", "cbind", "rbind"])

    def is_applicable(self, expr):
        # Only applicable if the source parameter is the same operator
        assert isinstance(expr, h2o.expr.ExprNode)
        return any(expr._children) and expr._children[0]._op == expr._op

    def get_optimizer(self, expr):
        def foptimizer(ctx):
            nested_expr = expr.arg(0)
            expr._children = nested_expr._children + expr._children[1:]
            return expr

        return foptimizer


class SkipExprOptimization(ExprOptimization):
    """
    The skip optimization removes unnecessary nodes
    from expression tree.

    For example:
      The expression `(col_py (append frame_with_100_columns dummy_col dummy_name) 1)` can
      be simplified to `(col_py frame_with_100_columns 1)`

    Note: right now this is really specific version only
    """

    def __init__(self):
        super(self.__class__, self).__init__(["cols_py"])

    def is_applicable(self, expr):
        assert isinstance(expr, h2o.expr.ExprNode)
        if any(expr._children):
            append_expr = expr.arg(0)
            # Now only supports single 'cols_py' parameters, and composition with append
            # Also `append` dst argument needs to have properly filled cache
            if expr.narg() == 2 and append_expr._op == "append" and any(append_expr._children):
                append_dst = append_expr.arg(0)
                cols_py_select = expr.arg(1)
                return isinstance(cols_py_select, int) and append_dst._cache.ncols_valid() and cols_py_select < append_dst._cache.ncols

        return False

    def get_optimizer(self, expr):
        def foptimizer(ctx):
            # The optimizier always expects that it is applied in applicable context
            append_expr = expr.arg(0)
            append_dst = append_expr.arg(0)
            expr._children = tuple([append_dst]) + expr._children[1:]
            return expr

        return foptimizer


def optimize(expr):
    assert isinstance(expr, h2o.expr.ExprNode)
    all_optimizers = get_optimization(expr._op)
    applicable_optimizers = [f for f in all_optimizers if f.is_applicable(expr)]
    # at this point we should select the right optimizer operator, but
    # we just pick the first one
    if applicable_optimizers:
        return applicable_optimizers[0].get_optimizer(expr)
    else:
        return None


def get_optimization(op):
    return [f for f in __REGISTERED_EXPR_OPTIMIZATIONS__ if f.supports(op)]


def id(expr):
    """
    This is identity optimization.
    :param expr:  expression to optimize
    :return:  a function which always returns expr
    """

    def identity(ctx):
        return expr

    return identity


#
# Global register of available expression optimizations
#
__REGISTERED_EXPR_OPTIMIZATIONS__ = [
    FoldExprOptimization(),
    SkipExprOptimization()
]
