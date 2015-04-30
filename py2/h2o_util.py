import subprocess
import gzip, shutil, random, time, re, copy
import os, zipfile, json, csv
import sys, math
import errno
from h2o_test import verboseprint, dump_json
import h2o_print as h2p

#************************************************************************
# stuff from ray
# list or tuple is okay. so are dicts. strings not okay
# or should we strictly check for list,tuple?
def list_to_dict(l, key):
    # assert not isinstance(l, basestring)
    assert isinstance(l, (list, tuple, dict))
    # print 'list_to_dict key: ', key
    keySplit = key.split("/")
    result = {}
    for v in l:
        # print 'list_to_dict v: ', v
        k = followPath(v, keySplit)
        # print 'list_to_dict k: ', k
        result[k] = v
    print "list_to_dict created dict with %s entries" % len(result)
    return result

# Assertion-type stuff
def make_sure_path_exists(path):
    try:
        os.makedirs(path)
    except OSError as exception:
        if exception.errno != errno.EEXIST:
            raise

# was d mutable to caller before? suppose not if the local assign is a new object.
def followPath(d, path_elems):
    dCopy = copy.deepcopy(d)
    for path_elem in path_elems:
        # print "followPath path_elem:", path_elem
        if "" != path_elem:
            idx = -1
            if path_elem.endswith("]"):
                idx = int(path_elem[path_elem.find("[") + 1 : path_elem.find("]")])
                path_elem = path_elem[:path_elem.find("[")]
            assert path_elem in dCopy, "Failed to find key: " + str(path_elem) + " in dict: " + str(dCopy)

            # does this create a new object so the caller is not affected?
            if -1 == idx:
                dCopy = dCopy[path_elem]
            else:
                dCopy = dCopy[path_elem][idx]

    return dCopy


def assertKeysExist(d, path, keys):
    path_elems = path.split("/")
    d = followPath(d, path_elems)
    for key in keys:
        assert key in d, "Failed to find key: " + key + " in dict: " + repr(d)

def assertKeysExistAndNonNull(d, path, keys):
    path_elems = path.split("/")
    d = followPath(d, path_elems)
    for key in keys:
        assert key in d, "Failed to find key: " + key + " in dict: " + repr(d)
        assert d[key] != None, "Value unexpectedly null: " + key + " in dict: " + repr(d)

def assertKeysDontExist(d, path, keys):
    path_elems = path.split("/")
    d = followPath(d, path_elems)
    for key in keys:
        assert key not in d, "Unexpectedly found key: " + key + " in dict: " + repr(d)



#************************************************************************
# Return file size.
def get_file_size(f):
    return os.path.getsize(f)

# Splits file into chunks of given size and returns an iterator over chunks.
def iter_chunked_file(file, chunk_size=2048):
    return iter(lambda: file.read(chunk_size), '')

# operations to get bit patterns for fp 
# Python internally uses the native endianity and 64-bits for floats
# Java floatToBits is the thing to convert fp to long bits
# if it's real, use this to convert. All reals should match
# long bits = Double.doubleToLongBits(myDouble);
# System.out.println(Long.toBinaryString(bits));

import struct
# Q is unsigned long long. 8 bytes
# d is double float
def doubleToUnsignedLongLong(d):
    s = struct.pack('>d', d)
    return struct.unpack('>Q', s)[0]
# floatToBits(173.3125)
# 1127043072
# hex(_)
# '0x432d5000'

# You can reverse the order of operations to round-trip:
def unsignedLongLongToDouble(Q):
    s = struct.pack('>Q', Q)
    return struct.unpack('>d', s)[0]

# bitsToFloat(0x432d5000)
# 173.3125

# takes fp or list of fp and returns same with just two digits of precision
# using print rounding
def twoDecimals(l):
    if isinstance(l, (list, tuple)):
        return ["%.2f" % v for v in l]
    elif isinstance(l, basestring):
        return "%s" % l
    elif l is None:
        return None
    else:
        return "%.2f" % l

