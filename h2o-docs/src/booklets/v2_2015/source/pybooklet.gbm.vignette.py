def gbm_vignette():
    from tests import pyunit_utils
    story1 = [
        pyunit_utils.locate("GBM_Vignette_code_examples/gbm_uploadfile_example.py"),
        pyunit_utils.locate("GBM_Vignette_code_examples/gbm_examplerun.py"),
        pyunit_utils.locate("GBM_Vignette_code_examples/gbm_examplerun_stochastic.py"),
        pyunit_utils.locate("GBM_Vignette_code_examples/gbm_extractmodelparams.py"),
        pyunit_utils.locate("GBM_Vignette_code_examples/gbm_predict.py"),
        pyunit_utils.locate("GBM_Vignette_code_examples/gbm_gridsearch.py"),
        pyunit_utils.locate("GBM_Vignette_code_examples/gbm_gridsearch_result.py"),
        pyunit_utils.locate("GBM_Vignette_code_examples/gbm_gridsearch_random.py")]
    story2 = [pyunit_utils.locate("GBM_Vignette_code_examples/gbm_quantile.py")]

    approved_py_code_examples = story1+story2

    pybooklet_utils.check_code_examples_in_dir(approved_py_code_examples,
                                               pyunit_utils.locate("GBM_Vignette_code_examples"))

    pybooklet_utils.check_story("story1",story1)
    pybooklet_utils.check_story("story2",story2)

gbm_vignette()
