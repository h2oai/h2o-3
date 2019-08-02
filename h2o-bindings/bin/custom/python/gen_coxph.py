extensions = dict(
    __class__="""
def _additional_used_columns(self, parms):
    \"""
    :return: Start and stop column if specified.
    \"""
    result = []
    for col in ["start_column", "stop_column"]:
        if col in parms and parms[col] is not None:
            result.append(parms[col])
    return result
"""
)

doc = dict(
    __class__="""
Trains a Cox Proportional Hazards Model (CoxPH) on an H2O dataset.
"""
)
