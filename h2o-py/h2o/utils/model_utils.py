# -*- encoding: utf-8 -*-
"""
Predefined distributions to use for custom distribution definition

:copyright: (c) 2019 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

from ..expr import ExprNode

def reset_model_threshold(model, threshold):
    return ExprNode("model.reset.threshold", model, threshold)._eager_scalar_tmp()
