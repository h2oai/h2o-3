def update_param(name, param):
    if name == 'distribution':
        param['values'].remove('custom')
        return param
    return None  # param untouched


def class_extensions():
    @property
    def Lambda(self):
        """DEPRECATED. Use ``self.lambda_`` instead"""
        return self._parms["lambda"] if "lambda" in self._parms else None

    @Lambda.setter
    def Lambda(self, value):
        self._parms["lambda"] = value

    def _additional_used_columns(self, parms):
        """
        :return: Gam columns if specified.
        """
        return parms["gam_columns"]

extensions = dict(
    __imports__="""import h2o""",
    __class__=class_extensions,
    __init__validation="""
if "Lambda" in kwargs: kwargs["lambda_"] = kwargs.pop("Lambda")
"""
)

overrides = dict(
    alpha=dict(
        setter="""
# For `alpha` and `lambda` the server reports type float[], while in practice simple floats are also ok
assert_is_type({pname}, None, numeric, [numeric])
self._parms["{sname}"] = {pname}
"""
    ),
    lambda_=dict(
        setter="""
assert_is_type({pname}, None, numeric, [numeric])
self._parms["{sname}"] = {pname}
"""
    ),
)

doc = dict(
    __class__="""
Fits a generalized additive model, specified by a response variable, a set of predictors, and a
description of the error distribution.

A subclass of :class:`ModelBase` is returned. The specific subclass depends on the machine learning task
at hand (if it's binomial classification, then an H2OBinomialModel is returned, if it's regression then a
H2ORegressionModel is returned). The default print-out of the models is shown, but further GAM-specific
information can be queried out of the object. Upon completion of the GAM, the resulting object has
coefficients, normalized coefficients, residual/null deviance, aic, and a host of model metrics including
MSE, AUC (for logistic regression), degrees of freedom, and confusion matrices.
"""
)
