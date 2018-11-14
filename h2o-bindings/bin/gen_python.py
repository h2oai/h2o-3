#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import unicode_literals
import bindings as bi
import sys
PY3 = sys.version_info[0] == 3
str_type = str if PY3 else (str, unicode)


# We specify these not as real types, but as parameter annotations in the docstrings
class PythonTypeTranslatorForCheck(bi.TypeTranslator):
    def __init__(self):
        super(PythonTypeTranslatorForCheck, self).__init__()
        self.types["byte"] = "int"
        self.types["short"] = "int"
        self.types["long"] = "int"
        self.types["double"] = "numeric"
        self.types["string"] = "str"
        self.types["boolean"] = "bool"
        self.types["Polymorphic"] = "object"
        self.types["Object"] = "object"
        self.types["VecSpecifier"] = "str"
        self.types["StringPair"] = "tuple"
        self.types["KeyValue"] = "dict"
        self.make_array = lambda vtype: "dict" if vtype == "dict" else "[%s]" % vtype
        self.make_array2 = lambda vtype: "[[%s]]" % vtype
        self.make_map = lambda ktype, vtype: "{%s: %s}" % (ktype, vtype)
        self.make_key = lambda itype, schema: "H2OFrame" if schema == "Key<Frame>" else "str"
        self.make_enum = lambda schema, values: \
            "Enum(%s)" % ", ".join(stringify(v) for v in values) if values else schema

type_adapter1 = PythonTypeTranslatorForCheck()
def translate_type_for_check(h2o_type, values=None):
    schema = h2o_type.replace("[]", "")
    return type_adapter1.translate(h2o_type, schema, values)


# We specify these not as real types, but as parameter annotations in the docstrings
class PythonTypeTranslatorForDoc(bi.TypeTranslator):
    def __init__(self):
        super(PythonTypeTranslatorForDoc, self).__init__()
        self.types["byte"] = "int"
        self.types["short"] = "int"
        self.types["long"] = "int"
        self.types["double"] = "float"
        self.types["string"] = "str"
        self.types["boolean"] = "bool"
        self.types["Polymorphic"] = "object"
        self.types["Object"] = "object"
        self.types["VecSpecifier"] = "str"
        self.types["StringPair"] = "tuple"
        self.types["KeyValue"] = "dict"
        self.make_array = lambda vtype: "dict" if vtype == "dict" else "List[%s]" % vtype
        self.make_array2 = lambda vtype: "List[List[%s]]" % vtype
        self.make_map = lambda ktype, vtype: "Dict[%s, %s]" % (ktype, vtype)
        self.make_key = lambda itype, schema: "H2OFrame" if schema == "Key<Frame>" else "str"
        self.make_enum = lambda schema, values: \
            "Enum[%s]" % ", ".join(stringify(v) for v in values) if values else schema

type_adapter2 = PythonTypeTranslatorForDoc()
def translate_type_for_doc(h2o_type, values=None):
    schema = h2o_type.replace("[]", "")
    return type_adapter2.translate(h2o_type, schema, values)


def normalize_enum_constant(s):
    """Return enum constant `s` converted to a canonical snake-case."""
    if s.islower(): return s
    if s.isupper(): return s.lower()
    return "".join(ch if ch.islower() else "_" + ch.lower() for ch in s).strip("_")

def stringify(v):
    if v == "Infinity": return u'∞'
    if isinstance(v, str_type): return '"%s"' % v
    if isinstance(v, float): return '%.10g' % v
    return str(v)

def reindent_block(string, new_indent):
    if not string: return ""
    add_indent = " " * new_indent
    lines = string.split("\n")
    if len(lines) == 1:
        return lines[0].strip()
    line0_indent = len(lines[0]) - len(lines[0].lstrip())
    line1_indent = len(lines[1]) - len(lines[1].lstrip())
    remove_indent = max(line0_indent, line1_indent)
    out = ""
    for line in lines:
        dedented_line = line.lstrip()
        if dedented_line:
            extra_indent = " " * (len(line) - len(dedented_line) - remove_indent)
            out += add_indent + extra_indent + dedented_line + "\n"
        else:
            out += "\n"
    return out.strip()


