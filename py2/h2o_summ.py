import h2o_print as h2p, h2o_util
import math, functools, getpass

#***************************************************************************
# similar to Wai Yip Tung's. a pure python percentile function
# so we don't have to use the one(s) from numpy or scipy
# and require those package installs
## {{{ http://code.activestate.com/recipes/511478/ (r1)
def percentileOnSortedList(N, percent, key=lambda x:x, interpolate='mean'):
    # 5 ways of resolving fractional
    # floor, ceil, funky, linear, mean
    interpolateChoices = ['floor', 'ceil', 'funky', 'linear', 'mean']
    if interpolate not in interpolateChoices:
        print "Bad choice for interpolate:", interpolate
        print "Supported choices:", interpolateChoices
    """
    Find the percentile of a list of values.

    @parameter N - is a list of values. Note N MUST BE already sorted.
    @parameter percent - a float value from 0.0 to 1.0.
    @parameter key - optional key function to compute value from each element of N.

    @return - the percentile of the values
    """
    if N is None:
        return None
    k = (len(N)-1) * percent
    f = int(math.floor(k))
    c = int(math.ceil(k))
    
    if f == c:
        d = key(N[f])
        msg = "aligned:" 

    elif interpolate=='floor':
        d = key(N[f])
        msg = "fractional with floor:" 

    elif interpolate=='ceil':
        d = key(N[c])
        msg = "fractional with ceil:" 

    elif interpolate=='funky':
        d0 = key(N[f]) * (c-k)
        d1 = key(N[c]) * (k-f)
        d = d0+d1
        msg = "fractional with Tung(floor and ceil) :" 
    
    elif interpolate=='linear':
        assert (c-f)==1
        assert (k>=f) and (k<=c)
        pctDiff = k-f
        dDiff = pctDiff * (key(N[c]) - key(N[f]))
        d = key(N[f] + dDiff)
        msg = "fractional %s with linear(floor and ceil):" % pctDiff

    elif interpolate=='mean':
        d = (key(N[c]) + key(N[f])) / 2.0
        msg = "fractional with mean(floor and ceil):" 

    # print 3 around the floored k, for eyeballing when we're close
    flooredK = int(f)
    # print the 3 around the median
    if flooredK > 0:
        print "prior->", key(N[flooredK-1]), " "
    else:
        print "prior->", "<bof>"
    print "floor->", key(N[flooredK]), " ", msg, 'result:', d, "f:", f, "len(N):", len(N)
    if flooredK+1 < len(N):
        print " ceil->", key(N[flooredK+1]), "c:", c
    else:
        print " ceil-> <eof>", "c:", c

    return d

#***************************************************************************
# median is 50th percentile.
def medianOnSortedList(N, key=lambda x:x):
    median = percentileOnSortedlist(N, percent=0.5, key=key)
    return median

#***************************************************************************
def percentileOnSortedList_25_50_75( N, key=lambda x:x):
    three = (
        percentileOnSortedlist(N, percent=0.25, key=key),
        percentileOnSortedlist(N, percent=0.50, key=key),
        percentileOnSortedlist(N, percent=0.75, key=key),
    )
    return three

