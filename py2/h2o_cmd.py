
import h2o_nodes

def runStoreView(*args, **kwargs):
    print "WARNING: faking store view"
    a = {'keys': {}}
    return a

def runInspect(node=None, key=None, timeoutSecs=30, verbose=False, **kwargs):
    if not key: raise Exception('No key for Inspect')
    if not node: node = h2o_nodes.nodes[0]
    a = node.frames(key, timeoutSecs=timeoutSecs, **kwargs)
    if verbose:
        print "inspect of %s:" % key, dump_json(a)
    return a