# a short quick version for relative comparion. But it's probably better to use approxEqual below
# the subsequent ones might be prefered, especially assertAlmostEqual(
# http://en.wikipedia.org/wiki/Relative_difference
# http://stackoverflow.com/questions/4028889/floating-point-equality-in-python
# def fp_approxEqual(a, b, rel):
#     c = abs(a-b) / max(abs(a), abs(b))
#     print "actual relative diff: %s allowed relative diff: %s" % (c, rel)
#    return c < rel

# Generic "approximately equal" function for any object type, with customisable error tolerance.
# When called with float arguments, approxEqual(x, y[, tol[, rel]) compares x and y numerically, 
# and returns True if y is within either absolute error tol or relative error rel of x, 
# otherwise return False. 

# The function defaults to sensible default values for tol and rel.
# or any other pair of objects, approxEqual() looks for a method __approxEqual__ and, if found, 
# calls it with arbitrary optional arguments. 
# This allows types to define their own concept of "close enough".
def _float_approxEqual(x, y, tol=1e-18, rel=1e-7, **kwargs):
    if tol is rel is None:
        raise TypeError('cannot specify both absolute and relative errors are None')
    tests = []
    if tol is not None: tests.append(abs(tol))
    if rel is not None: tests.append(abs(rel*x))
    assert tests
    return abs(abs(x) - abs(y)) <= max(tests)

# from http://code.activestate.com/recipes/577124-approximately-equal/
def approxEqual(x, y, *args, **kwargs):
    """approxEqual(float1, float2[, tol=1e-18, rel=1e-7]) -> True|False
    approxEqual(obj1, obj2[, *args, **kwargs]) -> True|False

    Return True if x and y are approximately equal, otherwise False.

    If x and y are floats, return True if y is within either absolute error
    tol or relative error rel of x. You can disable either the absolute or
    relative check by passing None as tol or rel (but not both).

    For any other objects, x and y are checked in that order for a method
    __approxEqual__, and the result of that is returned as a bool. Any
    optional arguments are passed to the __approxEqual__ method.

    __approxEqual__ can return NotImplemented to signal that it doesn't know
    how to perform that specific comparison, in which case the other object is
    checked instead. If neither object have the method, or both defer by
    returning NotImplemented, approxEqual falls back on the same numeric
    comparison used for floats.

    >>> almost_equal(1.2345678, 1.2345677)
    True
    >>> almost_equal(1.234, 1.235)
    False

    """
    if not (type(x) is type(y) is float):
        # Skip checking for __approxEqual__ in the common case of two floats.
        methodname = '__approxEqual__'
        # Allow the objects to specify what they consider "approximately equal",
        # giving precedence to x. If either object has the appropriate method, we
        # pass on any optional arguments untouched.
        for a,b in ((x, y), (y, x)):
            try:
                method = getattr(a, methodname)
            except AttributeError:
                continue
            else:
                result = method(b, *args, **kwargs)
                if result is NotImplemented:
                    print "WARNING: NotImplemented approxEqual for types"
                    continue
                return bool(result)

    # If we get here without returning, then neither x nor y knows how to do an
    # approximate equal comparison (or are both floats). Fall back to a numeric
    # comparison.
    return _float_approxEqual(x, y, *args, **kwargs)

# note this can take 'tol' and 'rel' parms for the float case
# just wraps approxEqual in an assert with a good print message
def assertApproxEqual(x, y, msg='', **kwargs):
    if not approxEqual(x, y, msg=msg, **kwargs):
        m = msg + '. h2o_util.assertApproxEqual failed comparing %s and %s. %s.' % (x, y, kwargs)
        raise Exception(m)

def cleanseInfNan(value):
    # change the strings returned in h2o json to the IEEE number values
    translate = {
        'NaN': float('NaN'),
        'Infinity': float('Inf'),
        '-Infinity': -float('Inf'),
    }
    if str(value) in translate:
        value = translate[str(value)]
    return value