# This is the list of all reserved keywords in Python. It is a syntax error to use any of them as an object's property.
# Currently we have only "lambda" in GLM as a violator, but keeping the whole list here just to be future-proof.
# For all such violating properties, we name the accessor with an underscore at the end (eg. lambda_), but at the same
# time keep the actual name in self.params (i.e. model.lambda_ ≡ model.params["lambda"]).
reserved_words = {
    "and", "del", "from", "not", "while", "as", "elif", "global", "or", "with", "assert", "else", "if", "pass",
    "yield", "break", "except", "import", "print", "class", "exec", "in", "raise", "continue", "finally", "is",
    "return", "def", "for", "lambda", "try"
}


# ----------------------------------------------------------------------------------------------------------------------
#   Generate per-model classes
# ----------------------------------------------------------------------------------------------------------------------
def gen_module(schema, algo):
    classname = algo_to_classname(algo)
    extra_imports = extra_imports_for(algo)
    help_preamble = help_preamble_for(algo)
    help_epilogue = help_epilogue_for(algo)
    init_extra = init_extra_for(algo)
    class_extra = class_extra_for(algo)
    module_extra = module_extra_for(algo)

    param_names = []
    for param in schema["parameters"]:
        assert (param["type"][:4] == "enum") == bool(param["values"]), "Values are expected for enum types only"
        if param["values"]:
            enum_values = [normalize_enum_constant(p) for p in param["values"]]
            if param["default_value"]:
                param["default_value"] = normalize_enum_constant(param["default_value"])
        else:
            enum_values = None
        pname = param["name"]
        if (pname==u'distribution') and (not(algo==u'glm') and not(algo==u'gbm')):    # quasibinomial only in glm, gbm
            enum_values.remove(u'quasibinomial')
        if (pname==u'distribution') and (not(algo==u'glm')):    # ordinal only in glm
            enum_values.remove(u'ordinal')
        if pname in reserved_words: pname += "_"
        param_names.append(pname)
        param["pname"] = pname
        param["ptype"] = translate_type_for_check(param["type"], enum_values)
        param["dtype"] = translate_type_for_doc(param["type"], enum_values)

    yield "#!/usr/bin/env python"
    yield "# -*- encoding: utf-8 -*-"
    yield "#"
    yield "# This file is auto-generated by h2o-3/h2o-bindings/bin/gen_python.py"
    yield "# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)"
    yield "#"
    yield "from __future__ import absolute_import, division, print_function, unicode_literals"
    yield ""
    yield "from h2o.estimators.estimator_base import H2OEstimator"
    yield "from h2o.exceptions import H2OValueError"
    yield "from h2o.frame import H2OFrame"
    if classname == "H2OStackedEnsembleEstimator":
        yield "from h2o.utils.typechecks import assert_is_type, Enum, numeric, is_type"
        yield "import json"
        yield "import ast"
    else:
        yield "from h2o.utils.typechecks import assert_is_type, Enum, numeric"
    if extra_imports:
        yield reindent_block(extra_imports, 0) + ""
    yield ""
    yield ""
    yield "class %s(H2OEstimator):" % classname
    yield "    \"\"\""
    yield "    " + schema["algo_full_name"]
    yield ""
    if help_preamble:
        yield "    %s" % reindent_block(help_preamble, 4)
    if help_epilogue:
        yield ""
        yield "    %s" % reindent_block(help_epilogue, 4)
    yield "    \"\"\""
    yield ""
    yield '    algo = "%s"' % algo
    yield ""
    yield "    def __init__(self, **kwargs):"
    yield "        super(%s, self).__init__()" % classname
    yield "        self._parms = {}"
    yield "        names_list = {%s}" % bi.wrap(", ".join('"%s"' % p for p in param_names),
                                                indent=(" " * 22), indent_first=False)
    yield '        if "Lambda" in kwargs: kwargs["lambda_"] = kwargs.pop("Lambda")'
    yield "        for pname, pvalue in kwargs.items():"
    yield "            if pname == 'model_id':"
    yield "                self._id = pvalue"
    yield '                self._parms["model_id"] = pvalue'
    if algo == 'word2vec':
        yield '            elif pname == \'pre_trained\':'
        yield '                setattr(self, pname, pvalue)'
        yield '                self._determine_vec_size();'
        yield '                setattr(self, \'vec_size\', self.vec_size)'
    yield "            elif pname in names_list:"
    yield "                # Using setattr(...) will invoke type-checking of the arguments"
    yield "                setattr(self, pname, pvalue)"
    yield "            else:"
    yield '                raise H2OValueError("Unknown parameter %s = %r" % (pname, pvalue))'
    if init_extra:
        yield "        " + reindent_block(init_extra, 8)
    yield ""
    for param in schema["parameters"]:
        pname = param["pname"]
        ptype = param["ptype"]
        if pname == "model_id": continue  # The getter is already defined in ModelBase
        sname = pname[:-1] if pname[-1] == '_' else pname

        if param["dtype"].startswith("Enum"):
            vals = param["dtype"][5:-1].split(", ")
            extrahelp = "One of: " + ", ".join("``%s``" % v for v in vals)
        else:
            if pname == "metalearner_params":
                extrahelp = "Type: ``dict``"
            else:
                extrahelp = "Type: ``%s``" % param["dtype"]
        if param["default_value"] is None:
            extrahelp += "."
        else:
            if pname == "metalearner_params":
                extrahelp += "  (default: ``None``)."
            else:
                extrahelp += "  (default: ``%s``)." % stringify(param["default_value"])

        if (pname == "offset_column" or pname == "distribution") and algo == "drf":
            yield "    @property"
            yield "    def %s(self):" % pname
            yield '        """'
            yield "        [Deprecated] %s" % bi.wrap(param["help"], indent=(" " * 8), indent_first=False)
            yield ""
            yield "        %s" % bi.wrap(extrahelp, indent=(" " * 8), indent_first=False)
        else:
            yield "    @property"
            yield "    def %s(self):" % pname
            yield '        """'
            yield "        %s" % bi.wrap(param["help"], indent=(" " * 8), indent_first=False)
            yield ""
            yield "        %s" % bi.wrap(extrahelp, indent=(" " * 8), indent_first=False)
        if pname == "metalearner_params":
            yield "        Example: metalearner_gbm_params = {'max_depth': 2, 'col_sample_rate': 0.3}"
        yield '        """'
        if pname != "metalearner_params":
            yield "        return self._parms.get(\"%s\")" % sname
        else:
            yield "        if self._parms.get(\"%s\") != None:" % sname
            yield "            metalearner_params_dict =  ast.literal_eval(self._parms.get(\"%s\"))" % sname
            yield "            for k in metalearner_params_dict:"
            yield "                if len(metalearner_params_dict[k]) == 1: #single parameter"
            yield "                    metalearner_params_dict[k] = metalearner_params_dict[k][0]"
            yield "            return metalearner_params_dict"
            yield "        else:"
            yield "            return self._parms.get(\"%s\")" % sname
        yield ""
        yield "    @%s.setter" % pname
        yield "    def %s(self, %s):" % (pname, pname)
        if pname in {"initial_weights", "initial_biases"}:
            yield "        assert_is_type(%s, None, [H2OFrame, None])" % pname
        elif pname in {"alpha", "lambda_"} and ptype == "[numeric]":
            # For `alpha` and `lambda` the server reports type float[], while in practice simple floats are also ok
            yield "        assert_is_type(%s, None, numeric, [numeric])" % pname
        elif pname in {"checkpoint", "pretrained_autoencoder"}:
            yield "        assert_is_type(%s, None, str, H2OEstimator)" % pname
        elif pname in {"base_models"}:
            yield "         if is_type(base_models,[H2OEstimator]):"
            yield      "            %s = [b.model_id for b in %s]" % (pname,pname)
            yield      "            self._parms[\"%s\"] = %s" % (sname, pname)
            yield "         else:"
            yield "            assert_is_type(%s, None, %s)" % (pname, ptype)
            yield "            self._parms[\"%s\"] = %s" % (sname, pname)
        elif pname in {"metalearner_params"}:
            yield "        assert_is_type(%s, None, %s)" % (pname, "dict")
            yield '        if %s is not None and %s != "":' % (pname, pname)
            yield "            for k in %s:" % (pname)
            yield '                if ("[" and "]") not in str(metalearner_params[k]):'
            yield "                    metalearner_params[k]=[metalearner_params[k]]"
            yield "            self._parms[\"%s\"] = str(json.dumps(%s))" % (sname, pname)
            yield "        else:"
            yield "            self._parms[\"%s\"] = None" % (sname)
        else:
            yield "        assert_is_type(%s, None, %s)" % (pname, ptype)
        if pname not in {"base_models", "metalearner_params"}:
            yield "        self._parms[\"%s\"] = %s" % (sname, pname)
        yield ""
        yield ""
    if class_extra:
        yield ""
        yield "    " + reindent_block(class_extra, 4)
    if module_extra:
        yield ""
        yield reindent_block(module_extra, 0)


