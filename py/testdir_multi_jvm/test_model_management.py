# TODO: ugh:
import sys, pprint
sys.path.extend(['.','..','py'])
import h2o, h2o_util

# pretty printer for debugging
pp = pprint.PrettyPrinter(indent=4)

a_node = h2o.H2O("127.0.0.1", 54321)
models = a_node.models()
frames = a_node.frames()

print 'Models: '
pp.pprint(models)

print 'Frames: '
pp.pprint(frames)

import_result = a_node.import_files(path="/Users/rpeck/Source/h2o2/smalldata/logreg/prostate.csv")
parse_result = a_node.parse(key=import_result['keys'][0]) # TODO: handle multiple files

pp.pprint(parse_result)