# use a random or selected fp format from the choices
# for testing different fp representations
# 'only' can be e, f or g, to restrict the choices
# it will wrap the 0-47 until the group (modulo e)
def fp_format(val=None, sel=None, only=None):

    def e0(val): return "%e" % val
    def e1(val): return "%20e" % val
    def e2(val): return "%-20e" % val
    def e3(val): return "%020e" % val
    def e4(val): return "%+e" % val
    def e5(val): return "%+20e" % val
    def e6(val): return "%+-20e" % val
    def e7(val): return "%+020e" % val
    def e8(val): return "%.4e" % val
    def e9(val): return "%20.4e" % val
    def e10(val): return "%-20.4e" % val
    def e11(val): return "%020.4e" % val
    def e12(val): return "%+.4e" % val
    def e13(val): return "%+20.4e" % val
    def e14(val): return "%+-20.4e" % val
    def e15(val): return "%+020.4e" % val

    def f0(val): return "%f" % val
    def f1(val): return "%20f" % val
    def f2(val): return "%-20f" % val
    def f3(val): return "%020f" % val
    def f4(val): return "%+f" % val
    def f5(val): return "%+20f" % val
    def f6(val): return "%+-20f" % val
    def f7(val): return "%+020f" % val
    def f8(val): return "%.4f" % val
    def f9(val): return "%20.4f" % val
    def f10(val): return "%-20.4f" % val
    def f11(val): return "%020.4f" % val
    def f12(val): return "%+.4f" % val
    def f13(val): return "%+20.4f" % val
    def f14(val): return "%+-20.4f" % val
    def f15(val): return "%+020.4f" % val

    def g0(val): return "%g" % val
    def g1(val): return "%20g" % val
    def g2(val): return "%-20g" % val
    def g3(val): return "%020g" % val
    def g4(val): return "%+g" % val
    def g5(val): return "%+20g" % val
    def g6(val): return "%+-20g" % val
    def g7(val): return "%+020g" % val
    def g8(val): return "%.4g" % val
    def g9(val): return "%20.4g" % val
    def g10(val): return "%-20.4g" % val
    def g11(val): return "%020.4g" % val
    def g12(val): return "%+.4g" % val
    def g13(val): return "%+20.4g" % val
    def g14(val): return "%+-20.4g" % val
    def g15(val): return "%+020.4g" % val

    # try a neat way to use a dictionary to case select functions
    # didn't want to use python advanced string format with variable as format
    # because they do left/right align outside of that??
    caseList=[
        e0, e1, e2, e3, e4, e5, e6, e7, e8, e9, e10, e11, e12, e13, e14, e15,
        f0, f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15,
        g0, g1, g2, g3, g4, g5, g6, g7, g8, g9, g10, g11, g12, g13, g14, g15,
        ]

    if not val:
        return len(caseList)

    if sel:
        if sel<0 or sel>=len(caseList):
            raise Exception("sel out of range in write_syn_dataset:", sel)
        choice = sel
    else:
        # pick one randomly if no sel
        choice = random.randint(0,len(caseList)-1)
        # print "Using fp format case", choice


    SUBGRPS = 3
    SUBGRP_SIZE = len(caseList) / SUBGRPS
    # should be int
    assert math.floor(SUBGRP_SIZE)==SUBGRP_SIZE, "You got a code problem in h2o_util.fp_format"

    if only:
        # make choice modulo 3 (can update if more subgroups are added
        choice = choice % SUBGRPS
        assert choice >= 0 and choice < SUBGRP_SIZE
        # now add a base offset = subgrou size. 
        # (assume we keep in sync with the subgroup sizes above)
        if only=='e':
            choice += 0
        elif only=='f':
            choice += 16
        elif only=='g':
            choice += 32
        else:
            # if a random choice, we should never get here because it's bounded to length of the list above
            raise Exception("Bad param combo of only: %s and sel: % in h2o_util.fp_format()" % (only, sel))

    f = caseList[choice]

    return f(val)


