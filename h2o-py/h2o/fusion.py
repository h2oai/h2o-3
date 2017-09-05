# -*- encoding: utf-8 -*-
"""
Rapids expression fusion.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import h2o.expr

class Fusion(object):
    #
    # Generic fusion representation
    #
    def __init__(self, supported_ops):
        self._supported_ops = supported_ops

    def supports(self, op):
        # Supports given operator
        return op in self._supported_ops

    def is_applicable(self, expr):
        # Is fusion applicable for given operator
        return False

    def get_fusion(self, expr):
        # Return fusion which is mapping from ExprNode to ExprNode
        return id(expr)

class FoldFusion(Fusion):
    """
    Fold fusion: support operators which
    accepts array of parameters (e.g., append, cbind):

    For example: append dst (src col_name)+
      (append (append dst srcX col_name_Y) src_A col_name_B) is transformed to
      (append dst src_X col_name_Y src_A col_name_B)

    Objective:
      - the folding save a temporary variable during evaluation
    """
    def __init__(self):
        super(self.__class__, self).__init__(["append", "cbind"])

    def is_applicable(self, expr):
        # Only applicable if the source parameter is the same operator
        assert isinstance(expr, h2o.expr.ExprNode)
        assert any(expr._children)
        return expr._children[0]._op == expr._op

    def get_fusion(self, expr):
        def fusion_fce(ctx):
            nested_expr = expr.arg(0)
            expr._children = nested_expr._children + expr._children[1:]
            return expr
        return fusion_fce

class SkipFusion(Fusion):
    """
    The skip fusion removes unnecessary operators
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
        assert any(expr._children)
        f_kid = expr.arg(0)
        # Now only supports single 'cols_py' parameters, and composition with append
        # Also `append` dst argument needs to have properly filled cache
        return expr.narg() == 2 and f_kid._op == "append" and f_kid.arg(0)._cache.ncols_valid()

    def get_fusion(self, expr):
        def fusion_fce(ctx):
            append_expr = expr.arg(0)
            append_dst = append_expr.arg(0)
            cols_py_select = expr.arg(1)
            append_ncols = append_dst._cache.ncols
            if isinstance(cols_py_select, int) and cols_py_select < append_ncols:
                expr._children = tuple([append_dst]) + expr._children[1:]
            return expr
        return fusion_fce


def fuse(expr):
    assert isinstance(expr, h2o.expr.ExprNode)
    all_fusions = get_fusions(expr._op)
    applicable_fusions = [f for f in all_fusions if f.is_applicable(expr)]
    # at this point we should select the right fusion operator, but
    # we just pick the first one
    if applicable_fusions:
        return applicable_fusions[0].get_fusion(expr)
    else:
        return id(expr)

def get_fusions(op):
    return [f for f in __REGISTERED_FUSIONS__ if f.supports(op)]

def id(expr):
    #
    # Identity transformation
    #
    def identity(ctx):
        return expr
    return identity

#
# Global fusions registered
#
__REGISTERED_FUSIONS__ = [
    FoldFusion(),
    SkipFusion()
]

