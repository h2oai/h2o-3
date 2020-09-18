# -*- encoding: utf-8 -*-

from ._mli import variable_importance_heatmap, model_correlation_heatmap, shap_explain_row, shap_summary_plot,\
    explain, explain_row, partial_dependences, individual_conditional_expectations, residual_analysis

__all__ = [
    "variable_importance_heatmap",
    "model_correlation_heatmap",
    "shap_explain_row",
    "shap_summary_plot",
    "explain",
    "explain_row",
    "partial_dependences",
    "individual_conditional_expectations",
    "residual_analysis"
]


def register_mli_methods():
    import h2o.model
    import h2o.automl._base  # NOQA

    h2o.model.H2ORegressionModel.residual_analysis = residual_analysis
    h2o.model.ModelBase.shap_summary_plot = shap_summary_plot
    h2o.model.ModelBase.shap_explain_row = shap_explain_row
    h2o.model.ModelBase.explain = explain
    h2o.model.ModelBase.explain_row = explain_row
    h2o.model.ModelBase.individual_conditional_expectations = individual_conditional_expectations

    h2o.automl._base.H2OAutoMLBaseMixin.variable_importance_heatmap = variable_importance_heatmap
    h2o.automl._base.H2OAutoMLBaseMixin.model_correlation_heatmap = model_correlation_heatmap
    h2o.automl._base.H2OAutoMLBaseMixin.explain = explain
    h2o.automl._base.H2OAutoMLBaseMixin.explain_row = explain_row

