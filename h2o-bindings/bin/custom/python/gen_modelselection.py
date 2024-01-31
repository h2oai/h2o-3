def class_extensions():
    def get_regression_influence_diagnostics(self, predictor_size=None):
        """
        Get the regression influence diagnostics frames for all models with different number of predictors.  If a 
        predictor size is specified, only one frame is returned for that predictor size.
        
        :param predictor_size: predictor subset size, will return regression influence diagnostics frame of that size
        :return: list of H2OFrames or just one frame that contains predictors, response and DFBETA_ predictors
        """
        if self.actual_params["mode"] == "maxrsweep" and not(self.actual_params["build_glm_model"]):
            raise H2OValueError("get_regression_influence_diagnostics can only be called if glm models are built."
                                "  For mode == 'maxrsweep', build_glm_model should be set to True.")
        model_ids = self._model_json["output"]["best_model_ids"]
        num_models = len(model_ids)
        if self.actual_params["influence"] == "dfbetas":
            if predictor_size is None or len(predictor_size) == 0:
                frame_list = [None]*num_models
                for index in range(0, num_models):
                    one_model = h2o.get_model(model_ids[index]['name'])
                    frame_list[index] = h2o.get_frame(one_model._model_json["output"]["regression_influence_diagnostics"]["name"])
                return frame_list
            else:
                max_pred_numbers = len(self._model_json["output"]["best_predictors_subset"][num_models-1])
                if predictor_size <= 0 or predictor_size > max_pred_numbers:
                    raise H2OValueError("predictor_size must be between 1 and the maximum number of predictors in"
                                        " the model {0}".format(max_pred_numbers))
                if mode == 'backward':
                    offset = max_pred_numbers - predictor_size
                    one_model = h2o.get_model(model_ids[num_models-1-offset]['name'])
                else:
                    one_model = h2o.get_model(model_ids[predictor_size-1]['name'])
                return h2o.get_frame(one_model._model_json["output"]["regression_influence_diagnostics"]["name"])
        else:
            raise H2OValueError("influence must be set to dfbetas in order to enable regression influence "
                                "diagnostics generation.")



    def coef_norm(self, predictor_size=None):
        """
        Get the normalized coefficients for all models built with different number of predictors.
    
        :param predictor_size: predictor subset size, will only return model coefficients of that subset size.
        :return: list of Python Dicts of coefficients for all models built with different predictor numbers

        :examples:
        
        >>> import h2o
        >>> from h2o.estimators import H2OModelSelectionEstimator
        >>> h2o.init()
        >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv")
        >>> predictors = ["AGE", "RACE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS"]
        >>> response = "GLEASON"
        >>> maxrModel = H2OModelSelectionEstimator(max_predictor_number=5,
        ...                                        seed=12345,
        ...                                        mode="maxr")
        >>> maxrModel.train(x=predictors, y=response, training_frame=prostate)
        >>> coeff_norm = maxrModel.coef_norm()
        >>> print(coeff_norm)
        >>> coeff_norm_3 = maxrModel.coef_norm(predictor_size=3) # print coefficient norm with 3 predictors
        >>> print(coeff_norm_3)
        """
        model_ids = self._model_json["output"]["best_model_ids"]
        if not(self.actual_params["build_glm_model"]) and self.actual_params["mode"]=="maxrsweep":
            coef_names = self._model_json["output"]["coefficient_names"]
            coef_values = self._model_json["output"]["coefficient_values_normalized"]
            num_models = len(coef_names)
            if predictor_size is None or len(predictor_size) == 0:
                coefs = [None]*num_models
                for index in range(0, num_models):
                    coef_name = coef_names[index]
                    coef_val = coef_values[index]
                    coefs[index] = dict(zip(coef_name, coef_val))
                return coefs
            else:
                if predictor_size > num_models:
                    raise H2OValueError("predictor_size (predictor subset size) cannot exceed the total number of predictors used.")
                if predictor_size <= 0:
                    raise H2OValueError("predictor_size (predictor subset size) must be between 0 and the total number of predictors used.")
                coef_name = coef_names[predictor_size-1]
                coef_val = coef_values[predictor_size-1]
                return dict(zip(coef_name, coef_val))
        else:
            model_numbers = len(model_ids)
            mode = self.get_params()['mode']
            if predictor_size is None:
                coefs = [None]*model_numbers
                for index in range(0, model_numbers):
                    one_model = h2o.get_model(model_ids[index]['name'])
                    tbl = one_model._model_json["output"]["coefficients_table"]
                    if tbl is not None:
                        coefs[index] =  {name: coef for name, coef in zip(tbl["names"], tbl["standardized_coefficients"])}
                return coefs
            max_pred_numbers = len(self._model_json["output"]["best_predictors_subset"][model_numbers-1])
            if predictor_size > max_pred_numbers:
                raise H2OValueError("predictor_size (predictor subset size) cannot exceed the total number of predictors used.")
            if predictor_size == 0:
                raise H2OValueError("predictor_size (predictor subset size) must be between 0 and the total number of predictors used.")

            if mode=='backward':
                offset = max_pred_numbers - predictor_size
                one_model = h2o.get_model(model_ids[model_numbers-1-offset]['name'])
            else:
                one_model = h2o.get_model(model_ids[predictor_size-1]['name'])
            tbl = one_model._model_json["output"]["coefficients_table"]
            if tbl is not None:
                return {name: coef for name, coef in zip(tbl["names"], tbl["standardized_coefficients"])}

    def coef(self, predictor_size=None):
        """
        Get the coefficients for all models built with different number of predictors.
        
        :param predictor_size: predictor subset size, will only return model coefficients of that subset size.
        :return: list of Python Dicts of coefficients for all models built with different predictor numbers

        :examples:
        
        >>> import h2o
        >>> from h2o.estimators import H2OModelSelectionEstimator
        >>> h2o.init()
        >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv")
        >>> predictors = ["AGE", "RACE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS"]
        >>> response = "GLEASON"
        >>> maxrModel = H2OModelSelectionEstimator(max_predictor_number=5,
        ...                                        seed=12345,
        ...                                        mode="maxr")
        >>> maxrModel.train(x=predictors, y=response, training_frame=prostate)
        >>> coeff = maxrModel.coef()
        >>> print(coeff)
        >>> coeff_3 = maxrModel.coef(predictor_size=3)
        >>> print(coeff_3)
        """
        if not self.actual_params["build_glm_model"] and self.actual_params["mode"]=="maxrsweep":
            coef_names = self._model_json["output"]["coefficient_names"]
            coef_values = self._model_json["output"]["coefficient_values"]
            num_models = len(coef_names)
            if predictor_size is None:
                coefs = [None]*num_models
                for index in range(0, num_models):
                    coef_name = coef_names[index]
                    coef_val = coef_values[index]
                    coefs[index] = dict(zip(coef_name, coef_val))
                return coefs  
            else:
                if predictor_size > num_models:
                    raise H2OValueError("predictor_size (predictor subset size) cannot exceed the total number of predictors used.")
                if predictor_size <= 0:
                    raise H2OValueError("predictor_size (predictor subset size) must be between 0 and the total number of predictors used.")
                coef_name = coef_names[predictor_size-1]
                coef_val = coef_values[predictor_size-1]
                return dict(zip(coef_name, coef_val))
        else:
            model_ids = self._model_json["output"]["best_model_ids"]
            if model_ids is None:
                return None
            else:
                model_numbers = len(model_ids)
                mode = self.get_params()['mode']
                if predictor_size is None:
                    coefs = [None]*model_numbers
                    for index in range(0, model_numbers):
                        one_model = h2o.get_model(model_ids[index]['name'])
                        tbl = one_model._model_json["output"]["coefficients_table"]
                        if tbl is not None:
                            coefs[index] =  dict(zip(tbl["names"], tbl["coefficients"]))
                    return coefs
                max_pred_numbers = len(self._model_json["output"]["best_predictors_subset"][model_numbers-1])
                if predictor_size > max_pred_numbers:
                    raise H2OValueError("predictor_size (predictor subset size) cannot exceed the total number of predictors used.")
                if predictor_size == 0:
                    raise H2OValueError("predictor_size (predictor subset size) must be between 0 and the total number of predictors used.")

                if mode=='backward':
                    offset = max_pred_numbers - predictor_size
                    one_model = h2o.get_model(model_ids[model_numbers-1-offset]['name'])
                else:
                    one_model = h2o.get_model(model_ids[predictor_size-1]['name'])
                tbl = one_model._model_json["output"]["coefficients_table"]
                if tbl is not None:
                    return dict(zip(tbl["names"], tbl["coefficients"]))

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
    
    def get_predictors_added_per_step(self):
        """
        Get list of predictors added at each step of the model building process

        :return: a list of predictors added at each step
        """ 
        if not(self.get_params()["mode"] == "backward"):
            return self._model_json["output"]["predictors_added_per_step"]
        else:
            print("backward mode does not have list predictor_added_per_step")

    def get_predictors_removed_per_step(self):
        """
        Get list of predictors removed at each step of the model building process

        :return: a list of predictors removed at each step
        """
        return self._model_json["output"]["predictors_removed_per_step"]           

    def get_best_model_predictors(self):
        """
        Get list of best models with 1 predictor, 2 predictors, ..., max_predictor_number of predictors that have the
        highest r2 values

        :return: a list of best predictors subset
        """
        return self._model_json["output"]["best_predictors_subset"]

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

