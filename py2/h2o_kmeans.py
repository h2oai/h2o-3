import h2o_cmd, h2o_util
import h2o_nodes
import re, math, random
from h2o_test import check_sandbox_for_errors
from operator import itemgetter
from h2o_test import OutputObj, dump_json

def pickRandKMeansParams(paramDict, params):
    randomGroupSize = random.randint(1,len(paramDict))
    for i in range(randomGroupSize):
        randomKey = random.choice(paramDict.keys())
        randomV = paramDict[randomKey]
        randomValue = random.choice(randomV)
        params[randomKey] = randomValue


# FIX! what about ignored columns during kmeans
def simpleCheckKMeans(modelResult, parameters, numRows, numCols, labels):
    # labels should have the ignored columns removed
    # numCols should be decremented by the ignored columns
    # the names order should then match the labels order
    ko = KMeansObj(modelResult, parameters)

    # to unzip the tuplesSorted. zip with *
    # ids, within_mse, rows, centers = zip(*tuplesSorted)
    return ko.tuplesSorted, ko.iterations, ko.totss, ko.names

class KMeansObj(OutputObj):
    def __init__(self, kmeansResult, parameters, numRows, numCols, labels, noPrint=False, **kwargs):
        super(KMeansObj, self).__init__(kmeansResult['models'][0]['output'], "KMeans", noPrint=noPrint)

        print self.withinss # per cluster
        print self.totss
        print self.tot_withinss
        print self.betweenss

        # should model builder add this to the kmeansResult?
        if 'python_elapsed' in kmeansResult:
            self.python_elapsed = kmeansResult['python_elapsed']

        size = self.size # [78, 5, 41, 76]
        model_category = self.model_category # Clustering
        iterations = self.iterations # 11.0
        domains = self.domains 
        names = self.names 
        categorical_column_count = self.categorical_column_count # 0
        centers_data = self.centers.data # [ 4 lists of centers ]
        # h2o returns it sliced across centers. transpose the list of lists, drop 0 which is the cluster id?
        # gotta turn the strings into numbers
        centersStr = [list(x) for x in zip(*centers_data[1:])]
        centers = [map(float, c) for c in centersStr]

        withinss = self.withinss
        totss = self.totss

        if numRows:
            assert numRows==sum(size)

        if 'k' in parameters:
            k = parameters['k']
            assert len(centers) == k
            assert len(size) == k

        if numCols:
            assert len(names) == numCols, \
                "Need to pass correct numCols after ignored columns decrement %s %s %s" % (len(names), numCols, names)
            for c in centers:
                assert len(c) == numCols, "%s %s" % (len(c), numCols)

        # this should be true 
        if labels:
            assert len(labels) == numCols, \
                "Need to pass correct labels and numCols after ignored columns removal %s %s" % (len(labels), numCols)
            assert len(labels) == len(names), \
                "Need to pass correct labels after ignored columns removal %s %s" % (len(labels), len(names))
            assert labels == names

        if 'max_iterations' in parameters:
            max_iterations = parameters['max_iterations']
            assert max_iterations >= iterations

        # we could check the centers are within the min/max of each column
        for i,c in enumerate(centers):
            for n in c:
                if math.isnan(float(n)):
                    raise Exception("cluster", i, "has NaN:", n, "center:", c)

        # create a tuple for each cluster result, then sort by rows for easy comparison
        # maybe should sort by centers?
        # put a cluster index in there too, (leftmost) so we don't lose track
        tuples = zip(range(len(centers)), centers, size, withinss)
        # print "tuples:", dump_json(tuples)
        # can we sort on the sum of the centers?
        self.tuplesSorted = sorted(tuples, key=lambda tup: sum(tup[1]))

        print "iterations:", iterations
        # undo for printing what the caller will see
        ids, centers, size, withinss = zip(*self.tuplesSorted)
        for i,c in enumerate(centers):
            print "cluster id %s (2 places):" % ids[i], h2o_util.twoDecimals(c)
            print "rows_per_cluster[%s]: " % i, size[i]
            print "withinss[%s]: " % i, withinss[i]
            print "size[%s]:" % i, size[i]

        print "KMeansObj created for:", "???"# vars(self)

        # shouldn't have any errors
        check_sandbox_for_errors()


# This is all messed up now...really want it to just do predict and compare histogram, and also do the compare results to expected
# will have to fix all this (and don't overlap with simpleCheck above)
def bigCheckResults(kmeansObj, kmeans, csvPathname, parseResult, predictKey, **kwargs):
    predictResult = h2o_nodes.nodes[0].generate_predictions(data_key=parseResult['destination_key'], model_key=model_key, destination_key=predictKey)
    summaryResult = h2o_nodes.nodes[0].summary_page(key=predictKey, timeoutSecs=120)
    hcnt = summaryResult['summaries'][0]['hcnt'] # histogram
    rows_per_cluster = hcnt

    # FIX! does the cluster order/naming match, compared to cluster variances
    sqr_error_per_cluster = cluster_variances
    
    if (len(centers)!=len(rows_per_cluster) or len(centers)!=len(sqr_error_per_cluster)):
        raise Exception("centers, rows_per_cluster, sqr_error_per_cluster should all be same length %s, %s, %s" % \
            (len(centers), len(rows_per_cluster), len(sqr_error_per_cluster)))
            
    print "Did iterations: %s  given max_iter: %s" % (iterations, max_iter)
    # shouldn't have to return a tuplesList from here any more


