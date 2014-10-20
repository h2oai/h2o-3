import unittest
from proboscis.asserts import assert_equal
from proboscis.asserts import assert_false
from proboscis.asserts import assert_raises
from proboscis.asserts import assert_true
from proboscis import after_class
from proboscis import before_class
from proboscis import SkipTest
from proboscis import test
from config import Config

import random
import types
import unittest
import sys, os, pprint
sys.path.extend(['.','..','py'])
import h2o, h2o_util

def validate_builder(builder):
    assert 'parameters' in builder and isinstance(builder['parameters'], list)
    parameters = builder['parameters']
    assert len(parameters) > 0
    parameter = parameters[0]
    h2o_util.assertKeysExist(parameter, '', ['name', 'label', 'help', 'required', 'type', 'default_value', 'actual_value', 'level', 'values'])


def list_to_dict(l, key):
    result = {}
    for entry in l:
        k = entry[key]
        result[k] = entry
    return result

def validate_actual_parameters(input_parameters, actual_parameters, training_frame, validation_frame):
    actuals_dict = list_to_dict(actual_parameters, 'name')
    for k, v in input_parameters.iteritems():
        # TODO: skipping some stuff for now because they aren't serialized properly
        if k is 'response_column':
            continue

        expected = str(v)
        # Python says True; json says true
        assert k in actuals_dict, "Expected key " + k + " not found in actual parameters list."

        if actuals_dict[k]['type'] == 'boolean':
            expected = expected.lower()

        assert expected == actuals_dict[k]['actual_value'], "Parameter with name: " + k + " expected to have input value: " + str(expected) + ", instead has: " + str(actuals_dict[k]['actual_value'])
        # TODO: training_frame, validation_frame


def cleanup(a_node, models=None, frames=None):
    ###################
    # test delete_model
    if models is None:
        a_node.delete_models()
    else:
        for model in models:
            a_node.delete_model(model)

    ms = a_node.models()
    if models is None:
        assert 'models' in ms and 0 == len(ms['models']), "Called delete_models and the models list isn't empty: " + h2o_util.dump_json(ms)
    else:
        for model in models:
            for m in ms['models']:
                assert m['key'] != model, 'Found model that we tried to delete in the models list: ' + model

    ###################
    # test delete_frame
    if frames is not None:
        for frame in frames:
            a_node.delete_frame(frame)
            ms = a_node.frames()

            found = False;
            for m in ms['frames']:
                assert m['key'] != frame, 'Found frame that we tried to delete in the frames list: ' + frame

def local_cloud(test):
    test.a_node = h2o.H2O(
        use_this_ip_addr=test.cfg['topology']['local_cloud']['ip'],
        port=test.cfg['topology']['local_cloud']['port']
    )
    test.timeoutSecs = test.cfg['topology']['local_cloud']['timeoutSecs']

def flat_file(test):
    # read the flatfile and pick the first address and port
    with open(test.cfg['topology']['cloudByFlatFile']['flatFile'], 'r') as f:
        first_line = f.readline()
        tokens = first_line.split(":", 2)
    test.a_node = h2o.H2O(
        use_this_ip_addr=tokens[0],
        port=int(tokens[1])
    )
    test.timeoutSecs = test.cfg['topology']['cloudByFlatFile']['timeoutSecs']

def connect_to_cloud(test, type):
    options = {
        'local_cloud' : local_cloud,
        'cloudByFlatFile' : flat_file
    }
    options[type](test)

