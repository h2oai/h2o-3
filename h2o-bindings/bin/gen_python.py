#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import unicode_literals
from copy import deepcopy
from functools import partial
from inspect import getsource
import sys

import bindings as bi
from custom import get_customizations_for, reformat_block

PY3 = sys.version_info[0] == 3
str_type = str if PY3 else (str, unicode)
get_customizations_for = partial(get_customizations_for, 'python')


def get_customizations_or_defaults_for(algo, prop, default=None):
    return get_customizations_for(algo, prop, get_customizations_for('defaults', prop, default))


def code_as_str(code):
    if code is None:
        return None
    if isinstance(code, str):
        return code
    if callable(code):
        return '\n'.join(getsource(code).splitlines()[1:])
    raise AssertionError("`code` param should be a string or a function definition")


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
        self.types["BlendingParams"] = "dict"
        self.types["StringPair"] = "tuple"
        self.types["KeyValue"] = "dict"
        self.make_array = lambda vtype: "dict" if vtype == "dict" else "[%s]" % vtype
        self.make_array2 = lambda vtype: "[[%s]]" % vtype
        self.make_map = lambda ktype, vtype: "{%s: %s}" % (ktype, vtype)
        self.make_key = lambda itype, schema: ("H2OFrame" if schema == "Key<Frame>"
                                               else "H2OEstimator" if schema == "Key<Model>"
                                               else "str")
        self.make_enum = lambda schema, values: ("Enum(%s)" % ", ".join(stringify(v) for v in values) if values
                                                 else schema)


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
        self.types["BlendingParams"] = "dict"
        self.types["StringPair"] = "tuple"
        self.types["KeyValue"] = "dict"
        self.make_array = lambda vtype: "dict" if vtype == "dict" else "List[%s]" % vtype
        self.make_array2 = lambda vtype: "List[List[%s]]" % vtype
        self.make_map = lambda ktype, vtype: "Dict[%s, %s]" % (ktype, vtype)
        self.make_key = lambda itype, schema: ("H2OFrame" if schema == "Key<Frame>"
                                               else "str")
        self.make_enum = lambda schema, values: ("Enum[%s]" % ", ".join(stringify(v) for v in values) if values
                                                 else schema)


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
    """
    Ideally we should be able to avoid logic specific to algos in this file.
    Instead, customizations are externalized in ./python/gen_{algo}.py files.
    Logic that is specific to python types (e.g. H2OFrame, enums as list...) should however stay here
    as the type translation is done in this file.
    """
    classname = algo_to_classname(algo)
    rest_api_version = get_customizations_for(algo, 'rest_api_version')
    extra_imports = get_customizations_for(algo, 'extensions.__imports__')
    class_doc = get_customizations_for(algo, 'doc.__class__')
    class_examples = get_customizations_for(algo, 'examples.__class__')
    class_init_validation = get_customizations_for(algo, 'extensions.__init__validation')
    class_init_setparams = get_customizations_for(algo, 'extensions.__init__setparams')
    class_extras = get_customizations_for(algo, 'extensions.__class__')
    module_extras = get_customizations_for(algo, 'extensions.__module__')

    update_param_defaults = get_customizations_for('defaults', 'update_param')
    update_param = get_customizations_for(algo, 'update_param')

    def extend_schema_params(param):
        pname = param.get('name')
        param = deepcopy(param)
        updates = None
        for update_fn in [update_param, update_param_defaults]:
            if callable(update_fn):
                updates = update_fn(pname, param)
            if updates is not None:
                param = updates
                break
        # return param if isinstance(param, (list, tuple)) else [param]  # always return array to support deprecated aliases
        return param

    extended_params = [extend_schema_params(p) for p in schema['parameters']]

    param_names = []
    for param in extended_params:
        pname = param.get('name')
        ptype = param.get('type')
        pvalues = param.get('values')
        pdefault = param.get('default_value')

        assert (ptype[:4] == 'enum') == bool(pvalues), "Values are expected for enum types only"
        if pvalues:
            enum_values = [normalize_enum_constant(p) for p in pvalues]
            if pdefault:
                pdefault = normalize_enum_constant(pdefault)
        else:
            enum_values = None

        if pname in reserved_words:
            pname += "_"
        param_names.append(pname)
        param['pname'] = pname
        param['default_value'] = pdefault
        param['ptype'] = translate_type_for_check(ptype, enum_values)
        param['dtype'] = translate_type_for_doc(ptype, enum_values)

    yield "#!/usr/bin/env python"
    yield "# -*- encoding: utf-8 -*-"
    yield "#"
    yield "# This file is auto-generated by h2o-3/h2o-bindings/bin/gen_python.py"
    yield "# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)"
    yield "#"
    yield "from __future__ import absolute_import, division, print_function, unicode_literals"
    yield ""
    if extra_imports:
        yield reformat_block(extra_imports)
    yield "from h2o.estimators.estimator_base import H2OEstimator"
    yield "from h2o.exceptions import H2OValueError"
    yield "from h2o.frame import H2OFrame"
    yield "from h2o.utils.typechecks import assert_is_type, Enum, numeric"
    yield ""
    yield ""
    yield "class %s(H2OEstimator):" % classname
    yield '    """'
    yield "    " + schema["algo_full_name"]
    yield ""
    if class_doc:
        yield reformat_block(class_doc, 4)
    if class_examples:
        yield ""
        yield "    :examples:"
        yield ""
        yield reformat_block(class_examples, 4)
    yield '    """'
    yield ""
    yield '    algo = "%s"' % algo
    yield "    param_names = {%s}" % bi.wrap(", ".join('"%s"' % p for p in param_names),
                                             indent=(" " * 19), indent_first=False)
    yield ""
    yield "    def __init__(self, **kwargs):"
    # TODO: generate __init__ docstring with all params (also generate exact signature to support auto-completion)
    yield "        super(%s, self).__init__()" % classname
    yield "        self._parms = {}"
    if class_init_validation:
        yield reformat_block(class_init_validation, 8)
    yield "        for pname, pvalue in kwargs.items():"
    yield "            if pname == 'model_id':"
    yield "                self._id = pvalue"
    yield '                self._parms["model_id"] = pvalue'
    if class_init_setparams:
        yield reformat_block(class_init_setparams, 12)
    yield "            elif pname in self.param_names:"
    yield "                # Using setattr(...) will invoke type-checking of the arguments"
    yield "                setattr(self, pname, pvalue)"
    yield "            else:"
    yield '                raise H2OValueError("Unknown parameter %s = %r" % (pname, pvalue))'
    if rest_api_version:
        yield '        self._parms["_rest_version"] = %s' % rest_api_version
    yield ""
    for param in extended_params:
        pname = param.get('pname')
        if pname == "model_id":
            continue  # The getter is already defined in ModelBase

        sname = pname[:-1] if pname[-1] == '_' else pname
        ptype = param.get('ptype')
        dtype = param.get('dtype')
        pdefault = param.get('default_value')

        if dtype.startswith("Enum"):
            vals = dtype[5:-1].split(", ")
            property_doc = "One of: " + ", ".join("``%s``" % v for v in vals)
        else:
            property_doc = "Type: ``%s``" % dtype
        property_doc += ("." if pdefault is None else "  (default: ``%s``)." % stringify(pdefault))

        deprecated = pname in get_customizations_for(algo, 'deprecated', [])
        yield "    @property"
        yield "    def %s(self):" % pname
        yield '        """'
        yield bi.wrap("%s%s" % ("[Deprecated] " if deprecated else "", param.get('help')), indent=8*' ')  # we need to wrap only for text coming from server
        yield ""
        yield bi.wrap(property_doc, indent=8*' ')
        custom_property_doc = get_customizations_for(algo, "doc.{}".format(pname))
        if custom_property_doc:
            yield ""
            yield reformat_block(custom_property_doc, 8)
        property_examples = get_customizations_for(algo, "examples.{}".format(pname))
        if property_examples:
            yield ""
            yield "        :examples:"
            yield ""
            yield reformat_block(property_examples, 8)
        yield '        """'
        property_getter = get_customizations_for(algo, "overrides.{}.getter".format(pname))  # check gen_stackedensemble.py for an example
        if property_getter:
            yield reformat_block(property_getter.format(**locals()), 8)
        else:
            yield "        return self._parms.get(\"%s\")" % sname

        yield ""
        yield "    @%s.setter" % pname
        yield "    def %s(self, %s):" % (pname, pname)
        property_setter = get_customizations_for(algo, "overrides.{}.setter".format(pname))  # check gen_stackedensemble.py for an example
        if property_setter:
            yield reformat_block(property_setter.format(**locals()), 8)
        else:
            # special types validation
            if ptype == "H2OEstimator":
                yield "        assert_is_type(%s, None, str, %s)" % (pname, ptype)
            elif ptype == "H2OFrame":
                yield "        self._parms[\"%s\"] = H2OFrame._validate(%s, '%s')" % (sname, pname, pname)
            else:
                # default validation
                yield "        assert_is_type(%s, None, %s)" % (pname, ptype)
            if ptype != "H2OFrame":
                # default assignment
                yield "        self._parms[\"%s\"] = %s" % (sname, pname)
        yield ""
        yield ""
    if class_extras:
        yield reformat_block(code_as_str(class_extras), 4)
    if module_extras:
        yield ""
        yield reformat_block(code_as_str(module_extras))


