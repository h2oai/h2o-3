#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import unicode_literals
import bindings as bi
import inspect, os, sys
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
    """
    Ideally we should be able to avoid logic specific to algos in this file.
    Instead, customizations are externalized in ./python/gen_{algo}.py files.
    Logic that is specific to python types (e.g. H2OFrame, enums as list...) should however stay here
    as the type translation is done in this file.
    """
    classname = algo_to_classname(algo)
    extra_imports = get_customizations_for(algo, 'imports')
    class_doc = get_customizations_for(algo, 'class_doc')
    class_examples = get_customizations_for(algo, 'class_examples')
    class_init_validation = get_customizations_for(algo, 'class_init_validation')
    class_init_setparams = get_customizations_for(algo, 'class_init_setparams')
    class_init_extra = get_customizations_for(algo, 'class_init_extra')
    class_extras = get_customizations_for(algo, 'class_extras')
    module_extras = get_customizations_for(algo, 'module_extras')
    properties = get_customizations_for(algo, 'properties', {})

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
        update_param = get_customizations_for(algo, 'update_param')
        if callable(update_param):
            param, enum_values = update_param(pname, param, enum_values)
        else:
            # could be done with update_param in each algo, cf. example in python/gen_isolationforest.py or python/gen_drf.py
            # but let's leave it here for now: besides I suspect that this is not fully up-to-date.
            if (pname==u'distribution') and (not(algo==u'glm') and not(algo==u'gbm')):    # quasibinomial only in glm, gbm
                enum_values.remove(u'quasibinomial')
            if (pname==u'distribution') and (not(algo==u'glm')):    # ordinal only in glm
                enum_values.remove(u'ordinal')
            if (pname==u'distribution') and (not(algo==u'gbm')):    # custom only in gbm
                enum_values.remove(u'custom')
            if (pname==u'stopping_metric') and (not(algo==u'isolationforest')):    # anomaly_score only in Isolation Forest
                enum_values.remove(u'anomaly_score')

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
    yield "from h2o.utils.typechecks import assert_is_type, Enum, numeric"
    if extra_imports:
        yield reindent_block(extra_imports, 0)
    yield ""
    yield ""
    yield "class %s(H2OEstimator):" % classname
    yield '    """'
    yield "    " + schema["algo_full_name"]
    yield ""
    if class_doc:
        yield "    %s" % reindent_block(class_doc, 4)
    if class_examples:
        yield ""
        yield "    :examples:"
        yield ""
        yield "    %s" % reindent_block(class_examples, 4)
    yield '    """'
    yield ""
    yield '    algo = "%s"' % algo
    yield ""
    yield "    def __init__(self, **kwargs):"
    yield "        super(%s, self).__init__()" % classname
    yield "        self._parms = {}"
    yield "        names_list = {%s}" % bi.wrap(", ".join('"%s"' % p for p in param_names),
                                                indent=(" " * 22), indent_first=False)
    if class_init_validation:
        yield "        %s" % reindent_block(class_init_validation, 8)
    yield '        if "Lambda" in kwargs: kwargs["lambda_"] = kwargs.pop("Lambda")'
    yield "        for pname, pvalue in kwargs.items():"
    yield "            if pname == 'model_id':"
    yield "                self._id = pvalue"
    yield '                self._parms["model_id"] = pvalue'
    if class_init_setparams:
        yield "            %s" % reindent_block(class_init_setparams, 12)
    yield "            elif pname in names_list:"
    yield "                # Using setattr(...) will invoke type-checking of the arguments"
    yield "                setattr(self, pname, pvalue)"
    yield "            else:"
    yield '                raise H2OValueError("Unknown parameter %s = %r" % (pname, pvalue))'
    if class_init_extra:
        yield "        " + reindent_block(class_init_extra, 8)
    yield ""
    for param in schema["parameters"]:
        pname = param["pname"]
        ptype = param["ptype"]
        if pname == "model_id": continue  # The getter is already defined in ModelBase
        sname = pname[:-1] if pname[-1] == '_' else pname

        if param["dtype"].startswith("Enum"):
            vals = param["dtype"][5:-1].split(", ")
            property_doc = "One of: " + ", ".join("``%s``" % v for v in vals)
        else:
            property_doc = "Type: ``%s``" % param["dtype"]
        if param["default_value"] is None:
            property_doc += "."
        else:
            property_doc += "  (default: ``%s``)." % stringify(param["default_value"])

        deprecated = pname in get_customizations_for(algo, 'deprecated_attributes', [])
        yield "    @property"
        yield "    def %s(self):" % pname
        yield '        """'
        yield "        %s%s" % ("[Deprecated] " if deprecated else "", bi.wrap(param["help"], indent=(" " * 8), indent_first=False))
        yield ""
        yield "        %s" % bi.wrap(property_doc, indent=(" " * 8), indent_first=False)
        property_doc = get_customizations_for(algo, "property_"+pname+"_doc", )
        if property_doc:
            yield ""
            yield "        %s" % reindent_block(property_doc, 8)
        property_examples = get_customizations_for(algo, "property_"+pname+"_examples")
        if property_examples:
            yield ""
            yield "        :examples:"
            yield ""
            yield "        %s" % reindent_block(property_examples, 8)
        yield '        """'
        property_getter = properties.get(pname, {}).get('getter')  # check gen_stackedensemble.py for an example
        if property_getter:
            yield "        %s" % reindent_block(property_getter.format(**locals()), 8)
        else:
            yield "        return self._parms.get(\"%s\")" % sname

        yield ""
        yield "    @%s.setter" % pname
        yield "    def %s(self, %s):" % (pname, pname)
        property_setter = properties.get(pname, {}).get('setter')  # check gen_stackedensemble.py for an example
        if property_setter:
            yield "        %s" % reindent_block(property_setter.format(**locals()), 8)
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
        yield "    " + reindent_block(class_extras, 4)
    if module_extras:
        yield ""
        yield reindent_block(module_extras, 0)


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
    return "H2O" + algo.capitalize() + "Estimator"


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
    yield bi.wrap(", ".join(module_strs), indent=" "*4)
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


_gen_customizations = dict()


def get_customizations_for(algo, property=None, default=None):
    if algo not in _gen_customizations:
        custom_file = os.path.join(os.path.dirname(__file__), 'custom', 'python', 'gen_'+algo.lower()+'.py')
        customizations = dict()
        if os.path.isfile(custom_file):
            with open(custom_file) as f:
                exec(f.read(), customizations)
        _gen_customizations.update({algo: customizations})

    customizations = _gen_customizations[algo]
    if property is None:
        return customizations
    else:
        return customizations.get(property, default)

# ----------------------------------------------------------------------------------------------------------------------
#   MAIN:
# ----------------------------------------------------------------------------------------------------------------------
def main():
    bi.init("Python", "../../../h2o-py/h2o/estimators", clear_dir=False)

    modules = [("deeplearning", "H2OAutoEncoderEstimator", "Unsupervised"),
               ("estimator_base", "H2OEstimator", "Miscellaneous"),
               ("grid_search", "H2OGridSearch", "Miscellaneous"),
               ("automl", "H2OAutoML", "Miscellaneous")]
    builders = bi.model_builders().items()
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
