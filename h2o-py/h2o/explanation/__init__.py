# -*- encoding: utf-8 -*-

from ._explain import varimp_heatmap, model_correlation_heatmap, shap_explain_row_plot, shap_summary_plot,\
    explain, explain_row, pd_plot, pd_multi_plot, ice_plot, residual_analysis_plot

__all__ = [
    "explain",
    "explain_row",
    "varimp_heatmap",
    "model_correlation_heatmap",
    "pd_multi_plot"
]


def register_explain_methods():
    import h2o.model
    import h2o.automl._base  # NOQA

    h2o.model.H2ORegressionModel.residual_analysis_plot = residual_analysis_plot
    h2o.model.ModelBase.shap_summary_plot = shap_summary_plot
    h2o.model.ModelBase.shap_explain_row_plot = shap_explain_row_plot
    h2o.model.ModelBase.explain = explain
    h2o.model.ModelBase.explain_row = explain_row
    h2o.model.ModelBase.pd_plot = pd_plot
    h2o.model.ModelBase.ice_plot = ice_plot

    h2o.automl._base.H2OAutoMLBaseMixin.pd_multi_plot = pd_multi_plot
    h2o.automl._base.H2OAutoMLBaseMixin.varimp_heatmap = varimp_heatmap
    h2o.automl._base.H2OAutoMLBaseMixin.model_correlation_heatmap = model_correlation_heatmap
    h2o.automl._base.H2OAutoMLBaseMixin.explain = explain
    h2o.automl._base.H2OAutoMLBaseMixin.explain_row = explain_row

