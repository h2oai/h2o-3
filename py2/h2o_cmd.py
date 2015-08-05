
import h2o_nodes
from h2o_test import dump_json, verboseprint
import h2o_util
import h2o_print as h2p
from h2o_test import OutputObj

#************************************************************************
def runStoreView(node=None, **kwargs):
    print "FIX! disabling runStoreView for now"
    return {}

    if not node: node = h2o_nodes.nodes[0]

    print "\nStoreView:"
    # FIX! are there keys other than frames and models
    a = node.frames(**kwargs)
    # print "storeview frames:", dump_json(a)
    frameList = [af['key']['name'] for af in a['frames']]

    for f in frameList:
        print "frame:", f
    print "# of frames:", len(frameList)

    b = node.models()
    # print "storeview models:", dump_json(b)
    modelList = [bm['key'] for bm in b['models']]
    for m in modelList:
        print "model:", m
    print "# of models:", len(modelList)
    
    return {'keys': frameList + modelList}

#************************************************************************
def runExec(node=None, **kwargs):
    if not node: node = h2o_nodes.nodes[0]
    a = node.rapids(**kwargs)
    return a


def runInspect(node=None, key=None, verbose=False, **kwargs):
    if not key: raise Exception('No key for Inspect')
    if not node: node = h2o_nodes.nodes[0]
    a = node.frames(key, **kwargs)
    if verbose:
        print "inspect of %s:" % key, dump_json(a)
    return a

#************************************************************************
def infoFromParse(parse):
    if not parse:
        raise Exception("parse is empty for infoFromParse")
    # assumes just one result from Frames
    if 'frames' not in parse:
        raise Exception("infoFromParse expects parse= param from parse result: %s" % parse)
    if len(parse['frames'])!=1:
        raise Exception("infoFromParse expects parse= param from parse result: %s " % parse['frames'])

    # it it index[0] or key '0' in a dictionary?
    frame = parse['frames'][0]
    # need more info about this dataset for debug
    numCols = len(frame['columns'])
    numRows = frame['rows']
    key_name = frame['frame_id']['name']

    return numRows, numCols, key_name


#************************************************************************
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

    # need more info about this dataset for debug
    columns = frame['columns']
    key_name = frame['frame_id']['name']
    missingList = []
    labelList = []
    typeList = []
    for i, colDict in enumerate(columns): # columns is a list
        if 'missing_count' not in colDict:
            # debug
            print "\ncolDict"
            for k in colDict:
                print "  key: %s" % k

            # data
            # domain
            # string_data
            # type
            # label
            # percentiles
            # precision
            # mins
            # maxs
            # mean
            # histogram_base
            # histogram_bins
            # histogram_stride
            # zero_count
            # missing_count
            # positive_infinity_count
            # negative_infinity_count
            # __meta


        mins = colDict['mins']
        maxs = colDict['maxs']
        missing = colDict['missing_count']
        label = colDict['label']
        stype = colDict['type']

        missingList.append(missing)
        labelList.append(label)
        typeList.append(stype)
        if missing!=0:
            print "%s: col: %s %s, missing: %d" % (key_name, i, label, missing)

    print "inspect typeList:", typeList

    # make missingList empty if all 0's
    if sum(missingList)==0:
        missingList = []

    # no type per col in inspect2
    numCols = len(frame['columns'])
    numRows = frame['rows']
    print "\n%s numRows: %s, numCols: %s" % (key_name, numRows, numCols)
    return missingList, labelList, numRows, numCols