def algo_to_classname(algo):
    if algo == "deeplearning": return "H2ODeepLearningEstimator"
    if algo == "deepwater": return "H2ODeepWaterEstimator"
    if algo == "xgboost": return "H2OXGBoostEstimator"
    if algo == "gbm": return "H2OGradientBoostingEstimator"
    if algo == "glm": return "H2OGeneralizedLinearEstimator"
    if algo == "glrm": return "H2OGeneralizedLowRankEstimator"
    if algo == "kmeans": return "H2OKMeansEstimator"
    if algo == "naivebayes": return "H2ONaiveBayesEstimator"
    if algo == "drf": return "H2ORandomForestEstimator"
    if algo == "svd": return "H2OSingularValueDecompositionEstimator"
    if algo == "pca": return "H2OPrincipalComponentAnalysisEstimator"
    if algo == "stackedensemble": return "H2OStackedEnsembleEstimator"
    if algo == "isolationforest": return "H2OIsolationForestEstimator"
    return "H2O" + algo.capitalize() + "Estimator"

def extra_imports_for(algo):
    if algo == "glm" or algo == "deepwater" or algo == "xgboost":
        return "import h2o"

def help_preamble_for(algo):
    if algo == "deeplearning":
        return """
            Build a Deep Neural Network model using CPUs
            Builds a feed-forward multilayer artificial neural network on an H2OFrame"""
    if algo == "deepwater":
        return """
            Build a Deep Learning model using multiple native GPU backends
            Builds a deep neural network on an H2OFrame containing various data sources"""
    if algo == "kmeans":
        return """Performs k-means clustering on an H2O dataset."""
    if algo == "glrm":
        return """Builds a generalized low rank model of a H2O dataset."""
    if algo == "glm":
        return """
            Fits a generalized linear model, specified by a response variable, a set of predictors, and a
            description of the error distribution."""
    if algo == "gbm":
        return """
            Builds gradient boosted trees on a parsed data set, for regression or classification.
            The default distribution function will guess the model type based on the response column type.
            Otherwise, the response column must be an enum for "bernoulli" or "multinomial", and numeric
            for all other distributions."""
    if algo == "xgboost":
        return """Builds a eXtreme Gradient Boosting model using the native XGBoost backend."""
    if algo == "naivebayes":
        return """
            The naive Bayes classifier assumes independence between predictor variables
            conditional on the response, and a Gaussian distribution of numeric predictors with
            mean and standard deviation computed from the training dataset. When building a naive
            Bayes classifier, every row in the training dataset that contains at least one NA will
            be skipped completely. If the test dataset has missing values, then those predictors
            are omitted in the probability calculation during prediction."""
    if algo == "stackedensemble":
        return """
            Builds a stacked ensemble (aka "super learner") machine learning method that uses two
            or more H2O learning algorithms to improve predictive performance. It is a loss-based
            supervised learning method that finds the optimal combination of a collection of prediction
            algorithms.This method supports regression and binary classification. """
    if algo == "isolationforest":
        return """
            Builds an Isolation Forest model. Isolation Forest algorithm samples the training frame
            and in each iteration builds a tree that partitions the space of the sample observations until
            it isolates each observation. Length of the path from root to a leaf node of the resulting tree
            is used to calculate the anomaly score. Anomalies are easier to isolate and their average
            tree path is expected to be shorter than paths of regular observations.
        """