@test(groups=["rgm"])
class TestModelManagement(object):
    @test(groups=['rgm'])
    def testConnect(self):
        self.cfg = Config('test_config.cfg')
        type = self.cfg['type']
        connect_to_cloud(self,type)
        self.algos = ['example', 'kmeans', 'deeplearning', 'glm']
        self.clean_up_after = False
        h2o.H2O.verbose = True
        self.models = self.a_node.models()
        self.frames = self.a_node.frames()
        self.model_metrics = self.a_node.model_metrics(timeoutSecs=self.timeoutSecs)


    @test(groups=["rgm"], depends_on=[testConnect])
    def testModelBuilders(self):
        #cleanup(self.a_node)
        model_builders = self.a_node.model_builders(timeoutSecs=self.timeoutSecs)
        for algo in self.algos:
            assert algo in model_builders['model_builders'], "Failed to find algo: " + algo
            validate_builder(model_builders['model_builders'][algo])

    @test(groups=["rgm"], depends_on=[testModelBuilders])
    def testImportProstate(self):
        cleanup(self.a_node)
        import_result = self.a_node.import_files(
            path=os.path.abspath(os.path.join(self.cfg.basedir, self.cfg.data['prostate']))
        )
        parse_result = self.a_node.parse(key=import_result['keys'][0]) # TODO: handle multiple files
        self.prostate_key = parse_result['frames'][0]['key']['name']
        # Test /Frames for prostate.csv
        frames = self.a_node.frames()['frames']
        frames_dict = h2o_util.list_to_dict(frames, 'key/name')
        assert 'prostate.hex' in frames_dict, "Failed to find prostate.hex in Frames list."
        # Test /Frames/{key} for prostate.csv
        frames = self.a_node.frames(key='prostate.hex')['frames']
        frames_dict = h2o_util.list_to_dict(frames, 'key/name')
        assert 'prostate.hex' in frames_dict, "Failed to find prostate.hex in Frames list."
        columns_dict = h2o_util.list_to_dict(frames[0]['columns'], 'label')
        assert 'CAPSULE' in columns_dict, "Failed to find CAPSULE in Frames/prostate.hex."
        assert 'AGE' in columns_dict, "Failed to find AGE in Frames/prostate.hex/columns."
        assert 'bins' in columns_dict['AGE'], "Failed to find bins in Frames/prostate.hex/columns/AGE."
        assert None is columns_dict['AGE']['bins'], "Failed to clear bins field." # should be cleared except for /summary
        frames = self.a_node.columns(key='prostate.hex')['frames']
        columns_dict = h2o_util.list_to_dict(frames[0]['columns'], 'label')
        assert 'ID' in columns_dict, "Failed to find ID in Frames/prostate.hex/columns."
        assert 'AGE' in columns_dict, "Failed to find AGE in Frames/prostate.hex/columns."
        assert 'bins' in columns_dict['AGE'], "Failed to find bins in Frames/prostate.hex/columns/AGE."
        assert None is columns_dict['AGE']['bins'], "Failed to clear bins field." # should be cleared except for /summary
        frames = self.a_node.column(key='prostate.hex', column='AGE')['frames']
        columns_dict = h2o_util.list_to_dict(frames[0]['columns'], 'label')
        assert 'AGE' in columns_dict, "Failed to find AGE in Frames/prostate.hex/columns."
        assert 'bins' in columns_dict['AGE'], "Failed to find bins in Frames/prostate.hex/columns/AGE."
        assert None is columns_dict['AGE']['bins'], "Failed to clear bins field." # should be cleared except for /summary
        frames = self.a_node.summary(key='prostate.hex', column='AGE')['frames']
        columns_dict = h2o_util.list_to_dict(frames[0]['columns'], 'label')
        assert 'AGE' in columns_dict, "Failed to find AGE in Frames/prostate.hex/columns/AGE/summary."
        col = columns_dict['AGE']
        h2o_util.assertKeysExistAndNonNull(col, '', ['label', 'missing', 'zeros', 'pinfs', 'ninfs', 'mins',
                'maxs', 'mean', 'sigma', 'type', 'data', 'precision', 'bins', 'base', 'stride', 'pctiles'])
        h2o_util.assertKeysExist(col, '', ['domain', 'str_data'])
        assert col['mins'][0] == 43, 'Failed to find 43 as the first min for AGE.'
        assert col['maxs'][0] == 79, 'Failed to find 79 as the first max for AGE.'
        assert col['mean'] == 66.03947368421052, 'Failed to find 66.03947368421052 as the mean for AGE.'
        assert col['sigma'] == 6.527071269173308, 'Failed to find 6.527071269173308 as the sigma for AGE.'
        assert col['type'] == 'int', 'Failed to find int as the type for AGE.'
        assert col['data'][0] == 65, 'Failed to find 65 as the first data for AGE.'
        assert col['precision'] == -1, 'Failed to find -1 as the precision for AGE.'
        assert col['bins'][0] == 1, 'Failed to find 1 as the first bin for AGE.'
        assert col['base'] == 43, 'Failed to find 43 as the base for AGE.'
        assert col['stride'] == 1, 'Failed to find 1 as the stride for AGE.'
        assert col['pctiles'][0] == 50.5, 'Failed to find 50.5 as the first pctile for AGE.'


    # @test(groups=["pending"], depends_on=[testImportProstate])
    # def testImportAirlines(self):
    #     import_result = self.a_node.import_files(
    #         path=os.path.abspath(os.path.join(self.cfg.basedir, self.cfg.data['airlines']))
    #         )
    #     parse_result = self.a_node.parse(key=import_result['keys'][0]) # TODO: handle multiple files
    #     self.airlines_key = parse_result['frames'][0]['key']['name']
    #     model_builders = self.a_node.model_builders(timeoutSecs=self.timeoutSecs)
    #     kmeans_builder = self.a_node.model_builders(algo='kmeans', timeoutSecs=self.timeoutSecs)['model_builders']['kmeans']
    #     self.kmeans_model_name = 'prostate_KMeans_1' # TODO: currently can't specify the target key
    #     self.kmeans_parameters = {'K': 2 }
    #     jobs = self.a_node.build_model(
    #         algo='kmeans', destination_key=self.kmeans_model_name,
    #         training_frame=self.prostate_key, parameters=self.kmeans_parameters, timeoutSecs=self.timeoutSecs) # synchronous
    #
    # @test( groups=["pending"], depends_on=[testImportAirlines] )
    # def testGoodParameters(self):
    #     dl_test_parameters = {'classification': True, 'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]" }
    #     parameters_validation = self.a_node.validate_model_parameters(algo='deeplearning',
    #             training_frame=self.prostate_key, parameters=dl_test_parameters, timeoutSecs=self.timeoutSecs) # synchronous
    #     assert 'validation_error_count' in parameters_validation, \
    #         "Failed to find validation_error_count in good-parameters parameters validation result."
    #     h2o.H2O.verboseprint("Bad params validation messages: ", repr(parameters_validation))
    #     assert 0 == parameters_validation['validation_error_count'], \
    #         "0 == validation_error_count in good-parameters parameters validation result."
    #
    # @test( groups=["pending"], depends_on=[testGoodParameters] )
    # def testBadParameters(self):
    #     # Bad parameters (hidden is null):
    #     dl_test_parameters = {'classification': True, 'response_column': 'CAPSULE',
    #             'hidden': "[10, 20, 10]", 'input_dropout_ratio': 27 }
    #     parameters_validation = self.a_node.validate_model_parameters(algo='deeplearning',
    #             training_frame=self.prostate_key, parameters=dl_test_parameters, timeoutSecs=self.timeoutSecs) # synchronous
    #     assert 'validation_error_count' in parameters_validation, \
    #         "Failed to find validation_error_count in bad-parameters parameters validation result."
    #     h2o.H2O.verboseprint("Good params validation messages: ", repr(parameters_validation))
    #     assert 0 < parameters_validation['validation_error_count'], \
    #         "0 != validation_error_count in bad-parameters parameters validation result."
    #     found_error = False
    #     for validation_message in parameters_validation['validation_messages']:
    #         if validation_message['message_type'] == 'ERROR' and validation_message['field_name'] == 'input_dropout_ratio':
    #             found_error = True
    #     assert found_error, "Failed to find error message about input_dropout_ratio in the validation messages."
    #
    #
    # @test( groups=["pending"], depends_on=[testBadParameters] )
    # def testDeepLearningModelProstate(self):
    #     #######################################
    #     # Build DeepLearning model for Prostate
    #     self.dl_prostate_model_name = 'prostate_DeepLearning_1'
    #     self.dl_prostate_1_parameters = {'classification': True, 'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]" }
    #     jobs = self.a_node.build_model(algo='deeplearning', destination_key=self.dl_prostate_model_name,
    #             training_frame=self.prostate_key, parameters=self.dl_prostate_1_parameters, timeoutSecs=self.timeoutSecs) # synchronous
    #     models = self.a_node.models()
    #     #######################################
    #     # Try to build DeepLearning model for Prostate but with bad parameters; we should get a ModelParametersSchema with the error.
    #     dl_prostate_model_name_bad = 'prostate_DeepLearning_bad'
    #     dl_prostate_bad_parameters = {'classification': True,
    #         'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]", 'input_dropout_ratio': 27  }
    #     parameters_validation = self.a_node.build_model(algo='deeplearning', destination_key=dl_prostate_model_name_bad,
    #         training_frame=self.prostate_key, parameters=dl_prostate_bad_parameters, timeoutSecs=self.timeoutSecs) # synchronous
    #     assert 'validation_error_count' in parameters_validation, \
    #         "Failed to find validation_error_count in bad-parameters build result."
    #     assert 0 < parameters_validation['validation_error_count'], \
    #         "0 != validation_error_count in bad-parameters build validation result."
    #     found_error = False
    #     for validation_message in parameters_validation['validation_messages']:
    #         if validation_message['message_type'] == 'ERROR' and \
    #                         validation_message['field_name'] == 'input_dropout_ratio':
    #             found_error = True
    #     assert found_error, \
    #         "Failed to find error message about input_dropout_ratio in the bad build validation messages."
    #
    # @test( groups=["pending"], depends_on=[testDeepLearningModelProstate] )
    # def testDeepLearningModelAirlines(self):
    #     #######################################
    #     # Build DeepLearning model for Airlines
    #     self.dl_airlines_model_name = 'airlines_DeepLearning_1'
    #     self.dl_airline_1_parameters = {'classification': True, 'response_column': 'IsDepDelayed' }
    #     jobs = self.a_node.build_model(algo='deeplearning', destination_key=self.dl_airlines_model_name,
    #         training_frame=self.airlines_key, parameters=self.dl_airline_1_parameters, timeoutSecs=self.timeoutSecs) # synchronous
    #
    #     self.models = self.a_node.models()
    #
    #     ############################
    #     # Check kmeans_model_name
    #     found_kmeans = False;
    #     kmeans_model = None
    #     for model in self.models['models']:
    #         if model['key'] == self.kmeans_model_name:
    #             found_kmeans = True
    #             kmeans_model = model
    #
    #     assert found_kmeans, 'Did not find ' + self.kmeans_model_name + ' in the models list.'
    #     validate_actual_parameters(self.kmeans_parameters, kmeans_model['parameters'], self.prostate_key, None)
    #
    #     ###################################
    #     # Check dl_prostate_model_name
    #     found_dl = False;
    #     dl_model = None
    #     for model in self.models['models']:
    #         if model['key'] == self.dl_prostate_model_name:
    #             found_dl = True
    #             dl_model = model
    #
    #     assert found_dl, 'Did not find ' + self.dl_prostate_model_name + ' in the models list.'
    #     validate_actual_parameters(self.dl_prostate_1_parameters, dl_model['parameters'], self.prostate_key, None)
    #
    #
    # @test( groups=["pending"], depends_on=[testDeepLearningModelAirlines] )
    # def testComputeAndCheckModelMetricsProstate(self):
    #     ###################################
    #     # Compute and check ModelMetrics for dl_prostate_model_name
    #     mm = self.a_node.compute_model_metrics(model=self.dl_prostate_model_name, frame=self.prostate_key)
    #     assert mm is not None, "Got a null result for scoring: " + \
    #         self.dl_prostate_model_name + " on: " + self.prostate_key
    #     assert 'auc' in mm, "ModelMetrics for scoring: " + \
    #         self.dl_prostate_model_name + " on: " + self.prostate_key + " does not contain an AUC."
    #     assert 'cm' in mm, "ModelMetrics for scoring: " + \
    #         self.dl_prostate_model_name + " on: " + self.prostate_key + " does not contain a CM."
    #     ###################################
    #     # Check for ModelMetrics for dl_prostate_model_name in full list
    #     mms = self.a_node.model_metrics() # fetch all
    #     assert 'model_metrics' in mms, 'Failed to find model_metrics in result of /3/ModelMetrics.'
    #     found_mm = False
    #     for mm in mms['model_metrics']:
    #         model_key = mm['model']['key']
    #         frame_key = mm['frame']['key']['name'] # TODO: should match
    #         if model_key == self.dl_prostate_model_name and frame_key == self.prostate_key:
    #             found_mm = True
    #     assert found_mm, "Failed to find ModelMetrics object for model: " + \
    #         self.dl_prostate_model_name + " and frame: " + self.prostate_key
    #
    # @test( groups=["pending"], depends_on=[testComputeAndCheckModelMetricsProstate] )
    # def testPredictAndCheckModelMetricsProstate(self):
    #     ###################################
    #     # Predict and check ModelMetrics for dl_prostate_model_name
    #     p = self.a_node.predict(model=self.dl_prostate_model_name, frame=self.prostate_key)
    #     assert p is not None, "Got a null result for scoring: " + self.dl_prostate_model_name + " on: " + self.prostate_key
    #     assert 'model_metrics' in p, "Predictions for scoring: " + self.dl_prostate_model_name + " on: " + \
    #         self.prostate_key + " does not contain a model_metrics object."
    #     mm = p['model_metrics'][0]
    #     h2o.H2O.verboseprint('mm: ', repr(mm))
    #     assert 'auc' in mm, "Predictions for scoring: " + self.dl_prostate_model_name + " on: " + \
    #         self.prostate_key + " does not contain an AUC."
    #     assert 'cm' in mm, "Predictions for scoring: " + self.dl_prostate_model_name + " on: " + \
    #         self.prostate_key + " does not contain a CM."
    #     assert 'predictions' in mm, "Predictions for scoring: " + self.dl_prostate_model_name + " on: " + \
    #         self.prostate_key + " does not contain an predictions section."
    #     predictions = mm['predictions']
    #     h2o.H2O.verboseprint ('p: ', repr(p))
    #     assert 'columns' in predictions, "Predictions for scoring: " + self.dl_prostate_model_name + " on: " + \
    #         self.prostate_key + " does not contain an columns section."
    #     assert len(predictions['columns']) > 0, "Predictions for scoring: " + self.dl_prostate_model_name + " on: " + \
    #         self.prostate_key + " does not contain any columns."
    #     assert 'label' in predictions['columns'][0], "Predictions for scoring: " + \
    #         self.dl_prostate_model_name + " on: " + self.prostate_key + " column 0 has no label element."
    #     assert 'predict' == predictions['columns'][0]['label'], "Predictions for scoring: " + \
    #         self.dl_prostate_model_name + " on: " + self.prostate_key + " column 0 is not 'predict'."
    #     assert 380 == predictions['rows'], "Predictions for scoring: " + \
    #         self.dl_prostate_model_name + " on: " + self.prostate_key + " has an unexpected number of rows."
    #     ###################################
    #     # Check dl_airlines_model_name
    #     found_dl = False;
    #     dl_model = None
    #     for model in self.models['models']:
    #         if model['key'] == self.dl_airlines_model_name:
    #             found_dl = True
    #             dl_model = model
    #
    #     assert found_dl, 'Did not find ' + self.dl_airlines_model_name + ' in the models list.'
    #     validate_actual_parameters(self.dl_airline_1_parameters, dl_model['parameters'], self.airlines_key, None)
    #
    # @test( groups=["pending"], depends_on=[testPredictAndCheckModelMetricsProstate] )
    # def testCheckWithModelAPI(self):
    #     ######################################################################
    #     # Now look for kmeans_model_name using the one-model API and find_compatible_frames, and check it
    #     model = self.a_node.models(key=self.kmeans_model_name, find_compatible_frames=True)
    #     found_kmeans = False;
    #     h2o_util.assertKeysExist(model['models'][0], '', ['compatible_frames'])
    #     assert self.prostate_key in model['models'][0]['compatible_frames'], \
    #         "Failed to find " + self.prostate_key + " in compatible_frames list."
    #     ######################################################################
    #     # Now look for prostate_key using the one-frame API and find_compatible_models, and check it
    #     result = self.a_node.frames(key='prostate.hex', find_compatible_models=True)
    #     frames = result['frames']
    #     frames_dict = h2o_util.list_to_dict(frames, 'key/name')
    #     assert 'prostate.hex' in frames_dict, "Failed to find prostate.hex in Frames list."
    #
    #     compatible_models = result['compatible_models']
    #     models_dict = h2o_util.list_to_dict(compatible_models, 'key')
    #     assert self.dl_prostate_model_name in models_dict, "Failed to find " + \
    #                 self.dl_prostate_model_name + " in compatible models list."
    #
    #     assert self.dl_prostate_model_name in frames[0]['compatible_models']
    #     assert self.kmeans_model_name in frames[0]['compatible_models']


## ----------------- proboscis boiler plate hook -------------------------
# no reason to modify anything below
def run_tests():
    from proboscis import TestProgram
    TestProgram().run_and_exit()

if __name__ =='__main__':
    run_tests()