#************************************************************************
# does all columns unless you specify column index.
# only will return first or specified column
def runSummary(node=None, key=None, column=None, expected=None, maxDelta=None, noPrint=False, **kwargs):
    if not key: raise Exception('No key for Summary')
    if not node: node = h2o_nodes.nodes[0]
    # return node.summary(key, **kwargs)

    i = InspectObj(key=key)
    # just so I don't have to change names below
    missingList = i.missingList
    labelList = i.labelList
    numRows = i.numRows
    numCols = i.numCols
    print "labelList:", labelList
    assert labelList is not None

    # doesn't take indices? only column labels?
    # return first column, unless specified

    if not (column is None or isinstance(column, (basestring, int))):
        raise Exception("column param should be string or integer index or None %s %s" % (type(column), column))

    # either return the first col, or the col indentified by label. the column identifed could be string or index?
    if column is None: # means the summary json when we ask for col 0, will be what we return (do all though)
        colNameToDo = labelList
        colIndexToDo = range(len(labelList))
    elif isinstance(column, int):
        colNameToDo = [labelList[column]]
        colIndexToDo = [column]
    elif isinstance(column, basestring):
        colNameToDo = [column]
        if column not in labelList:
            raise Exception("% not in labellist: %s" % (column, labellist))
        colIndexToDo = [labelList.index(column)]
    else:
        raise Exception("wrong type %s for column %s" % (type(column), column))

    # we get the first column as result after walking across all, if no column parameter
    desiredResult = None
    for (colIndex, colName) in zip(colIndexToDo, colNameToDo):
        print "doing summary on %s %s" % (colIndex, colName)
        # ugly looking up the colIndex
        co = SummaryObj(key=key, colIndex=colIndex, colName=colName)
        if not desiredResult:
            desiredResult = co

        if not noPrint:
            for k,v in co:
                # only print [0] of mins and maxs because of the e308 values when they don't have dataset values
                if k=='mins' or k=='maxs':
                    print "%s[0]" % k, v[0]
                else:
                    print k, v

        if expected is not None:
            print "len(co.histogram_bins):", len(co.histogram_bins)
            print "co.label:", co.label, "mean (2 places):", h2o_util.twoDecimals(co.mean)
            # what is precision. -1?
            print "co.label:", co.label, "std dev. (2 places):", h2o_util.twoDecimals(co.sigma)

            # print "FIX! hacking the co.percentiles because it's short by two"
            # if co.percentiles:
            #     percentiles = [0] + co.percentiles + [0]
            # else:
            #     percentiles = None
            percentiles = co.percentiles
            assert len(co.percentiles) == len(co.default_percentiles)

            # the thresholds h2o used, should match what we expected
                # expected = [0] * 5
            # Fix. doesn't check for expected = 0?

            # max of one bin
            if maxDelta is None:
                maxDelta = (co.maxs[0] - co.mins[0])/1000

            if expected[0]: h2o_util.assertApproxEqual(co.mins[0], expected[0], tol=maxDelta, 
                msg='min is not approx. expected')
            if expected[1]: h2o_util.assertApproxEqual(percentiles[2], expected[1], tol=maxDelta, 
                msg='25th percentile is not approx. expected')
            if expected[2]: h2o_util.assertApproxEqual(percentiles[4], expected[2], tol=maxDelta, 
                msg='50th percentile (median) is not approx. expected')
            if expected[3]: h2o_util.assertApproxEqual(percentiles[6], expected[3], tol=maxDelta, 
                msg='75th percentile is not approx. expected')
            if expected[4]: h2o_util.assertApproxEqual(co.maxs[0], expected[4], tol=maxDelta, 
                msg='max is not approx. expected')

            # figure out the expected max error
            # use this for comparing to sklearn/sort
            MAX_QBINS = 1000
            if expected[0] and expected[4]:
                expectedRange = expected[4] - expected[0]
                # because of floor and ceil effects due we potentially lose 2 bins (worst case)
                # the extra bin for the max value, is an extra bin..ignore
                expectedBin = expectedRange/(MAX_QBINS-2)
                maxErr = expectedBin # should we have some fuzz for fp?

            else:
                print "Test won't calculate max expected error"
                maxErr = 0

            pt = h2o_util.twoDecimals(percentiles)

            # only look at [0] for now...bit e308 numbers if unpopulated due to not enough unique values in dataset column
            mx = h2o_util.twoDecimals(co.maxs[0])
            mn = h2o_util.twoDecimals(co.mins[0])

            print "co.label:", co.label, "co.percentiles (2 places):", pt
            print "co.default_percentiles:", co.default_percentiles
            print "co.label:", co.label, "co.maxs: (2 places):", mx
            print "co.label:", co.label, "co.mins: (2 places):", mn

            # FIX! why would percentiles be None? enums?
            if pt is None:
                compareActual = mn, [None] * 3, mx
            else:
                compareActual = mn, pt[2], pt[4], pt[6], mx

            h2p.green_print("actual min/25/50/75/max co.label:", co.label, "(2 places):", compareActual)
            h2p.green_print("expected min/25/50/75/max co.label:", co.label, "(2 places):", expected)

    return desiredResult


# this parses the json object returned for one col from runSummary...returns an OutputObj object
# summaryResult = h2o_cmd.runSummary(key=hex_key, column=0)
# co = h2o_cmd.infoFromSummary(summaryResult)
# print co.label

# legacy
def infoFromSummary(summaryResult, column=None):
    return SummaryObj(summaryResult, column=column)

class ParseObj(OutputObj):
    # the most basic thing is that the data frame has the # of rows and cols we expected
    # embed that checking here, so every test doesn't have to
    def __init__(self, parseResult, expectedNumRows=None, expectedNumCols=None, noPrint=False, **kwargs):
        super(ParseObj, self).__init__(parseResult['frames'][0], "Parse", noPrint=noPrint)
        # add my stuff
        self.numRows, self.numCols, self.parse_key = infoFromParse(parseResult)
        # h2o_import.py does this for test support
        if 'python_elapsed' in parseResult:
            self.python_elapsed = parseResult['python_elapsed']
        if expectedNumRows is not None:
            assert self.numRows == expectedNumRows, "%s %s" % (self.numRows, expectedNumRows)
        if expectedNumCols is not None:
            assert self.numCols == expectedNumCols, "%s %s" % (self.numCols, expectedNumCols)
        print "ParseObj created for:", self.parse_key # vars(self)