def algo_to_classname(algo):
    if algo == "coxph": return "H2OCoxProportionalHazardsEstimator"
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
    if algo == "psvm": return "H2OSupportVectorMachineEstimator"
    if algo == "targetencoder": return "H2OTargetEncoderEstimator"
    return "H2O" + algo.capitalize() + "Estimator"


def gen_init(modules):
    yield "#!/usr/bin/env python"
    yield "# -*- encoding: utf-8 -*-"
    yield "#"
    yield "# This file is auto-generated by h2o-3/h2o-bindings/bin/gen_python.py"
    yield "# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)"
    yield "#"
    yield "import inspect"
    yield "import sys"
    yield ""
    module_strs = []
    # imports estimators
    for full_module, module, clz, category in sorted(modules):
        if module in ["grid", "automl"]:
            continue
        module_strs.append('"%s"' % clz)
        yield "from .%s import %s" % (module, clz)
    # global methods for h2o.estimators module
    yield """

module = sys.modules[__name__]

def _algo_for_estimator_(shortname, cls):
    if shortname == 'H2OAutoEncoderEstimator':
        return 'autoencoder'
    return cls.algo

_estimator_cls_by_algo_ = {_algo_for_estimator_(name, cls): cls
                           for name, cls in inspect.getmembers(module, inspect.isclass)
                           if hasattr(cls, 'algo')}

def create_estimator(algo, **params):
    if algo not in _estimator_cls_by_algo_:
        raise ValueError("Unknown algo type: " + algo)
    return _estimator_cls_by_algo_[algo](**params)

"""
    # auto-exports
    yield "__all__ = ("
    yield bi.wrap('"create_estimator",', indent=" "*4)
    yield bi.wrap(", ".join(module_strs), indent=" "*4)
    yield ")"


