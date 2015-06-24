# -*- coding: utf-8 -*-
# import numpy    no numpy cuz windoz
import collections, csv, itertools, os, re, tempfile, uuid, urllib2, sys, urllib
from expr import h2o,ExprNode
import gc


class H2OFrame:

  # Magical count-of-5:   (get 2 more when looking at it in debug mode)
  #  2 for _do_it frame, 2 for _do_it local dictionary list, 1 for parent
  MAGIC_REF_COUNT = 5 if sys.gettrace() is None else 7  # M = debug ? 7 : 5

  def __init__(self, python_obj=None, file_path=None, raw_id=None, expr=None):
    """
    Create a new H2OFrame object by passing a file path or a list of H2OVecs.

    If `remote_fname` is not None, then a REST call will be made to import the
    data specified at the location `remote_fname`.  This path is relative to the
    H2O cluster, NOT the local Python process

    If `python_obj` is not None, then an attempt to upload the python object to H2O
    will be made. A valid python object has type `list`, or `dict`.

    For more information on the structure of the input for the various native python
    data types ("native" meaning non-H2O), please see the general documentation for
    this object.

    :param python_obj: A "native" python object - list, dict, tuple.
    :param remote_fname: A remote path to a data source. Data is cluster-local.
    :param vecs: A list of H2OVec objects.
    :param text_key: A raw key resulting from an upload_file.
    :return: An instance of an H2OFrame object.
    """
    self._id        = H2OFrame.py_tmp_key()  # gets overwritten if a parse happens
    self._nrows     = None
    self._ncols     = None
    self._col_names = None
    self._computed  = False
    self._ast       = None

    if expr is not None:         self._ast = expr
    elif python_obj is not None: self._upload_python_object(python_obj)
    elif file_path is not None:  self._import_parse(file_path)
    elif raw_id:                 self._handle_text_key(raw_id)
    else: raise ValueError("H2OFrame instances require a python object, a file path, or a raw import file identifier.")

  def __str__(self): return self._id

  def _import_parse(self,file_path):
    rawkey = h2o.import_file(file_path)
    setup = h2o.parse_setup(rawkey)
    parse = h2o.parse(setup, H2OFrame.py_tmp_key())  # create a new key
    self._id = parse["job"]["dest"]["name"]
    self._computed=True
    rows = self._nrows = parse['rows']   # FIXME: this returns 0???
    cols = self._col_names = parse['column_names']
    self._ncols = len(cols)
    thousands_sep = h2o.H2ODisplay.THOUSANDS
    if isinstance(file_path, str):
      print "Imported {}. Parsed {} rows and {} cols".format(file_path,thousands_sep.format(rows), thousands_sep.format(len(cols)))
    else:
      h2o.H2ODisplay([["File"+str(i+1),f] for i,f in enumerate(file_path)],None, "Parsed {} rows and {} cols".format(thousands_sep.format(rows), thousands_sep.format(len(cols))))

  def _upload_python_object(self, python_obj):
    """
    Properly handle native python data types. For a discussion of the rules and
    permissible data types please refer to the main documentation for H2OFrame.

    :param python_obj: A tuple, list, dict, collections.OrderedDict
    :return: None
    """
    # [] and () cases -- folded together since H2OFrame is mutable
    if isinstance(python_obj, (list, tuple)): header, data_to_write = _handle_python_lists(python_obj)

    # {} and collections.OrderedDict cases
    elif isinstance(python_obj, (dict, collections.OrderedDict)): header, data_to_write = _handle_python_dicts(python_obj)

    # handle a numpy.ndarray
    # elif isinstance(python_obj, numpy.ndarray):
    #
    #     header, data_to_write = H2OFrame._handle_numpy_array(python_obj)
    else: raise ValueError("`python_obj` must be a tuple, list, dict, collections.OrderedDict. Got: " + str(type(python_obj)))

    if header is None or data_to_write is None: raise ValueError("No data to write")

    #
    ## write python data to file and upload
    #

    # create a temporary file that will be written to
    tmp_handle,tmp_path = tempfile.mkstemp(suffix=".csv")
    tmp_file = os.fdopen(tmp_handle,'wb')
    # create a new csv writer object thingy
    csv_writer = csv.DictWriter(tmp_file, fieldnames=header, restval=None, dialect="excel", extrasaction="ignore", delimiter=",")
    csv_writer.writeheader()                 # write the header
    csv_writer.writerows(data_to_write)      # write the data
    tmp_file.close()                         # close the streams
    self._upload_raw_data(tmp_path, header)  # actually upload the data to H2O
    os.remove(tmp_path)                      # delete the tmp file

  def _handle_text_key(self, text_key):
    """
    Handle result of upload_file
    :param test_key: A key pointing to raw text to be parsed
    :return: Part of the H2OFrame constructor.
    """
    # perform the parse setup
    setup = h2o.parse_setup(text_key)
    # blocking parse, first line is always a header (since "we" wrote the data out)
    parse = h2o.parse(setup, H2OFrame.py_tmp_key(), first_line_is_header=1)
    # a hack to get the column names correct since "parse" does not provide them
    self._id = parse["destination_frame"]["name"]
    self._col_names = cols = parse['column_names'] if parse["column_names"] else ["C" + str(x) for x in range(1,len(parse['vec_ids'])+1)]
    self._ncols = len(cols)
    # set the rows
    self._nrows = rows = parse['rows']
    self._computed=True
    thousands_sep = h2o.H2ODisplay.THOUSANDS
    print "Uploaded {} into cluster with {} rows and {} cols".format(text_key, thousands_sep.format(rows), thousands_sep.format(len(cols)))

  def _upload_raw_data(self, tmp_file_path, column_names):
    # file upload info is the normalized path to a local file
    fui = {"file": os.path.abspath(tmp_file_path)}
    # create a random name for the data
    dest_key = H2OFrame.py_tmp_key()
    # do the POST -- blocking, and "fast" (does not real data upload)
    h2o.H2OConnection.post_json("PostFile", fui, destination_frame=dest_key)
    # actually parse the data and setup self._vecs
    self._handle_text_key(dest_key)

  def __iter__(self):
    """
    Allows for list comprehensions over an H2OFrame

    :return: An iterator over the H2OFrame
    """
    self._eager()
    ncol = self._ncols
    return (self[i] for i in range(ncol))

  def logical_negation(self): H2OFrame(expr=ExprNode("not", self))

  # ops
  def __add__ (self, i): return H2OFrame(expr=ExprNode("+",   self,i))
  def __sub__ (self, i): return H2OFrame(expr=ExprNode("-",   self,i))
  def __mul__ (self, i): return H2OFrame(expr=ExprNode("*",   self,i))
  def __div__ (self, i): return H2OFrame(expr=ExprNode("/",   self,i))
  def __mod__ (self, i): return H2OFrame(expr=ExprNode("mod", self,i))
  def __or__  (self, i): return H2OFrame(expr=ExprNode("|",   self,i))
  def __and__ (self, i): return H2OFrame(expr=ExprNode("&",   self,i))
  def __ge__  (self, i): return H2OFrame(expr=ExprNode(">=",  self,i))
  def __gt__  (self, i): return H2OFrame(expr=ExprNode(">",   self,i))
  def __le__  (self, i): return H2OFrame(expr=ExprNode("<=",  self,i))
  def __lt__  (self, i): return H2OFrame(expr=ExprNode("<",   self,i))
  def __eq__  (self, i): return H2OFrame(expr=ExprNode("==",  self,i))
  def __ne__  (self, i): return H2OFrame(expr=ExprNode("!=",  self,i))
  def __pow__ (self, i): return H2OFrame(expr=ExprNode("^",   self,i))
  # rops
  def __rmod__(self, i): return H2OFrame(expr=ExprNode("mod",i,self))
  def __radd__(self, i): return self.__add__(i)
  def __rsub__(self, i): return H2OFrame(expr=ExprNode("-",i,  self))
  def __rand__(self, i): return self.__and__(i)
  def __ror__ (self, i): return self.__or__ (i)
  def __rdiv__(self, i): return H2OFrame(expr=ExprNode("/",i,  self))
  def __rmul__(self, i): return self.__mul__(i)
  def __rpow__(self, i): return H2OFrame(expr=ExprNode("^",i,  self))
  # unops
  def __abs__ (self):    return H2OFrame(expr=ExprNode("abs",self))

  def cos(self)     :    return H2OFrame(expr=ExprNode("cos", self))
  def sin(self)     :    return H2OFrame(expr=ExprNode("sin", self))
  def tan(self)     :    return H2OFrame(expr=ExprNode("tan", self))
  def acos(self)    :    return H2OFrame(expr=ExprNode("acos", self))
  def asin(self)    :    return H2OFrame(expr=ExprNode("asin", self))
  def atan(self)    :    return H2OFrame(expr=ExprNode("atan", self))
  def cosh(self)    :    return H2OFrame(expr=ExprNode("cosh", self))
  def sinh(self)    :    return H2OFrame(expr=ExprNode("sinh", self))
  def tanh(self)    :    return H2OFrame(expr=ExprNode("tanh", self))
  def acosh(self)   :    return H2OFrame(expr=ExprNode("acosh", self))
  def asinh(self)   :    return H2OFrame(expr=ExprNode("asinh", self))
  def atanh(self)   :    return H2OFrame(expr=ExprNode("atanh", self))
  def cospi(self)   :    return H2OFrame(expr=ExprNode("cospi", self))
  def sinpi(self)   :    return H2OFrame(expr=ExprNode("sinpi", self))
  def tanpi(self)   :    return H2OFrame(expr=ExprNode("tanpi", self))
  def abs(self)     :    return H2OFrame(expr=ExprNode("abs", self))
  def sign(self)    :    return H2OFrame(expr=ExprNode("sign", self))
  def sqrt(self)    :    return H2OFrame(expr=ExprNode("sqrt", self))
  def trunc(self)   :    return H2OFrame(expr=ExprNode("trunc", self))
  def ceil(self)    :    return H2OFrame(expr=ExprNode("ceiling", self))
  def floor(self)   :    return H2OFrame(expr=ExprNode("floor", self))
  def log(self)     :    return H2OFrame(expr=ExprNode("log", self))
  def log10(self)   :    return H2OFrame(expr=ExprNode("log10", self))
  def log1p(self)   :    return H2OFrame(expr=ExprNode("log1p", self))
  def log2(self)    :    return H2OFrame(expr=ExprNode("log2", self))
  def exp(self)     :    return H2OFrame(expr=ExprNode("exp", self))
  def expm1(self)   :    return H2OFrame(expr=ExprNode("expm1", self))
  def gamma(self)   :    return H2OFrame(expr=ExprNode("gamma", self))
  def lgamma(self)  :    return H2OFrame(expr=ExprNode("lgamma", self))
  def digamma(self) :    return H2OFrame(expr=ExprNode("digamma", self))
  def trigamma(self):    return H2OFrame(expr=ExprNode("trigamma", self))


  @staticmethod
  def mktime(year=1970,month=0,day=0,hour=0,minute=0,second=0,msec=0):
    """
    All units are zero-based (including months and days).  Missing year is 1970.

    :return: Returns msec since the Epoch.
    """
    raise NotImplementedError
    # Some error checking on length
    # xlen = -1
    # for x in [msec,second,minute,hour,day,month,year]:
    #   if not isinstance(x,int):
    #     l2 = len(x)
    #     if xlen != l2:
    #       if xlen == -1: xlen = l2
    #       else:  raise ValueError("length of "+str(xlen)+" not compatible with "+str(l2))
    # e = None
    # for x in [msec,second,minute,hour,day,month,year]:
    #   x = Expr(x) if isinstance(x,int) else x
    #   e = Expr(",", x, e)
    # e2 = Expr("mktime",e,None,xlen)
    # return e2 if xlen==1 else H2OVec("mktime",e2)

  def col_names(self):
    """
    Retrieve the column names (one name per H2OVec) for this H2OFrame.

    :return: A character list[] of column names.
    """
    self._eager()
    return self._col_names

  def sd(self):
    """
    :return: Standard deviation of the H2OVec elements.
    """
    raise NotImplementedError
    # return Expr("sd", self._expr).eager()

  def names(self):
    """
    Retrieve the column names (one name per H2OVec) for this H2OFrame.

    :return: A character list[] of column names.
    """
    self._eager()
    return self.col_names()

  def nrow(self):
    """
    Get the number of rows in this H2OFrame.

    :return: The number of rows in this dataset.
    """
    self._eager()
    return self._nrows

  def ncol(self):
    """
    Get the number of columns in this H2OFrame.

    :return: The number of columns in this H2OFrame.
    """
    self._eager()
    return self._ncols

  def filterNACols(self, frac=0.2):
    """
    Filter columns with prportion of NAs >= frac.
    :param frac: Fraction of NAs in the column.
    :return: A  list of column indices.
    """
    fr = H2OFrame(ExprNode("filterNACols"), self, frac)
    fr._eager()
    return fr

  def dim(self):
    """
    Get the number of rows and columns in the H2OFrame.

    :return: The number of rows and columns in the H2OFrame as a list [rows, cols].
    """
    return [self.nrow(), self.ncol()]

  def show(self): self.head(10,sys.maxint)  # all columns

  def head(self, rows=10, cols=200, **kwargs):
    """
    Analgous to R's `head` call on a data.frame. Display a digestible chunk of the H2OFrame starting from the beginning.

    :param rows: Number of rows to display.
    :param cols: Number of columns to display.
    :param kwargs: Extra arguments passed from other methods.
    :return: None
    """
    self._eager()
    nrows = min(self.nrow(), rows)
    ncols = min(self.ncol(), cols)
    colnames = self.names()[0:ncols]
    head = self[0:10,:]
    res = head.as_data_frame(False)[1:]
    print "First {} rows and first {} columns: ".format(nrows, ncols)
    h2o.H2ODisplay(res,["Row ID"]+colnames)

  def _scalar(self):
    res = self.as_data_frame(False)
    res = res[1][0]
    try:    return float(res)
    except: return res

  def tail(self, rows=10, cols=200, **kwargs):
    """
    Analgous to R's `tail` call on a data.frame. Display a digestible chunk of the H2OFrame starting from the end.

    :param rows: Number of rows to display.
    :param cols: Number of columns to display.
    :param kwargs: Extra arguments passed from other methods.
    :return: None
    """
    self._eager()
    nrows = min(self.nrow(), rows)
    ncols = min(self.ncol(), cols)
    colnames = self.names()[0:ncols]
    start_idx = max(self.nrow()-nrows,0)
    tail = self[start_idx:nrows,:]
    res = tail.as_data_frame(False)
    print "Last {} rows and first {} columns: ".format(nrows,ncols)
    h2o.H2ODisplay(res,["Row ID"]+colnames)

  def levels(self, col=0):
    """
    Get the factor levels for this frame and the specified column index.

    :param col: A column index in this H2OFrame.
    :return: a list of strings that are the factor levels for the column.
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # if col < 0: col = 0
    # if col >= self.ncol(): col = self.ncol() - 1
    # vec = self._vecs[col]
    # res = H2OConnection.get_json("Frames/{}/columns/{}/domain".format(urllib.quote(vec._expr.eager()), "C1"))
    # return res["domain"][0]

  def nlevels(self, col=0):
    """
    Get the number of factor levels for this frame and the specified column index.

    :param col: A column index in this H2OFrame.
    :return: an integer.
    """
    nlevels = self.levels(col=col)
    return len(nlevels) if nlevels else 0

  def setLevel(self, level):
    """
    A method to set all column values to one of the levels.
    :param level: The level at which the column will be set (a string)
    :return: An H2OFrame with all entries set to the desired level
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # if len(self) != 1: raise(ValueError, "`setLevel` can only be called on a single H2OVec or an H2OFrame with "
    #                                      "one column")
    # return H2OFrame(vecs=[self[0].setLevel(level=level)])

  def setLevels(self, levels):
    """
    Works on a single categorical vector. New domains must be aligned with the old domains. This call has SIDE
    EFFECTS and mutates the column in place (does not make a copy).
    :param level: The level at which the column will be set (a string)
    :param x: A single categorical column.
    :param levels: A list of strings specifying the new levels. The number of new levels must match the number of
    old levels.
    :return: None
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # if len(self) != 1: raise(ValueError, "`setLevels` can only be called on a single H2OVec or an H2OFrame with "
    #                                      "one column")
    # self[0].setLevels(levels=levels)

  def setNames(self,names):
    """
    Change the column names to `names`.

    :param names: A list of strings equal to the number of columns in the H2OFrame.
    :return: None. Rename the column names in this H2OFrame.
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # if not names or not isinstance(names,list):
    #   raise ValueError("names parameter must be a list of strings")
    # if len(names) != self.ncol():
    #   raise ValueError("names parameter must be a list of ncol names")
    # for s in names:
    #   if not isinstance(s,str):
    #     raise ValueError("all names in names parameter must be strings")
    # for name, vec in zip(names,self._vecs):
    #   vec._name = name

  def describe(self):
    """
    Generate an in-depth description of this H2OFrame.

    The description is a tabular print of the type, min, max, sigma, number of zeros,
    and number of missing elements for each H2OVec in this H2OFrame.

    :return: None (print to stdout)
    """
    thousands_sep = h2o.H2ODisplay.THOUSANDS
    print "Rows:", thousands_sep.format(self._nrows), "Cols:", thousands_sep.format(self._ncols)
    chunk_dist_sum = h2o.frame(self._id)["frames"][0]
    dist_summary = chunk_dist_sum["distribution_summary"]
    chunk_summary = chunk_dist_sum["chunk_summary"]
    chunk_summary.show()
    dist_summary.show()
    self.summary()

  def summary(self):
    """
    Generate summary of the frame on a per-Vec basis.
    :return: None
    """
    fr_sum =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(self._id) + "/summary")["frames"][0]
    type = ["type"]
    mins = ["mins"]
    mean = ["mean"]
    maxs = ["maxs"]
    sigma= ["sigma"]
    zeros= ["zero_count"]
    miss = ["missing_count"]
    for v in fr_sum["columns"]:
      type.append(v["type"])
      mins.append(v["mins"][0] if v is not None else v["mins"])
      mean.append(v["mean"])
      maxs.append(v["maxs"][0] if v is not None else v["maxs"])
      sigma.append(v["sigma"])
      zeros.append(v["zero_count"])
      miss.append(v["missing_count"])

    table = [type,mins,maxs,sigma,zeros,miss]
    headers = self._col_names
    h2o.H2ODisplay(table, [""] + headers, "Column-by-Column Summary")

  # def __repr__(self):
  #   if self._vecs is None or self._vecs == []:
  #     raise ValueError("Frame Removed")
  #   self.show()
  #   return ""

  def as_date(self,format):
    """
    Return the column with all elements converted to millis since the epoch.
    :param format: The date time format string
    :return: H2OFrame
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # if len(self) != 1: raise(ValueError, "`setLevels` can only be called on a single H2OVec or an H2OFrame with "
    #                                      "one column")
    # return H2OFrame(vecs=[self[0].as_date(format=format)])

  def as_data_frame(self, use_pandas=True):
    self._eager()
    url = 'http://' + h2o.H2OConnection.ip() + ':' + str(h2o.H2OConnection.port()) + "/3/DownloadDataset?frame_id=" + urllib.quote(self._id) + "&hex_string=false"
    response = urllib2.urlopen(url)
    if h2o.can_use_pandas() and use_pandas:
      import pandas
      return pandas.read_csv(response, low_memory=False)
    else:
      cr = csv.reader(response)
      rows = []
      for row in cr: rows.append(row)
      return rows

  # Find a named H2OVec and return the zero-based index for it.  Error is name is missing
  def _find_idx(self,name):
    for i,v in enumerate(self._col_names):
      if name == v: return i
    raise ValueError("Name " + name + " not in Frame")

  def __getitem__(self, item):
    """
    Frame slicing.
    Supports R-like row and column slicing.

    Examples:
      fr[0:5,:]          # first 5 rows, all columns
      fr[fr[0] > 1, :]   # all rows greater than 1 in the first column, all columns
      fr[[1,5,6]]        # columns 1, 5, and 6
      fr[0:50, [1,2,3]]  # first 50 rows, columns 1,2, and 3

    :param item: A tuple, a list, a string, or an int.
                 If a tuple, then this indicates both row and column selection. The tuple
                 must be exactly length 2.
                 If a list, then this indicates column selection.
                 If a int, the this indicates a single column to be retrieved at the index.
                 If a string, then slice on the column with this name.
    :return: An H2OFrame.
    """
    if isinstance(item, (int,str,list,slice)): return H2OFrame(expr=ExprNode("[", self, None, item))  # just columns
    elif isinstance(item, tuple):
      rows = item[0]
      cols = item[1]
      allrows = False
      allcols = False
      if isinstance(cols, slice):
        allcols = all([a is None for a in [cols.start,cols.step,cols.stop]])
      if isinstance(rows, slice):
        allrows = all([a is None for a in [rows.start,rows.step,rows.stop]])

      if allrows and allcols: return self                              # fr[:,:]    -> all rows and columns.. return self
      if allrows: return H2OFrame(expr=ExprNode("[",self,None,item[1]))  # fr[:,cols] -> really just a column slice
      if allcols: return H2OFrame(expr=ExprNode("[",self,item[0],None))  # fr[rows,:] -> really just a row slices
      return H2OFrame(expr=ExprNode("[", ExprNode("[", self, None, item[1]), item[0], None))

  def __setitem__(self, b, c):
    """
    Replace a column in an H2OFrame.

    :param b: A 0-based index or a column name.
    :param c: The vector that 'b' is replaced with.
    :return: Returns this H2OFrame.
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # #  b is a named column, fish out the H2OVec and its index
    # ncols = len(self._vecs)
    # if isinstance(b, str):
    #   for i in xrange(ncols):
    #     if b == self._vecs[i]._name:
    #       break
    #   else:
    #     i = ncols               # Not found, so append at end
    # # b is a 0-based column index
    # elif isinstance(b, int):
    #   if b < 0 or b > self.__len__():
    #     raise ValueError("Index out of range: 0 <= " + b + " < " + self.__len__())
    #   i = b
    #   b = self._vecs[i]._name
    # else:  raise NotImplementedError
    # self._len_check(c)
    # # R-like behavior: the column name remains the same, even if replacing via index
    # c._name = b
    # if i >= ncols: self._vecs.append(c)
    # else:          self._vecs[i] = c

  # Modifies the collection in-place to remove a named item
  def __delitem__(self, i):
    """
    Remove a vec specified at the index i.

    :param i: The index of the vec to delete.
    :return: The Vec to be deleted.
    """
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # if isinstance(i, str):
    #   return self._vecs.pop(self._find_idx(i))
    raise NotImplementedError

  def __del__(self):
    if self._computed: h2o.remove(self)

  # Makes a new collection
  def drop(self, i):
    """
    Column selection via integer, string(name) returns a Vec
    Column selection via slice returns a subset Frame

    :param i: Column to select
    :return: Returns an H2OVec or H2OFrame.
    """
    raise ValueError("drop: unimpl")
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # # i is a named column
    # if isinstance(i, str):
    #   for v in self._vecs:
    #     if i == v._name:
    #       return H2OFrame(vecs=[v for v in self._vecs if i != v._name])
    #   raise ValueError("Name " + i + " not in Frame")
    # # i is a 0-based column
    # elif isinstance(i, int):
    #   if i < 0 or i >= self.__len__():
    #     raise ValueError("Index out of range: 0 <= " + str(i) + " < " + str(self.__len__()))
    #   return H2OFrame(vecs=[v for v in self._vecs if v._name != self._vecs[i]._name])
    # raise NotImplementedError

  def __len__(self):
    """
    :return: Number of columns in this H2OFrame
    """
    return self.ncol()

  @staticmethod
  def py_tmp_key():
    """
    :return: a unique h2o key obvious from python
    """
    return unicode("py" + str(uuid.uuid4()))

    # @staticmethod
    # def _handle_numpy_array(python_obj):
    #     header = H2OFrame._gen_header(python_obj.shape[1])
    #
    #     as_list = python_obj.tolist()
    #     lol = H2OFrame._is_list_of_lists(as_list)
    #     data_to_write = [dict(zip(header, row)) for row in as_list] \
    #         if lol else [dict(zip(header, as_list))]
    #
    #     return header, data_to_write
  # Quantiles
  def quantile(self, prob=None, combine_method="interpolate"):
    """
    Compute quantiles over a given H2OFrame.

    :param prob: A list of probabilties, default is [0.01,0.1,0.25,0.333,0.5,0.667,0.75,0.9,0.99]. You may provide any sequence of any length.
    :param combine_method: For even samples, how to combine quantiles. Should be one of ["interpolate", "average", "low", "hi"]
    :return: an H2OFrame containing the quantiles and probabilities.
    """
    raise ValueError("quantile: unimpl")
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # if len(self) == 0: return self
    # if not prob: prob=[0.01,0.1,0.25,0.333,0.5,0.667,0.75,0.9,0.99]
    # if not isinstance(prob, list): raise ValueError("prob must be a list")
    # probs = "(dlist #"+" #".join([str(p) for p in prob])+")"
    # if combine_method not in ["interpolate","average","low","high"]:
    #   raise ValueError("combine_method must be one of: [" + ",".join(["interpolate","average","low","high"])+"]")
    #
    # key = self.send_frame()
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (quantile '{}' {} '{}'".format(tmp_key,key,probs,combine_method)
    # h2o.rapids(expr)
    # # Remove h2o temp frame after groupby
    # h2o.removeFrameShallow(key)
    # # Make backing H2OVecs for the remote h2o vecs
    # j = h2o.frame(tmp_key)
    # fr = j['frames'][0]       # Just the first (only) frame
    # rows = fr['rows']         # Row count
    # veckeys = fr['vec_ids']  # List of h2o vec keys
    # cols = fr['columns']      # List of columns
    # colnames = [col['label'] for col in cols]
    # vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
    # h2o.removeFrameShallow(tmp_key)
    # return H2OFrame(vecs=vecs)

  # H2OFrame Mutating cbind
  def cbind(self,data):
    """
    :param data: H2OFrame or H2OVec to cbind to self
    :return: void
    """
    return H2OFrame(expr=ExprNode("cbind", self, data))

  def split_frame(self, ratios=[0.75], destination_frames=None):
    """
    Split a frame into distinct subsets of size determined by the given ratios.
    The number of subsets is always 1 more than the number of ratios given.
    :param data: The dataset to split.
    :param ratios: The fraction of rows for each split.
    :param destination_frames: names of the split frames
    :return: a list of frames
    """
    raise NotImplementedError
    # fr = data.send_frame()
    # if destination_frames is None: destination_frames=""
    # j = H2OConnection.post_json("SplitFrame", dataset=fr, ratios=ratios, destination_frames=destination_frames) #, "Split Frame").poll()
    # splits = []
    # for i in j["destination_frames"]:
    #   splits += [get_frame(i["name"])]
    #   removeFrameShallow(i["name"])
    # removeFrameShallow(fr)
    # return splits

  # ddply in h2o
  def ddply(self,cols,fun):
    """
    :param cols: Column names used to control grouping
    :param fun: Function to execute on each group.  Right now limited to textual Rapids expression
    :return: New frame with 1 row per-group, of results from 'fun'
    """
    raise ValueError("ddply: unimpl")
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # # Confirm all names present in dataset; collect column indices
    # rapids_series = "(llist #"+" #".join([str(self._find_idx(name)) for name in cols])+")"
    #
    # # Eagerly eval and send the cbind'd frame over
    # key = self.send_frame()
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (h2o.ddply %{} {} {}))".format(tmp_key,key,rapids_series,fun)
    # h2o.rapids(expr) # ddply in h2o
    # # Remove h2o temp frame after ddply
    # h2o.removeFrameShallow(key)
    # # Make backing H2OVecs for the remote h2o vecs
    # j = h2o.frame(tmp_key) # Fetch the frame as JSON
    # fr = j['frames'][0]    # Just the first (only) frame
    # rows = fr['rows']      # Row count
    # veckeys = fr['vec_ids']# List of h2o vec keys
    # cols = fr['columns']   # List of columns
    # colnames = [col['label'] for col in cols]
    # vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
    # h2o.removeFrameShallow(tmp_key)
    # return H2OFrame(vecs=vecs)

  def group_by(self,cols,a,order_by=None):
    """
    GroupBy
    :param cols: The columns to group on.
    :param a: A dictionary of aggregates having the following shape: \
    {"colname":[aggregate, column, naMethod]}\
    e.g.: {"bikes":["count", 0, "all"]}\

    The naMethod is one of "all", "ignore", or "rm", which specifies how to handle
    NAs that appear in columns that are being aggregated.

    "all" - include NAs
    "rm"  - exclude NAs
    "ignore" - ignore NAs in aggregates, but count them (e.g. in denominators for mean, var, sd, etc.)
    :param order_by: A list of column names or indices on which to order the results.
    :return: The group by frame.
    """
    raise ValueError("groupby: unimpl")
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # rapids_series = "(llist #"+" #".join([str(self._find_idx(name)) for name in cols])+")"
    # aggregates = copy.deepcopy(a)
    # key = self.send_frame()
    # tmp_key = H2OFrame.py_tmp_key()
    #
    # aggs = []
    #
    # # transform cols in aggregates to their indices...
    # for k in aggregates:
    #   if isinstance(aggregates[k][1],str):
    #     aggregates[k][1] = '#'+str(self._find_idx(aggregates[k][1]))
    #   else:
    #     aggregates[k][1] = '#'+str(aggregates[k][1])
    #   aggs+=["\"{1}\" {2} \"{3}\" \"{0}\"".format(str(k),*aggregates[k])]
    # aggs = "(agg {})".format(" ".join(aggs))
    #
    # # deal with order by
    # if order_by is None: order_by="()"
    # else:
    #   if isinstance(order_by, list):
    #     oby = [cols.index(i) for i in order_by]
    #     order_by = "(llist #"+" #".join([str(o) for o in oby])+")"
    #   elif isinstance(order_by, str):
    #     order_by = "#" + str(self._find_idx(order_by))
    #   else:
    #     order_by = "#" + str(order_by)
    #
    # expr = "(= !{} (GB %{} {} {} {}))".format(tmp_key,key,rapids_series,aggs,order_by)
    # h2o.rapids(expr)  # group by
    # # Remove h2o temp frame after groupby
    # h2o.removeFrameShallow(key)
    # # Make backing H2OVecs for the remote h2o vecs
    # j = h2o.frame(tmp_key)
    # fr = j['frames'][0]       # Just the first (only) frame
    # rows = fr['rows']         # Row count
    # veckeys = fr['vec_ids']  # List of h2o vec keys
    # cols = fr['columns']      # List of columns
    # colnames = [col['label'] for col in cols]
    # vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
    # h2o.removeFrameShallow(tmp_key)
    # return H2OFrame(vecs=vecs)

  def impute(self,column,method,combine_method,by,inplace):
    """
    Impute a column in this H2OFrame.

    :param column: The column to impute
    :param method: How to compute the imputation value.
    :param combine_method: For even samples and method="median", how to combine quantiles.
    :param by: Columns to group-by for computing imputation value per groups of columns.
    :param inplace: Impute inplace?
    :return: the imputed frame.
    """
    raise ValueError("impute: unimpl")
    # sanity check columns, get the column index
    # col_id = -1
    #
    # if isinstance(column, list): column = column[0]  # only take the first one ever...
    #
    # if isinstance(column, (unicode,str)):
    #   col_id = self._find_idx(column)
    # elif isinstance(column, int):
    #   col_id = column
    # elif isinstance(column, H2OVec):
    #   try:
    #     col_id = [a._name==v._name for a in self].index(True)
    #   except:
    #     raise ValueError("No column found to impute.")
    #
    #     # setup the defaults, "mean" for numeric, "mode" for enum
    # if isinstance(method, list) and len(method) > 1:
    #   if self[col_id].isfactor(): method="mode"
    #   else:                       method="mean"
    # elif isinstance(method, list):method=method[0]
    #
    # # choose "interpolate" by default for combine_method
    # if isinstance(combine_method, list) and len(combine_method) > 1: combine_method="interpolate"
    # if combine_method == "lo":                                       combine_method = "low"
    # if combine_method == "hi":                                       combine_method = "high"
    #
    # # sanity check method
    # if method=="median":
    #   # no by and median!
    #   if by is not None:
    #     raise ValueError("Unimplemented: No `by` and `median`. Please select a different method (e.g. `mean`).")
    #
    # # method cannot be median or mean for factor columns
    # if self[col_id].isfactor() and method not in ["ffill", "bfill", "mode"]:
    #   raise ValueError("Column is categorical, method must not be mean or median.")
    #
    #
    # # setup the group by columns
    # gb_cols = "()"
    # if by is not None:
    #   if not isinstance(by, list):          by = [by]  # just make it into a list...
    #   if isinstance(by[0], (unicode,str)):  by = [self._find_idx(name) for name in by]
    #   elif isinstance(by[0], int):          by = by
    #   elif isinstance(by[0], H2OVec):       by = [[a._name==v._name for a in self].index(True) for v in by]  # nested list comp. WOWZA
    #   else:                                 raise ValueError("`by` is not a supported type")
    #
    # if by is not None:                      gb_cols = "(llist #"+" #".join([str(b) for b in by])+")"
    #
    # key = self.send_frame()
    # tmp_key = H2OFrame.py_tmp_key()
    #
    # if inplace:
    #   # frame, column, method, combine_method, gb_cols, inplace
    #   expr = "(h2o.impute %{} #{} \"{}\" \"{}\" {} %TRUE".format(key, col_id, method, combine_method, gb_cols)
    #   h2o.rapids(expr)  # exec the thing
    #   h2o.removeFrameShallow(key)  # "soft" delete of the frame key, keeps vecs live
    #   return self
    # else:
    #   expr = "(= !{} (h2o.impute %{} #{} \"{}\" \"{}\" {} %FALSE))".format(tmp_key,key,col_id,method,combine_method,gb_cols)
    #   h2o.rapids(expr)  # exec the thing
    #   h2o.removeFrameShallow(key)
    #   # Make backing H2OVecs for the remote h2o vecs
    #   j = h2o.frame(tmp_key)
    #   fr = j['frames'][0]       # Just the first (only) frame
    #   rows = fr['rows']         # Row count
    #   veckeys = fr['vec_ids']   # List of h2o vec keys
    #   cols = fr['columns']      # List of columns
    #   colnames = [col['label'] for col in cols]
    #   raise ValueError("impute: unimpl")
    # vecs = H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
    # h2o.removeFrameShallow(tmp_key) # soft delete the new Frame, keep the imputed Vecs alive
    # return H2OFrame(vecs=vecs)

  def merge(self, other, allLeft=False, allRite=False):
    """
    Merge two datasets based on common column names

    :param other: Other dataset to merge.  Must have at least one column in common with self, and all columns in common are used as the merge key.  If you want to use only a subset of the columns in common, rename the other columns so the columns are unique in the merged result.
    :param allLeft: If true, include all rows from the left/self frame
    :param allRite: If true, include all rows from the right/other frame
    :return: Original self frame enhanced with merged columns and rows
    """
    raise ValueError("merge: unimpl")
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # for v0 in self._vecs:
    #   for v1 in other._vecs:
    #     if v0._name==v1._name: break
    #   if v0._name==v1._name: break
    # else:
    #   raise ValueError("frames must have some columns in common to merge on")
    # # Eagerly eval and send the cbind'd frame over
    # lkey = self .send_frame()
    # rkey = other.send_frame()
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (merge %{} %{} %{} %{}))".format(tmp_key,lkey,rkey,
    #                                                 "TRUE" if allLeft else "FALSE",
    #                                                 "TRUE" if allRite else "FALSE")
    # # Remove h2o temp frame after merge
    # expr2 = "(, "+expr+" (del %"+lkey+" #0) (del %"+rkey+" #0) )"
    #
    # h2o.rapids(expr2)       # merge in h2o
    # # Make backing H2OVecs for the remote h2o vecs
    # j = h2o.frame(tmp_key)  # Fetch the frame as JSON
    # fr = j['frames'][0]     # Just the first (only) frame
    # rows = fr['rows']       # Row count
    # veckeys = fr['vec_ids']# List of h2o vec keys
    # cols = fr['columns']    # List of columns
    # colnames = [col['label'] for col in cols]
    # vecs=H2OVec.new_vecs(zip(colnames, veckeys), rows) # Peel the Vecs out of the returned Frame
    # h2o.removeFrameShallow(tmp_key)
    # return H2OFrame(vecs=vecs)

  def insert_missing_values(self, fraction = 0.1, seed=None):
    """
    Inserting Missing Values to an H2OFrame
    *This is primarily used for testing*. Randomly replaces a user-specified fraction of entries in a H2O dataset with
    missing values.
    WARNING: This will modify the original dataset. Unless this is intended, this function should only be called on a
    subset of the original.

    :param fraction: A number between 0 and 1 indicating the fraction of entries to replace with missing.
    :param seed: A random number used to select which entries to replace with missing values. Default of seed = -1 will
    automatically generate a seed in H2O.
    :return: H2OFrame with missing values inserted
    """
    raise NotImplementedError
    # kwargs = {}
    # data_key = self.send_frame()
    # kwargs['dataset'] = data_key
    # kwargs['fraction'] = fraction
    # if seed is not None: kwargs['seed'] = seed
    #
    # job = {}
    # job['job'] = H2OConnection.post_json("MissingInserter", **kwargs)
    # H2OJob(job, job_type=("Insert Missing Values")).poll()
    # return self

  # generic reducers (min, max, sum, var)
  def min(self):
    """
    :return: The minimum value of all frame entries
    """
    return H2OFrame(expr=ExprNode("min", self))

  def max(self):
    """
    :return: The maximum value of all frame entries
    """
    return H2OFrame(expr=ExprNode("max", self))

  def sum(self):
    """
    :return: The sum of all frame entries
    """
    return H2OFrame(expr=ExprNode("sum", self))

  def mean(self,na_rm=False):
    """
    :param na_rm: True or False to remove NAs from computation.
    :return: The mean of the column.
    """
    return H2OFrame(expr=ExprNode("mean", self, 0, na_rm))._scalar()

  def median(self):
    """
    :return: Median of this H2OVec.
    """
    raise NotImplementedError
    # return Expr("median", self._expr).eager()

  def var(self,na_rm=False,use="everything"):
    """
    :param na_rm: True or False to remove NAs from computation.
    :param use: One of "everything", "complete.obs", or "all.obs".
    :return: The covariance matrix of the columns in this H2OFrame.
    """
    return H2OFrame(expr=ExprNode("var", self,na_rm,use))

  def asfactor(self):
    """
    :return: A lazy Expr representing this vec converted to a factor
    """
    raise NotImplementedError
    # return H2OVec(self._name, Expr("as.factor", self._expr, None))

  def isfactor(self):
    """
    :return: A lazy Expr representing the truth of whether or not this vec is a factor.
    """
    raise NotImplementedError
    # return Expr("is.factor", self._expr, None, length=1).eager()

  def anyfactor(self):
    """
    :return: Whether or not the frame has any factor columns
    """
    return H2OFrame(expr=ExprNode("anyfactor", self))

  def transpose(self):
    """
    :return: The transpose of the H2OFrame.
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # frame_keys = [self.send_frame()]
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (t %{}))".format(tmp_key,frame_keys[0])
    # return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def strsplit(self, pattern):
    """
    Split the strings in the target column on the given pattern
    :return: H2OFrame
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # frame_keys = [self.send_frame()]
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (strsplit %{} {}))".format(tmp_key, frame_keys[0], "\""+pattern+"\"")
    # return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def trim(self):
    """
    Trim the edge-spaces in a column of strings (only operates on frame with one column)
    :return: H2OFrame
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # frame_keys = [self.send_frame()]
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (trim %{}))".format(tmp_key,frame_keys[0])
    # return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def table(self, data2=None):
    """
    :return: a frame of the counts at each combination of factor levels
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # frame_keys = [self.send_frame()]
    # if data2: frame_keys.append(data2.send_frame())
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (table %{} {}))".format(tmp_key,frame_keys[0],"%"+frame_keys[1] if data2 else "()")
    # return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def sub(self, pattern, replacement, ignore_case=False):
    """
    sub and gsub perform replacement of the first and all matches respectively.
    Of note, mutates the frame.
    :return: H2OFrame
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # frame_keys = [self.send_frame()]
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (sub {} {} %{} {}))".format(tmp_key, "\""+pattern+"\"", "\""+replacement+"\"", frame_keys[0], "%TRUE" if ignore_case else "%FALSE")
    # return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def gsub(self, pattern, replacement, ignore_case=False):
    """
    sub and gsub perform replacement of the first and all matches respectively.
    Of note, mutates the frame.
    :return: H2OFrame
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # frame_keys = [self.send_frame()]
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (gsub {} {} %{} {}))".format(tmp_key, "\""+pattern+"\"", "\""+replacement+"\"", frame_keys[0], "%TRUE" if ignore_case else "%FALSE")
    # return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def toupper(self):
    """
    Translate characters from lower to upper case for a particular column
    Of note, mutates the frame.
    :return: H2OFrame
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # frame_keys = [self.send_frame()]
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (toupper %{}))".format(tmp_key, frame_keys[0])
    # return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def tolower(self):
    """
    Translate characters from upper to lower case for a particular column
    Of note, mutates the frame.
    :return: H2OFrame
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # frame_keys = [self.send_frame()]
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (tolower %{}))".format(tmp_key, frame_keys[0])
    # return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def rep_len(self, length_out):
    """
    Replicates the values in `data` in the H2O backend
    :param length_out: the number of columns of the resulting H2OFrame
    :return: an H2OFrame
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # frame_keys = [self.send_frame()]
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (rep_len %{} #{}))".format(tmp_key,frame_keys[0],length_out)
    # return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, frame_keys)

  def scale(self, center=True, scale=True):
    """
    Centers and/or scales the columns of the H2OFrame
    :return: H2OFrame
    :param center: either a ‘logical’ value or numeric list of length equal to the number of columns of the H2OFrame
    :param scale: either a ‘logical’ value or numeric list of length equal to the number of columns of H2OFrame.
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # if   isinstance(center, bool) and isinstance(scale, bool):
    #   return H2OFrame(vecs=[vec.scale(center=center, scale=scale) for vec in self._vecs])
    # elif isinstance(center, list) and isinstance(scale, bool):
    #   return H2OFrame(vecs=[vec.scale(center=c, scale=scale) for vec, c in zip(self._vecs,center)])
    # elif isinstance(center, list) and isinstance(scale, list):
    #   return H2OFrame(vecs=[vec.scale(center=c, scale=s) for vec, c, s in zip(self._vecs, center, scale)])
    # elif isinstance(center, bool) and isinstance(scale, list):
    #   return H2OFrame(vecs=[vec.scale(center=center, scale=s) for vec, s in zip(self._vecs,scale)])
    # else: raise(ValueError, "`center` and `scale` arguments (for a frame) must be bools or lists of numbers, but got "
    #                         "center: {0}, scale: {1}".format(center, scale))

  def signif(self, digits=6):
    """
    :return: The rounded values in the H2OFrame to the specified number of significant digits.
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # return H2OFrame(vecs=[vec.signif(digits=digits) for vec in self._vecs])

  def round(self, digits=0):
    """
    :return: The rounded values in the H2OFrame to the specified number of decimal digits.
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # return H2OFrame(vecs=[vec.round(digits=digits) for vec in self._vecs])

  def asnumeric(self):
    """
    :return: A frame with factor columns converted to numbers (numeric columns untouched).
    """
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # return H2OFrame(vecs=[vec.asnumeric() for vec in self._vecs])

  def ascharacter(self):
    """
    :return: A lazy Expr representing this vec converted to characters
    """
    raise NotImplementedError
    # return H2OVec(self._name, Expr("as.character", self._expr, None))

  def isna(self):
    """
    :return: Returns a new boolean H2OVec.
    """
    raise NotImplementedError
    # return H2OVec("", Expr("is.na", self._expr, None))

  def year(self):
    """
    :return: Returns a new year column from a msec-since-Epoch column
    """
    raise NotImplementedError
    # return H2OVec(self._name, Expr("year", self._expr, None))

  def month(self):
    """
    :return: Returns a new month column from a msec-since-Epoch column
    """
    raise NotImplementedError
    # return H2OVec(self._name, Expr("month", self._expr, None))

  def week(self):
    """
    :return: Returns a new week column from a msec-since-Epoch column
    """
    raise NotImplementedError
    # return H2OVec(self._name, Expr("week", self._expr, None))

  def day(self):
    """
    :return: Returns a new day column from a msec-since-Epoch column
    """
    raise NotImplementedError
    # return H2OVec(self._name, Expr("day", self._expr, None))

  def dayOfWeek(self):
    """
    :return: Returns a new Day-of-Week column from a msec-since-Epoch column
    """
    raise NotImplementedError
    # return H2OVec(self._name, Expr("dayOfWeek", self._expr, None))

  def hour(self):
    """
    :return: Returns a new Hour-of-Day column from a msec-since-Epoch column
    """
    raise NotImplementedError
    # return H2OVec(self._name, Expr("hour", self._expr, None))

  def runif(self, seed=None):
    """
    :param seed: A random seed. If None, then one will be generated.
    :return: A new H2OVec filled with doubles sampled uniformly from [0,1).
    """
    raise NotImplementedError
    # if not seed:
    #   import random
    #   seed = random.randint(123456789, 999999999)  # generate a seed
    # return H2OVec("", Expr("h2o.runif", self._expr, Expr(seed)))

  def match(self, table, nomatch=0):
    """
    Makes a vector of the positions of (first) matches of its first argument in its second.
    :return: bit H2OVec
    """
    raise NotImplementedError
    # make table to pass to rapids
    # rtable = ""
    # if hasattr(table, '__iter__'): # make slist or list
    #   if all([isinstance(t, (int,float)) for t in table]): # make list
    #     rtable += "(list"
    #     for t in table: rtable += " #"+str(t)
    #     rtable += ")"
    #   elif all([isinstance(t, str) for t in table]): # make slist
    #     rtable += "(slist"
    #     for t in table: rtable += " \""+str(t)+"\""
    #     rtable += ")"
    # elif isinstance(table, (int, float)): # make #
    #   rtable += "#"+str(table)
    # elif isinstance(table, str): # make str
    #   rtable += "\""+table+"\""
    # else:
    #   raise ValueError("`table` must be a scaler (str, int, float), or a iterable of scalers of the same type.")
    #
    # tmp_key = H2OFrame.py_tmp_key()
    # expr = "(= !{} (match %{} {} #{} ()))".format(tmp_key,self.key(),rtable,nomatch)
    # return H2OFrame._get_frame_from_rapids_string(expr, tmp_key, [])

  def cut(self, breaks, labels=None, include_lowest=False, right=True, dig_lab=3):
    raise NotImplementedError
    # breaks_list = "(dlist #"+" #".join([str(b) for b in breaks])+")"
    # labels_list = "()"
    # if labels is not None:
    #   labels_list = "(slist \"" + '" "'.join(labels) +"\")"
    #
    # if self.key() == "": self._expr.eager()
    #
    # expr = "(cut '{}' {} {} {} {} #{}".format(self.key(), breaks_list, labels_list, "%TRUE" if include_lowest else "%FALSE", "%TRUE" if right else "%FALSE", dig_lab)
    # res = h2o.rapids(expr)
    # return H2OVec(self._name, Expr(op=res["vec_ids"][0]["name"], length=res["num_rows"]))

  def _get_vec_from_rapids_string(self, expr, tmp_key):
    raise NotImplementedError
    # h2o.rapids(expr)
    # j = h2o.frame(tmp_key)
    # fr = j['frames'][0]
    # rows = fr['rows']
    # veckeys = fr['vec_ids']
    # cols = fr['columns']
    # colnames = [col['label'] for col in cols]
    # vec=H2OVec.new_vecs(zip(colnames, veckeys), rows)[0]
    # vec.setName(self._name)
    # h2o.removeFrameShallow(tmp_key)

  def _eager(self, pytmp=True):
    if not self._computed:
      # top-level call to execute all subparts of self._ast
      sb = self._ast._eager()
      if pytmp:
        h2o.rapids(ExprNode._collapse_sb(sb), self._id)
        sb = ["%", self._id," "]
        self._update()   # fill out _nrows, _ncols, _col_names, _computed
      return sb

  def _do_it(self,sb):
    # this method is only ever called from ExprNode._do_it
    # it's the "long" way 'round the mutual recursion from ExprNode to H2OFrame
    #
    #  Here's a diagram that illustrates the call order:
    #
    #           H2OFrame:                   ExprNode:
    #               _eager ---------------->  _eager
    #
    #                 ^^                       ^^ ||
    #                 ||                       || \/
    #
    #               _do_it <----------------  _do_it
    #
    #  the "long" path:
    #     pending exprs in DAG with exterior refs must be saved (refs >= magic count)
    #
    if self._computed: sb += ['%',self._id+" "]
    else:              sb += self._eager(True) if (len(gc.get_referrers(self)) >= H2OFrame.MAGIC_REF_COUNT) else self._eager(False)

  def _update(self):
    # get ncols,nrows,names and exclude everything else
    # frames_ex = ["row_offset", "row_count", "checksum", "default_percentiles", "compatible_models",
    #              "vec_ids","chunk_summary","distribution_summary"]
    # columns_ex = ["missing_count", "zero_count", "positive_infinity_count", "negative_infinity_count",
    #               "mins", "maxs", "mean", "sigma", "type", "domain", "data", "string_data",
    #               "precision", "histogram_bins", "histogram_base", "histogram_stride", "percentiles"]
    #
    # frames_ex = "frames/" + ",frames/".join(frames_ex)
    # columns_ex = "frames/columns/" + ",frames/columns/".join(columns_ex)
    # exclude="?_exclude_fields={},{}".format(frames_ex,columns_ex)
    res = h2o.frame(self._id)["frames"][0]  # TODO: exclude here?
    self._nrows = res["rows"]
    self._ncols = len(res["columns"])
    self._col_names = [c["label"] for c in res["columns"]]
    self._computed=True
    self._ast=None