# Let's experiment with creating new objects that are an api I control for generic operations (Inspect)
class InspectObj(OutputObj):
    # the most basic thing is that the data frame has the # of rows and cols we expected
    # embed that checking here, so every test doesn't have to
    def __init__(self, key,
        expectedNumRows=None, expectedNumCols=None, expectedMissingList=None, expectedLabelList=None,
        noPrint=False, **kwargs):

        inspectResult = runInspect(key=key)
        super(InspectObj, self).__init__(inspectResult['frames'][0], "Inspect", noPrint=noPrint)
        # add my stuff
        self.missingList, self.labelList, self.numRows, self.numCols = infoFromInspect(inspectResult)
        if expectedNumRows is not None:
            assert self.numRows == expectedNumRows, "%s %s" % (self.numRows, expectedNumRows)
        if expectedNumCols is not None:
            assert self.numCols == expectedNumCols, "%s %s" % (self.numCols, expectedNumCols)
        if expectedMissingList is not None:
            assert self.missingList == expectedMissingList, "%s %s" % (self.MissingList, expectedMissingList)
        if expectedLabelList is not None:
            assert self.labelList == expectedLabelList, "%s %s" % (self.labelList, expectedLabelList)
        print "InspectObj created for:", key  #,  vars(self)


class SummaryObj(OutputObj):
    @classmethod
    def check(self,
        expectedNumRows=None, expectedNumCols=None, 
        expectedLabel=None, expectedType=None, expectedMissing=None, expectedDomain=None, expectedBinsSum=None,
        noPrint=False, **kwargs):

        if expectedLabel is not None:
            assert self.label != expectedLabel
        if expectedType is not None:
            assert self.type != expectedType
        if expectedMissing is not None:
            assert self.missing != expectedMissing
        if expectedDomain is not None:
            assert self.domain != expectedDomain
        if expectedBinsSum is not None:
            assert self.binsSum != expectedBinsSum

    # column is column name?
    def __init__(self, key, colIndex, colName,
        expectedNumRows=None, expectedNumCols=None, 
        expectedLabel=None, expectedType=None, expectedMissing=None, expectedDomain=None, expectedBinsSum=None,
        noPrint=False, timeoutSecs=30, **kwargs):

        # we need both colInndex and colName for doing Summary efficiently
        # ugly.
        assert colIndex is not None
        assert colName is not None
        summaryResult = h2o_nodes.nodes[0].summary(key=key, column=colName, timeoutSecs=timeoutSecs, **kwargs)
        # this should be the same for all the cols? Or does the checksum change?
        frame = summaryResult['frames'][0]
        default_percentiles = frame['default_percentiles']
        checksum = frame['checksum']
        rows = frame['rows']

        # assert colIndex < len(frame['columns']), "You're asking for colIndex %s but there are only %s. " % \
        #     (colIndex, len(frame['columns']))
        # coJson = frame['columns'][colIndex]

        # is it always 0 now? the one I asked for ?
        coJson = frame['columns'][0]

        assert checksum !=0 and checksum is not None
        assert rows!=0 and rows is not None

        # FIX! why is frame['key'] = None here?
        # assert frame['key'] == key, "%s %s" % (frame['key'], key)
        super(SummaryObj, self).__init__(coJson, "Summary for %s" % colName, noPrint=noPrint)

        # how are enums binned. Stride of 1? (what about domain values)
        # touch all
        # print "vars", vars(self)

        coList = [
            len(self.data),
            self.domain,
            self.string_data,
            self.type,
            self.label,
            self.percentiles,
            self.precision,
            self.mins,
            self.maxs,
            self.mean,
            self.histogram_base,
            len(self.histogram_bins),
            self.histogram_stride,
            self.zero_count,
            self.missing_count,
            self.positive_infinity_count,
            self.negative_infinity_count,
            ]

        assert self.label==colName, "%s You must have told me the wrong colName %s for the given colIndex %s" % \
            (self.label, colName, colIndex)

        print "you can look at this attributes in the returned object (which is OutputObj if you assigned to 'co')"
        for k,v in self:
            print "%s" % k,

        # hack these into the column object from the full summary
        self.default_percentiles = default_percentiles
        self.checksum = checksum
        self.rows = rows

        print "\nSummaryObj for", key, "for colName", colName, "colIndex:", colIndex
        print "SummaryObj created for:", key # vars(self)
        
        # now do the assertion checks
        self.check(expectedNumRows, expectedNumCols, 
            expectedLabel, expectedType, expectedMissing, expectedDomain, expectedBinsSum,
            noPrint=noPrint, **kwargs)