examples = dict(
    build_glm_model="""
>>> import h2o
>>> from h2o.estimators import H2OModelSelectionEstimator
>>> h2o.init()
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv")
>>> predictors = ["AGE", "RACE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS"]
>>> response = "GLEASON"
>>> maxrModel = H2OModelSelectionEstimator(max_predictor_number=5,
...                                        seed=12345,
...                                        mode="maxrsweep",
...                                        build_glm_model=True)
>>> maxrModel.train(x=predictors, y=response, training_frame=prostate)
>>> result = maxrModel.result()
>>> # get the GLM model with the best performance for a fixed predictor size:
>>> one_model = h2o.get_model(result["model_id"][1, 0])
>>> predict = one_model.predict(prostate)
>>> # print a version of the predict frame:
>>> print(predict)
""",
    influence="""
>>> import h2o
>>> from h2o.estimators import H2OModelSelectionEstimator
>>> h2o.init()
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv")
>>> predictors = ["AGE", "RACE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS"]
>>> response = "GLEASON"
>>> maxrModel = H2OModelSelectionEstimator(max_predictor_number=5,
...                                        seed=12345,
...                                        mode="maxr",
...                                        influence="dfbetas")
>>> maxrModel.train(x=predictors, y=response, training_frame=prostate)
>>> glm_rid = maxrModel.get_regression_influence_diagnostics()
>>> print(glm_rid)
""",
    p_values_threshold="""
>>> import h2o
>>> from h2o.estimators import H2OModelSelectionEstimator
>>> h2o.init()
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv")
>>> predictors = ["AGE", "RACE", "CAPSULE", DCAPS", "PSA", "VOL", "DPROS"]
>>> response = "GLEASON"
>>> backwardModel = H2OModelSelectionEstimator(min_predictor_number=2,
...                                            seed=12345,
...                                            mode="backward",
...                                            p_values_threshold=0.001)
>>> backwardModel.train(x=predictors, y=response, training_frame=prostate)
>>> result = backwardModel.result()
>>> print(result)
""",
    mode="""
>>> import h2o
>>> from h2o.estimators import H2OModelSelectionEstimator
>>> h2o.init()
>>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv")
>>> predictors = ["AGE", "RACE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS"]
>>> response = "GLEASON"
>>> maxrModel = H2OModelSelectionEstimator(max_predictor_number=5,
...                                        seed=12345,
...                                        mode="maxr")
>>> maxrModel.train(x=predictors, y=response, training_frame=prostate)
>>> results = maxrModel.result()
>>> print(results)
"""
)
