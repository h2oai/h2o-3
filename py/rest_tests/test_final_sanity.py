import h2o
import h2o_test_utils


def test(a_node, pp):

    ######################################################################
    # Now look for kmeans_prostate_model_name using the one-model API and find_compatible_frames, and check it
    model = a_node.models(key='kmeans_prostate', find_compatible_frames=True)
    found_kmeans = False;
    h2o.H2O.verboseprint('k-means model with find_compatible_frames output: ')
    h2o.H2O.verboseprint('/Models/', 'kmeans_prostate', '?find_compatible_frames=true: ', repr(model))
    h2o_test_utils.assertKeysExist(model['models'][0], '', ['compatible_frames'])
    assert 'prostate_binomial' in model['models'][0]['compatible_frames'], "FAIL: Failed to find " + 'prostate_binomial' + " in compatible_frames list."
    
    
    ######################################################################
    # Now look for 'prostate_binomial' using the one-frame API and find_compatible_models, and check it
    result = a_node.frames(key='prostate_binomial', find_compatible_models=True, row_count=5)
    frames = result['frames']
    frames_dict = h2o_test_utils.list_to_dict(frames, 'frame_id/name')
    assert 'prostate_binomial' in frames_dict, "FAIL: Failed to find prostate.hex in Frames list."
    
    compatible_models = result['compatible_models']
    models_dict = h2o_test_utils.list_to_dict(compatible_models, 'model_id/name')
    assert 'deeplearning_prostate_binomial' in models_dict, "FAIL: Failed to find " + 'deeplearning_prostate_binomial' + " in compatible models list: " + repr(result)
    
    assert 'deeplearning_prostate_binomial' in frames[0]['compatible_models'], "FAIL: failed to find deeplearning_prostate_binomial in compatible_models for prostate."
    assert 'kmeans_prostate' in frames[0]['compatible_models'], "FAIL: failed to find kmeans_prostate in compatible_models for prostate."
    h2o.H2O.verboseprint('/Frames/prosate.hex?find_compatible_models=true: ', repr(result))
    
    