def help_epilogue_for(algo):
    if algo == "deeplearning":
        return """Examples
                       --------
                         >>> import h2o
                         >>> from h2o.estimators.deeplearning import H2ODeepLearningEstimator
                         >>> h2o.connect()
                         >>> rows = [[1,2,3,4,0], [2,1,2,4,1], [2,1,4,2,1], [0,1,2,34,1], [2,3,4,1,0]] * 50
                         >>> fr = h2o.H2OFrame(rows)
                         >>> fr[4] = fr[4].asfactor()
                         >>> model = H2ODeepLearningEstimator()
                         >>> model.train(x=range(4), y=4, training_frame=fr)"""
    if algo == "stackedensemble":
        return """Examples
                       --------
                         >>> import h2o
                         >>> h2o.init()
                         >>> from h2o.estimators.random_forest import H2ORandomForestEstimator
                         >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
                         >>> from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
                         >>> col_types = ["numeric", "numeric", "numeric", "enum", "enum", "numeric", "numeric", "numeric", "numeric"]
                         >>> data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv", col_types=col_types)
                         >>> train, test = data.split_frame(ratios=[.8], seed=1)
                         >>> x = ["CAPSULE","GLEASON","RACE","DPROS","DCAPS","PSA","VOL"]
                         >>> y = "AGE"
                         >>> nfolds = 5
                         >>> my_gbm = H2OGradientBoostingEstimator(nfolds=nfolds, fold_assignment="Modulo", keep_cross_validation_predictions=True)
                         >>> my_gbm.train(x=x, y=y, training_frame=train)
                         >>> my_rf = H2ORandomForestEstimator(nfolds=nfolds, fold_assignment="Modulo", keep_cross_validation_predictions=True)
                         >>> my_rf.train(x=x, y=y, training_frame=train)
                         >>> stack = H2OStackedEnsembleEstimator(model_id="my_ensemble", training_frame=train, validation_frame=test, base_models=[my_gbm.model_id, my_rf.model_id])
                         >>> stack.train(x=x, y=y, training_frame=train, validation_frame=test)
                         >>> stack.model_performance()"""
    if algo == "glm":
        return """
            A subclass of :class:`ModelBase` is returned. The specific subclass depends on the machine learning task
            at hand (if it's binomial classification, then an H2OBinomialModel is returned, if it's regression then a
            H2ORegressionModel is returned). The default print-out of the models is shown, but further GLM-specific
            information can be queried out of the object. Upon completion of the GLM, the resulting object has
            coefficients, normalized coefficients, residual/null deviance, aic, and a host of model metrics including
            MSE, AUC (for logistic regression), degrees of freedom, and confusion matrices."""

