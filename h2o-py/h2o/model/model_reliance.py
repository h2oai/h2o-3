# -*- encoding: utf-8 -*-
"""
H2O Model Reliance.

:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

from h2o.frame import H2OFrame
from h2o.expr import ExprNode


def model_reliance(frame, model):
    #TODO extract the TwoDimTable and return that instead
    
    #extract the model. (find out how API for getting works)
    #Now that I think about it you only need to extract the TwoDimTable and work with the findings not the actual model... 

    if type(frame) is not H2OFrame:
        m_frame = H2OFrame._expr(ExprNode("Perm_Feature_importance", frame, model))
    else:
        print("note H2O frame") #TODO Handle properly
        return None
        
    return m_frame
