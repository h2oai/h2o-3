import h2o_cmd, h2o_util
import h2o_nodes
import re, math, random
from h2o_test import check_sandbox_for_errors
from operator import itemgetter

def pickRandKMeansParams(paramDict, params):
    randomGroupSize = random.randint(1,len(paramDict))
    for i in range(randomGroupSize):
        randomKey = random.choice(paramDict.keys())
        randomV = paramDict[randomKey]
        randomValue = random.choice(randomV)
        params[randomKey] = randomValue


class KMeansOutput(object):
    def __init__(self, output):
        assert isinstance(output, dict)
        for k,v in output.iteritems():
            setattr(self, k, v) # achieves self.k = v

# parameters is what I sent to kmeans?
# might not be full set?
def simpleCheckKMeans(self, modelResult, parameters, numRows, numCols, labels):
    # labels should have the ignored columns removed
    # numCols should be decremented by the ignored columns
    # the names order should then match the labels order

    output = modelResult['models'][0]['output']
    # print "model output:", dump_json(output)
    # find out what results we get
    ko = KMeansOutput(output)
    if 1==0:
        for attr, value in ko.__dict__.iteritems():
            # create some python prints to use
            print "%s = ko.%s # %s" % (attr, attr, value)

    # these should sum to the rows in the dataset
    rows = ko.rows # [78, 5, 41, 76]
    model_category = ko.model_category # Clustering
    iters = ko.iters # 11.0
    schema_version = ko.schema_version # 2
    domains = ko.domains # [None, None, None, None, None, None, None, None, None, None, None, None, None, None]
    # 
    names = ko.names # [u'STR', u'OBS', u'AGMT', u'FNDX', u'HIGD', u'DEG', u'CHK', u'AGP1', u'AGMN', u'NLV', u'LIV', u'WT', u'AGLP', u'MST']
    schema_name = ko.schema_name # KMeansModelOutputV2
    schema_type = ko.schema_type # KMeansOutput
    ncats = ko.ncats # 0
    clusters = ko.clusters # [ 4 lists of centers ]
    mse = ko.mse # 505.632581773
    mses = ko.mses # [476.37866653867707, 563.7343365736649, 606.3007046232348, 477.5260498976912]

    if numRows:
        assert numRows==sum(rows)

    if 'K' in parameters:
        K = parameters['K']
        assert len(mses) == K
        assert len(clusters) == K
        assert len(rows) == K

    if numCols:
        assert len(names) == numCols, \
            "Need to pass correct numCols after ignored columns decrement %s %s" % (len(names), numCols)
        for c in clusters:
            assert len(c) == numCols, "%s %s" % (len(c), numCols)

    # this should be true 
    if labels:
        assert len(labels) == numCols, \
            "Need to pass correct labels and numCols after ignored columns removal %s %s" % (len(labels), numCols)
        assert len(labels) == len(names), \
            "Need to pass correct labels after ignored columns removal %s %s" % (len(labels), len(names))
        assert labels == names

    if 'max_iters' in parameters:
        max_iters = parameters['max_iters']
        assert max_iters >= iters

    # we could check the centers are within the min/max of each column
    for i,c in enumerate(clusters):
        for n in c:
            if math.isnan(float(n)):
                raise Exception("cluster", i, "has NaN:", n, "center:", c)

    # shouldn't have any errors
    check_sandbox_for_errors()

    # create a tuple for each cluster result, then sort by rows for easy comparison
    # maybe should sort by centers?
    # put a cluster index in there too, (leftmost) so we don't lose teack
    tuples = zip(range(len(clusters)), mses, rows, clusters)
    tuplesSorted = sorted(tuples, key=itemgetter(3))

    # undo for printing what the caller will see
    ids, mses, rows, clusters = zip(*tuplesSorted)

    print "\nmse:", mse
    print "iters:", iters
    print "ids:", ids
    print "mses:", mses
    print "rows:", rows
    for i,c in enumerate(clusters):
        print "cluster id %s (2 places):" % ids[i], h2o_util.twoDecimals(c)

    
    # to unzip the tuplesSorted. zip with *
    # ids, mses, rows, clusters = zip(*tuplesSorted)
    return tuplesSorted, iters, mse, names


def bigCheckResults(self, kmeans, csvPathname, parseResult, predictKey, **kwargs):
    simpleCheckKMeans(self, kmeans, **kwargs)
    # can't use inspect on a model key? now?
    model = kmeans['model']
    model_key = model['_key']
    centers = model['centers']
    size = model["size"]
    cluster_variances = model["within_cluster_variances"]
    error = model["total_within_SS"]
    iterations = model["iterations"]
    normalized = model["normalized"]
    max_iter = model["max_iter"]
    kmeansResult = kmeans

    predictResult = h2o_nodes.nodes[0].generate_predictions(data_key=parseResult['destination_key'], model_key=model_key, destination_key=predictKey)
    summaryResult = h2o_nodes.nodes[0].summary_page(key=predictKey, timeoutSecs=120)
    hcnt = summaryResult['summaries'][0]['hcnt'] # histogram
    rows_per_cluster = hcnt
    # FIX! does the cluster order/naming match, compared to cluster variances
    sqr_error_per_cluster = cluster_variances
    
    tupleResultList = []
    print "\nerror: ", error

    if (len(centers)!=len(rows_per_cluster) or len(centers)!=len(sqr_error_per_cluster)):
        raise Exception("centers, rows_per_cluster, sqr_error_per_cluster should all be same length %s, %s, %s" % \
            (len(centers), len(rows_per_cluster), len(sqr_error_per_cluster)))
            
    print "Did iterations: %s  given max_iter: %s" % (iterations, max_iter)
    for i,c in enumerate(centers):
        print "\ncenters[%s]: " % i, [round(c,2) for c in centers[i]]
        print "rows_per_cluster[%s]: " % i, rows_per_cluster[i]
        print "sqr_error_per_cluster[%s]: " % i, sqr_error_per_cluster[i]
        print "size[%s]:" % i, size[i]
        tupleResultList.append( (centers[i], rows_per_cluster[i], sqr_error_per_cluster[i]) )

    return (centers, tupleResultList)