def init_extra_for(algo):
    if algo == "deeplearning":
        return "if isinstance(self, H2OAutoEncoderEstimator): self._parms['autoencoder'] = True"
    if algo == "glrm":
        return """self._parms["_rest_version"] = 3"""
    if algo == "stackedensemble":
        return """self._parms["_rest_version"] = 99"""
    if algo == "aggregator":
        return """self._parms["_rest_version"] = 99"""

def class_extra_for(algo):
    if algo == "aggregator":
        # Add aggregated_frame to model
        return """
            @property
            def aggregated_frame(self):
                if (self._model_json is not None and
                    self._model_json.get("output", {}).get("output_frame", {}).get("name") is not None):
                    out_frame_name = self._model_json["output"]["output_frame"]["name"]
                    return H2OFrame.get_frame(out_frame_name)"""
    if algo == "glm":
        # Before we were replacing .lambda property with .Lambda. However that violates Python naming conventions for
        # variables, so now we prefer to map that property to .lambda_. The old name remains, for compatibility reasons.
        return """
            @property
            def Lambda(self):
                \"""DEPRECATED. Use ``self.lambda_`` instead\"""
                return self._parms["lambda"] if "lambda" in self._parms else None

            @Lambda.setter
            def Lambda(self, value):
                self._parms["lambda"] = value

            @staticmethod
            def getGLMRegularizationPath(model):
                \"\"\"
                Extract full regularization path explored during lambda search from glm model.

                :param model: source lambda search model
                \"\"\"
                x = h2o.api("GET /3/GetGLMRegPath", data={"model": model._model_json["model_id"]["name"]})
                ns = x.pop("coefficient_names")
                res = {
                    "lambdas": x["lambdas"],
                    "explained_deviance_train": x["explained_deviance_train"],
                    "explained_deviance_valid": x["explained_deviance_valid"],
                    "coefficients": [dict(zip(ns, y)) for y in x["coefficients"]],
                }
                if "coefficients_std" in x:
                    res["coefficients_std"] = [dict(zip(ns, y)) for y in x["coefficients_std"]]
                return res

            @staticmethod
            def makeGLMModel(model, coefs, threshold=.5):
                \"\"\"
                Create a custom GLM model using the given coefficients.

                Needs to be passed source model trained on the dataset to extract the dataset information from.

                :param model: source model, used for extracting dataset information
                :param coefs: dictionary containing model coefficients
                :param threshold: (optional, only for binomial) decision threshold used for classification
                \"\"\"
                model_json = h2o.api(
                    "POST /3/MakeGLMModel",
                    data={"model": model._model_json["model_id"]["name"],
                          "names": list(coefs.keys()),
                          "beta": list(coefs.values()),
                          "threshold": threshold}
                )
                m = H2OGeneralizedLinearEstimator()
                m._resolve_model(model_json["model_id"]["name"], model_json)
                return m"""

    elif algo == "deepwater":
        return """
        # Ask the H2O server whether a Deep Water model can be built (depends on availability of native backends)
        @staticmethod
        def available():
            \"\"\"Returns True if a deep water model can be built, or False otherwise.\"\"\"
            builder_json = h2o.api("GET /3/ModelBuilders", data={"algo": "deepwater"})
            visibility = builder_json["model_builders"]["deepwater"]["visibility"]
            if visibility == "Experimental":
                print("Cannot build a Deep Water model - no backend found.")
                return False
            else:
                return True
        """

    elif algo == "xgboost":
        return """
        # Ask the H2O server whether a XGBoost model can be built (depends on availability of native backends)
        @staticmethod
        def available():
            \"\"\"
            Returns True if a XGBoost model can be built, or False otherwise.
            \"\"\"
            if "XGBoost" not in h2o.cluster().list_core_extensions():
                print("Cannot build an XGBoost model - no backend found.")
                return False
            else:
                return True
        """
    elif algo == "stackedensemble":
        return """
        # Print the metalearner of an H2OStackedEnsembleEstimator.
        def metalearner(self):
            model = self._model_json["output"]
            if "metalearner" in model and model["metalearner"] is not None:
                return model["metalearner"]
            print("No metalearner for this model")  
              
        #Fetch the levelone_frame_id for an H2OStackedEnsembleEstimator.   
        def levelone_frame_id(self):
            model = self._model_json["output"]
            if "levelone_frame_id" in model and model["levelone_frame_id"] is not None:
                return model["levelone_frame_id"]
            print("No levelone_frame_id for this model")         
        """
    elif algo == "word2vec":
        return """
        def _requires_training_frame(self):
            \"\"\"
            Determines if Word2Vec algorithm requires a training frame.
            :return: False.
            \"\"\"
            return False

        @staticmethod
        def from_external(external=H2OFrame):
            \"\"\"
            Creates new H2OWord2vecEstimator based on an external model.
            :param external: H2OFrame with an external model
            :return: H2OWord2vecEstimator instance representing the external model
            \"\"\"
            w2v_model = H2OWord2vecEstimator(pre_trained=external)
            w2v_model.train()
            return w2v_model

        def _determine_vec_size(self):
            \"\"\"
            Determines vec_size for a pre-trained model after basic model verification.
            \"\"\"
            first_column = self.pre_trained.types[self.pre_trained.columns[0]]

            if first_column != 'string':
                raise H2OValueError("First column of given pre_trained model %s is required to be a String",
                                    self.pre_trained.frame_id)

            if list(self.pre_trained.types.values()).count('string') > 1:
                raise H2OValueError("There are multiple columns in given pre_trained model %s with a String type.",
                                    self.pre_trained.frame_id)

            self.vec_size = self.pre_trained.dim[1] - 1;
        """

