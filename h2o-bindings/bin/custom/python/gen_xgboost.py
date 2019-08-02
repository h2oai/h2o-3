extensions = dict(
    __imports__="""import h2o""",
    __class__="""
@staticmethod
def available():
    \"""
    Ask the H2O server whether a XGBoost model can be built (depends on availability of native backends).
    :return: True if a XGBoost model can be built, or False otherwise.
    \"""
    if "XGBoost" not in h2o.cluster().list_core_extensions():
        print("Cannot build an XGBoost model - no backend found.")
        return False
    else:
        return True
"""
)

doc = dict(
    __class__="""
Builds a eXtreme Gradient Boosting model using the native XGBoost backend.
""",
)