def compareResultsToExpected(tupleResultList, expected=None, allowedDelta=None, allowError=False, allowRowError=False):
    # the expected/tupleResultlist should be sorted already by center sum, but just in case...
    tupleResultList.sort(key=lambda tup: sum(tup[1]))

    if expected is not None:
        # sort expected, just in case, for the comparison
        expected.sort(key=lambda tup: sum(tup[1]))
        print "\nExpected:"
        for e in expected:
            print e

    # now compare to expected, with some delta allowed
    print "\nActual:"
    for t in tupleResultList:
        print t, "," # so can cut and paste and put results in an expected = [..] list

    if expected is not None and not allowError: # allowedDelta must exist if expected exists
        for i, (expCid, expCenter, expRows, expError)  in enumerate(expected):
            (actCid, actCenter, actRows, actError) = tupleResultList[i]

            for (a,b) in zip(expCenter, actCenter): # compare list of floats
                absAllowedDelta = abs(allowedDelta[0] * a)
                absAllowedDelta = max(absAllowedDelta, allowedDelta[0]) # comparing to 0?
                h2o_util.assertApproxEqual(a, b, tol=absAllowedDelta,
                    msg="Center value expected: %s actual: %s delta > %s" % (a, b, absAllowedDelta))

            if not allowRowError and expRows: # allow error in row count? 
                absAllowedDelta = abs(allowedDelta[1] * expRows)
                absAllowedDelta = max(absAllowedDelta, allowedDelta[1]) # comparing to 0?
                h2o_util.assertApproxEqual(expRows, actRows, tol=absAllowedDelta,
                    msg="Rows expected: %s actual: %s delta > %s" % (expRows, actRows, absAllowedDelta))

            if not allowRowError and expError: # allow error in row count? 
                absAllowedDelta = abs(allowedDelta[2] * expError)
                absAllowedDelta = max(absAllowedDelta, allowedDelta[2]) # comparing to 0?
                h2o_util.assertApproxEqual(expRows, actRows, tol=absAllowedDelta,
                    msg="Error expected: %s actual: %s delta > %s" % (expError, actError, absAllowedDelta))

# just print info on the distribution
def showClusterDistribution(tupleResultList, expected=None, allowedDelta=None, allowError=False, trial=0):
    # sort the tuple list by center for the comparison. (this will be visible to the caller?)
    from operator import itemgetter
    if expected is not None:
        # sort expected, just in case, for the comparison
        expected.sort(key=itemgetter(0))
        # get total row and total error
        totalRows = 0
        totalError = 0
        print "\nExpected distribution, rows and error:"
        for i, (expCid, expCenter, expRows, expError)  in enumerate(expected):
            totalRows += expRows
            totalError += expError
        # now go thru again and print percentages
        print "totalRows:", totalRows, "totalError:", totalError
        for i, (expCid, expCenter, expRows, expError)  in enumerate(expected):
            print expCenter, "pctRows: %0.2f" % (expRows/(totalRows+0.0)), "pctError: %0.2f" % (expError/(totalError+0.0))

    if tupleResultList is not None:
        tupleResultList.sort(key=itemgetter(0))
        totalRows = 0
        totalError = 0
        print "\nActual distribution, rows and error:"
        for i, (actCid, actCenter, actRows, actError)  in enumerate(tupleResultList):
            totalRows += actRows
            totalError += actError
        # now go thru again and print percentages
        print "totalRows:", totalRows, "totalError:", totalError
        for i, (actCid, actCenter, actRows, actError)  in enumerate(tupleResultList):
            print actCenter, "pctRows: %0.2f" % (actRows/(totalRows+0.0)), "pctError: %0.2f" % (actError/(totalError+0.0))


# compare this cluster centers to last one. since the files are concatenations,
# the results should be similar? 10% of first is allowed delta
def compareToFirstKMeans(self, centers, firstcenters):
    # cluster centers could be a list or not. if a list, don't want to create list
    # of that list so use extend on an empty list. covers all cases?
    if type(centers) is list:
        kList  = centers
        firstkList = firstcenters
    elif type(centers) is dict:
        raise Exception("compareToFirstKMeans: Not expecting dict for " + key)
    else:
        kList  = [centers]
        firstkList = [firstcenters]

    print "kList:", kList, "firstkList:", firstkList
    for k, firstk in zip(kList, firstkList):
        # delta must be a positive number?
        # too bad we can't do an assertAlmostEqual on the list directly..have to break them out
        for k1, firstk1 in zip(k, firstk):
            delta = .1 * abs(float(firstk1))
            print "k1:", k1, "firstk1:", firstk1
            msg = "Too large a delta (>" + str(delta) + ") comparing current and first cluster centers: " + \
                str(float(k1)) + ", " + str(float(firstk1))
            self.assertAlmostEqual(float(k1), float(firstk1), delta=delta, msg=msg)
            self.assertGreaterEqual(abs(float(k1)), 0.0, str(k1) + " abs not >= 0.0 in current")

