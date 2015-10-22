def python_vignette():
    from tests import pyunit_utils
    story1 = [pyunit_utils.locate("python/ipython_dataprep_input.py")]
    story2 = [pyunit_utils.locate("python/ipython_machinelearning_input.py")]

    approved_py_code_examples = story1+story2

    pybooklet_utils.check_code_examples_in_dir(approved_py_code_examples,
                                               pyunit_utils.locate("python"))

    pybooklet_utils.check_story("story1",story1)
    pybooklet_utils.check_story("story2",story2)

python_vignette()