# http://eli.thegreenplace.net/2010/01/22/weighted-random-generation-in-python/
# given [2, 3, 5] it returns 0 (the index of the first element) with probability 0.2, 
# 1 with probability 0.3 and 2 with probability 0.5. 
# The weights need not sum up to anything in particular, and can actually be 
# arbitrary Python floating point numbers.

# The weights need to cover the whole list? otherwise you don't get the rest of the choises
#     random_data = [6,7,8]
#     weights = [2,3,5]
#     d = random_data[h2o_util.weighted_choice(weights)]
def weighted_choice(weights):
    rnd = random.random() * sum(weights)
    for i, w in enumerate(weights):
        rnd -= w
        if rnd < 0:
            return i

# x = choice_with_probability( [('one',0.25), ('two',0.25), ('three',0.5)] )
# need to sum to 1 or less. check error case if you go negative
def choice_with_probability(tupleList):
    n = random.uniform(0, 1)
    for item, prob in tupleList:
        if n < prob: break
        n = n - prob
        if n < 0: 
            raise Exception("h2o_util.choice_with_probability() error, prob's sum > 1")
    return item

# pick a random param from a dictionary of lists of params
def pickRandParams(paramDict, params):
    randomGroupSize = random.randint(1,len(paramDict))
    for i in range(randomGroupSize):
        randomKey = random.choice(paramDict.keys())
        randomV = paramDict[randomKey]
        randomValue = random.choice(randomV)
        params[randomKey] = randomValue

# this reads a single col out a csv file into a list, without using numpy
# so we can port some jenkins tests without needing numpy
def file_read_csv_col(csvPathname, col=0, skipHeader=True, datatype='float', preview=5):
    # only can skip one header line. numpy provides a number N. could update to that.
    with open(csvPathname, 'rb') as f:
        reader = csv.reader(f, quoting=csv.QUOTE_NONE) # no extra handling for quotes
        print "csv read of", csvPathname, "column", col
        # print "Preview of 1st %s lines:" % preview
        rowNum = 0
        dataList = []
        lastRowLength = None
        try:
            for row in reader:
                if skipHeader and rowNum==0:
                    print "Skipping header in this csv"
                else:
                    NA = False
                    if col > len(row)-1:
                        print "col (zero indexed): %s points past the # entries in this row %s" % (col, row)
                    if lastRowLength and len(row)!=lastRowLength:
                        print "Current row length: %s is different than last row length: %s" % (row, lastRowLength)

                    if col > len(row)-1:
                        colData = None
                    else:
                        colData = row[col]
                    # only print first 5 for seeing
                    # don't print big col cases
                    if rowNum < preview and len(row) <= 10: 
                        print colData
                    dataList.append(colData)
                rowNum += 1
                if rowNum%10==0:
                    # print rowNum
                    pass
                lastRowLength = len(row)
        except csv.Error, e:
            sys.exit('file %s, line %d: %s' % (csvPathname, reader.line_num, e))

        # now we have a list of strings
        # change them to float if asked for, or int
        # elimate empty strings
        if datatype=='float':
            D1 = [float(i) for i in dataList if i]
        if datatype=='int':
            D1 = [int(i) for i in dataList if i]
        print "D1 done"
    return D1

def file_line_count(fname):
    return sum(1 for line in open(fname))

def file_size_formatted(fname):
    size = os.path.getsize(fname)
    print "size:", size
    for x in ['bytes','KB','MB','GB','TB']:
        if size < 1024.0:
            return "%3.1f %s" % (size, x)
        size /= 1024.0
    return "%3.1f %s" % (size, 'TB')

# the logfiles are zipped with directory structure
# unzip it to the zipdir, throwing away the directory structure.
# (so we don't have to know the names of the intermediate directories)
def flat_unzip(my_zip, my_dir):
    resultList = []
    with zipfile.ZipFile(my_zip) as zip_file:
        for member in zip_file.namelist():
            filename = os.path.basename(member)
            # skip directories
            if not filename:
                continue
            # copy file (taken from zipfile's extract)
            source = zip_file.open(member)
            target = file(os.path.join(my_dir, filename), "wb")
            with source, target:
                shutil.copyfileobj(source, target)
                # update to have resultList just be the pathname
                resultList.append(os.path.abspath(target.name))
            source.close()
            target.close()
    return resultList

