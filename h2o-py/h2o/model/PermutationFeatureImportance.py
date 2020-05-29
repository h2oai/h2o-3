# -*- encoding: utf-8 -*-
"""
H2O Model Reliance.

:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

from h2o.frame import H2OFrame
from h2o.expr import ExprNode


def permutation_featue_importance(frame, model):
    
    if type(frame) is H2OFrame:
        m_frame = H2OFrame._expr(ExprNode("Perm_Feature_importance", frame, model.model_id))
    else:
        raise ValueError("Frame is not H2OFrame")
    
    # TODO 
    # twoDimTable = model.getTable()
    # return twoDimTable    
    return m_frame
