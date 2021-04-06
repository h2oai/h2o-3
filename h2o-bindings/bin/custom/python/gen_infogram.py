rest_api_version = 3  # type: int

def update_param(name, param):
    if name == 'algorithm_params':
        param['type'] = 'KeyValue'
        param['default_value'] = None
        return param
    return None  # param untouched


def class_extensions():    
    def _extract_x_from_model(self):
        """
        extract admissible features from an Infogram model.
        
        :param self: 
        :return: 
        """
        features = self._model_json.get('output', {}).get('admissible_features')
        if features is None:
            raise ValueError("model %s doesn't have any admissible features" % self.key)
        return set(features)
    
    def get_relevance_cmi_frame(self):
        """
        Get the relevance and CMI for all attributes returned by Infogram as an H2O Frame.
        :param self: 
        :return: H2OFrame
        """
        keyString = self._model_json["output"]["relevance_cmi_key"]
            
        if keyString is None:
            return None
        else:
            return h2o.get_frame(keyString['name'])

    def get_admissible_attributes(self):
        """
        Get the admissible attributes
        :param self: 
        :return: 
        """
        if self._model_json["output"]["admissible_features"] is None:
            return None
        else:
            return self._model_json["output"]["admissible_features"]

    def get_admissible_relevance(self):
        """
        Get the relevance of admissible attributes
        :param self: 
        :return: 
        """
        if self._model_json["output"]["admissible_relevance"] is None:
            return None
        else:
            return self._model_json["output"]["admissible_relevance"]

    def get_admissible_cmi(self):
        """
        Get the normalized cmi of admissible attributes
        :param self: 
        :return: 
        """
        if self._model_json["output"]["admissible_cmi"] is None:
            return None
        else:
            return self._model_json["output"]["admissible_cmi"]

    def get_admissible_cmi_raw(self):
        """
        Get the raw cmi of admissible attributes
        :param self: 
        :return: 
        """
        if self._model_json["output"]["admissible_cmi_raw"] is None:
            return None
        else:
            return self._model_json["output"]["admissible_cmi_raw"]

    def get_all_predictor_relevance(self):
        """
        Get relevance of all predictors
        :param self: 
        :return: two tuples, first one is predictor names and second one is relevance
        """
        if self._model_json["output"]["all_predictor_names"] is None:
            return None
        else:
            return self._model_json["output"]["all_predictor_names"], self._model_json["output"]["relevance"]

    def get_all_predictor_cmi(self):
        """
        Get normalized cmi of all predictors.
        :param self: 
        :return: two tuples, first one is predictor names and second one is cmi
        """
        if self._model_json["output"]["all_predictor_names"] is None:
            return None
        else:
            return self._model_json["output"]["all_predictor_names"], self._model_json["output"]["cmi"]

    def get_all_predictor_cmi_raw(self):
        """
        Get raw cmi of all predictors.
        :param self: 
        :return: two tuples, first one is predictor names and second one is cmi
        """
        if self._model_json["output"]["all_predictor_names"] is None:
            return None
        else:
            return self._model_json["output"]["all_predictor_names"], self._model_json["output"]["cmi_raw"]
        
    # Override train method to support infogram needs
    def train(self, x=None, y=None, training_frame=None, verbose=False, **kwargs):
        sup = super(self.__class__, self)
        
        def extend_parms(parms):  # add parameter checks specific to infogram
            if parms["data_fraction"] is not None:
                assert_is_type(parms["data_fraction"], numeric)
                assert parms["data_fraction"] > 0 and parms["data_fraction"] <= 1, "data_fraction should exceed 0" \
                                                                                   " and <= 1."
        
        parms = sup._make_parms(x,y,training_frame, extend_parms_fn = extend_parms, **kwargs)
 
        sup._train(parms, verbose=verbose)
        # can probably get rid of model attributes that Erin does not want here
        return self

