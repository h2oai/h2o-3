def python_vignette():
    from tests import pyunit_utils
    story1 = [pyunit_utils.locate("Python_Vignette_code_examples/python_add_common_column_to_orig_dataframe.py")]
    story2 = [pyunit_utils.locate("Python_Vignette_code_examples/python_apply_funct_to_columns.py")]
    story3 = [pyunit_utils.locate("Python_Vignette_code_examples/python_apply_funct_to_rows.py")]

    approved_py_code_examples = story1+story2+story3

    pybooklet_utils.check_code_examples_in_dir(approved_py_code_examples,
                                               pyunit_utils.locate("Python_Vignette_code_examples"))

    pybooklet_utils.check_story("story1",story1)
    pybooklet_utils.check_story("story2",story2)
    pybooklet_utils.check_story("story3",story3)

python_vignette()