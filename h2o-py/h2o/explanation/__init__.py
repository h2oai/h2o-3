# -*- encoding: utf-8 -*-


def _complain_about_matplotlib(*args, **kwargs):
    raise ImportError("Plotting functionality requires matplotlib. Please install matplotlib.")


def _register_dummy_methods():
    import h2o.model
    import h2o.automl._base  # NOQA
    h2o.model.H2ORegressionModel.residual_analysis_plot = _complain_about_matplotlib
    h2o.model.ModelBase.shap_summary_plot = _complain_about_matplotlib
    h2o.model.ModelBase.shap_explain_row_plot = _complain_about_matplotlib
    h2o.model.ModelBase.explain = _complain_about_matplotlib
    h2o.model.ModelBase.explain_row = _complain_about_matplotlib
    h2o.model.ModelBase.pd_plot = _complain_about_matplotlib
    h2o.model.ModelBase.ice_plot = _complain_about_matplotlib
    h2o.model.ModelBase.learning_curve_plot = _complain_about_matplotlib

    h2o.automl._base.H2OAutoMLBaseMixin.pd_multi_plot = _complain_about_matplotlib
    h2o.automl._base.H2OAutoMLBaseMixin.varimp_heatmap = _complain_about_matplotlib
    h2o.automl._base.H2OAutoMLBaseMixin.model_correlation_heatmap = _complain_about_matplotlib
    h2o.automl._base.H2OAutoMLBaseMixin.explain = _complain_about_matplotlib
    h2o.automl._base.H2OAutoMLBaseMixin.explain_row = _complain_about_matplotlib
    h2o.automl._base.H2OAutoMLBaseMixin.model_correlation = _complain_about_matplotlib
    h2o.automl._base.H2OAutoMLBaseMixin.varimp = _complain_about_matplotlib



try:
    import numpy
    import matplotlib
    from ._explain import *

    __all__ = [
        "explain",
        "explain_row",
        "varimp_heatmap",
        "model_correlation_heatmap",
        "pd_multi_plot",
        "varimp",
        "model_correlation",
        "pareto_front"
    ]
except ImportError as e:  # Numpy, Matplotlib
    _register_dummy_methods()
    raise e


def register_explain_methods():
    import h2o.model
    import h2o.automl._base  # NOQA
    import h2o.grid.grid_search

    h2o.model.H2ORegressionModel.residual_analysis_plot = residual_analysis_plot
    h2o.model.ModelBase.shap_summary_plot = shap_summary_plot
    h2o.model.ModelBase.shap_explain_row_plot = shap_explain_row_plot
    h2o.model.ModelBase.explain = explain
    h2o.model.ModelBase.explain_row = explain_row
    h2o.model.ModelBase.pd_plot = pd_plot
    h2o.model.ModelBase.ice_plot = ice_plot
    h2o.model.ModelBase.learning_curve_plot = learning_curve_plot

    h2o.automl._base.H2OAutoMLBaseMixin.pd_multi_plot = pd_multi_plot
    h2o.automl._base.H2OAutoMLBaseMixin.varimp_heatmap = varimp_heatmap
    h2o.automl._base.H2OAutoMLBaseMixin.model_correlation_heatmap = model_correlation_heatmap
    h2o.automl._base.H2OAutoMLBaseMixin.explain = explain
    h2o.automl._base.H2OAutoMLBaseMixin.explain_row = explain_row
    h2o.automl._base.H2OAutoMLBaseMixin.model_correlation = model_correlation
    h2o.automl._base.H2OAutoMLBaseMixin.varimp = varimp

