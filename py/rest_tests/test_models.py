import requests

import h2o
import h2o_test_utils
from h2o_test_utils import ModelSpec
from h2o_test_utils import GridSpec


def build_and_test(a_node, pp, datasets, algos, algo_additional_default_params):
    ####################################################################################################
    # Build and do basic validation checks on models
    ####################################################################################################
    models_to_build = [
        ModelSpec.for_dataset('kmeans_prostate', 'kmeans', datasets['prostate_clustering'], { 'k': 2 } ),
    
        ModelSpec.for_dataset('glm_prostate_regression', 'glm', datasets['prostate_regression'], {'family': 'gaussian'} ),
    
        ModelSpec.for_dataset('glm_prostate_binomial', 'glm', datasets['prostate_binomial'], {'family': 'binomial'} ),
        # TODO: Crashes: ModelSpec('glm_airlines_binomial', 'glm', 'airlines_binomial', {'response_column': 'IsDepDelayed', 'do_classification': True, 'family': 'binomial'}, 'Binomial'),
        # Multinomial doesn't make sense for glm: ModelSpec('glm_iris_multinomial', 'glm', iris_multinomial, {'response_column': 'class', 'do_classification': True, 'family': 'gaussian'}, 'Regression'),
    
        ModelSpec.for_dataset('deeplearning_prostate_regression', 'deeplearning', datasets['prostate_regression'], { 'epochs': 1, 'loss': 'Quadratic' } ),
        # TODO: add toEnum of the response column and put back:    ModelSpec.for_dataset('deeplearning_prostate_binomial', 'deeplearning', datasets['prostate_binomial'], { 'epochs': 1, 'hidden': [20, 20], 'loss': 'CrossEntropy' } ),
        ModelSpec.for_dataset('deeplearning_airlines_binomial', 'deeplearning', datasets['airlines_binomial'], { 'epochs': 1, 'hidden': [10, 10], 'loss': 'CrossEntropy' } ),
        ModelSpec.for_dataset('deeplearning_iris_multinomial', 'deeplearning', datasets['iris_multinomial'], { 'epochs': 1, 'loss': 'CrossEntropy' } ),
    
        ModelSpec.for_dataset('gbm_prostate_regression', 'gbm', datasets['prostate_regression'], { 'ntrees': 5, 'distribution': 'gaussian' } ),
        # TODO: add toEnum of the response column and put back:        ModelSpec.for_dataset('gbm_prostate_binomial', 'gbm', datasets['prostate_binomial'], { 'ntrees': 5, 'distribution': 'multinomial' } ),
        ModelSpec.for_dataset('gbm_airlines_binomial', 'gbm', datasets['airlines_binomial'], { 'ntrees': 5, 'distribution': 'multinomial' } ),
        ModelSpec.for_dataset('gbm_iris_multinomial', 'gbm', datasets['iris_multinomial'], { 'ntrees': 5, 'distribution': 'multinomial' } ),
       ]
    
    built_models = {}
    for model_spec in models_to_build:
        model = model_spec.build_and_validate_model(a_node)
        built_models[model_spec['dest_key']] = model
    
    

    grids_to_build = [
        GridSpec.for_dataset('kmeans_prostate_grid', 'kmeans', datasets['prostate_clustering'], { }, { 'k': [2, 3, 4] } ),
    
        GridSpec.for_dataset('glm_prostate_regression_grid', 'glm', datasets['prostate_regression'], {'family': 'gaussian'}, { 'lambda': [0.0001, 0.001, 0.01, 0.1] } ),
    
        GridSpec.for_dataset('glm_prostate_binomial_grid', 'glm', datasets['prostate_binomial'], {'family': 'binomial'}, { 'lambda': [0.0001, 0.001, 0.01, 0.1] }  ),
        # TODO: Crashes: ModelSpec('glm_airlines_binomial', 'glm', 'airlines_binomial', {'response_column': 'IsDepDelayed', 'do_classification': True, 'family': 'binomial'}, 'Binomial'),
        # Multinomial doesn't make sense for glm: ModelSpec('glm_iris_multinomial', 'glm', iris_multinomial, {'response_column': 'class', 'do_classification': True, 'family': 'gaussian'}, 'Regression'),
    
        GridSpec.for_dataset('deeplearning_prostate_regression_grid', 'deeplearning', datasets['prostate_regression'], { 'loss': 'Quadratic' }, { 'epochs': [0.1, 0.5, 1] } ),
        # TODO: add toEnum of the response column and put back:    ModelSpec.for_dataset('deeplearning_prostate_binomial_grid', 'deeplearning', datasets['prostate_binomial'], { 'epochs': 1, 'hidden': [20, 20], 'loss': 'CrossEntropy' } ),
        GridSpec.for_dataset('deeplearning_airlines_binomial_grid', 'deeplearning', datasets['airlines_binomial'], { 'hidden': [10, 10], 'loss': 'CrossEntropy' }, { 'epochs': [0.1, 0.5, 1] }  ),
        GridSpec.for_dataset('deeplearning_iris_multinomial_grid', 'deeplearning', datasets['iris_multinomial'], { 'loss': 'CrossEntropy' }, { 'epochs': [0.1, 0.5, 1] }  ),
    
        GridSpec.for_dataset('gbm_prostate_regression_grid', 'gbm', datasets['prostate_regression'], { 'distribution': 'gaussian' }, { 'ntrees': [1, 5, 10], 'max_depth': [1, 3, 5] }  ),
        # TODO: add toEnum of the response column and put back:        ModelSpec.for_dataset('gbm_prostate_binomial_grid', 'gbm', datasets['prostate_binomial'], { 'ntrees': 5, 'distribution': 'multinomial' } ),
        GridSpec.for_dataset('gbm_airlines_binomial_grid', 'gbm', datasets['airlines_binomial'], { 'distribution': 'multinomial' }, { 'ntrees': [1, 5, 10], 'max_depth': [1, 3, 5] } ),
        GridSpec.for_dataset('gbm_iris_multinomial_grid', 'gbm', datasets['iris_multinomial'], { 'distribution': 'multinomial' }, { 'ntrees': [1, 5, 10], 'max_depth': [1, 3, 5] } ),
        # TODO: this should trigger a parameter validation error, but instead the non-grid ntrees silently overrides the drid values:        GridSpec.for_dataset('gbm_iris_multinomial_grid', 'gbm', datasets['iris_multinomial'], { 'ntrees': 5, 'distribution': 'multinomial' }, { 'ntrees': [1, 5, 10], 'max_depth': [1, 3, 5] } ),
       ]
    
    for grid_spec in grids_to_build:
        grid = grid_spec.build_and_validate_grid(a_node)

        for model_key in grid['model_ids']:
            model_key = model_key['name']
            built_models[model_key] = a_node.models(key=model_key)




    #######################################
    # Test default parameters validation for each model builder
    #
    if h2o_test_utils.isVerbose(): print 'Testing ModelBuilder default parameters. . .'
    model_builders = a_node.model_builders(timeoutSecs=240)['model_builders']
    
    # Do we know about all of them?
    server_algos = model_builders.keys()
    assert len(set(server_algos) - set(algos)) == 0, "FAIL: Our set of algos doesn't match what the server knows about.  Ours: " + repr(algos) + "; server's: " + repr(server_algos)
    
    for algo, model_builder in model_builders.iteritems():
        parameters_list = model_builder['parameters']
        test_parameters = { value['name'] : value['default_value'] for value in parameters_list } # collect default parameters
        if algo in algo_additional_default_params:
            test_parameters.update(algo_additional_default_params[algo])
    
        parameters_validation = a_node.validate_model_parameters(algo=algo, training_frame=None, parameters=test_parameters, timeoutSecs=240) # synchronous
        assert 'error_count' in parameters_validation, "FAIL: Failed to find error_count in good-parameters parameters validation result."
        h2o.H2O.verboseprint("Bad params validation messages: ", repr(parameters_validation))
    
        expected_count = 0
        if expected_count != parameters_validation['error_count']:
            print "validation errors: "
            pp.pprint(parameters_validation)
        assert expected_count == parameters_validation['error_count'], "FAIL: " + str(expected_count) + " != error_count in good-parameters parameters validation result."
    
    
    #######################################
    # Test DeepLearning parameters validation
    #
    # Default parameters:
    model_builder = a_node.model_builders(algo='deeplearning', timeoutSecs=240)['model_builders']['deeplearning']
    dl_test_parameters_list = model_builder['parameters']
    dl_test_parameters = {value['name'] : value['default_value'] for value in dl_test_parameters_list}
    
    parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame=None, parameters=dl_test_parameters, timeoutSecs=240) # synchronous
    assert 'error_count' in parameters_validation, "FAIL: Failed to find error_count in good-parameters parameters validation result."
    h2o.H2O.verboseprint("Bad params validation messages: ", repr(parameters_validation))
    if 0 != parameters_validation['error_count']:
        print "validation errors: "
        pp.pprint(parameters_validation)
    assert 0 == parameters_validation['error_count'], "FAIL: 0 != error_count in good-parameters parameters validation result."
    
    # Good parameters (note: testing with null training_frame):
    dl_test_parameters = {'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]" }
    parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame=None, parameters=dl_test_parameters, timeoutSecs=240) # synchronous
    assert 'error_count' in parameters_validation, "FAIL: Failed to find error_count in good-parameters parameters validation result."
    h2o.H2O.verboseprint("Bad params validation messages: ", repr(parameters_validation))
    if 0 != parameters_validation['error_count']:
        print "validation errors: "
        pp.pprint(parameters_validation)
    assert 0 == parameters_validation['error_count'], "FAIL: 0 != error_count in good-parameters parameters validation result."
    
    # Bad parameters (hidden is null):
    # (note: testing with null training_frame)
    dl_test_parameters = {'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]", 'input_dropout_ratio': 27 }
    parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame=None, parameters=dl_test_parameters, timeoutSecs=240) # synchronous
    assert 'error_count' in parameters_validation, "FAIL: Failed to find error_count in bad-parameters parameters validation result (input_dropout_ratio)."
    h2o.H2O.verboseprint("Good params validation messages: ", repr(parameters_validation))
    assert 0 != parameters_validation['error_count'], "FAIL: 0 == error_count in bad-parameters parameters validation result: " + repr(parameters_validation)
    
    found_expected_error = False
    for validation_message in parameters_validation['messages']:
        if validation_message['message_type'] == 'ERROR' and validation_message['field_name'] == 'input_dropout_ratio':
            found_expected_error = True
    assert found_expected_error, "FAIL: Failed to find error message about input_dropout_ratio in the validation messages."
    
    # Bad parameters (no response_column):
    dl_test_parameters = {'hidden': "[10, 20, 10]" }
    parameters_validation = a_node.validate_model_parameters(algo='deeplearning', training_frame='prostate_binomial', parameters=dl_test_parameters, timeoutSecs=240) # synchronous
    assert 'error_count' in parameters_validation, "FAIL: Failed to find error_count in bad-parameters parameters validation result (response_column)."
    h2o.H2O.verboseprint("Good params validation messages: ", repr(parameters_validation))
    assert 0 != parameters_validation['error_count'], "FAIL: 0 == error_count in bad-parameters parameters validation result: " + repr(parameters_validation)
    
    
    #######################################
    # Try to build DeepLearning model for Prostate but with bad parameters; we should get a ModelParametersSchema with the error.
    if h2o_test_utils.isVerbose(): print 'About to try to build a DeepLearning model with bad parameters. . .'
    dl_prostate_bad_parameters = {'response_column': 'CAPSULE', 'hidden': "[10, 20, 10]", 'input_dropout_ratio': 27  }
    parameters_validation = a_node.build_model(algo='deeplearning', model_id='deeplearning_prostate_binomial_bad', training_frame='prostate_binomial', parameters=dl_prostate_bad_parameters, timeoutSecs=240) # synchronous
    h2o_test_utils.validate_validation_messages(parameters_validation, ['input_dropout_ratio'])
    assert parameters_validation['__http_response']['status_code'] == requests.codes.precondition_failed, "FAIL: expected 412 Precondition Failed from a bad build request, got: " + str(parameters_validation['__http_response']['status_code'])
    if h2o_test_utils.isVerbose(): print 'Done trying to build DeepLearning model with bad parameters.'
    
    #####################################
    # Early test of predict()
    # TODO: remove after we remove the early exit
    p = a_node.predict(model='deeplearning_airlines_binomial', frame='airlines_binomial', predictions_frame='deeplearning_airlines_binomial_predictions')
    h2o_test_utils.validate_predictions(a_node, p, 'deeplearning_airlines_binomial', 'airlines_binomial', 43978, predictions_frame='deeplearning_airlines_binomial_predictions')
    h2o_test_utils.validate_frame_exists(a_node, 'deeplearning_airlines_binomial_predictions')
    h2o.H2O.verboseprint("Predictions for scoring: ", 'deeplearning_airlines_binomial', " on: ", 'airlines_binomial', ":  ", repr(p))
    
    # print h2o_test_utils.dump_json(p)
    
