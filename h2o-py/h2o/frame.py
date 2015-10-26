# -*- coding: utf-8 -*-
# import numpy    no numpy cuz windoz
import collections, csv, itertools, os, re, tempfile, uuid, urllib2, urllib,imp,copy,weakref,inspect,ast
import h2o, expr
from astfun import _bytecode_decompile_lambda
from group_by import GroupBy

# TODO: Automatically convert column names into Frame properties!

class H2OFrame:

  def __init__(self,ex):
    if not isinstance(ex,expr.ExprNode): 
      raise ValueError("H2OFrame must be passed an ExprNode, not a "+str(ex.__class__)+" and the constructor is an internal method.\nDid you mean H2OFrame.fromPython(python_obj)?")
    self._ex = ex

  @staticmethod
  def get_frame(frame_id):
    """
    Create an H2OFrame mapped to an existing id in the cluster
    :return: Created H2OFrame mapping to pre-existing Frame "id" in the cluster
    """
    rows = 10     # Default fetch only 10 rows to match head()
    res = h2o.H2OConnection.get_json("Frames/"+urllib.quote(frame_id)+"?row_count="+str(rows))["frames"][0]
    # Construct a Frame and an Expr and fill in relavent fields here
    fr = H2OFrame(expr.ExprNode("Frame",False))
    fr._ex._id = res["frame_id"]["name"]
    fr._ex._fill_rows(res)
    return fr

  @property
  def col_names(self):
    """
    :return: A list of column names.
    """
    return self._ex._fetch_data(0).keys()

  @property
  def columns(self):
    """
    :return: A list of column names.
    """
    return self.col_names

  @property
  def names(self):
    """
    :return: A list of column names.
    """
    return self.col_names

  def name(self,i):
    """
    Note: does work O(ncols) to return O(1) data.
    :return: The name for column i
    """
    return self.col_names[i]

  @property
  def nrow(self):
    """
    :return: The number of rows
    """
    if self._ex._nrows: return self._ex._nrows
    self._ex._fetch_data(0)
    return self._ex._nrows

  def __len__(self):
    """
    :return: Number of rows
    """
    return self.nrow

  @property
  def ncol(self):
    """
    :return: The number of columns
    """
    return self._ex._ncols or self._eager()._ncols

  @property
  def dim(self):
    """
    :return: A list [nrow, ncol].
    """
    return [self.nrow, self.ncol]

  @property
  def shape(self):
    """
    :return: A tuple (nrow, ncol)
    """
    return (self.nrow, self.ncol)

  @property
  def frame_id(self):
    """
    :return: Get the frame name
    """
    return self._ex._id or self._eager()._id

  @property
  def types(self):
    """
    :return: The types for each column
    """
    return [col["type"] for col in self._ex._fetch_data(0).itervalues()]

  def type(self,name):
    """
    :return: The type for a named column
    """
    return self._ex._fetch_data(0)[name]["type"]

  def __iter__(self):
    """
    Allows for list comprehensions
    :return: An iterator over the columns
    """
    return (self[i] for i in range(self.ncol))

  def __str__(self): return str(self._ex)

  def __repr__(self): return self._ex.__repr__()

  def show(self): return self._ex.show()

  def summary(self): return self._ex.summary()

  def describe(self): return self._ex.describe()

  def _eager(self): return self._ex._eager()

  def head(self,rows=10,cols=200):
    """
    Analogous to Rs `head` call on a data.frame.  Return a digestible
    chunk of the H2OFrame starting from the beginning.
    :param rows: Number of rows starting from the topmost
    :param cols: Number of columns starting from the leftmost
    :return: A sliced H2OFrame
    """
    nrows = min(self.nrow, rows)
    ncols = min(self.ncol, cols)
    return self[:nrows,:ncols]

  def tail(self, rows=10, cols=200):
    """
    Analogous to Rs `tail` call on a data.frame.  Return a digestible chunk of
    the H2OFrame starting from the end.
    :param rows: Number of rows starting from the bottommost
    :param cols: Number of columns starting from the leftmost
    :return: A sliced H2OFrame
    """
    nrows = min(self.nrow, rows)
    ncols = min(self.ncol, cols)
    start_idx = self.nrow - nrows
    return self[start_idx:start_idx+nrows,:ncols]

  @staticmethod
  def read_csv(file_path, destination_frame, header=(-1,0,1), separator="", column_names=None, column_types=None, na_strings=None):
    """
    Build an H2OFrame from parsing a CSV at file_path.  This path is relative to
    the H2O cluster, NOT the local Python process
    :param file_path:  A remote path to a data source.  Data is cluster-local.
    :param destination_frame:  The result *Key* name in the H2O cluster
    """
    rawkey = h2o.lazy_import(file_path)
    setup = h2o.parse_setup(rawkey, destination_frame, header, separator, column_names, column_types, na_strings)
    parse = h2o._parse(setup)
    destination_frame = parse["destination_frame"]["name"]
    res = H2OFrame.get_frame(destination_frame)
    nrows = res.nrow
    ncols = res.ncol
    if isinstance(file_path, str): print "Imported {}. Parsed {} rows and {} cols".format(file_path,"{:,}".format(nrows), "{:,}".format(ncols))
    else:                          h2o.H2ODisplay([["File"+str(i+1),f] for i,f in enumerate(file_path)],None, "Parsed {} rows and {} cols".format("{:,}".format(nrows), "{:,}".format(ncols)))
    return res

  # Init a H2OFrame by importing a primitive Python `list` or `dict` object
  # For more information on the structure of the input for the various native python
  # data types ("native" meaning non-H2O), please see the general documentation for
  # this object.
  #
  # :param python_obj: A "native" python object - list, dict, tuple.
  @staticmethod
  def fromPython(python_obj):
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
    csv_writer.writeheader()             # write the header
    csv_writer.writerows(data_to_write)  # write the data
    tmp_file.close()                     # close the streams
    res = H2OFrame.read_csv(tmp_path, _py_tmp_key(), header=1)
    os.remove(tmp_path)                  # delete the tmp file
    return res

  @staticmethod
  def fromRawText(text_key, check_header=None):
    """
    Handle result of upload_file
    :param text_key: A key pointing to raw text to be parsed
    :return: Part of the H2OFrame constructor.
    """
    setup = h2o.parse_setup(text_key)
    if check_header is not None: setup["check_header"] = check_header
    parse = h2o._parse(setup)
    destination_frame = parse["destination_frame"]["name"]
    res = H2OFrame.get_frame(destination_frame)
    print "Uploaded {} into cluster with {:,} rows and {:,} cols".format(text_key, res.nrow, res.ncol)
    return res

  def _newExpr(self,op,*args): return H2OFrame(expr.ExprNode(op,*args))

  def _scalar(self,op,*args): return expr.ExprNode(op,*args)._eager_scalar()

  def logical_negation(self): return self._newExpr("not", self)

  # ops
  def __add__ (self, i): return self._newExpr("+",   self,i)
  def __sub__ (self, i): return self._newExpr("-",   self,i)
  def __mul__ (self, i): return self._newExpr("*",   self,i)
  def __div__ (self, i): return self._newExpr("/",   self,i)
  def __floordiv__(self, i): return self._newExpr("intDiv",self,i)
  def __mod__ (self, i): return self._newExpr("mod", self,i)
  def __or__  (self, i): return self._newExpr("|",   self,i)
  def __and__ (self, i): return self._newExpr("&",   self,i)
  def __ge__  (self, i): return self._newExpr(">=",  self,i)
  def __gt__  (self, i): return self._newExpr(">",   self,i)
  def __le__  (self, i): return self._newExpr("<=",  self,i)
  def __lt__  (self, i): return self._newExpr("<",   self,i)
  def __eq__  (self, i): return self._newExpr("==",  self,i)
  def __ne__  (self, i): return self._newExpr("!=",  self,i)
  def __pow__ (self, i): return self._newExpr("^",   self,i)
  # rops
  def __rmod__(self, i): return self._newExpr("mod",i,self)
  def __radd__(self, i): return self.__add__(i)
  def __rsub__(self, i): return self._newExpr("-",i,  self)
  def __rand__(self, i): return self.__and__(i)
  def __ror__ (self, i): return self.__or__ (i)
  def __rdiv__(self, i): return self._newExpr("/",i,  self)
  def __rfloordiv__(self, i): return self._newExpr("intDiv",i,self)
  def __rmul__(self, i): return self.__mul__(i)
  def __rpow__(self, i): return self._newExpr("^",i,  self)
  # unops
  def __abs__ (self):        return self._newExpr("abs",self)
  def __contains__(self, i): return all([(t==self).any() for t in i]) if _is_list(i) else (i==self).any()

  def mult(self, matrix):
    """
    Perform matrix multiplication.

    :param matrix: The matrix to multiply to the left of self.
    :return: The multiplied matrices.
    """
    return self._newExpr("x", self, matrix)

  def cos(self)     :    return self._newExpr("cos", self)
  def sin(self)     :    return self._newExpr("sin", self)
  def tan(self)     :    return self._newExpr("tan", self)
  def acos(self)    :    return self._newExpr("acos", self)
  def asin(self)    :    return self._newExpr("asin", self)
  def atan(self)    :    return self._newExpr("atan", self)
  def cosh(self)    :    return self._newExpr("cosh", self)
  def sinh(self)    :    return self._newExpr("sinh", self)
  def tanh(self)    :    return self._newExpr("tanh", self)
  def acosh(self)   :    return self._newExpr("acosh", self)
  def asinh(self)   :    return self._newExpr("asinh", self)
  def atanh(self)   :    return self._newExpr("atanh", self)
  def cospi(self)   :    return self._newExpr("cospi", self)
  def sinpi(self)   :    return self._newExpr("sinpi", self)
  def tanpi(self)   :    return self._newExpr("tanpi", self)
  def abs(self)     :    return self._newExpr("abs", self)
  def sign(self)    :    return self._newExpr("sign", self)
  def sqrt(self)    :    return self._newExpr("sqrt", self)
  def trunc(self)   :    return self._newExpr("trunc", self)
  def ceil(self)    :    return self._newExpr("ceiling", self)
  def floor(self)   :    return self._newExpr("floor", self)
  def log(self)     :    return self._newExpr("log", self)
  def log10(self)   :    return self._newExpr("log10", self)
  def log1p(self)   :    return self._newExpr("log1p", self)
  def log2(self)    :    return self._newExpr("log2", self)
  def exp(self)     :    return self._newExpr("exp", self)
  def expm1(self)   :    return self._newExpr("expm1", self)
  def gamma(self)   :    return self._newExpr("gamma", self)
  def lgamma(self)  :    return self._newExpr("lgamma", self)
  def digamma(self) :    return self._newExpr("digamma", self)
  def trigamma(self):    return self._newExpr("trigamma", self)


  @staticmethod
  def mktime(year=1970,month=0,day=0,hour=0,minute=0,second=0,msec=0):
    """
    All units are zero-based (including months and days).  Missing year is 1970.

    :return: Returns msec since the Epoch.
    """
    return self._newExpr("mktime", year,month,day,hour,minute,second,msec)

  def filterNACols(self, frac=0.2):
    """
    Filter columns with prportion of NAs >= frac.
    :param frac: Fraction of NAs in the column.
    :return: A list of column indices.
    """
    return self._newExpr("filterNACols", self, frac)

  def unique(self):
    """
    Extract the unique values in the column.
    :return: A new H2OFrame of just the unique values in the column.
    """
    return self._newExpr("unique", self)

  def levels(self, col=None):
    """
    Get the factor levels for this frame and the specified column index.

    :param col: A column index in this self._newExpr.
    :return: a list of strings that are the factor levels for the column.
    """
    if self.ncol==1 or col is None:
      lol=h2o.as_list(self._newExpr("levels", self), False)[1:]
      levels=[level for l in lol for level in l] if self.ncol==1 else lol
    elif col is not None:
      lol=h2o.as_list(self._newExpr("levels", self._newExpr("cols", self, col)),False)[1:]
      levels=[level for l in lol for level in l]
    else:                             levels=None
    return None if levels is None or levels==[] else levels

  def nlevels(self, col=None):
    """
    Get the number of factor levels for this frame and the specified column index.

    :param col: A column index in this self._newExpr.
    :return: an integer.
    """
    nlevels = self.levels(col=col)
    return len(nlevels) if nlevels else 0

  def set_level(self, level):
    """
    A method to set all column values to one of the levels.

    :param level: The level at which the column will be set (a string)
    :return: An self._newExpr with all entries set to the desired level
    """
    return self._newExpr("setLevel", self, level)

  def set_levels(self, levels):
    """
    Works on a single categorical vector. New domains must be aligned with the old domains.
    This call has copy-on-write semantics.


    :param levels: A list of strings specifying the new levels. The number of new levels must match the number of
    old levels.
    :return: A new column with the domains.
    """
    return self._newExpr("setDomain",self,levels)

  def set_names(self,names):
    """
    Change the column names to `names`.

    :param names: A list of strings equal to the number of columns in the self._newExpr.
    :return: None. Rename the column names in this self._newExpr.
    """
    self._ex = expr.ExprNode("colnames=",self, range(self.ncol), names) # Update-in-place, but still lazy
    return self

  def set_name(self,col=None,name=None):
    """
    Set the name of the column at the specified index.
    :param col: Index of the column whose name is to be set; may be skipped for 1-column frames
    :param name: The new name of the column to set
    :return: the input frame
    """
    if isinstance(col, basestring): col = self.names.index(col)  # lookup the name!
    if not isinstance(col, int) and self.ncol > 1: raise ValueError("`col` must be an index. Got: " + str(col))
    if self.ncol == 1: col = 0
    self._ex = expr.ExprNode("colnames=",self,col,name) # Update-in-place, but still lazy
    return self

  def as_date(self,format):
    """
    Return the column with all elements converted to millis since the epoch.

    :param format: The date time format string
    :return: H2OFrame
    """
    return self._newExpr("as.Date",self,format)

  def cumsum(self):
    """
    :return: The cumulative sum over the column.
    """
    return self._newExpr("cumsum",self)

  def cumprod(self):
    """
    :return: The cumulative product over the column.
    """
    return self._newExpr("cumprod",self)

  def cummin(self):
    """
    :return: The cumulative min over the column.
    """
    return self._newExpr("cummin",self)

  def cummax(self):
    """
    :return: The cumulative max over the column.
    """
    return self._newExpr("cummax",self)

  def prod(self,na_rm=False):
    """
    :param na_rm: True or False to remove NAs from computation.
    :return: The product of the column.
    """
    return self._scalar("prod.na" if na_rm else "prod",self)

  def any(self):
    """
    :return: True if any element is True or NA in the column.
    """
    return bool(self._scalar("any",self))

  def any_na_rm(self):
    """
    :return: True if any element is True in the column.
    """
    return bool(self._scalar("any.na",self))

  def all(self):
    """
    :return: True if every element is True or NA in the column.
    """
    return bool(self._scalar("all",self))

  def isnumeric(self):
    """
    :return: True if the column is numeric, otherwise return False
    """
    return self._scalar("is.numeric",self)

  def isstring(self):
    """
    :return: True if the column is a string column, otherwise False (same as ischaracter)
    """
    return self._scalar("is.character",self)

  def ischaracter(self):
    """
    :return: True if the column is a character column, otherwise False (same as isstring)
    """
    return self.isstring()

  def kfold_column(self, n_folds=3, seed=-1):
    """
    Build a fold assignments column for cross-validation. This call will produce a column
    having the same data layout as the calling object.

    :param n_folds: Number of folds.
    :param seed:Seed for random numbers (affects sampling when balance_classes=T)
    :return: A column of fold IDs.
    """
    return self._newExpr("kfold_column",self,n_folds,seed)

  def modulo_kfold_column(self, n_folds=3):
    """
    Build a fold assignments column for cross-validation. Rows are assigned a fold according
    to the current row number modulo n_folds.

    Parameters
    ----------
      n_folds : int
        The number of folds to build.

    :return: An self._newExpr holding a single column of the fold assignments.
    """
    return self._newExpr("modulo_kfold_column",self,n_folds)

  def stratified_kfold_column(self, n_folds=3, seed=-1):
    """
    Build a fold assignment column with the constraint that each fold has the same class
    distribution as the fold column.

    Parameters
    ----------
      n_folds: int
        The number of folds to build.
      seed: int
        A random seed.

    :return: An self._newExpr holding a single column of the fold assignments.
    """
    return self._newExpr("stratified_kfold_column",self,n_folds,seed)


  def structure(self):
    """
    Similar to R's str method: Compactly Display the Structure of this self._newExpr instance.

    :return: None
    """
    df = self.as_data_frame(use_pandas=False)
    nr = self.nrow
    nc = len(df[0])
    cn = df.pop(0)
    width = max([len(c) for c in cn])
    isfactor = [c.isfactor() for c in self]
    numlevels  = [self.nlevels(i) for i in range(nc)]
    lvls = self.levels()
    print "self._newExpr '{}': \t {} obs. of {} variables(s)".format(self.frame_id,nr,nc)
    for i in range(nc):
      print "$ {} {}: ".format(cn[i], ' '*(width-max(0,len(cn[i])))),
      if isfactor[i]:
        nl = numlevels[i]
        print "Factor w/ {} level(s) {},..: ".format(nl, '"' + '","'.join(zip(*lvls)[i]) + '"'),
        print " ".join(it[0] for it in h2o.as_list(self[:10,i].match(list(zip(*lvls)[i])), False)[1:]),
        print "..."
      else:
        print "num {} ...".format(" ".join(it[0] for it in h2o.as_list(self[:10,i], False)[1:]))

  def as_data_frame(self, use_pandas=True):
    """
    Obtain the dataset as a python-local object (pandas frame if possible, list otherwise)

    :param use_pandas: A flag specifying whether or not to return a pandas DataFrame.
    :return: A local python object (a list of lists of strings, each list is a row, if use_pandas=False, otherwise a
    pandas DataFrame) containing this self._newExpr instance's data.
    """
    url = 'http://' + h2o.H2OConnection.ip() + ':' + str(h2o.H2OConnection.port()) + "/3/DownloadDataset?frame_id=" + urllib.quote(self.frame_id) + "&hex_string=false"
    response = urllib2.urlopen(url)
    if h2o.can_use_pandas() and use_pandas:
      import pandas
      df = pandas.read_csv(response,low_memory=False)
      time_cols = []
      category_cols = []
      if self.types is not None:
        for col_name in self.names:
          xtype = self.type(col_name)
          if xtype.lower() == 'time': time_cols.append(col_name)
          elif xtype.lower() == 'enum': category_cols.append(col_name)
        #change Time to pandas datetime
        if time_cols:
          #hacky way to get the utc offset
          from datetime import datetime
          sample_timestamp = 1380610868
          utc_offset = 1000 * ((datetime.utcfromtimestamp(sample_timestamp) - datetime.fromtimestamp(sample_timestamp)).total_seconds())
          try:
            df[time_cols] = (df[time_cols] - utc_offset).astype('datetime64[ms]')
          except pandas.tslib.OutOfBoundsDatetime:
            pass
        #change Enum to pandas category
        for cat_col in category_cols: #for loop is required
          df[cat_col] = df[cat_col].astype('category')
      return df
    else:
      cr = csv.reader(response)
      return [[''] if row == [] else row for row in cr]

  def flatten(self):
    return self._scalar("flatten",self)

  def __getitem__(self, item):  ex = self._ex[item]; return H2OFrame(ex) if isinstance(ex,expr.ExprNode) else ex
  def __setitem__(self, b, c):
    self._ex = self._ex._setitem(b,c) # Update-in-place, but still lazy

  def __int__(self):
    if self.ncol != 1 or self.nrow != 1: raise ValueError("Not a 1x1 Frame")
    return int(self.flatten())

  def __float__(self): 
    if self.ncol != 1 or self.nrow != 1: raise ValueError("Not a 1x1 Frame")
    return float(self.flatten())

  def drop(self, i):
    """
    :param i: Column to drop
    :return: H2OFrame with the column at index i dropped.
    """
    if isinstance(i, basestring): i = self.names.index(i)
    return self._newExpr("cols", self,-(i+1))

  def pop(self,i):
    """
    :param i: The index or name of the column to pop.
    :return: The column dropped from the frame; the frame is side-effected to lose the column
    """
    if isinstance(i, basestring): i=self.names.index(i)
    col = self._newExpr("cols",self,i)
    self._ex = expr.ExprNode("cols", self,-(i+1))
    return col
  

  def quantile(self, prob=None, combine_method="interpolate"):
    """
    Compute quantiles over a given self._newExpr.

    :param prob: A list of probabilties, default is [0.01,0.1,0.25,0.333,0.5,0.667,0.75,0.9,0.99]. You may provide any sequence of any length.
    :param combine_method: For even samples, how to combine quantiles. Should be one of ["interpolate", "average", "low", "hi"]
    :return: an self._newExpr containing the quantiles and probabilities.
    """
    if len(self) == 0: return self
    if not prob: prob=[0.01,0.1,0.25,0.333,0.5,0.667,0.75,0.9,0.99]
    return self._newExpr("quantile",self,prob,combine_method)

  def cbind(self,data):
    """
    :param data: self._newExpr or H2OVec to cbind to self
    :return: void
    """
    return self._newExpr("cbind", self, data)

  def rbind(self, data):
    """
    Combine H2O Datasets by Rows.
    Takes a sequence of H2O data sets and combines them by rows.
    :param data: an self._newExpr
    :return: self, with data appended (row-wise)
    """
    if not isinstance(data, H2OFrame): raise ValueError("`data` must be an H2OFrame, but got {0}".format(type(data)))
    return self._newExpr("rbind", self, data)

  def split_frame(self, ratios=[0.75], destination_frames=""):
    """
    Split a frame into distinct subsets of size determined by the given ratios.
    The number of subsets is always 1 more than the number of ratios given.

    :param ratios: The fraction of rows for each split.
    :param destination_frames: names of the split frames
    :return: a list of frames
    """
    j = h2o.H2OConnection.post_json("SplitFrame", dataset=self.frame_id, ratios=ratios, destination_frames=destination_frames)
    h2o.H2OJob(j, "Split Frame").poll()
    return [h2o.get_frame(i["name"]) for i in j["destination_frames"]]

  # ddply in h2o
  def ddply(self,cols,fun):
    """
    :param cols: Column names used to control grouping
    :param fun: Function to execute on each group.  Right now limited to textual Rapids expression
    :return: New frame with 1 row per-group, of results from 'fun'
    """
    return self._newExpr("ddply", self, cols, fun)

  def group_by(self,by,order_by=None):
    """
    Returns a new GroupBy object using this frame and the desired grouping columns.

    :param by: The columns to group on.
    :param order_by: A list of column names or indices on which to order the results.
    :return: A new GroupBy object.
    """
    return GroupBy(self,by,order_by)

  def impute(self,column,method="mean",combine_method="interpolate",by=None,inplace=True):
    """
    Impute a column in this H2OFrame
    :param column: The column to impute
    :param method: How to compute the imputation value.
    :param combine_method: For even samples and method="median", how to combine quantiles.
    :param by: Columns to group-by for computing imputation value per groups of columns.
    :param inplace: Impute inplace?
    :return: the imputed frame.
    """
    if isinstance(column, basestring): column = self.names.index(column)
    if isinstance(by, basestring):     by     = self.names.index(by)
    ex = expr.ExprNode("h2o.impute", self, column, method, combine_method, by)
    # Note: if the backend does in-place imputation on demand, then we must be
    # eager here because in-place implies side effects which need to be ordered
    # with (possibly lazy posssible eager) readers
    if not inplace: return H2OFrame(ex)
    self._ex = ex
    return self
    

  def merge(self, other, allLeft=True, allRite=False):
    """
    Merge two datasets based on common column names

    :param other: Other dataset to merge.  Must have at least one column in common with self, and all columns in common are used as the merge key.  If you want to use only a subset of the columns in common, rename the other columns so the columns are unique in the merged result.
    :param allLeft: If true, include all rows from the left/self frame
    :param allRite: If true, include all rows from the right/other frame
    :return: Original self frame enhanced with merged columns and rows
    """
    return self._newExpr("merge", self, other, allLeft, allRite)

  def insert_missing_values(self, fraction=0.1, seed=None):
    """
    Inserting Missing Values into an H2OFrame.  *This is primarily used for
    testing*.  Randomly replaces a user-specified fraction of entries in a H2O
    dataset with missing values.  WARNING: This will modify the original
    dataset.  Unless this is intended, this function should only be called on a
    subset of the original.

    :param fraction: A number between 0 and 1 indicating the fraction of entries to replace with missing.
    :param seed: A random number used to select which entries to replace with missing values. Default of seed = -1 will automatically generate a seed in H2O.
    :return: H2OFrame with missing values inserted
    """
    kwargs = {}
    kwargs['dataset'] = self.frame_id  # Eager; forces eval now for following REST call
    kwargs['fraction'] = fraction
    if seed is not None: kwargs['seed'] = seed
    job = {}
    job['job'] = h2o.H2OConnection.post_json("MissingInserter", **kwargs)
    h2o.H2OJob(job, job_type=("Insert Missing Values")).poll()
    return self

  # generic reducers (min, max, sum, var)
  def min(self):
    """
    :return: The minimum value of all frame entries
    """
    return self._scalar("min", self)

  def max(self):
    """
    :return: The maximum value of all frame entries
    """
    return self._scalar("max", self)

  def sum(self, na_rm=False):
    """
    :return: The sum of all frame entries
    """
    return self._scalar("sumNA" if na_rm else "sum", self)

  def mean(self,na_rm=False):
    """
    :param na_rm: True or False to remove NAs from computation.
    :return: The mean of the column.
    """
    return self._scalar("mean", self, na_rm)

  def median(self, na_rm=False):
    """
    :return: Median of this column.
    """
    return self._scalar("median", self, na_rm)

  def var(self,y=None,use="everything"):
    """
    :param use: One of "everything", "complete.obs", or "all.obs".
    :return: The covariance matrix of the columns in this H2OFrame if y is
             given, or a eagerly computed scalar if y is not given.
    """
    if y is None: y = self
    if self.nrow==1 or (self.ncol==1 and y.ncol==1): return self._scalar("var",self,y,use)
    return self._newExpr("var",self,y,use)

  def sd(self):
    """
    :param na_rm: True or False to remove NAs from computation.
    :return: Standard deviation of the H2OVec elements.
    """
    return self._scalar("sd", self)

  def asfactor(self):
    """
    :return: A lazy Expr representing this vec converted to a factor
    """
    return self._newExpr("as.factor",self)

  def isfactor(self):
    """
    :return: A Expr representing the truth of whether or not this vec is a factor.
    """
    return self._scalar("is.factor", self)

  def anyfactor(self):
    """
    :return: Whether or not the frame has any factor columns
    """
    return self._scalar("any.factor", self)

  def transpose(self):
    """
    :return: The transpose of the self._newExpr.
    """
    return self._newExpr("t", self)

  def strsplit(self, pattern):
    """
    Split the strings in the target column on the given pattern

    Parameters
    ----------
      pattern : str
        The split pattern.

    :return: H2OFrame
    """
    return self._newExpr("strsplit", self, pattern)

  def countmatches(self, pattern):
    """
    Split the strings in the target column on the given pattern

    Parameters
    ----------
      pattern : str
        The pattern to count matches on in each string.

    :return: H2OFrame
    """
    return self._newExpr("countmatches", self, pattern)

  def trim(self):
    """
    Trim the edge-spaces in a column of strings (only operates on frame with one column)
    :return: H2OFrame
    """
    return self._newExpr("trim", self)

  def length(self):
    """
    Create a column containing the length of the strings in the target column (only operates on frame with one column)

    :return: H2OFrame
    """
    return self._newExpr("length", self)


  def table(self, data2=None):
    """
    Parameters
    ----------
      data2 : self._newExpr
        Default is None, can be an optional single column to aggregate counts by.

    :return: An self._newExpr of the counts at each combination of factor levels
    """
    return self._newExpr("table",self,data2) if data2 is not None else self._newExpr("table",self)

  def hist(self, breaks="Sturges", plot=True, **kwargs):
    """
    Compute a histogram over a numeric column. If breaks=="FD", the MAD is used over the IQR in computing bin width.

    :param breaks: breaks Can be one of the following: A string: "Sturges", "Rice", "sqrt", "Doane", "FD", "Scott." A single number for the number of breaks splitting the range of the vec into number of breaks bins of equal width. Or, A vector of numbers giving the split points, e.g., c(-50,213.2123,9324834)
    :param plot: A logical value indicating whether or not a plot should be generated (default is TRUE).
    :return: if plot is True, then return None, else, an self._newExpr with these columns: breaks, counts, mids_true, mids, and density
    """
    frame = self._newExpr("hist", self, breaks)
    total = frame["counts"].sum(True)
    densities = [(frame[i,"counts"]/total)*(1/(frame[i,"breaks"]-frame[i-1,"breaks"])) for i in range(1,frame["counts"].nrow)]
    densities.insert(0,0)
    densities_frame = H2OFrame.fromPython([[d] for d in densities])
    densities_frame.set_names(["density"])
    frame = frame.cbind(densities_frame)

    if plot:
      try:
        imp.find_module('matplotlib')
        import matplotlib
        if 'server' in kwargs.keys() and kwargs['server']: matplotlib.use('Agg', warn=False)
        import matplotlib.pyplot as plt
      except ImportError:
        print "matplotlib is required to make the histogram plot. Set `plot` to False, if a plot is not desired."
        return

      lower = float(frame[0,"breaks"])
      clist = h2o.as_list(frame["counts"], use_pandas=False)
      clist.pop(0)
      clist.pop(0)
      mlist = h2o.as_list(frame["mids"], use_pandas=False)
      mlist.pop(0)
      mlist.pop(0)
      counts = [float(c[0]) for c in clist]
      counts.insert(0,0)
      mids = [float(m[0]) for m in mlist]
      mids.insert(0,lower)
      plt.xlabel(self._col_names[0])
      plt.ylabel('Frequency')
      plt.title('Histogram of {0}'.format(self._col_names[0]))
      plt.bar(mids, counts)
      if not ('server' in kwargs.keys() and kwargs['server']): plt.show()

    else: return frame

  def sub(self, pattern, replacement, ignore_case=False):
    """
    sub performs replacement of the first matches respectively.

    :param pattern:
    :param replacement:
    :param ignore_case:

    :return: H2OFrame
    """
    return self._newExpr("replacefirst", self, pattern, replacement, ignore_case)

  def gsub(self, pattern, replacement, ignore_case=False):
    """
    gsub performs replacement of all matches respectively.

    :param pattern:
    :param replacement:
    :param ignore_case:
    :return: H2OFrame
    """
    return self._newExpr("replaceall", self, pattern, replacement, ignore_case)

  def interaction(self, factors, pairwise, max_factors, min_occurrence, destination_frame=None):
    """
    Categorical Interaction Feature Creation in H2O.
    Creates a frame in H2O with n-th order interaction features between categorical columns, as specified by
    the user.

    :param factors: factors Factor columns (either indices or column names).
    :param pairwise: Whether to create pairwise interactions between factors (otherwise create one higher-order interaction). Only applicable if there are 3 or more factors.
    :param max_factors: Max. number of factor levels in pair-wise interaction terms (if enforced, one extra catch-all factor will be made)
    :param min_occurrence: Min. occurrence threshold for factor levels in pair-wise interaction terms
    :param destination_frame: A string indicating the destination key. If empty, this will be auto-generated by H2O.
    :return: H2OFrame
    """
    return h2o.interaction(data=self, factors=factors, pairwise=pairwise, max_factors=max_factors,
                           min_occurrence=min_occurrence, destination_frame=destination_frame)

  def toupper(self):
    """
    Translate characters from lower to upper case for a particular column
    :return: H2OFrame
    """
    return self._newExpr("toupper", self)

  def tolower(self):
    """
    Translate characters from upper to lower case for a particular column
    :return: H2OFrame
    """
    return self._newExpr("tolower", self)

  def rep_len(self, length_out):
    """
    Replicates the values in `data` in the H2O backend

    :param length_out: the number of columns of the resulting self._newExpr
    :return: an self._newExpr
    """
    return self._newExpr("rep_len", self, length_out)

  def scale(self, center=True, scale=True):
    """
    Centers and/or scales the columns of the self._newExpr

    :return: H2OFrame
    :param center: either a ‘logical’ value or numeric list of length equal to the number of columns of the self._newExpr
    :param scale: either a ‘logical’ value or numeric list of length equal to the number of columns of self._newExpr.
    """
    return self._newExpr("scale", self, center, scale)

  def signif(self, digits=6):
    """
    :param digits:
    :return: The rounded values in the self._newExpr to the specified number of significant digits.
    """
    return self._newExpr("signif", self, digits)

  def round(self, digits=0):
    """
    :param digits:
    :return: The rounded values in the self._newExpr to the specified number of decimal digits.
    """
    return self._newExpr("round", self, digits)

  def asnumeric(self):
    """
    :return: A frame with factor columns converted to numbers (numeric columns untouched).
    """
    return self._newExpr("as.numeric", self)

  def ascharacter(self):
    """
    :return: A lazy Expr representing this vec converted to characters
    """
    return self._newExpr("as.character", self)

  def na_omit(self):
    """
    :return: Removes rows with NAs
    """
    return self._newExpr("na.omit", self)

  def isna(self):
    """
    :return: Returns a new boolean H2OVec.
    """
    return self._newExpr("is.na", self)

  def year(self):
    """
    :return: Returns a new year column from a msec-since-Epoch column
    """
    return self._newExpr("year", self)

  def month(self):
    """
    :return: Returns a new month column from a msec-since-Epoch column
    """
    return self._newExpr("month", self)

  def week(self):
    """
    :return: Returns a new week column from a msec-since-Epoch column
    """
    return self._newExpr("week", self)

  def day(self):
    """
    :return: Returns a new day column from a msec-since-Epoch column
    """
    return self._newExpr("day", self)

  def dayOfWeek(self):
    """
    :return: Returns a new Day-of-Week column from a msec-since-Epoch column
    """
    return self._newExpr("dayOfWeek", self)

  def hour(self):
    """
    :return: Returns a new Hour-of-Day column from a msec-since-Epoch column
    """
    return self._newExpr("hour", self)

  def runif(self, seed=None):
    """
    :param seed: A random seed. If None, then one will be generated.
    :return: A new H2OVec filled with doubles sampled uniformly from [0,1).
    """
    return self._newExpr("h2o.runif", self, -1 if seed is None else seed)

  def stratified_split(self,test_frac=0.2,seed=-1):
    """
    Construct a column that can be used to perform a random stratified split.

    Parameters
    ----------
      test_frac : float
        The fraction of rows that will belong to the "test".
      seed      : int
        For seeding the random splitting.


    :return: A categorical column of two levels "train" and "test".

    Examples
    --------
      >>> my_stratified_split = my_frame["response"].stratified_split(test_frac=0.3,seed=12349453)
      >>> train = my_frame[my_stratified_split=="train"]
      >>> test  = my_frame[my_stratified_split=="test"]

      # check the distributions among the initial frame, and the train/test frames match
      >>> my_frame["response"].table()["Count"] / my_frame["response"].table()["Count"].sum()
      >>> train["response"].table()["Count"] / train["response"].table()["Count"].sum()
      >>> test["response"].table()["Count"] / test["response"].table()["Count"].sum()
    """
    return self._newExpr('h2o.random_stratified_split', self, test_frac, seed)

  def match(self, table, nomatch=0):
    """
    Makes a vector of the positions of (first) matches of its first argument in its second.

    :param table:
    :param nomatch:

    :return: H2OFrame of one boolean column
    """
    return self._newExpr("match", self, table, nomatch, None)

  def cut(self, breaks, labels=None, include_lowest=False, right=True, dig_lab=3):
    """
    Cut a numeric vector into factor "buckets". Similar to R's cut method.
    :param breaks: The cut points in the numeric vector (must span the range of the col.)
    :param labels: Factor labels, defaults to set notation of intervals defined by breaks.s
    :param include_lowest: By default,  cuts are defined as (lo,hi]. If True, get [lo,hi].
    :param right: Include the high value: (lo,hi]. If False, get (lo,hi).
    :param dig_lab: Number of digits following the decimal point to consider.
    :return: A factor column.
    """
    return self._newExpr("cut",self,breaks,labels,include_lowest,right,dig_lab)

  def which(self):
    """
    Equivalent to [ index for index,value in enumerate(self) if value ]
    :return: A H2OFrame of 1 column filled with 0-based indices for which the
    elements are not zero
    """
    return self._newExpr("which",self)

  def ifelse(self,yes,no):
    """Equivalent to [y if t else n for t,y,n in zip(self,yes,no)]

    Based on the booleans in the test vector, the output has the values of the
    yes and no vectors interleaved (or merged together).  All Frames must have
    the same row count.  Single column frames are broadened to match wider
    Frames.  Scalars are allowed, and are also broadened to match wider frames.
    :param test: Frame of values treated as booleans; may be a single column
    :param yes: Frame to use if [test] is true ; may be a scalar or single column
    :param no:  Frame to use if [test] is false; may be a scalar or single column
    :return: A H2OFrame
    """
    return self._newExpr("ifelse",self,yes,no)

  def apply(self, fun=None, axis=0):
    """
    Apply a lambda expression to an self._newExpr.

    :param fun: A lambda expression to be applied per row or per column
    :param axis: 0: apply to each column; 1: apply to each row
    :return: An self._newExpr
    """
    if axis not in [0,1]:
      raise ValueError("margin must be either 0 (cols) or 1 (rows).")
    if fun is None:
      raise ValueError("No function to apply.")
    if isinstance(fun, type(lambda:0)) and fun.__name__ == (lambda:0).__name__:  # have lambda
      res = _bytecode_decompile_lambda(fun.func_code)
      return self._newExpr("apply",self, 1+(axis==0),*res)
    else:
      raise ValueError("unimpl: not a lambda")

  #### DO NOT ADD ANY MEMBER METHODS HERE ####



