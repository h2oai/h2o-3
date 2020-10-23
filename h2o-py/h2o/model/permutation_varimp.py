# -*- encoding: utf-8 -*-
"""
H2O Permutation Feature Importance.

:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

from h2o.frame import H2OFrame
from h2o.expr import ExprNode
from h2o.utils.shared_utils import can_use_pandas

import h2o


def H2OFrame_to_pandas(m_frame):
    import pandas
    pd = h2o.as_list(m_frame)
    
    rel_imp = pd.loc[pd['ID'] == 'Relative Importance']
    rel_imp = rel_imp.drop(columns=['ID'])
    
    scaled_imp = pd.loc[pd['ID'] == 'Scaled Importance']
    scaled_imp = scaled_imp.drop(columns=['ID'])
    
    perc_imp = pd.loc[pd['ID'] == 'Percentage']
    perc_imp = perc_imp.drop(columns=['ID'])

    return pandas.DataFrame(rel_imp, columns=rel_imp.columns)


def permutation_varimp(model, validation_frame, use_pandas=True, metric="mse"):
    m_frame = H2OFrame._expr(ExprNode("PermutationVarImp", model, validation_frame, metric))

    if type(m_frame) is not H2OFrame:
        raise ValueError("Frame is not H2OFrame")

    if use_pandas and can_use_pandas():
        return H2OFrame_to_pandas(m_frame)

    rel_imp = m_frame[1,:]
    sc_imp = m_frame[2,:]
    perc_imp = m_frame[3,:]

    names = m_frame.names
    names.pop() # removing 'ID'
    rel_imp_final = rel_imp[:, names] # range(m_frame.ncols - 1)
    sc_imp_final = sc_imp[:, names]
    perc_imp_final = perc_imp[:, names]

    return rel_imp_final  
