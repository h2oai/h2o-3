import h2o
import h2o_test_utils


def test(a_node, pp):
    ###################################
    # Compute and check ModelMetrics for 'deeplearning_prostate_binomial'
    mm = a_node.compute_model_metrics(model='deeplearning_prostate_binomial', frame='prostate_binomial')
    assert mm is not None, "FAIL: Got a null result for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial'
    assert 'model_category' in mm, "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " does not contain a model_category."
    assert 'Binomial' == mm['model_category'], "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " model_category is not Binomial, it is: " + str(mm['model_category'])
    assert 'AUC' in mm, "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " does not contain an AUC element: " + h2o_test_utils.dump_json(mm)
    assert type(mm['AUC']) is float, "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " AUC element is not a float: " + h2o_test_utils.dump_json(mm)
    
    assert 'confusion_matrices' in mm, "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " does not contain a confusion_matrices element: " + h2o_test_utils.dump_json(mm)
    assert type(mm['confusion_matrices']) is list, "FAIL: ModelMetrics for scoring: " + 'deeplearning_prostate_binomial' + " on: " + 'prostate_binomial' + " confusion_matrices element is not a list: " + h2o_test_utils.dump_json(mm)
    
    # print h2o_test_utils.dump_json(mm)
    h2o.H2O.verboseprint("ModelMetrics for scoring: ", 'deeplearning_prostate_binomial', " on: ", 'prostate_binomial', ":  ", repr(mm))
    
    ###################################
    # Check for ModelMetrics for 'deeplearning_prostate_binomial' in full list
    mms = a_node.model_metrics() # fetch all
    assert 'model_metrics' in mms, 'FAIL: Failed to find model_metrics in result of /3/ModelMetrics.'
    found_mm = False
    for mm in mms['model_metrics']:
        assert 'model' in mm, "FAIL: mm does not contain a model element: " + repr(mm)
        assert 'name' in mm['model'], "FAIL: mm[model] isn't a key with a name: " + repr(mm)
        assert 'type' in mm['model'], "FAIL: mm[model] does not contain a type: " + repr(mm)
        assert 'Key<Model>' == mm['model']['type'], "FAIL: mm[model] type is not Key<Model>: " + repr(mm['model']['type'])
    
        assert 'frame' in mm, "FAIL: mm does not contain a frame element: " + repr(mm)
        assert 'name' in mm['frame'], "FAIL: mm[frame] does not contain a name: " + repr(mm)
        assert 'type' in mm['frame'], "FAIL: mm[frame] does not contain a type: " + repr(mm)
        assert 'Key<Frame>' == mm['frame']['type'], "FAIL: mm[frame] type is not Key<Frame>: " + repr(mm)
    
        model_key = mm['model']['name']
        frame_key = mm['frame']['name'] # TODO: should match
        if model_key == 'deeplearning_prostate_binomial' and frame_key == 'prostate_binomial':
            found_mm = True
    assert found_mm, "FAIL: Failed to find ModelMetrics object for model: " + 'deeplearning_prostate_binomial' + " and frame: " + 'prostate_binomial'
    
    # test delete_model_metrics
    mms = a_node.model_metrics('deeplearning_prostate_binomial', 'prostate_binomial')
    assert len(mms['model_metrics']) == 1, "FAIL: expected 1 ModelMetrics, found: " + str(len(mms['model_metrics']))
    a_node.delete_model_metrics('deeplearning_prostate_binomial', 'prostate_binomial')
    mms = a_node.model_metrics('deeplearning_prostate_binomial', 'prostate_binomial')
    assert len(mms['model_metrics']) == 0, "FAIL: expected 0 ModelMetrics, found: " + str(len(mms['model_metrics']))
    
    
    ###################################
    # Predict and check ModelMetrics for 'deeplearning_prostate_binomial'
    p = a_node.predict(model='deeplearning_prostate_binomial', frame='prostate_binomial', predictions_frame='deeplearning_prostate_binomial_predictions')
    h2o_test_utils.validate_predictions(a_node, p, 'deeplearning_prostate_binomial', 'prostate_binomial', 380, predictions_frame='deeplearning_prostate_binomial_predictions')
    h2o_test_utils.validate_frame_exists(a_node, 'deeplearning_prostate_binomial_predictions')
    h2o.H2O.verboseprint("Predictions for scoring: ", 'deeplearning_prostate_binomial', " on: ", 'prostate_binomial', ":  ", repr(p))
    
    ###################################
    # Predict and check ModelMetrics for 'deeplearning_prostate_regression'
    p = a_node.predict(model='deeplearning_prostate_regression', frame='prostate_binomial')
    h2o_test_utils.validate_predictions(a_node, p, 'deeplearning_prostate_regression', 'prostate_binomial', 380)
    h2o.H2O.verboseprint("Predictions for scoring: ", 'deeplearning_prostate_regression', " on: ", 'prostate_binomial', ":  ", repr(p))
    
    ###################################
    # Predict and check ModelMetrics for 'gbm_prostate_binomial'
    p = a_node.predict(model='gbm_prostate_binomial', frame='prostate_binomial')
    h2o_test_utils.validate_predictions(a_node, p, 'gbm_prostate_binomial', 'prostate_binomial', 380)
    h2o.H2O.verboseprint("Predictions for scoring: ", 'gbm_prostate_binomial', " on: ", 'prostate_binomial', ":  ", repr(p))
    
    ###################################
    # Predict and check ModelMetrics for 'gbm_prostate_regression'
    p = a_node.predict(model='gbm_prostate_regression', frame='prostate_binomial')
    h2o_test_utils.validate_predictions(a_node, p, 'gbm_prostate_regression', 'prostate_binomial', 380)
    h2o.H2O.verboseprint("Predictions for scoring: ", 'gbm_prostate_regression', " on: ", 'prostate_binomial', ":  ", repr(p))
    
    ###################################
    # Predict and check ModelMetrics (empty now except for predictions frame) for 'kmeans_prostate'
    p = a_node.predict(model='kmeans_prostate', frame='prostate_binomial')
    h2o_test_utils.validate_predictions(a_node, p, 'kmeans_prostate', 'prostate_binomial', 380)
    h2o.H2O.verboseprint("Predictions for scoring: ", 'kmeans_prostate', " on: ", 'prostate_binomial', ":  ", repr(p))
    
    ###################################
    # Predict with reversed keys (should get an H2OErrorV1):
    # TODO: this works, but I'm not handling 500s yet in the automated test:
    # p = a_node.predict(frame='kmeans_prostate', model='prostate_binomial')
    # print repr(p)
    
    