# private static methods
_id_ctr = 0
def _py_tmp_key(): 
  global _id_ctr   
  _id_ctr=_id_ctr+1 
  return "py_" + str(_id_ctr)
def _gen_header(cols): return ["C" + str(c) for c in range(1, cols + 1, 1)]
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
  lol = _is_list_of_lists(python_obj)  # do we have a list of lists: [[...], ..., [...]] ?
  if lol:
    _check_lists_of_lists(python_obj)  # must be a list of flat lists, raise ValueError if not
    # have list of lists, each list is a row
    # length of the longest list is the number of columns
    cols = max([len(l) for l in python_obj])

  # create the header
  header = _gen_header(cols)
  # shape up the data for csv.DictWriter
  data_to_write = [dict(zip(header, row)) for row in python_obj] if lol else [dict(zip(header, python_obj))]
  return header, data_to_write
def _is_list(l)    : return isinstance(l, (tuple, list))
def _is_str_list(l): return isinstance(l, (tuple, list)) and all([isinstance(i,basestring) for i in l])
def _is_num_list(l): return isinstance(l, (tuple, list)) and all([isinstance(i,(float,int)) for i in l])
def _is_list_of_lists(o):                  return any(isinstance(l, (list, tuple)) for l in o)
def _handle_numpy_array(python_obj):       return _handle_python_lists(python_obj=python_obj.tolist())
def _handle_pandas_data_frame(python_obj): return _handle_numpy_array(python_obj=python_obj.as_matrix())
def _handle_python_dicts(python_obj):
  header = python_obj.keys()
  is_valid = all([re.match(r'^[a-zA-Z_][a-zA-Z0-9_.]*$', col) for col in header])  # is this a valid header?
  if not is_valid:
    raise ValueError("Did not get a valid set of column names! Must match the regular expression: ^[a-zA-Z_][a-zA-Z0-9_.]*$ ")
  for k in python_obj:  # check that each value entry is a flat list/tuple
    v = python_obj[k]
    if isinstance(v, (tuple, list)):  # if value is a tuple/list, then it must be flat
      if _is_list_of_lists(v):
        raise ValueError("Values in the dictionary must be flattened!")

  rows = map(list, itertools.izip_longest(*python_obj.values()))
  data_to_write = [dict(zip(header, row)) for row in rows]
  return header, data_to_write