def module_extra_for(algo):
    if algo == "deeplearning":
        return """
            class H2OAutoEncoderEstimator(H2ODeepLearningEstimator):
                \"\"\"
                Examples
                --------
                  >>> import h2o as ml
                  >>> from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
                  >>> ml.init()
                  >>> rows = [[1,2,3,4,0]*50, [2,1,2,4,1]*50, [2,1,4,2,1]*50, [0,1,2,34,1]*50, [2,3,4,1,0]*50]
                  >>> fr = ml.H2OFrame(rows)
                  >>> fr[4] = fr[4].asfactor()
                  >>> model = H2OAutoEncoderEstimator()
                  >>> model.train(x=range(4), training_frame=fr)
                \"\"\"
                pass"""


def gen_init(modules):
    yield "#!/usr/bin/env python"
    yield "# -*- encoding: utf-8 -*-"
    yield "#"
    yield "# This file is auto-generated by h2o-3/h2o-bindings/bin/gen_python.py"
    yield "# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)"
    yield "#"
    module_strs = []
    for module, clz, category in sorted(modules):
        if clz == "H2OGridSearch": continue
        module_strs.append('"%s"' % clz)
        if clz == "H2OAutoML": continue
        module_strs.append('"%s"' % clz)
        yield "from .%s import %s" % (module, clz)
    yield ""
    yield "__all__ = ("
    yield bi.wrap(", ".join(module_strs), indent="    ")
    yield ")"


