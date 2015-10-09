#############################
# Test that the cloud responds
import requests

import h2o
import h2o_test_utils

def test(a_node, pp, algos):
    ##################################
    # Test object collection endpoints
    models = a_node.models()
    if h2o_test_utils.isVerboser():
        print 'Models: '
        pp.pprint(models)
    
    models = a_node.models(api_version=92)  # note: tests API version fallback
    if h2o_test_utils.isVerboser():
        print 'ModelsV92: '
        pp.pprint(models)
    
    frames = a_node.frames(row_count=5)
    if h2o_test_utils.isVerboser():
        print 'Frames: '
        pp.pprint(frames)
    
    # TODO: all other collections, including /Jobs and eventually /DKV
    # TODO: test /Cloud


    ####################################
    # test model_builders collection GET
    if h2o_test_utils.isVerbose(): print 'Testing /ModelBuilders. . .'
    model_builders = a_node.model_builders(timeoutSecs=240)
    
    if h2o_test_utils.isVerboser():
        print 'ModelBuilders: '
        pp.pprint(model_builders)
    
    for algo in algos:
        assert algo in model_builders['model_builders'], "FAIL: Failed to find algo: " + algo
        builder = model_builders['model_builders'][algo]
        h2o_test_utils.validate_builder(algo, builder)
    
    
    ####################################
    # test model_builders individual GET
    if h2o_test_utils.isVerbose(): print 'Testing /ModelBuilders/{algo}. . .'
    for algo in algos:
        model_builder = a_node.model_builders(algo=algo, timeoutSecs=240)
        assert algo in model_builder['model_builders'], "FAIL: Failed to find algo: " + algo
        builder = model_builders['model_builders'][algo]
        h2o_test_utils.validate_builder(algo, builder)
    
    ####################################
    # test model_metrics collection GET
    if h2o_test_utils.isVerbose(): print 'Testing /ModelMetrics. . .'
    model_metrics = a_node.model_metrics(timeoutSecs=240)
    
    if h2o_test_utils.isVerboser():
        print 'ModelMetrics: '
        pp.pprint(model_metrics)
    
    ####################################
    # test model_metrics individual GET
    # TODO
    
