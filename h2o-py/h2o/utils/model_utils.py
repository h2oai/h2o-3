# -*- encoding: utf-8 -*-
"""
Model utils
:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

from ..expr import ExprNode
from ..frame import H2OFrame


def reset_model_threshold(model, threshold):
    """
    Reset model threshold - performance metric will be recalculated, the new threshold will be used for predictions.
    :param model: H2OModel instance
    :param threshold: new threshold to be set to model
    :return: old threshold value
    """

    fr = H2OFrame._expr(ExprNode("model.reset.threshold", model, threshold))
    return fr.flatten()
