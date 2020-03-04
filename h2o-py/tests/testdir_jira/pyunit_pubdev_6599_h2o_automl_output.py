import unittest
import h2o
from h2o.automl import H2OAutoML, get_automl
from tests import pyunit_utils


class H2OAutoMLOutputTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # get an H2OAutoMLOutput object
        cls._train_data = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_train.csv"))
        cls._test_data = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_test.csv"))
        aml = H2OAutoML(max_models=1)
        aml.train(['sepal_len', 'sepal_wid', 'petal_len', 'petal_wid'], 'species', training_frame=cls._train_data)
        cls._aml_output = get_automl(aml.project_name)
        
    def test_backward_compatibility(self):
        self.assertIsNotNone(self._aml_output['project_name'])
        self.assertIsNotNone(self._aml_output['leader'])
        self.assertIsNotNone(self._aml_output['leaderboard'])
        self.assertIsNotNone(self._aml_output['event_log'])
        self.assertIsNotNone(self._aml_output['training_info'])
        
    def test_properties(self):
        self.assertIsNotNone(self._aml_output.project_name)
        self.assertIsNotNone(self._aml_output.leader)
        self.assertIsNotNone(self._aml_output.leaderboard)
        self.assertIsNotNone(self._aml_output.event_log)
        self.assertIsNotNone(self._aml_output.training_info)
        with self.assertRaises(KeyError):
            self._aml_output['predict']

    def test_predict(self):
        predictions = self._aml_output.predict(self._test_data)
        self.assertTrue(float((self._test_data['species'] == predictions['predict']).mean()) > 0.95)


if __name__ == '__main__':
    pyunit_utils.standalone_test(unittest.main)
else:
    unittest.main()