# list of tuples: center, rows, sqr_error
# expected = [ # tupleResultList is returned by bigCheckResults like this
#       ([-2.2824436059344264, -0.9572469619836067], 61, 71.04484889371177),
#       ([0.04072444664179102, 1.738305108029851], 67, 118.83608173427331),
#       ([2.7300104405999996, -1.16148755108], 50, 68.67496427685141)
# ]
# delta is a tuple of multipliers against the tupleResult for abs delta
# allowedDelta = (0.01, 0.1, 0.01)
def compareResultsToExpected(self, tupleResultList, expected=None, allowedDelta=None, allowError=False, allowRowError=False, trial=0):
    # sort the tuple list by center for the comparison. (this will be visible to the caller?)
    from operator import itemgetter
    tupleResultList.sort(key=itemgetter(0))

    if expected is not None:
        # sort expected, just in case, for the comparison
        expected.sort(key=itemgetter(0))
        print "\nTrial #%d Expected:" % trial
        for e in expected:
            print e

    # now compare to expected, with some delta allowed
    print "\nTrial #%d Actual:" % trial
    for t in tupleResultList:
        print t, "," # so can cut and paste and put results in an expected = [..] list

    if expected is not None and not allowError: # allowedDelta must exist if expected exists
        for i, (expCenter, expRows, expError)  in enumerate(expected):
            (actCenter, actRows, actError) = tupleResultList[i]

            for (a,b) in zip(expCenter, actCenter): # compare list of floats
                absAllowedDelta = abs(allowedDelta[0] * a)
                self.assertAlmostEqual(a, b, delta=absAllowedDelta,
                    msg="Trial %d Center value expected: %s actual: %s delta > %s" % (trial, a, b, absAllowedDelta))

            if not allowRowError: # allow error in row count? 
                absAllowedDelta = abs(allowedDelta[1] * expRows)
                self.assertAlmostEqual(expRows, actRows, delta=absAllowedDelta,
                    msg="Trial %d Rows expected: %s actual: %s delta > %s" % (trial, expRows, actRows, absAllowedDelta))

            # fix, we don't compare the actual error # (what is it?)

# just print info on the distribution
def showClusterDistribution(self, tupleResultList, expected=None, allowedDelta=None, allowError=False, trial=0):
    # sort the tuple list by center for the comparison. (this will be visible to the caller?)
    from operator import itemgetter
    if expected is not None:
        # sort expected, just in case, for the comparison
        expected.sort(key=itemgetter(0))
        # get total row and total error
        totalRows = 0
        totalError = 0
        print "\nExpected distribution, rows and error:"
        for i, (expCenter, expRows, expError)  in enumerate(expected):
            totalRows += expRows
            totalError += expError
        # now go thru again and print percentages
        print "totalRows:", totalRows, "totalError:", totalError
        for i, (expCenter, expRows, expError)  in enumerate(expected):
            print expCenter, "pctRows: %0.2f" % (expRows/(totalRows+0.0)), "pctError: %0.2f" % (expError/(totalError+0.0))

    if tupleResultList is not None:
        tupleResultList.sort(key=itemgetter(0))
        totalRows = 0
        totalError = 0
        print "\nActual distribution, rows and error:"
        for i, (actCenter, actRows, actError)  in enumerate(tupleResultList):
            totalRows += actRows
            totalError += actError
        # now go thru again and print percentages
        print "totalRows:", totalRows, "totalError:", totalError
        for i, (actCenter, actRows, actError)  in enumerate(tupleResultList):
            print actCenter, "pctRows: %0.2f" % (actRows/(totalRows+0.0)), "pctError: %0.2f" % (actError/(totalError+0.0))


# compare this clusters to last one. since the files are concatenations, 
# the results should be similar? 10% of first is allowed delta
def compareToFirstKMeans(self, clusters, firstclusters):
    # clusters could be a list or not. if a list, don't want to create list of that list
    # so use extend on an empty list. covers all cases?
    if type(clusters) is list:
        kList  = clusters
        firstkList = firstclusters
    elif type(clusters) is dict:
        raise Exception("compareToFirstKMeans: Not expecting dict for " + key)
    else:
        kList  = [clusters]
        firstkList = [firstclusters]

    print "kList:", kList, "firstkList:", firstkList
    for k, firstk in zip(kList, firstkList):
        # delta must be a positive number?
        # too bad we can't do an assertAlmostEqual on the list directly..have to break them out
        for k1, firstk1 in zip(k, firstk):
            delta = .1 * abs(float(firstk1))
            print "k1:", k1, "firstk1:", firstk1
            msg = "Too large a delta (>" + str(delta) + ") comparing current and first clusters: " + \
                str(float(k1)) + ", " + str(float(firstk1))
            self.assertAlmostEqual(float(k1), float(firstk1), delta=delta, msg=msg)
            self.assertGreaterEqual(abs(float(k1)), 0.0, str(k1) + " abs not >= 0.0 in current")

