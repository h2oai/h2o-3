extensions = dict(
    extra_params=dict(verbose='FALSE'),
    module="""
#' Determines whether an XGBoost model can be built
#'
#' Ask the H2O server whether a XGBoost model can be built. (Depends on availability of native backend.)
#' Returns True if a XGBoost model can be built, or False otherwise.
#' @export
h2o.xgboost.available <- function() {
    if (!("XGBoost" %in% h2o.list_core_extensions())) {
        print("Cannot build a XGboost model - no backend found.")
        return(FALSE)
    } else {
        return(TRUE)
    }
}
""",
)

doc = dict(
    preamble="""
Build an eXtreme Gradient Boosting model

Builds a eXtreme Gradient Boosting model using the native XGBoost backend.
""",
    params=dict(
        verbose="""
\code{Logical}. Print scoring history to the console (Metrics per tree). Defaults to FALSE.
"""
    ),
)