# gunzip gzfile to outfile
def file_gunzip(gzfile, outfile):
    print "\nGunzip-ing", gzfile, "to", outfile
    start = time.time()
    zipped_file = gzip.open(gzfile, 'rb')
    out_file = open(outfile, 'wb')
    out_file.writelines(zipped_file)
    out_file.close()
    zipped_file.close()
    print "\nGunzip took",  (time.time() - start), "secs"

# gzip infile to gzfile
def file_gzip(infile, gzfile):
    print "\nGzip-ing", infile, "to", gzfile
    start = time.time()
    in_file = open(infile, 'rb')
    zipped_file = gzip.open(gzfile, 'wb')
    zipped_file.writelines(in_file)
    in_file.close()
    zipped_file.close()
    print "\nGzip took",  (time.time() - start), "secs"


# cat file1 and file2 to outfile
def file_cat(file1, file2, outfile):
    print "\nCat'ing", file1, file2, "to", outfile
    start = time.time()
    destination = open(outfile,'wb')
    shutil.copyfileobj(open(file1,'rb'), destination)
    shutil.copyfileobj(open(file2,'rb'), destination)
    destination.close()
    print "\nCat took",  (time.time() - start), "secs"

# used in loop, so doing always print
def file_append(infile, outfile):
    verboseprint("\nAppend'ing", infile, "to", outfile)
    start = time.time()
    in_file = open(infile,'rb')
    out_file = open(outfile,'a')
    out_file.write(in_file.read())
    in_file.close()
    out_file.close()
    verboseprint("\nAppend took",  (time.time() - start), "secs")


def file_shuffle(infile, outfile):
    print "\nShuffle'ing", infile, "to", outfile
    start = time.time()
#    lines = open(infile).readlines()
#    random.shuffle(lines)
#    open(outfile, 'w').writelines(lines)
    fi = open(infile, 'r')
    fo = open(outfile, 'w')
    subprocess.call(["sort", "-R"],stdin=fi, stdout=fo)
    print "\nShuffle took",  (time.time() - start), "secs"
    fi.close()
    fo.close()


# FIX! This is a hack to deal with parser bug
def file_strip_trailing_spaces(csvPathname1, csvPathname2):
    infile = open(csvPathname1, 'r')
    outfile = open(csvPathname2,'w') # existing file gets erased
    for line in infile.readlines():
        # remove various lineends and whitespace (leading and trailing)
        # make it unix linend
        outfile.write(line.strip(" \n\r") + "\n")
    infile.close()
    outfile.close()
    print "\n" + csvPathname1 + " stripped to " + csvPathname2

# can R deal with comments in a csv?
def file_strip_comments(csvPathname1, csvPathname2):
    infile = open(csvPathname1, 'r')
    outfile = open(csvPathname2,'w') # existing file gets erased
    for line in infile.readlines():
        if not line.startswith('#'): outfile.write(line)
    infile.close()
    outfile.close()
    print "\n" + csvPathname1 + " w/o comments to " + csvPathname2

def file_spaces_to_comma(csvPathname1, csvPathname2):
    infile = open(csvPathname1, 'r')
    outfile = open(csvPathname2,'w') # existing file gets erased
    for line in infile.readlines():
        outfile.write(re.sub(r' +',r',',line))
    infile.close()
    outfile.close()
    print "\n" + csvPathname1 + " with space(s)->comma to " + csvPathname2

