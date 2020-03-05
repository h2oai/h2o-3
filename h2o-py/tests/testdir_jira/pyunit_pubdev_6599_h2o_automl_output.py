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
        cls._aml = H2OAutoML(max_models=1)
        cls._aml.train(['sepal_len', 'sepal_wid', 'petal_len', 'petal_wid'], 'species', training_frame=cls._train_data)
        cls._aml_output = get_automl(cls._aml.project_name)
        
    def test_backward_compatibility(self):
        self.assertEqual(self._aml.project_name, self._aml_output['project_name'])
        self.assertEqual(self._aml.leader.model_id, self._aml_output['leader'].model_id)
        self.assertEqual(self._aml.leaderboard.frame_id, self._aml_output['leaderboard'].frame_id)
        self.assertEqual(self._aml.event_log.frame_id, self._aml_output['event_log'].frame_id)
        self.assertEqual(self._aml.training_info, self._aml_output['training_info'])
        
    def test_properties(self):
        self.assertEqual(self._aml.project_name, self._aml_output.project_name)
        self.assertEqual(self._aml.leader.model_id, self._aml_output.leader.model_id)
        self.assertEqual(self._aml.leaderboard.frame_id, self._aml_output.leaderboard.frame_id)
        self.assertEqual(self._aml.event_log.frame_id, self._aml_output.event_log.frame_id)
        self.assertEqual(self._aml.training_info, self._aml_output.training_info)
        with self.assertRaises(KeyError):
            self._aml_output['predict']

    def test_predict(self):
        predictions = self._aml.predict(self._test_data)
        predictions_from_output = self._aml_output.predict(self._test_data)
        self.assertTrue(((predictions == predictions_from_output).all()))


if __name__ == '__main__':
    pyunit_utils.standalone_test(unittest.main)
else:
    suite = unittest.TestLoader().loadTestsFromTestCase(H2OAutoMLOutputTest)
    unittest.TextTestRunner().run(suite)
