def deeplearning_vignette():
    from tests import pyunit_utils
    story1 = [
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_importfile_example.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_examplerun.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_crossval.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_inspect_model.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_predict.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_varimp.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_gridsearch.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_gridsearch_result.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_gridsearch_random.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_checkpoint.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_savemodel.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_loadmodel_checkpoint.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_getmodel.py"),
        pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_anomaly.py")]
    story2 = [pyunit_utils.locate("DeepLearning_Vignette_code_examples/deeplearning_quantile.py")]

    approved_py_code_examples = story1+story2

    pybooklet_utils.check_code_examples_in_dir(approved_py_code_examples,
                                               pyunit_utils.locate("DeepLearning_Vignette_code_examples"))

    pybooklet_utils.check_story("story1",story1)
    pybooklet_utils.check_story("story2",story2)

deeplearning_vignette()