# UPDATE: R seems to be doing some kind of expand_cat on cols with '.' in them for NA
# (the umass/princeton) data sets. Change to 0 for now so both H2O and R use them the 
# same way
def file_clean_for_R(csvPathname1, csvPathname2):
    infile = open(csvPathname1, 'r')
    outfile = open(csvPathname2,'w') # existing file gets erased
    for line in infile.readlines():
        # 1) remove comments and header???
        # ignore lines with NA? cheap hack ,, doesn't work for end cols
        # if not ',,' in line and not line.startswith('#') and not re.match('[A-Za-z]+',line):
        if not line.startswith('#') and not re.match('[A-Za-z]+',line):
            # 2) remove various lineends and whitespace (leading and trailing)..make it unix linend
            line = line.strip(" \n\r") + "\n"
            # 3) change spaces to comma (don't worry about spaces in enums..don't have them for now)
            line = re.sub(r' +',r',',line)
            # 4) change "." fields to 0
            line = re.sub(',\.,',',0,',line) # middle of line
            line = re.sub('^\.,','0,',line)  # beginning of line
            line = re.sub(',\.$',',0',line)  # end of line
            outfile.write(line)
    infile.close()
    outfile.close()
    print "\n" + csvPathname1 + " cleaned for R to " + csvPathname2


# this might be slightly pessimistic, but should be superset
def might_h2o_think_whitespace(token):
    # we allow $ prefix and % suffix as decorators to numbers?
    whitespaceRegex = re.compile(r"""
        \s*$     # begin, white space or empty space, end
        """, re.VERBOSE)
    if whitespaceRegex.match(token):
        return True
    else:
        return False


# this might be slightly pessimistic, but should be superset
def might_h2o_think_number_or_whitespace(token):
    # this matches white space? makes all white space count as number?
    specialRegex = re.compile(r"""
        \s*
        [\$+-]? # single chars that might be considered numbers. alow spaces in between
        \s*$ 
        """, re.VERBOSE)

    # this matches white space? makes all white space count as number?
    number1Regex = re.compile(r"""
        [\s\$\%]*     # begin, white space or empty space. any number of leading % or $ too
        [+-]?    # plus or minus. maybe h2o matches multiple?
        ([0-9]*\.[0-9]*)?  # decimal point focused. optional whole and fractional digits. h2o thinks whole thing optional?
        ([eE][-+]*[0-9]+)? # optional exponent. A single e matches (incorrectly) apparently repeated +- after the e doesn't matter
        (\s*\[\% ]*)? # can have zero or more percent. Percent can have a space?
        [\s\$\%]*$     # white space or empty space, any number of trailing % or $ too. end
        """, re.VERBOSE)


    # apparently these get detected as number
    # +e+++10
    # +e---10

    # this matches white space? makes all white space count as number?
    number2Regex = re.compile(r"""
        [\s\$\%]*     # begin, white space or empty space. any number of leading % or $ too
        [+-]?    # plus or minus. maybe h2o matches multiple?
        ([0-9]+)? # one or more digits. h2o thinks whole thing optional
        (\.[0-9]*)? # optional decimal point and fractional digits
        ([eE][-+]*[0-9]+)? # optional exponent. a single e matches (incorrectly) apparently repeated +- after the e doesn't matter
        (\s*\[\% ]*)? # can have zero or more percent. Percent can have a space?
        [\s\$\%]*$     # white space or empty space, any number of trailing % or $ too. end
        """, re.VERBOSE)

    # can nans have the +-%$ decorators?. allow any case?
    nanRegex = re.compile(r"""
        [\s\$\%]*     # begin, white space or empty space. any number of leading % or $ too
        [+-]?    # plus or minus
        [Nn][Aa][Nn]? # nan or na
        (\s*\[\% ]*)? # can have zero or more percent. Percent can have a space?
        [\s\$\%]*$     # white space or empty space, any number of trailing % or $ too. end
        """, re.VERBOSE)

    if specialRegex.match(token) or number1Regex.match(token) or number2Regex.match(token) or nanRegex.match(token):
        return True
    else:
        return False

