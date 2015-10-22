import h2o
import h2o_test_utils

def test(a_node, pp):
    ####################################
    # test schemas collection GET
    if h2o_test_utils.isVerbose(): print 'Testing /Metadata/schemas. . .'
    schemas = a_node.schemas(timeoutSecs=240)
    assert 'schemas' in schemas, "FAIL: failed to find 'schemas' field in output of /Metadata/schemas: " + repr(schemas)
    assert type(schemas['schemas']) is list, "'schemas' field in output of /Metadata/schemas is not a list: " + repr(schemas)
    assert len(schemas['schemas']) > 0, "'schemas' field in output of /Metadata/schemas is empty: " + repr(schemas)
    
    if h2o_test_utils.isVerboser():
        print 'Schemas: '
        pp.pprint(schemas)
    
    
    ####################################
    # test schemas individual GET
    if h2o_test_utils.isVerbose(): print 'Testing /Metadata/schemas/FrameV3. . .'
    schemas = a_node.schema(schemaname='FrameV3', timeoutSecs=240)
    assert 'schemas' in schemas, "FAIL: failed to find 'schemas' field in output of /Metadata/schemas/FrameV3: " + repr(schemas)
    assert type(schemas['schemas']) is list, "'schemas' field in output of /Metadata/schemas/FrameV3 is not a list: " + repr(schemas)
    assert len(schemas['schemas']) == 1, "'schemas' field in output of /Metadata/schemas/FrameV3 has an unexpected length: " + repr(schemas)
    
    if h2o_test_utils.isVerboser():
        print 'Schemas: '
        pp.pprint(schemas)
    

    #########################
    # test Metadata/endpoints
    if h2o_test_utils.isVerbose(): print 'Testing /Metadata/endpoints. . .'
    endpoints = a_node.endpoints()
    assert 'routes' in endpoints, "FAIL: failed to find routes in the endpoints result."
    assert type(endpoints['routes']) is list, "FAIL: routes in the endpoints result is not a list."
    assert len(endpoints['routes']) > 0, "FAIL: routes list in the endpoints result is empty."
    assert type(endpoints['routes'][0]) is dict, "FAIL: routes[0] in the endpoints result is not a dict."
    assert 'input_schema' in endpoints['routes'][0], "FAIL: routes[0] in the endpoints result does not have an 'input_schema' field."
    
    
    #########################
    # test Metadata/schemas
    if h2o_test_utils.isVerbose(): print 'Testing /Metadata/schemas. . .'
    schemas = a_node.schemas()
    assert 'schemas' in schemas, "FAIL: failed to find schemas in the schemas result."
    assert type(schemas['schemas']) is list, "FAIL: schemas in the schemas result is not a list."
    assert len(schemas['schemas']) > 0, "FAIL: schemas list in the schemas result is empty."
    assert type(schemas['schemas'][0]) is dict, "FAIL: schemas[0] in the schemas result is not a dict."
    assert 'fields' in schemas['schemas'][0], "FAIL: schemas[0] in the schemas result does not have an 'fields' field."
    
    
