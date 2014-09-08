# TODO: ugh:
import sys, pprint
sys.path.extend(['.','..','py'])
import h2o, h2o_util

#########
# Config:
algos = ['example', 'kmeans', 'deeplearning', 'glm']


###########
# Utilities
pp = pprint.PrettyPrinter(indent=4)  # pretty printer for debugging

def validate_builder(builder):
    assert 'parameters' in builder and isinstance(builder['parameters'], list)
    parameters = builder['parameters']
    assert len(parameters) > 0
    parameter = parameters[0]
    h2o_util.assertKeysExist(parameter, '', ['name', 'label', 'help', 'required', 'type', 'default_value', 'actual_value', 'level', 'dependencies', 'validation', 'values'])


################
# The test body:
a_node = h2o.H2O("127.0.0.1", 54321)

# TODO: remove die fast test case:
if False:
    import_result = a_node.import_files(path="/Users/rpeck/Source/h2o2/smalldata/logreg/prostate.csv")
    parse_result = a_node.parse(key=import_result['keys'][0]) # TODO: handle multiple files
    prostate_key = parse_result['frames'][0]['key']['name']

    a_node.build_model(algo='kmeans', training_frame=prostate_key, parameters={'K': 2 }, timeoutSecs=240)

    sys.exit()

models = a_node.models()
print 'Models: '
pp.pprint(models)

frames = a_node.frames()
print 'Frames: '
pp.pprint(frames)


# test model_builders collection GET
print 'Testing /ModelBuilders. . .'
model_builders = a_node.model_builders()

print 'ModelBuilders: '
pp.pprint(model_builders)

for algo in algos:
    assert algo in model_builders['model_builders'], "Failed to find algo: " + algo
    builder = model_builders['model_builders'][algo]
    validate_builder(builder)
    

# test model_builders individual GET
print 'Testing /ModelBuilders/{algo}. . .'
for algo in algos:
    model_builder = a_node.model_builders(algo=algo)
    assert algo in model_builder['model_builders'], "Failed to find algo: " + algo
    builder = model_builders['model_builders'][algo]
    validate_builder(builder)

import_result = a_node.import_files(path="/Users/rpeck/Source/h2o2/smalldata/logreg/prostate.csv")
parse_result = a_node.parse(key=import_result['keys'][0]) # TODO: handle multiple files

pp.pprint(parse_result)

prostate_key = parse_result['frames'][0]['key']['name']

model_builders = a_node.model_builders()
pp.pprint(model_builders)

kmeans_builder = a_node.model_builders(algo='kmeans')['model_builders']['kmeans']

kmeans_model_name = 'KMeansModel' # TODO: currently can't specify the target key

jobs = a_node.build_model(algo='kmeans', training_frame=prostate_key, parameters={'K': 2 }, timeoutSecs=240) # synchronous
models = a_node.models()

print 'After Model build: Models: '
pp.pprint(models)

found_kmeans = False;
for model in models['models']:
    if model['key'] == 'KMeansModel':
        found_kmeans = True

assert found_kmeans, 'Did not find KMeansModel in the models list.'

# test delete_model
a_node.delete_model(kmeans_model_name)
models = a_node.models()

found_kmeans = False;
for model in models['models']:
    if model['key'] == 'KMeansModel':
        found_kmeans = True

assert not found_kmeans, 'Found KMeansModel in the models list: ' + h2o_util.dump_json(models)

# test delete_models
jobs = a_node.build_model(algo='kmeans', training_frame=prostate_key, parameters={'K': 2 }, timeoutSecs=240) # synchronous
a_node.delete_models()
models = a_node.models()

assert 'models' in models and 0 == len(models['models']), "Called delete_models and the models list isn't empty: " + h2o_util.dump_json(models)
