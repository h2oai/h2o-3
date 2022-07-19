import os
import sys

sys.path.insert(1, "../../")

# Output checks common for multiple algorithms


def compare_output(original, generic, strip_part, algo_name, generic_algo_name):
    original = original[original.find(strip_part):].replace(algo_name, '').strip()
    generic = generic[generic.find(strip_part):].replace(generic_algo_name, '').strip()
    assert generic == original, "expected:\n%s\n\nbut got instead generic:\n%s" % (original, generic)
    # print("expected:\n%s\n\nand got generic:\n%s" % (original, generic))


def compare_params(original, generic):
    original_params = original.params
    generic_params = generic.params

    assert original is not None
    assert generic_params is not None

    assert len(original_params) == len(generic_params) - 2  # Two more in Generic: _model_key and model_path

    for param_name in original_params:
        if param_name == "model_id":
            continue
        generic_param = generic_params[param_name]
        original_param = original_params[param_name]
        if param_name == "ignored_columns":
            assert generic_param == original_param
        assert generic_param is not None
        assert original_param is not None
