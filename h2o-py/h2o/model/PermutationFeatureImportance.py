# -*- encoding: utf-8 -*-
"""
H2O Permutation Feature Importance.

:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

from h2o.frame import H2OFrame
from h2o.expr import ExprNode


def permutation_varimp(validation_frame, model, use_pandas=True):

    m_frame = H2OFrame._expr(ExprNode("Perm_Feature_importance", validation_frame, model))

    if type(m_frame) is not H2OFrame:
        raise ValueError("Frame is not H2OFrame")

    m_frame_oat = H2OFrame._expr(ExprNode("Perm_Feature_importance_OAT", validation_frame, model))
    
    if type(m_frame_oat) is not H2OFrame:
        raise ValueError("Frame is not H2OFrame")

    m_frame.show()
    m_frame_oat.show()
    
    # if use_pandas and can_use_pandas():
    #     return pandas.DataFrame(m_frame)
        
    return m_frame