def gen_models_docs(modules):
    yield ".. This file is autogenerated from gen_python.py, DO NOT MODIFY"
    yield ":tocdepth: 3"
    yield ""
    yield "Modeling In H2O"
    yield "==============="
    modules_with_globals = ['automl']
    for cat in ["Supervised", "Unsupervised", "Miscellaneous"]:
        yield ""
        yield cat
        yield "+" * len(cat)
        yield ""
        for full_module, module, clz, category in sorted(modules):
            if category != cat: continue
            # doc for module
            if module in modules_with_globals:
                yield ":mod:`%s`" % module
                yield "-" * (7 + len(module))
                yield ".. automodule:: %s" % full_module
                yield "    :members:"
                yield "    :exclude-members: %s" % clz
                yield ""
            # doc for class
            full_clz = '.'.join([full_module, clz])
            yield ":mod:`%s`" % clz
            yield "-" * (7 + len(clz))
            yield ".. autoclass:: %s" % full_clz
            yield "    :show-inheritance:"
            yield "    :members:"
            yield ""


# ----------------------------------------------------------------------------------------------------------------------
#   MAIN:
# ----------------------------------------------------------------------------------------------------------------------
def main():
    bi.init("Python", "../../../h2o-py/h2o/estimators", clear_dir=False)

    modules = [("h2o.estimators.deeplearning", "deeplearning", "H2OAutoEncoderEstimator", "Unsupervised"),
               ("h2o.estimators.estimator_base", "estimator_base", "H2OEstimator", "Miscellaneous"),
               ("h2o.grid", "grid", "H2OGridSearch", "Miscellaneous"),
               ("h2o.automl", "automl", "H2OAutoML", "Miscellaneous")]
    builders = bi.model_builders().items()
    algo_to_module = dict(
        drf="random_forest",
        naivebayes="naive_bayes",
        isolationforest="isolation_forest"
    )
    algo_to_category = dict(
        svd="Miscellaneous",
        word2vec="Miscellaneous"
    )
    for name, mb in builders:
        module = name
        if name in algo_to_module:
            module = algo_to_module[name]
        bi.vprint("Generating model: " + name)
        bi.write_to_file("%s.py" % module, gen_module(mb, name))
        category = algo_to_category[name] if name in algo_to_category \
            else "Supervised" if mb["supervised"] \
            else "Unsupervised"
        full_module = '.'.join(["h2o.estimators", module])
        modules.append((full_module, module, algo_to_classname(name), category))

    bi.write_to_file("__init__.py", gen_init(modules))
    bi.write_to_file("../../docs/modeling.rst", gen_models_docs(modules))

    type_adapter1.vprint_translation_map()


if __name__ == "__main__":
    main()
