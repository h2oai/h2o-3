def glm_vignette():
    from tests import pyunit_utils
    story1 = [pyunit_utils.locate("GLM_Vignette_code_examples/glm_gaussian_example.py")]
    story2 = [pyunit_utils.locate("GLM_Vignette_code_examples/glm_binomial_example.py")]
    story3 = [pyunit_utils.locate("GLM_Vignette_code_examples/glm_poisson_example.py")]
    story4 = [pyunit_utils.locate("GLM_Vignette_code_examples/glm_gamma_example.py")]
    story5 = [pyunit_utils.locate("GLM_Vignette_code_examples/coerce_column_to_factor.py")]
    story6 = [pyunit_utils.locate("GLM_Vignette_code_examples/glm_stopping_criteria.py")]
    story7 = [pyunit_utils.locate("GLM_Vignette_code_examples/glm_download_pojo.py")]

    approved_py_code_examples = story1+story2+story3+story4+story5+story6+story7

    pybooklet_utils.check_code_examples_in_dir(approved_py_code_examples,
                                               pyunit_utils.locate("GLM_Vignette_code_examples"))

    pybooklet_utils.check_story("story1",story1)
    pybooklet_utils.check_story("story2",story2)
    pybooklet_utils.check_story("story3",story3)
    pybooklet_utils.check_story("story4",story4)
    pybooklet_utils.check_story("story5",story5)
    pybooklet_utils.check_story("story6",story6)
    pybooklet_utils.check_story("story7",story7)

glm_vignette()