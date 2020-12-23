# -*- encoding: utf-8 -*-

import h2o
from h2o.frame import H2OFrame
from h2o.expr import ExprNode
from h2o.exceptions import H2OValueError
from h2o.model.model_base import get_matplotlib_pyplot
from h2o.utils.shared_utils import can_use_pandas


def permutation_varimp_oat(model, frame):
    """
    One At a time Morris method to analyse which variables are influential (non-linear and/or interacting),
    influential (non-interacting), less influential, non-influential. This method plots the mean and standard
    deviation of the Permutation Variable Importance.
    :param model: trained model
    :param frame: training frame
    """
    if type(frame) is not H2OFrame:
        raise H2OValueError("Frame is not H2OFrame")

    m_frame = H2OFrame._expr(ExprNode("PermutationVarImpOat", model, frame))

    import matplotlib.pyplot as plt
    mean = []
    std_dev = []
    annotations = []
    for col in m_frame.col_names:
        if col == "indices":
            continue  # has string values
        mean.append(m_frame[0, col])
        std_dev.append(m_frame[1, col])
        annotations.append(col)

    fig, ax = plt.subplots()
    ax.scatter(mean, std_dev)
    ax.set_xlim(left=0, right=max(mean) + 1 / 10 * max(mean))  # x axis limits
    ax.set_ylim(bottom=0, top=max(std_dev) + 1 / 10 * max(std_dev))  # y axis limits
    ax.set_xlabel('μ*')  # x axis label
    ax.set_ylabel('σ')  # y axis label

    ax.set_yticklabels([])
    ax.set_xticklabels([])

    for index, annotation in enumerate(annotations):
        ax.annotate(annotation, (mean[index], std_dev[index]))

    plt.show()


def permutation_varimp(model, frame, use_pandas=True, metric="mse"):
    """
    Get Permutation Variable Importance Frame. 
    :param model: model after training
    :param frame: training frame
    :param use_pandas: select true to return pandas data frame
    :param metric: (str) loss function metrics to be used 
    :return: H2OFrame or Pandas data frame
    """

    if type(frame) is not H2OFrame:
        raise H2OValueError("Frame is not H2OFrame")

    existring_metrics = model._model_json['output']['training_metrics']._metric_json
    if metric not in existring_metrics:
        raise H2OValueError("Metric " + metric + " doesn't exist for this model.")

    m_frame = H2OFrame._expr(ExprNode("PermutationVarImp", model, frame, metric))
    if use_pandas and can_use_pandas():
        import pandas
        pd = h2o.as_list(m_frame)
        return pandas.DataFrame(pd, columns=pd.columns)

    return m_frame


def plot_permutation_var_imp(importance, algo_name, metric="mse", server=False):
    """
    Plot Permutation Variable Importance, by default scaled importance is plotted
    Inspired from model_base.varimp_plot() to stay consistent with the manner of plotting
    :param importance: frame of variable importance
    :param algo_name: algorithm of the model
    :param metric: loss function metric that was used for calculation
    :param Specify whether to activate matplotlib "server" mode. In this case, the plots are saved to a file instead of being rendered.
    :return: 
    """

    importance_val = []
    for col in importance.columns:
        if col == "importance":
            continue  # has string values
        importance_val.append(importance.loc[2][col])

    # specify bar centers on the y axis, but flip the order so largest bar appears at top
    pos = range(len(importance_val))[::-1]

    num_of_features = min(len(importance_val), 10)
    plt = get_matplotlib_pyplot(server)
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
    plt.yticks(pos[0:num_of_features], importance.columns[1:num_of_features + 1])  # col 0 is str: importance
    plt.ylim([min(pos[0:num_of_features]) - 1, max(pos[0:num_of_features]) + 1])
    # ax.margins(y=0.5)
    plt.title("Permutation Variable Importance: " + algo_name + " (" + metric + ")", fontsize=20)
    if not server:
        plt.show()
