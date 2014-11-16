
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


# make this be the basic way to get numRows, numCols
def infoFromInspect(inspect):
    if not inspect:
        raise Exception("inspect is empty for infoFromInspect")
    # assumes just one result from Frames
    if 'frames' not in inspect:
        raise Exception("infoFromInspect expects inspect= param from Frames result (single): %s" % inspect)
    if len(inspect['frames'])!=1:
        raise Exception("infoFromInspect expects inspect= param from Frames result (single): %s " % inspect['frames'])

    # it it index[0] or key '0' in a dictionary?
    frame = inspect['frames'][0]

    if frame['isText']:
        raise Exception("infoFromInspect only for parsed frames?: %s " % frame['isText'])

    # need more info about this dataset for debug
    columns = frame['columns']
    key_name = frame['key']['name']
    # look for nonzero num_missing_values count in each col
    missingList = []
    labelList = []
    for i, colDict in enumerate(columns): # columns is a list
        missing = colDict['missing']
        if missing != 0:
            print "%s: col: %d, %s: %d" % (key_name, i, 'missing', missing)
            # this doesn't have labels to the cols with missing..
            missingList.append(missing)

        labelList.append(colDict['label'])
    # no type per col in inspect2
    numCols = len(frame['columns'])
    numRows = frame['rows']
    byteSize = frame['byteSize']

    print "\n%s numRows: %s, numCols: %s, byteSize: %s" % (key_name, numRows, numCols, byteSize)

    return missingList, labelList, numRows, numCols
