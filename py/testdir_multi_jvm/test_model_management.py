# TODO: ugh:
import sys, pprint
sys.path.extend(['.','..','py'])
import h2o, h2o_util

# pretty printer for debugging
pp = pprint.PrettyPrinter(indent=4)

a_node = h2o.H2O("127.0.0.1", 54321)

# TODO: remove die fast test case:
if False:
    import_result = a_node.import_files(path="/Users/rpeck/Source/h2o2/smalldata/logreg/prostate.csv")
    parse_result = a_node.parse(key=import_result['keys'][0]) # TODO: handle multiple files
    prostate_key = parse_result['frames'][0]['key']['name']

    a_node.build_model(algo='kmeans', training_frame=prostate_key, parameters={'K': 2 }, timeoutSecs=240)

    sys.exit()

models = a_node.models()
frames = a_node.frames()

print 'Models: '
pp.pprint(models)

print 'Frames: '
pp.pprint(frames)

import_result = a_node.import_files(path="/Users/rpeck/Source/h2o2/smalldata/logreg/prostate.csv")
parse_result = a_node.parse(key=import_result['keys'][0]) # TODO: handle multiple files

pp.pprint(parse_result)

prostate_key = parse_result['frames'][0]['key']['name']

model_builders = a_node.model_builders()
pp.pprint(model_builders)

kmeans_builder = a_node.model_builders(key='kmeans')['model_builders']['kmeans']

jobs = a_node.build_model(algo='kmeans', training_frame=prostate_key, parameters={'K': 2 }, timeoutSecs=240) # synchronous

models = a_node.models()

print 'After Model build: Models: '
pp.pprint(models)