# from nmb10 at http://djangosnippets.org/snippets/2247/
# Shows difference between two json like python objects. 
# Shows properties, values from first object that are not in the second.
# Examples:
# import json # or other json serializer
# first = json.loads('{"first_name": "Poligraph", "last_name": "Sharikov",}')
# second = json.loads('{"first_name": "Poligraphovich", "pet_name": "Sharik"}')
# df = JsonDiff(first, second)
# df.difference is ["path: last_name"]
# JsonDiff(first, second, vice_versa=True) gives you difference from both objects in the one result.
# df.difference is ["path: last_name", "path: pet_name"]
# JsonDiff(first, second, with_values=True) gives you difference of the values strings. 
class JsonDiff(object):
    def __init__(self, first, second, with_values=False, vice_versa=False):
        self.difference = []
        self.check(first, second, with_values=with_values)
        if vice_versa:
            self.check(second, first, with_values=with_values)
        
    def check(self, first, second, path='', with_values=False):
        if second != None:
            if not isinstance(first, type(second)):
                message = '%s- %s, %s' % (path, type(first), type(second))
                TYPE = None
                self.save_diff(message, TYPE)

        if isinstance(first, dict):
            for key in first:
                # the first part of path must not have trailing dot.
                if len(path) == 0:
                    new_path = key
                else:
                    new_path = "%s.%s" % (path, key)

                if isinstance(second, dict):
                    if second.has_key(key):
                        sec = second[key]
                    else:
                        #  there are key in the first, that is not presented in the second
                        PATH = None
                        self.save_diff(new_path, PATH)
                        # prevent further values checking.
                        sec = None

                    # recursive call
                    self.check(first[key], sec, path=new_path, with_values=with_values)
                else:
                    # second is not dict. every key from first goes to the difference
                    PATH = None
                    self.save_diff(new_path, PATH)                
                    self.check(first[key], second, path=new_path, with_values=with_values)
                
        # if object is list, loop over it and check.
        elif isinstance(first, list):
            for (index, item) in enumerate(first):
                new_path = "%s[%s]" % (path, index)
                # try to get the same index from second
                sec = None
                if second != None:
                    try:
                        sec = second[index]
                    except (IndexError, KeyError):
                        # goes to difference
                        TYPE = None
                        self.save_diff('%s - %s, %s' % (new_path, type(first), type(second)), TYPE)
                # recursive call
                self.check(first[index], sec, path=new_path, with_values=with_values)

        # not list, not dict. check for equality (only if with_values is True) and return.
        else:
            if with_values and second != None:
                if first != second:
                    self.save_diff('%s - %s | %s' % (path, first, second), 'diff')
        return
            
    def save_diff(self, diff_message, type_):
        message = '%s: %s' % (type_, diff_message)
        if diff_message not in self.difference:
            self.difference.append(message)

# per Alex Kotliarov
# http://stackoverflow.com/questions/2343535/easiest-way-to-serialize-a-simple-class-object-with-json
#This function will produce JSON-formatted string for
#    an instance of a custom class,
#    a dictionary that have instances of custom classes as leaves,
#    a list of instances of custom classes
# added depth limiting to original
def json_repr(obj, curr_depth=0, max_depth=4):
    """Represent instance of a class as JSON.
    Arguments:
    obj -- any object
    Return:
    String that represent JSON-encoded object.
    """
    def serialize(obj, curr_depth):
        """Recursively walk object's hierarchy. Limit to max_depth"""
        if curr_depth>max_depth:
            return
        if isinstance(obj, (bool, int, long, float, basestring)):
            return obj
        elif isinstance(obj, dict):
            obj = obj.copy()
            for key in obj:
                obj[key] = serialize(obj[key], curr_depth+1)
            return obj
        elif isinstance(obj, list):
            return [serialize(item, curr_depth+1) for item in obj]
        elif isinstance(obj, tuple):
            return tuple(serialize([item for item in obj], curr_depth+1))
        elif hasattr(obj, '__dict__'):
            return serialize(obj.__dict__, curr_depth+1)
        else:
            return repr(obj) # Don't know how to handle, convert to string

    return (serialize(obj, curr_depth+1))
    # b = convert_json(a, 'ascii')

    # a = json.dumps(serialize(obj))
    # c = json.loads(a)
      
