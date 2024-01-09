options = dict(
    model_extensions=[
        'h2o.model.extensions.ScoringHistoryGLM',
        'h2o.model.extensions.Fairness',
    ],
)
deprecated_params = dict(Lambda='lambda_')


def update_param(name, param):
    if name == 'distribution':
        param['values'].remove('custom')
        return param
    return None  # param untouched


def class_extensions():
    def _additional_used_columns(self, parms):
        """
        :return: Gam columns if specified.
        """
        return parms["gam_columns"]

    def _summary(self):
        """Return a detailed summary of the model."""
        model = self._model_json["output"]
        if "glm_model_summary" in model and model["glm_model_summary"] is not None:
            return model["glm_model_summary"]

    def scoring_history(self):
        """
        Retrieve Model Score History.

        :returns: The score history as an H2OTwoDimTable or a Pandas DataFrame.
        """
        model = self._model_json["output"]
        if "glm_scoring_history" in model and model["glm_scoring_history"] is not None:
            return model["glm_scoring_history"].as_data_frame()
        print("No score history for this model")
        
    def get_knot_locations(self, gam_column=None):
        """
        Retrieve gam columns knot locations if store_knot_location parameter is enabled.  If a gam column name is 
        specified, the know loations corresponding to that gam column is returned.  Otherwise, all knot locations are
        returned for all gam columns.  The order of the gam columns are specified in gam_knot_column_names of the 
        model output.
        
        :return: knot locations of gam columns.
        """
        if not(self.actual_params["store_knot_locations"]):
            raise H2OValueError("Knot locations are not available.  Please re-run with store_knot_locations=True")
        knot_locations = self._model_json['output']['knot_locations']
        gam_names = self._model_json['output']['gam_knot_column_names']
        if gam_column is None:
            return knot_locations
        else:
            if gam_column in gam_names:
                return knot_locations[gam_names.index(gam_column)]
            else:
                raise H2OValueError("{0} is not a valid gam column name.".format(gam_column))

    def get_gam_knot_column_names(self):
        """
        Retrieve gam column names corresponding to the knot locations that will be returned if store_knot_location
        parameter is enabled.  
     
        :return: gam column names whose knot locations are stored in the knot_locations.
        """
        if not(self.actual_params["store_knot_locations"]):
            raise H2OValueError("Knot locations are not available.  Please re-run with store_knot_locations=True")

        return self._model_json['output']['gam_knot_column_names']

extensions = dict(
    __imports__="""
import h2o
from h2o.utils.typechecks import U
from h2o.exceptions import H2OValueError
""",
    __class__=class_extensions,
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
    gam_columns=dict(
        setter="""
assert_is_type(gam_columns, None, [U(str, [str])])
if gam_columns:  # standardize as a nested list
    gam_columns = [[g] if isinstance(g, str) else g for g in gam_columns]
self._parms["gam_columns"] = gam_columns
"""
    )
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
examples = dict(
    bs="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], bs=[0,2,3,1])
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    gam_columns="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key])
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    get_gam_knot_column_names="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key])
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    get_knot_locations="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key])
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    interaction_pairs="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> interaction_pairs = [("start","stop")]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], interaction_pairs=interaction_pairs)#does not run#
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    interactions="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> interactions = ['start','stop']
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key],interactions=interactions)#does not run#
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    intercept="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], intercept=True)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    keep_gam_cols="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], keep_gam_cols=False)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    knot_ids="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key])
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    num_knots="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key])
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    remove_collinear_columns="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], remove_collinear_columns=False)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    scale_tp_penalty_mat="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], scale_tp_penalty_mat=False)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    splines_non_negative="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], splines_non_negative=None)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    spline_orders="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], spline_orders=None)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    standardize_tp_gam_cols="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], standardize_tp_gam_cols=False)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    startval="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], startval=None)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    store_knot_locations="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], store_knot_locations=False)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    tweedie_link_power="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], tweedie_link_power=0.0)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
""",
    tweedie_variance_power="""
>>> import h2o
>>> from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
>>> h2o.init()
>>> knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
>>> frameKnots1 = h2o.H2OFrame(python_obj=knots1)
>>> knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
>>> frameKnots2 = h2o.H2OFrame(python_obj=knots2)
>>> knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
>>> frameKnots3 = h2o.H2OFrame(python_obj=knots3)
>>> h2o_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv")
>>> h2o_data["C1"] = h2o_data["C1"].asfactor()
>>> h2o_data["C2"] = h2o_data["C2"].asfactor()
>>> h2o_data["C11"] = h2o_data["C11"].asfactor()
>>> train, test = h2o_data.split_frame(ratios = [.8])
>>> y = "C11"
>>> x = ["C1","C2"]
>>> numKnots = [5,5,5]
>>> h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"], scale=[1,1,1],num_knots=numKnots,knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key], tweedie_variance_power=0.0)
>>> h2o_model.train(x=x, y=y, training_frame=train)
>>> h2o_model.coef()
"""
)