def gen_models_docs(modules):
    yield ".. This file is autogenerated from gen_python.py, DO NOT MODIFY"
    yield ":tocdepth: 3"
    yield ""
    yield "Modeling In H2O"
    yield "==============="
    for cat in ["Supervised", "Unsupervised", "Miscellaneous"]:
        yield ""
        yield cat
        yield "+" * len(cat)
        yield ""
        for module, clz, category in sorted(modules):
            if category != cat: continue
            fullmodule = "h2o.estimators.%s.%s" % (module, clz)
            if clz == "H2OGridSearch":
                fullmodule = "h2o.grid.grid_search.H2OGridSearch"
            if clz == "H2OAutoML":
                fullmodule = "h2o.automl.autoh2o.H2OAutoML"
            yield ":mod:`%s`" % clz
            yield "-" * (7 + len(clz))
            yield ".. autoclass:: %s" % fullmodule
            yield "    :show-inheritance:"
            yield "    :members:"
            yield ""



# ----------------------------------------------------------------------------------------------------------------------
#   MAIN:
# ----------------------------------------------------------------------------------------------------------------------
def main():
    bi.init("Python", "../../../h2o-py/h2o/estimators", clear_dir=False)

    modules = [("deeplearning", "H2OAutoEncoderEstimator", "Unsupervised"),
               ("estimator_base", "H2OEstimator", "Miscellaneous"),
               ("grid_search", "H2OGridSearch", "Miscellaneous"),
               ("automl", "H2OAutoML", "Miscellaneous")]
    builders = filter(lambda b: b[0] != 'coxph', bi.model_builders().items()) # CoxPH is not supported in Python yet
    for name, mb in builders:
        module = name
        if name == "drf": module = "random_forest"
        if name == "naivebayes": module = "naive_bayes"
        if name == "isolationforest": module = "isolation_forest"
        bi.vprint("Generating model: " + name)
        bi.write_to_file("%s.py" % module, gen_module(mb, name))
        category = "Supervised" if mb["supervised"] else "Unsupervised"
        if name in {"svd", "word2vec"}:
            category = "Miscellaneous"
        modules.append((module, algo_to_classname(name), category))

    bi.write_to_file("__init__.py", gen_init(modules))
    bi.write_to_file("../../docs/modeling.rst", gen_models_docs(modules))

    type_adapter1.vprint_translation_map()


if __name__ == "__main__":
    main()