extensions = dict(
    __imports__="""
import ast
import json
import warnings
import h2o
from h2o.utils.typechecks import assert_is_type, is_type, numeric
from h2o.frame import H2OFrame
import numpy as np
""",
    __class__=class_extensions
)
       
overrides = dict(
    algorithm_params=dict(
        getter="""
if self._parms.get("{sname}") != None:
    algorithm_params_dict =  ast.literal_eval(self._parms.get("{sname}"))
    for k in algorithm_params_dict:
        if len(algorithm_params_dict[k]) == 1: #single parameter
            algorithm_params_dict[k] = algorithm_params_dict[k][0]
    return algorithm_params_dict
else:
    return self._parms.get("{sname}")
""",
        setter="""
assert_is_type({pname}, None, {ptype})
if {pname} is not None and {pname} != "":
    for k in {pname}:
        if ("[" and "]") not in str(algorithm_params[k]):
            algorithm_params[k] = [algorithm_params[k]]
    self._parms["{sname}"] = str(json.dumps({pname}))
else:
    self._parms["{sname}"] = None
"""
    ),
    relevance_index_threshold=dict(
        setter="""
if relevance_index_threshold <= -1: # not set
    if self._parms["protected_columns"] is not None:    # fair infogram
        self._parms["relevance_index_threshold"]=0.1
else: # it is set
    if self._parms["protected_columns"] is not None:    # fair infogram
        self._parms["relevance_index_threshold"] = relevance_index_threshold
    else: # core infogram should not have been set
        warnings.warn("Should not set relevance_index_threshold for core infogram runs.  Set total_information_threshold instead.  Using default of 0.1 if not set", RuntimeWarning)
"""
    ),
    safety_index_threshold=dict(
        setter="""
if safety_index_threshold <= -1: # not set
    if self._parms["protected_columns"] is not None:
        self._parms["safety_index_threshold"]=0.1
else: # it is set
    if self._parms["protected_columns"] is not None: # fair infogram
        self._parms["safety_index_threshold"] = safety_index_threshold
    else: # core infogram should not have been set
        warnings.warn("Should not set safety_index_threshold for core infogram runs.  Set net_information_threshold instead.  Using default of 0.1 if not set", RuntimeWarning)
"""
    ),
    net_information_threshold=dict(
        setter="""
if net_information_threshold <= -1: # not set
    if self._parms["protected_columns"] is None:
        self._parms["net_information_threshold"]=0.1
else:  # set
    if self._parms["protected_columns"] is not None: # fair infogram
        warnings.warn("Should not set net_information_threshold for fair infogram runs.  Set safety_index_threshold instead.  Using default of 0.1 if not set", RuntimeWarning)
    else:
        self._parms["net_information_threshold"]=net_information_threshold
"""
    ),
    total_information_threshold=dict(
        setter="""
if total_information_threshold <= -1: # not set
    if self._parms["protected_columns"] is None:
        self._parms["total_information_threshold"] = 0.1
else:
    if self._parms["protected_columns"] is not None: # fair infogram
        warnings.warn("Should not set total_information_threshold for fair infogram runs.  Set relevance_index_threshold instead.  Using default of 0.1 if not set", RuntimeWarning)
    else:
        self._parms["total_information_threshold"] = total_information_threshold
"""
    )
)

doc = dict(
    __class__="""
Given a sensitive/unfair predictors list, Infogram will add all predictors that contains information on the 
 sensitive/unfair predictors list to the sensitive/unfair predictors list.  It will return a set of predictors that
 do not contain information on the sensitive/unfair list and hence user can build a fair model.  If no sensitive/unfair
 predictor list is given, Infogram will return a list of core predictors that should be used to build a final model.
 Infogram can significantly cut down the number of predictors needed to build a model and hence will build a simple
 model that is more interpretable, less susceptible to overfitting, runs faster while providing similar accuracy
 as models built using all attributes.
"""
)
