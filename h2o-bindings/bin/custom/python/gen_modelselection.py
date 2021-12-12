def class_extensions():
    def coef_norm(self, predictor_size=None):
        """
        Get the normalized coefficients for all models built with different number of predictors.
    
        :param predictor_size: predictor subset size, will only return model coefficients of that subset size.
        :return: list of Python Dicts of coefficients for all models built with different predictor numbers
        """
        model_ids = self._model_json["output"]["best_model_ids"]
        if model_ids is None:
            return None
        else:
            model_numbers = len(model_ids)
            if predictor_size==None:
                coefs = [None]*model_numbers
                for index in range(0, model_numbers):
                    one_model = h2o.get_model(model_ids[index]['name'])
                    tbl = one_model._model_json["output"]["coefficients_table"]
                    if tbl is not None:
                        coefs[index] =  {name: coef for name, coef in zip(tbl["names"], tbl["standardized_coefficients"])}
                return coefs
            if predictor_size > model_numbers:
                raise H2OValueError("predictor_size (predictor subset size) cannot exceed the total number of predictors used.")
            if predictor_size == 0:
                raise H2OValueError("predictor_size (predictor subset size) must be between 0 and the total number of predictors used.")

            one_model = h2o.get_model(model_ids[predictor_size-1]['name'])
            tbl = one_model._model_json["output"]["coefficients_table"]
            if tbl is not None:
                return {name: coef for name, coef in zip(tbl["names"], tbl["standardized_coefficients"])}

    def coef(self, predictor_size=None):
        """
        Get the coefficients for all models built with different number of predictors.
        
        :param predictor_size: predictor subset size, will only return model coefficients of that subset size.
        :return: list of Python Dicts of coefficients for all models built with different predictor numbers
        """
        model_ids = self._model_json["output"]["best_model_ids"]
        if model_ids is None:
            return None
        else:
            model_numbers = len(model_ids)
            if predictor_size==None:
                coefs = [None]*model_numbers
                for index in range(0, model_numbers):
                    one_model = h2o.get_model(model_ids[index]['name'])
                    tbl = one_model._model_json["output"]["coefficients_table"]
                    if tbl is not None:
                        coefs[index] =  {name: coef for name, coef in zip(tbl["names"], tbl["coefficients"])}
                return coefs
            if predictor_size > model_numbers:
                raise H2OValueError("predictor_size (predictor subset size) cannot exceed the total number of predictors used.")
            if predictor_size == 0:
                raise H2OValueError("predictor_size (predictor subset size) must be between 0 and the total number of predictors used.")

            one_model = h2o.get_model(model_ids[predictor_size-1]['name'])
            tbl = one_model._model_json["output"]["coefficients_table"]
            if tbl is not None:
                return {name: coef for name, coef in zip(tbl["names"], tbl["coefficients"])}

    def result(self):
        """
        Get result frame that contains information about the model building process like for modelselection and anovaglm.
        :return: the H2OFrame that contains information about the model building process like for modelselection and anovaglm.
        """
        return H2OFrame._expr(expr=ExprNode("result", ASTId(self.key)))._frame(fill_cache=True)

    def get_best_R2_values(self):
        """
        Get list of best R2 values of models with 1 predictor, 2 predictors, ..., max_predictor_number of predictors

        :return: a list of best r2 values
        """
        return self._model_json["output"]["best_r2_values"]

    def get_best_model_predictors(self):
        """
        Get list of best models with 1 predictor, 2 predictors, ..., max_predictor_number of predictors that have the
        highest r2 values

        :return: a list of best r2 values
        """
        return self._model_json["output"]["best_model_predictors"]

extensions = dict(
    __imports__="""
    import h2o
    from h2o.base import Keyed
    from h2o.frame import H2OFrame
    from h2o.expr import ExprNode
    from h2o.expr import ASTId
    from h2o.exceptions import H2OValueError
    """,
    __class__=class_extensions,
    __init__validation="""
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
H2O ModelSelection is used to build the best model with one predictor, two predictors, ... up to max_predictor_number 
specified in the algorithm parameters when mode=allsubsets.  The best model is the one with the highest R2 value.  When
mode=maxr, the model returned is no longer guaranteed to have the best R2 value.
"""
)