# private static methods
def _gen_header(cols):
  return ["C" + str(c) for c in range(1, cols + 1, 1)]


def _check_lists_of_lists(python_obj):
  # all items in the list must be a list too
  lol_all = all(isinstance(l, (tuple, list)) for l in python_obj)
  # All items in the list must be a list!
  if not lol_all:
    raise ValueError("`python_obj` is a mixture of nested lists and other types.")

  # in fact, we must have a list of flat lists!
  for l in python_obj:
    if any(isinstance(ll, (tuple, list)) for ll in l):
      raise ValueError("`python_obj` is not a list of flat lists!")


def _handle_python_lists(python_obj):
  cols = len(python_obj)  # cols will be len(python_obj) if not a list of lists
  # do we have a list of lists: [[...], ..., [...]] ?
  lol = _is_list_of_lists(python_obj)
  if lol:
    # must be a list of flat lists, raise ValueError if not
    _check_lists_of_lists(python_obj)
    # have list of lists, each list is a row
    # length of the longest list is the number of columns
    cols = max([len(l) for l in python_obj])

  # create the header
  header = _gen_header(cols)
  # shape up the data for csv.DictWriter
  data_to_write = [dict(zip(header, row)) for row in python_obj] if lol else [dict(zip(header, python_obj))]
  return header, data_to_write


def _is_list_of_lists(o):                  return any(isinstance(l, (list, tuple)) for l in o)
def _handle_numpy_array(python_obj):       return _handle_python_lists(python_obj=python_obj.tolist())
def _handle_pandas_data_frame(python_obj): return _handle_numpy_array(python_obj=python_obj.as_matrix())
def _handle_python_dicts(python_obj):
  header = python_obj.keys()
  # is this a valid header?
  is_valid = all([re.match(r'^[a-zA-Z_][a-zA-Z0-9_.]*$', col) for col in header])
  if not is_valid:
    raise ValueError("Did not get a valid set of column names! Must match the regular expression: ^[a-zA-Z_][a-zA-Z0-9_.]*$ ")
  # check that each value entry is a flat list/tuple
  for k in python_obj:
    v = python_obj[k]
    # if value is a tuple/list, then it must be flat
    if isinstance(v, (tuple, list)):
      if _is_list_of_lists(v):
        raise ValueError("Values in the dictionary must be flattened!")

  rows = map(list, itertools.izip_longest(*python_obj.values()))
  data_to_write = [dict(zip(header, row)) for row in rows]
  return header, data_to_write
