# -*- encoding: utf-8 -*-
import functools as ft
from inspect import getdoc
import re

import h2o
from h2o.automl._base import H2OAutoMLBaseMixin
from h2o.automl._h2o_automl_output import H2OAutoMLOutput
from h2o.base import Keyed
from h2o.estimators import H2OEstimator
from h2o.exceptions import H2OResponseError, H2OValueError
from h2o.frame import H2OFrame
from h2o.job import H2OJob
from h2o.utils.metaclass import h2o_meta
from h2o.utils.shared_utils import check_id
from h2o.utils.typechecks import assert_is_type, is_type, numeric

class H2OInfoGram(h2o_meta(Keyed)):
    def __init__(self,
                 model_id=None,
                 seed=None,
                 max_runtime_secs=None,
                 infogram_algorithm=None,
                 infogram_algorithm_params=None,
                 model_algorithm=None,
                 model_algorithm_params=None,
                 sensitive_attributes=None,
                 conditional_info_threshold=0.1,
                 varimp_threshold=0.1,
                 data_fraction=1.0,
                 parallelism=None,
                 ntop=50,
                 pval=False,
                 **kwargs):
        # Check if H2O jar contains AdmissibleML
        try:
            h2o.api("GET /3/Metadata/schemas/InfoGramV99")
        except h2o.exceptions.H2OResponseError as e:
            print(e)
            print("*******************************************************************\n" \
                  "*Please verify that your H2O jar has the proper AdmissibleML extensions.*\n" \
                  "*******************************************************************\n" \
                  "\nVerbose Error Message:")
        # check for valid parameter settings    
        assert_is_type(model_id, None, str)
        assert_is_type(max_runtime_secs, None, int)
        assert_is_type(seed, None, int)
        assert_is_type(infogram_algorithm, None, str)
        assert_is_type(infogram_algorithm_params, None, dict)
        assert_is_type(model_algorithm, None, str)
        assert_is_type(model_algorithm_params, None, dict)
        assert_is_type(sensitive_attributes, None, tuple)
        if isinstance(sensitive_attributes):
            for obj in sensitive_attributes:
                assert_is_type(obj, str)
        assert_is_type(conditional_info_threshold, numeric)
        assert conditional_info_threshold >= 0 and conditional_info_threshold <= 1, "conditional_info_threshold should be between 0 and 1."
        assert_is_type(varimp_threshold, numeric)
        assert varimp_threshold >= 0 and varimp_threshold <= 1, "varimp_threshold should be between 0 and 1."
        assert_is_type(data_fraction, numeric)
        assert data_fraction > 0 and data_fraction <= 1, "data_fraction should exceed 0 and <= 1."  
        assert_is_type(parallelism, None, int)
        assert_is_type(ntop, int)
        assert_is_type(pval, bool)
        # set parameters
        self._future = False  # used by __repr__/show to query job state#
        self._job = None  # used when _future is True#
        self.model_id = model_id
        self.max_runtime_secs = max_runtime_secs
        self.seed = seed
        self.infogram_algorithm = infogram_algorithm
        self.infogram_algorithm_params = infogram_algorithm_params
        self.model_algorithm = model_algorithm
        self.model_algorithm_params = model_algorithm_params
        self.sensitive_attributes = sensitive_attributes
        self.conditional_info_threshold = conditional_info_threshold
        self.varimp_threshold = varimp_threshold
        self.data_fraction = data_fraction
        self.parallelism = parallelism
        self.ntop = ntop
        self.pval = pval

    @property
    def key(self):
        return self._id
