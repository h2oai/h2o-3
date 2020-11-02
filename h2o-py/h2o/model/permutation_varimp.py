# -*- encoding: utf-8 -*-
"""
H2O Permutation Feature Importance.

:copyright: (c) 2020 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""

from h2o.frame import H2OFrame
from h2o.expr import ExprNode
from h2o.model.model_base import _get_matplotlib_pyplot
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

    pd = pd.drop(columns=['ID'])
    return pandas.DataFrame(pd, columns=pd.columns)


def permutation_varimp(model, validation_frame, use_pandas=True, metric="mse"):
    m_frame = H2OFrame._expr(ExprNode("PermutationVarImp", model, validation_frame, metric))

    if type(m_frame) is not H2OFrame:
        raise ValueError("Frame is not H2OFrame")

    if use_pandas and can_use_pandas():
        return H2OFrame_to_pandas(m_frame)

    rel_imp = m_frame[1, :]
    sc_imp = m_frame[2, :]
    perc_imp = m_frame[3, :]

    names = m_frame.names
    names.pop()  # removing 'ID'
    rel_imp_final = rel_imp[:, names]  # range(m_frame.ncols - 1)
    sc_imp_final = sc_imp[:, names]
    perc_imp_final = perc_imp[:, names]

    return rel_imp_final


def plot_permutation_var_imp(importance, algo_name, metric="mse", server=False):
    importance_val = []
    for col in importance.columns:
        importance_val.append(importance.loc[2][col])

    # specify bar centers on the y axis, but flip the order so largest bar appears at top
    pos = range(len(importance_val))[::-1]

    num_of_features = min(len(importance_val), 10)
    plt = _get_matplotlib_pyplot(server)
    if not plt: return

    fig, ax = plt.subplots(1, 1, figsize=(14, 10))

    plt.barh(pos[0:num_of_features], importance_val[0:num_of_features], align="center",
             height=0.8, color="#1F77B4", edgecolor="none")
    # Hide the right and top spines, color others grey
    ax.spines["right"].set_visible(False)
    ax.spines["top"].set_visible(False)
    ax.spines["bottom"].set_color("#7B7B7B")
    ax.spines["left"].set_color("#7B7B7B")
    # Only show ticks on the left and bottom spines
    ax.yaxis.set_ticks_position("left")
    ax.xaxis.set_ticks_position("bottom")
    plt.yticks(pos[0:num_of_features], importance.columns[0:num_of_features])
    plt.ylim([min(pos[0:num_of_features]) - 1, max(pos[0:num_of_features]) + 1])
    # ax.margins(y=0.5)
    plt.title("Permutation Variable Importance: " + algo_name + " (" + metric + ")", fontsize=20)
    if not server:
        plt.show()