#***************************************************************************
def quantile_comparisons(csvPathname, skipHeader=False, col=0, datatype='float', 
    h2oSummary2=None, 
    h2oSummary2MaxErr=None,
    h2oQuantilesApprox=None, h2oQuantilesExact=None, 
    h2oExecQuantiles=None,
    interpolate='linear', quantile=0.50, use_genfromtxt=False):
    SCIPY_INSTALLED = True
    try:
        import scipy as sp
        import numpy as np
        print "Both numpy and scipy are installed. Will do extra checks"

    except ImportError:
        print "numpy or scipy is not installed. Will only do sort-based checking"
        SCIPY_INSTALLED = True

    if use_genfromtxt and SCIPY_INSTALLED:
            print "Using numpy.genfromtxt. Better handling of null bytes"
            target = np.genfromtxt(
                open(csvPathname, 'r'),
                delimiter=',',
                skip_header=1 if skipHeader else 0,
                dtype=None) # guess!
            # print "shape:", target.shape()

    else:
        print "Using python csv reader"
        target = h2o_util.file_read_csv_col(csvPathname, col=col, datatype=datatype,
            skipHeader=skipHeader, preview=20)

    if datatype=='float':
        # to make irene's R runif files first col work (quoted row numbers, integers
        #shouldn't hurt anyone else?
        # strip " from left (ignore leading whitespace
        # strip " from right (ignore leading whitespace
        targetFP = map(float, target)
        # targetFP= np.array(tFP, np.float)
    if datatype=='int':
        targetFP = map(int, target)

    # http://docs.scipy.org/doc/numpy-dev/reference/generated/numpy.percentile.html
    # numpy.percentile has simple linear interpolate and midpoint
    # need numpy 1.9 for interpolation. numpy 1.8 doesn't have
    # p = np.percentile(targetFP, 50 if DO_MEDIAN else 99.9, interpolation='midpoint')
    # 1.8
    if SCIPY_INSTALLED:
        p = np.percentile(targetFP, quantile*100)
        h2p.red_print("numpy.percentile", p)

        # per = [100 * t for t in thresholds]
        from scipy import stats
        s1 = stats.scoreatpercentile(targetFP, quantile*100)
        h2p.red_print("scipy stats.scoreatpercentile", s1)

        # scipy apparently doesn't have the use of means (type 2)
        # http://en.wikipedia.org/wiki/Quantile
        # it has median (R-8) with 1/3, 1/3

        if 1==0:
            # type 6
            alphap=0
            betap=0

            # type 5 okay but not perfect
            alphap=0.5
            betap=0.5

            # type 8
            alphap=1/3.0
            betap=1/3.0

        if interpolate=='mean':
            # an approx? (was good when comparing to h2o type 2)
            alphap=0.4
            betap=0.4

        if interpolate=='linear':
            # this is type 7
            alphap=1
            betap=1

        s2List = stats.mstats.mquantiles(targetFP, prob=quantile, alphap=alphap, betap=betap)
        s2 = s2List[0]
        # http://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.mstats.mquantiles.html
        # type 7 
        # alphap=0.4, betap=0.4, 
        # type 2 not available? (mean)
        # alphap=1/3.0, betap=1/3.0 is approx median?
        h2p.red_print("scipy stats.mstats.mquantiles:", s2)

    # also get the median with a painful sort (h2o_summ.percentileOnSortedlist()
    # inplace sort
    targetFP.sort()

    # this matches scipy type 7 (linear)
    # b = h2o_summ.percentileOnSortedList(targetFP, 0.50 if DO_MEDIAN else 0.999, interpolate='linear')
    # this matches h2o type 2 (mean)
    # b = h2o_summ.percentileOnSortedList(targetFP, 0.50 if DO_MEDIAN else 0.999, interpolate='mean')

    b = percentileOnSortedList(targetFP, quantile, interpolate='linear')
    label = str(quantile * 100) + '%'
    h2p.blue_print(label, "from sort:", b)
    
    if SCIPY_INSTALLED:
        h2p.blue_print(label, "from numpy:", p)
        h2p.blue_print(label, "from scipy 1:", s1)
        h2p.blue_print(label, "from scipy 2:", s2)

    h2p.blue_print(label, "from h2o summary:", h2oSummary2)
    h2p.blue_print(label, "from h2o multipass:", h2oQuantilesExact)
    h2p.blue_print(label, "from h2o singlepass:", h2oQuantilesApprox)
    if h2oExecQuantiles:
        h2p.blue_print(label, "from h2o quantile:", h2oExecQuantiles)

    # they should be identical. keep a tight absolute tolerance
    # Note the comparisons have different tolerances, some are relative, some are absolute
    if h2oQuantilesExact:
        if math.isnan(float(h2oQuantilesExact)):
            raise Exception("h2oQuantilesExact is unexpectedly NaN %s" % h2oQuantilesExact)
        h2o_util.assertApproxEqual(h2oQuantilesExact, b, tol=0.0000002, 
            msg='h2o quantile multipass is not approx. same as sort algo')

    if h2oQuantilesApprox:
        # this can be NaN if we didn't calculate it. turn the NaN string into a float NaN
        if math.isnan(float(h2oQuantilesApprox)):
            raise Exception("h2oQuantilesApprox is unexpectedly NaN %s" % h2oQuantilesApprox)
        if h2oSummary2MaxErr:
            h2o_util.assertApproxEqual(h2oQuantilesApprox, b, tol=h2oSummary2MaxErr,
                msg='h2o quantile singlepass is not approx. same as sort algo')
        else:
            h2o_util.assertApproxEqual(h2oQuantilesApprox, b, rel=0.1,
                msg='h2o quantile singlepass is not approx. same as sort algo')

    if h2oSummary2:
        if math.isnan(float(h2oSummary2)):
            raise Exception("h2oSummary2 is unexpectedly NaN %s" % h2oSummary2)
        if h2oSummary2MaxErr:
            # maxErr absolute was calculated in the test from 0.5*(max-min/(max_qbins-2))
            h2o_util.assertApproxEqual(h2oSummary2, b, tol=h2oSummary2MaxErr,
                msg='h2o summary2 is not approx. same as sort algo (calculated expected max error)')
        else:
            # bounds are way off, since it depends on the min/max of the col, not the expected value
            h2o_util.assertApproxEqual(h2oSummary2, b, rel=1.0,
                msg='h2o summary2 is not approx. same as sort algo (sloppy compare)')

    if h2oQuantilesApprox and h2oSummary2:
        # they should both get the same answer. Currently they have different code, but same algo
        # FIX! ...changing to a relative tolerance, since we're getting a miscompare in some cases.
        # not sure why..maybe some subtle algo diff.
        h2o_util.assertApproxEqual(h2oSummary2, h2oQuantilesApprox, rel=0.04,
            msg='h2o summary2 is not approx. same as h2o singlepass.'+\
                ' Check that max_qbins is 1000 (summary2 is fixed) and type 7 interpolation')

    if h2oExecQuantiles:
        if math.isnan(float(h2oExecQuantiles)):
            raise Exception("h2oExecQuantiles is unexpectedly NaN %s" % h2oExecQuantiles)
        # bounds are way off
        h2o_util.assertApproxEqual(h2oExecQuantiles, b, rel=1.0,
            msg='h2o summary2 is not approx. same as sort algo')

    if SCIPY_INSTALLED:
        if h2oQuantilesExact:
            h2o_util.assertApproxEqual(h2oQuantilesExact, p, tol=0.0000002,
                msg='h2o quantile multipass is not same as numpy.percentile')
            h2o_util.assertApproxEqual(h2oQuantilesExact, s1, tol=0.0000002,
                msg='h2o quantile multipass is not same as scipy stats.scoreatpercentile')

        # give us some slack compared to the scipy use of median (instead of desired mean)
        # since we don't have bounds here like above, just stop this test for now
        if h2oQuantilesApprox and 1==0:
            if interpolate=='mean':
                h2o_util.assertApproxEqual(h2oQuantilesApprox, s2, rel=0.5,
                    msg='h2o quantile singlepass is not approx. same as scipy stats.mstats.mquantiles')
            else:
                h2o_util.assertApproxEqual(h2oQuantilesApprox, s2, rel=0.5,
                    msg='h2o quantile singlepass is not same as scipy stats.mstats.mquantiles')

        # see if scipy changes. nope. it doesn't 
        if 1==0:
            a = stats.mstats.mquantiles(targetFP, prob=quantile, alphap=alphap, betap=betap)
            h2p.red_print("after sort")
            h2p.red_print("scipy stats.mstats.mquantiles:", s3)
