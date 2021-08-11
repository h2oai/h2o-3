from __future__ import print_function
import sys
import unittest


class TestCausalmlMakeUpliftClassificationIsWorking(unittest.TestCase):

    @unittest.skipIf(sys.version_info[0] < 3 or (sys.version_info[0] == 3 and sys.version_info[1] <= 5), "Tested only on >3.5, causalml is not supported on lower python version")
    def test_causalml_make_uplift_classification_smoke(self):
        from causalml.dataset import make_uplift_classification
        import causalml
        print("Causalml library smoke test")
        print("Version of causalml: {0}".format(causalml.__version__))
        n_samples = 500
        seed = 12345

        train, x_names = make_uplift_classification(n_samples=n_samples,
                                                    treatment_name=['control', 'treatment'],
                                                    n_classification_features=10,
                                                    n_classification_informative=10,
                                                    random_seed=seed
                                                    )
        assert not train.empty
        assert x_names


suite = unittest.TestLoader().loadTestsFromTestCase(TestCausalmlMakeUpliftClassificationIsWorking)
unittest.TextTestRunner().run(suite)
