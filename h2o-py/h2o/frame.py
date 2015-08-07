# -*- coding: utf-8 -*-
# import numpy    no numpy cuz windoz
import collections, csv, itertools, os, re, tempfile, uuid, urllib2, sys, urllib,imp
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
    self._id        = _py_tmp_key()  # gets overwritten if a parse happens
    self._keep      = False
    self._nrows     = None
    self._ncols     = None
    self._col_names = None
    self._computed  = False
    self._ast       = None

    if expr is not None:         self._ast = expr
    elif python_obj is not None: self._upload_python_object(python_obj)
    elif file_path is not None:  self._import_parse(file_path)
    elif raw_id is not None:     self._handle_text_key(raw_id)
    else: pass

  @staticmethod
  def get_frame(frame_id):
    res = h2o.H2OConnection.get_json("Frames/"+urllib.quote(frame_id))["frames"][0]
    fr = H2OFrame()
    fr._nrows = res["rows"]
    fr._ncols = res["total_column_count"]
    fr._id = res["frame_id"]["name"]
    fr._computed = True
    fr._keep = True
    fr._col_names = [c["label"] for c in res["columns"]]
    return fr

  def __str__(self): return self.__repr__()

  def _import_parse(self,file_path):
    rawkey = h2o.import_file(file_path)
    setup = h2o.parse_setup(rawkey)
    parse = h2o.parse(setup, _py_tmp_key())  # create a new key
    self._id = parse["job"]["dest"]["name"]
    self._computed=True
    self._nrows = int(H2OFrame(expr=ExprNode("nrow", self))._scalar())
    self._ncols = parse["number_columns"]
    self._col_names = parse['column_names'] if parse["column_names"] else ["C" + str(x) for x in range(1,self._ncols+1)]
    self._keep = True
    thousands_sep = h2o.H2ODisplay.THOUSANDS
    if isinstance(file_path, str): print "Imported {}. Parsed {} rows and {} cols".format(file_path,thousands_sep.format(self._nrows), thousands_sep.format(self._ncols))
    else:                          h2o.H2ODisplay([["File"+str(i+1),f] for i,f in enumerate(file_path)],None, "Parsed {} rows and {} cols".format(thousands_sep.format(self._nrows), thousands_sep.format(self._ncols)))

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
    csv_writer.writeheader()             # write the header
    csv_writer.writerows(data_to_write)  # write the data
    tmp_file.close()                     # close the streams
    self._upload_raw_data(tmp_path)      # actually upload the data to H2O
    os.remove(tmp_path)                  # delete the tmp file

  def _handle_text_key(self, text_key, check_header=None):
    """
    Handle result of upload_file
    :param test_key: A key pointing to raw text to be parsed
    :return: Part of the H2OFrame constructor.
    """
    # perform the parse setup
    setup = h2o.parse_setup(text_key)
    if check_header is not None: setup["check_header"] = check_header
    parse = h2o.parse(setup, _py_tmp_key())
    self._computed=True
    self._id = parse["destination_frame"]["name"]
    self._ncols = parse["number_columns"]
    self._col_names = cols = parse['column_names'] if parse["column_names"] else ["C" + str(x) for x in range(1,self._ncols+1)]
    self._nrows = int(H2OFrame(expr=ExprNode("nrow", self))._scalar())
    self._keep = True
    thousands_sep = h2o.H2ODisplay.THOUSANDS
    print "Uploaded {} into cluster with {} rows and {} cols".format(text_key, thousands_sep.format(self._nrows), thousands_sep.format(len(cols)))

  def _upload_raw_data(self, tmp_file_path):
    fui = {"file": os.path.abspath(tmp_file_path)}                            # file upload info is the normalized path to a local file
    dest_key = _py_tmp_key()                                                  # create a random name for the data
    h2o.H2OConnection.post_json("PostFile", fui, destination_frame=dest_key)  # do the POST -- blocking, and "fast" (does not real data upload)
    self._handle_text_key(dest_key, 1)                                        # actually parse the data and setup self._vecs

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
  def __floordiv__(self, i): return H2OFrame(expr=ExprNode("intDiv",self,i))
  def __mod__ (self, i): return H2OFrame(expr=ExprNode("mod", self,i))
  def __or__  (self, i): return H2OFrame(expr=ExprNode("|",   self,i))
  def __and__ (self, i): return H2OFrame(expr=ExprNode("&",   self,i))
  def __ge__  (self, i): return H2OFrame(expr=ExprNode(">=",  self,i))
  def __gt__  (self, i): return H2OFrame(expr=ExprNode(">",   self,i))
  def __le__  (self, i): return H2OFrame(expr=ExprNode("<=",  self,i))
  def __lt__  (self, i): return H2OFrame(expr=ExprNode("<",   self,i))
  def __eq__  (self, i): return H2OFrame(expr=ExprNode("==",  self,i))
  def __ne__  (self, i): return H2OFrame(expr=ExprNode("N",  self,i))
  def __pow__ (self, i): return H2OFrame(expr=ExprNode("^",   self,i))
  # rops
  def __rmod__(self, i): return H2OFrame(expr=ExprNode("mod",i,self))
  def __radd__(self, i): return self.__add__(i)
  def __rsub__(self, i): return H2OFrame(expr=ExprNode("-",i,  self))
  def __rand__(self, i): return self.__and__(i)
  def __ror__ (self, i): return self.__or__ (i)
  def __rdiv__(self, i): return H2OFrame(expr=ExprNode("/",i,  self))
  def __rfloordiv__(self, i): return H2OFrame(expr=ExprNode("intDiv",i,self))
  def __rmul__(self, i): return self.__mul__(i)
  def __rpow__(self, i): return H2OFrame(expr=ExprNode("^",i,  self))
  # unops
  def __abs__ (self):        return H2OFrame(expr=ExprNode("abs",self))
  def __contains__(self, i): return all([(t==self).any() for t in i]) if _is_list(i) else (i==self).any()

  def mult(self, matrix):
    """
    Perform matrix multiplication.

    :param matrix: The matrix to multiply to the left of self.
    :return: The multiplied matrices.
    """
    return H2OFrame(expr=ExprNode("x", self, matrix))

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
    return H2OFrame(expr=ExprNode("mktime", year,month,day,hour,minute,second,msec))._frame()

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
    return H2OFrame(expr=ExprNode("sd", self))._scalar()

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
    return H2OFrame(expr=ExprNode("filterNACols", self, frac))._frame()

  def dim(self):
    """
    Get the number of rows and columns in the H2OFrame.

    :return: The number of rows and columns in the H2OFrame as a list [rows, cols].
    """
    return [self.nrow(), self.ncol()]

  def unique(self):
    """
    Extract the unique values in the column.

    :return: A new H2OFrame of just the unique values in the column.
    """
    return H2OFrame(expr=ExprNode("unique", self))._frame()

  def show(self): self.head(rows=10,cols=sys.maxint,show=True)  # all columns

  def head(self, rows=10, cols=200, show=False, **kwargs):
    """
    Analgous to R's `head` call on a data.frame. Display a digestible chunk of the H2OFrame starting from the beginning.

    :param rows: Number of rows to display.
    :param cols: Number of columns to display.
    :param show: Display the output.
    :param kwargs: Extra arguments passed from other methods.
    :return: None
    """
    self._eager()
    nrows = min(self.nrow(), rows)
    ncols = min(self.ncol(), cols)
    colnames = self.names()[0:ncols]
    head = self[0:10,0:ncols]
    res = head.as_data_frame(False)[1:]
    if show:
      print "First {} rows and first {} columns: ".format(nrows, ncols)
      h2o.H2ODisplay(res,colnames)
    return head

  def tail(self, rows=10, cols=200, show=False, **kwargs):
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
    start_idx = max(self.nrow()-nrows,0)
    tail = self[start_idx:(start_idx+nrows),:]
    res = tail.as_data_frame(False)
    colnames = res.pop(0)
    if show:
      print "Last {} rows and first {} columns: ".format(nrows,ncols)
      h2o.H2ODisplay(res,colnames)
    return tail

  def levels(self, col=None):
    """
    Get the factor levels for this frame and the specified column index.

    :param col: A column index in this H2OFrame.
    :return: a list of strings that are the factor levels for the column.
    """
    if self.ncol()==1 or col is None:
      lol=h2o.as_list(H2OFrame(expr=ExprNode("levels", self))._frame(), False)[1:]
      levels=[level for l in lol for level in l] if self.ncol()==1 else lol
    elif col is not None:
      lol=h2o.as_list(H2OFrame(expr=ExprNode("levels", ExprNode("[", self, None,col)))._frame(),False)[1:]
      levels=[level for l in lol for level in l]
    else:                             levels=None
    return None if levels is None or levels==[] else levels

  def nlevels(self, col=None):
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
    return H2OFrame(expr=ExprNode("setLevel", self, level))._frame()

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
    h2o.rapids(ExprNode._collapse_sb(ExprNode("setDomain", self, levels)._eager()))
    self._update()
    return self

  def setNames(self,names):
    """
    Change the column names to `names`.

    :param names: A list of strings equal to the number of columns in the H2OFrame.
    :return: None. Rename the column names in this H2OFrame.
    """
    h2o.rapids(ExprNode._collapse_sb(ExprNode("colnames=", self, range(self.ncol()), names)._eager()),id=self._id)
    self._update()
    return self

  def setName(self,col=None,name=None):
    """
    Set the name of the column at the specified index.

    :param col: Index of the column whose name is to be set.
    :param name: The new name of the column to set
    :return: the input frame
    """
    if not isinstance(col, int) and self.ncol() > 1: raise ValueError("`col` must be an index. Got: " + str(col))
    if self.ncol() == 1: col = 0
    h2o.rapids(ExprNode._collapse_sb(ExprNode("colnames=", self, col, name)._eager()),id=self._id)
    self._update()
    return self

  def describe(self):
    """
    Generate an in-depth description of this H2OFrame.

    The description is a tabular print of the type, min, max, sigma, number of zeros,
    and number of missing elements for each H2OVec in this H2OFrame.

    :return: None (print to stdout)
    """
    self._eager()
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
    self._eager()
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

  def __repr__(self):
    if sys.gettrace() is None:
      self.show()
    return ""

  def as_date(self,format):
    """
    Return the column with all elements converted to millis since the epoch.

    :param format: The date time format string
    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("as.Date",self,format))

  def cumsum(self):
    """
    :return: The cumulative sum over the column.
    """
    return H2OFrame(expr=ExprNode("cumsum",self))

  def cumprod(self):
    """
    :return: The cumulative product over the column.
    """
    return H2OFrame(expr=ExprNode("cumprod",self))

  def cummin(self):
    """
    :return: The cumulative min over the column.
    """
    return H2OFrame(expr=ExprNode("cummin",self))

  def cummax(self):
    """
    :return: The cumulative max over the column.
    """
    return H2OFrame(expr=ExprNode("cummax",self))

  def prod(self,na_rm=False):
    """
    :return: The product of the column.
    """
    return H2OFrame(expr=ExprNode("prod",self,na_rm))._scalar()

  def any(self,na_rm=False):
    """
    :return: True if any element is True in the column.
    """
    return H2OFrame(expr=ExprNode("any",self,na_rm))._scalar()

  def all(self):
    """
    :return: True if every element is True in the column.
    """
    return H2OFrame(expr=ExprNode("all",self,False))._scalar()

  def isnumeric(self):
    """
    :return: True if the column is numeric, otherwise return False
    """
    return H2OFrame(expr=ExprNode("is.numeric",self))._scalar()

  def isstring(self):
    """
    :return: True if the column is a string column, otherwise False (same as ischaracter)
    """
    return H2OFrame(expr=ExprNode("is.character",self))._scalar()

  def ischaracter(self):
    """
    :return: True if the column is a character column, otherwise False (same as isstring)
    """
    return self.isstring()

  def remove_vecs(self, cols):
    """
    :param cols: Drop these columns.
    :return: A frame with the columns dropped.
    """
    self._eager()
    is_char = all([isinstance(i,(unicode,str)) for i in cols])
    if is_char:
      cols = [self._find_idx(col) for col in cols]
    cols = sorted(cols)
    return H2OFrame(expr=ExprNode("removeVecs",self,cols))._frame()

  def structure(self):
    """
    Similar to R's str method: Compactly Display the Structure of this H2OFrame instance.

    :return: None
    """
    df = self.head().as_data_frame(use_pandas=False)
    nr = self.nrow()
    nc = len(df[0])
    cn = df.pop(0)
    width = max([len(c) for c in cn])
    isfactor = [c.isfactor() for c in self]
    numlevels  = [self.nlevels(i) for i in range(nc)]
    lvls = self.levels()
    print "H2OFrame '{}': \t {} obs. of {} variables(s)".format(self._id,nr,nc)
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

    :param use_pandas: A flag specifying whether or not to attempt to coerce to Pandas.
    :return: A local python object containing this H2OFrame instance's data.s
    """
    self._eager()
    url = 'http://' + h2o.H2OConnection.ip() + ':' + str(h2o.H2OConnection.port()) + "/3/DownloadDataset?frame_id=" + urllib.quote(self._id) + "&hex_string=false"
    response = urllib2.urlopen(url)
    if h2o.can_use_pandas() and use_pandas:
      import pandas
      return pandas.read_csv(response, low_memory=False)
    else:
      cr = csv.reader(response)
      rows = []
      for row in cr: rows.append([''] if row == [] else row)
      return rows

  # Find a named H2OVec and return the zero-based index for it.  Error is name is missing
  def _find_idx(self,name):
    for i,v in enumerate(self._col_names):
      if name == v: return i
    raise ValueError("Name " + name + " not in Frame")

  def index(self,name):
    self._eager()
    return self._find_idx(name)

  def flatten(self):
    return H2OFrame(expr=ExprNode("flatten",self))._scalar()

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
    if isinstance(item, (int,str,list)): return H2OFrame(expr=ExprNode("cols",self,item))  # just columns
    elif isinstance(item, slice):
      item = slice(item.start,min(self.ncol(),item.stop))
      return H2OFrame(expr=ExprNode("cols",self,item))
    elif isinstance(item, H2OFrame):           return H2OFrame(expr=ExprNode("rows",self,item))  # just rows
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
      if allrows: return H2OFrame(expr=ExprNode("cols",self,item[1]))  # fr[:,cols] -> really just a column slice
      if allcols: return H2OFrame(expr=ExprNode("rows",self,item[0]))  # fr[rows,:] -> really just a row slices

      res = H2OFrame(expr=ExprNode("rows", ExprNode("cols",self,item[1]),item[0]))
      return res.flatten() if isinstance(item[0], (str,unicode,int)) and isinstance(item[1],(str,unicode,int)) else res

  def __setitem__(self, b, c):
    """
    Replace a column in an H2OFrame.

    :param b: A 0-based index or a column name or tuple
    :param c: The vector that 'b' is replaced with.
    :return: Returns this H2OFrame.
    """
    # (= dst src col_expr row_expr)
    update_index=-1
    newcolname=None
    if isinstance(b, (str,unicode)):
      if b not in self.col_names():
        newcolname=b
        update_index=self._ncols
      else:
        update_index=self.col_names().index(b)
    elif isinstance(b, int): update_index=b

    col_expr = b[1] if isinstance(b,tuple) else update_index
    if isinstance(col_expr, (str,unicode)): col_expr=self.col_names().index(col_expr)

    if col_expr == -1:   # no columns given, just have row_expr => select all columns...
      col_expr=slice(0,self.ncol())

    if isinstance(col_expr, slice):
      if col_expr.start is None and col_expr.stop is None:
        col_expr = slice(0,self.ncol())

    row_expr = b[0] if isinstance(b,tuple) else b if isinstance(b, H2OFrame) else slice(0,self.nrow())
    src = c._frame() if isinstance(c,H2OFrame) else float("nan") if c is None else c
    expr = ExprNode("=", self, src, col_expr, row_expr)
    h2o.rapids(ExprNode._collapse_sb(expr._eager()), self._id)
    self._update()
    if newcolname is not None: self.setName(update_index, newcolname)

  def __int__(self):   return int(self._scalar())

  def __float__(self): return self._scalar()

  def __del__(self):
    if not self._keep and self._computed: h2o.remove(self)

  def keep(self): self._keep = True

  def drop(self, i):
    """
    Returns a Frame with the column at index i dropped.

    :param i: Column to drop
    :return: Returns an H2OFrame
    """
    if isinstance(i, (unicode,str)): i = self._find_idx(i)
    return H2OFrame(expr=ExprNode("cols", self,-(i+1)))._frame()

  def __len__(self):
    """
    :return: Number of columns in this H2OFrame
    """
    return self.ncol()

  def quantile(self, prob=None, combine_method="interpolate"):
    """
    Compute quantiles over a given H2OFrame.

    :param prob: A list of probabilties, default is [0.01,0.1,0.25,0.333,0.5,0.667,0.75,0.9,0.99]. You may provide any sequence of any length.
    :param combine_method: For even samples, how to combine quantiles. Should be one of ["interpolate", "average", "low", "hi"]
    :return: an H2OFrame containing the quantiles and probabilities.
    """
    if len(self) == 0: return self
    if not prob: prob=[0.01,0.1,0.25,0.333,0.5,0.667,0.75,0.9,0.99]
    return H2OFrame(expr=ExprNode("quantile",self,prob,combine_method))._frame()

  def cbind(self,data):
    """
    :param data: H2OFrame or H2OVec to cbind to self
    :return: void
    """
    return H2OFrame(expr=ExprNode("cbind", self, data))

  def rbind(self, data):
    """
    Combine H2O Datasets by Rows.
    Takes a sequence of H2O data sets and combines them by rows.
    :param data: an H2OFrame
    :return: self, with data appended (row-wise)
    """
    if not isinstance(data, H2OFrame): raise ValueError("`data` must be an H2OFrame, but got {0}".format(type(data)))
    return H2OFrame(expr=ExprNode("rbind", self, data))

  def split_frame(self, ratios=[0.75], destination_frames=""):
    """
    Split a frame into distinct subsets of size determined by the given ratios.
    The number of subsets is always 1 more than the number of ratios given.

    :param data: The dataset to split.
    :param ratios: The fraction of rows for each split.
    :param destination_frames: names of the split frames
    :return: a list of frames
    """
    j = h2o.H2OConnection.post_json("SplitFrame", dataset=self._id, ratios=ratios, destination_frames=destination_frames)
    h2o.H2OJob(j, "Split Frame").poll()
    return [h2o.get_frame(i["name"]) for i in j["destination_frames"]]

  # ddply in h2o
  def ddply(self,cols,fun):
    """
    :param cols: Column names used to control grouping
    :param fun: Function to execute on each group.  Right now limited to textual Rapids expression
    :return: New frame with 1 row per-group, of results from 'fun'
    """
    return H2OFrame(expr=ExprNode("ddply", self, cols, fun))._frame()

  def group_by(self,cols,aggregates,order_by=None):
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
    aggs = []
    for k in aggregates: aggs += (aggregates[k] + [str(k)])
    aggs = h2o.ExprNode("agg", *aggs)
    return H2OFrame(expr=ExprNode("GB", self,cols,aggs,order_by))._frame()

  def impute(self,column,method="mean",combine_method="interpolate",by=None,inplace=True):
    """
    Impute a column in this H2OFrame.

    :param column: The column to impute
    :param method: How to compute the imputation value.
    :param combine_method: For even samples and method="median", how to combine quantiles.
    :param by: Columns to group-by for computing imputation value per groups of columns.
    :param inplace: Impute inplace?
    :return: the imputed frame.
    """
    if isinstance(column, (str, unicode)): column = self._find_idx(column)
    if isinstance(by, (str, unicode)):     by     = self._find_idx(by)
    return H2OFrame(expr=ExprNode("h2o.impute", self, column, method, combine_method, by, inplace))._frame()

  def merge(self, other, allLeft=False, allRite=False):
    """
    Merge two datasets based on common column names

    :param other: Other dataset to merge.  Must have at least one column in common with self, and all columns in common are used as the merge key.  If you want to use only a subset of the columns in common, rename the other columns so the columns are unique in the merged result.
    :param allLeft: If true, include all rows from the left/self frame
    :param allRite: If true, include all rows from the right/other frame
    :return: Original self frame enhanced with merged columns and rows
    """
    return H2OFrame(expr=ExprNode("merge", self, other, allLeft, allRite))._frame()

  def insert_missing_values(self, fraction=0.1, seed=None):
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
    self._eager()
    kwargs = {}
    kwargs['dataset'] = self._id
    kwargs['fraction'] = fraction
    if seed is not None: kwargs['seed'] = seed
    job = {}
    job['job'] = h2o.H2OConnection.post_json("MissingInserter", **kwargs)
    h2o.H2OJob(job, job_type=("Insert Missing Values")).poll()
    return self

  # generic reducers (min, max, sum, var)
  def min(self, na_rm=False):
    """
    :return: The minimum value of all frame entries
    """
    return H2OFrame(expr=ExprNode("minNA" if na_rm else "min", self))._scalar()

  def max(self, na_rm=False):
    """
    :return: The maximum value of all frame entries
    """
    return H2OFrame(expr=ExprNode("maxNA" if na_rm else "max", self))._scalar()

  def sum(self, na_rm=False):
    """
    :return: The sum of all frame entries
    """
    return H2OFrame(expr=ExprNode("sumNA" if na_rm else "sum", self))._scalar()

  def mean(self,na_rm=False):
    """
    :param na_rm: True or False to remove NAs from computation.
    :return: The mean of the column.
    """
    return H2OFrame(expr=ExprNode("meanNA" if na_rm else "mean", self))._scalar()

  def median(self):
    """
    :return: Median of this column.
    """
    return H2OFrame(expr=ExprNode("median", self))._scalar()

  def var(self,y=None,use="everything"):
    """
    :param use: One of "everything", "complete.obs", or "all.obs".
    :return: The covariance matrix of the columns in this H2OFrame.
    """
    return H2OFrame(expr=ExprNode("var",self,y,use))

  def asfactor(self):
    """
    :return: A lazy Expr representing this vec converted to a factor
    """
    return H2OFrame(expr=ExprNode("as.factor",self))._frame()

  def isfactor(self):
    """
    :return: A lazy Expr representing the truth of whether or not this vec is a factor.
    """
    return H2OFrame(expr=ExprNode("is.factor", self))._scalar()

  def anyfactor(self):
    """
    :return: Whether or not the frame has any factor columns
    """
    return H2OFrame(expr=ExprNode("any.factor", self))._scalar()

  def transpose(self):
    """
    :return: The transpose of the H2OFrame.
    """
    return H2OFrame(expr=ExprNode("t", self))

  def strsplit(self, pattern):
    """
    Split the strings in the target column on the given pattern

    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("strsplit", self, pattern))

  def trim(self):
    """
    Trim the edge-spaces in a column of strings (only operates on frame with one column)

    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("trim", self))

  def table(self, data2=None):
    """
    :return: a frame of the counts at each combination of factor levels
    """
    return H2OFrame(expr=ExprNode("table",self,data2))

  def hist(self, breaks="Sturges", plot=True, **kwargs):
    """
    Compute a histogram over a numeric column. If breaks=="FD", the MAD is used over the IQR in computing bin width.

    :param breaks: breaks Can be one of the following: A string: "Sturges", "Rice", "sqrt", "Doane", "FD", "Scott." A
    single number for the number of breaks splitting the range of the vec into number of breaks bins of equal width. Or,
    A vector of numbers giving the split points, e.g., c(-50,213.2123,9324834)
    :param plot: A logical value indicating whether or not a plot should be generated (default is TRUE).
    :return: if plot is True, then return None, else, an H2OFrame with these columns: breaks, counts, mids_true, mids,
    and density
    """
    frame = H2OFrame(expr=ExprNode("hist", self, breaks))._frame()

    total = frame["counts"].sum(True)
    densities = [(frame[i,"counts"]/total)*(1/(frame[i,"breaks"]-frame[i-1,"breaks"])) for i in range(1,frame["counts"].nrow())]
    densities.insert(0,0)
    densities_frame = H2OFrame(python_obj=[[d] for d in densities])
    densities_frame.setNames(["density"])
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
    sub and gsub perform replacement of the first and all matches respectively.
    Of note, mutates the frame.

    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("sub",pattern,replacement,self,ignore_case))

  def gsub(self, pattern, replacement, ignore_case=False):
    """
    sub and gsub perform replacement of the first and all matches respectively.
    Of note, mutates the frame.
    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("gsub", pattern, replacement, self, ignore_case))

  def interaction(self, factors, pairwise, max_factors, min_occurrence, destination_frame=None):
    """
    Categorical Interaction Feature Creation in H2O.
    Creates a frame in H2O with n-th order interaction features between categorical columns, as specified by
    the user.

    :param factors: factors Factor columns (either indices or column names).
    :param pairwise: Whether to create pairwise interactions between factors (otherwise create one
    higher-order interaction). Only applicable if there are 3 or more factors.
    :param max_factors: Max. number of factor levels in pair-wise interaction terms (if enforced, one extra catch-all
    factor will be made)
    :param min_occurrence: Min. occurrence threshold for factor levels in pair-wise interaction terms
    :param destination_frame: A string indicating the destination key. If empty, this will be auto-generated by H2O.
    :return: H2OFrame
    """
    return h2o.interaction(data=self, factors=factors, pairwise=pairwise, max_factors=max_factors,
                           min_occurrence=min_occurrence, destination_frame=destination_frame)

  def toupper(self):
    """
    Translate characters from lower to upper case for a particular column
    Of note, mutates the frame.
    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("toupper", self))

  def tolower(self):
    """
    Translate characters from upper to lower case for a particular column
    Of note, mutates the frame.
    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("tolower", self))

  def rep_len(self, length_out):
    """
    Replicates the values in `data` in the H2O backend

    :param length_out: the number of columns of the resulting H2OFrame
    :return: an H2OFrame
    """
    return H2OFrame(expr=ExprNode("rep_len", self, length_out))

  def scale(self, center=True, scale=True):
    """
    Centers and/or scales the columns of the H2OFrame

    :return: H2OFrame
    :param center: either a ‘logical’ value or numeric list of length equal to the number of columns of the H2OFrame
    :param scale: either a ‘logical’ value or numeric list of length equal to the number of columns of H2OFrame.
    """
    return H2OFrame(expr=ExprNode("scale", self, center, scale))

  def signif(self, digits=6):
    """
    :return: The rounded values in the H2OFrame to the specified number of significant digits.
    """
    return H2OFrame(expr=ExprNode("signif", self, digits))

  def round(self, digits=0):
    """
    :return: The rounded values in the H2OFrame to the specified number of decimal digits.
    """
    return H2OFrame(expr=ExprNode("round", self, digits))

  def asnumeric(self):
    """
    :return: A frame with factor columns converted to numbers (numeric columns untouched).
    """
    return H2OFrame(expr=ExprNode("as.numeric", self))

  def ascharacter(self):
    """
    :return: A lazy Expr representing this vec converted to characters
    """
    return H2OFrame(expr=ExprNode("as.character", self))

  def na_omit(self):
    """
    :return: Removes rows with NAs
    """
    return H2OFrame(expr=ExprNode("na.omit", self))._frame()

  def isna(self):
    """
    :return: Returns a new boolean H2OVec.
    """
    return H2OFrame(expr=ExprNode("is.na", self))

  def year(self):
    """
    :return: Returns a new year column from a msec-since-Epoch column
    """
    return H2OFrame(expr=ExprNode("year", self))

  def month(self):
    """
    :return: Returns a new month column from a msec-since-Epoch column
    """
    return H2OFrame(expr=ExprNode("month", self))

  def week(self):
    """
    :return: Returns a new week column from a msec-since-Epoch column
    """
    return H2OFrame(expr=ExprNode("week", self))

  def day(self):
    """
    :return: Returns a new day column from a msec-since-Epoch column
    """
    return H2OFrame(expr=ExprNode("day", self))

  def dayOfWeek(self):
    """
    :return: Returns a new Day-of-Week column from a msec-since-Epoch column
    """
    return H2OFrame(expr=ExprNode("dayOfWeek", self))

  def hour(self):
    """
    :return: Returns a new Hour-of-Day column from a msec-since-Epoch column
    """
    return H2OFrame(expr=ExprNode("hour", self))

  def runif(self, seed=None):
    """
    :param seed: A random seed. If None, then one will be generated.
    :return: A new H2OVec filled with doubles sampled uniformly from [0,1).
    """
    return H2OFrame(expr=ExprNode("h2o.runif", self, -1 if seed is None else seed))

  def match(self, table, nomatch=0):
    """
    Makes a vector of the positions of (first) matches of its first argument in its second.

    :return: bit H2OVec
    """
    return H2OFrame(expr=ExprNode("match", self, table, nomatch, None))

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
    return H2OFrame(expr=ExprNode("cut",self,breaks,labels,include_lowest,right,dig_lab))



  # convenience methods for eagering sclars, frames, and deflating 1x1 frames to scalars
  def _scalar(self):
    res = h2o.rapids(ExprNode._collapse_sb(self._ast._eager()))["scalar"]
    if res == "TRUE": return True
    if res == "FALSE":return False
    try:    return float(res)
    except: return res

  def _frame(self):  # force an eval on the frame and return it
    self._eager()
    return self

  ##### WARNING: MAGIC REF COUNTING CODE BELOW.
  #####          CHANGE AT YOUR OWN RISK.
  ##### ALSO:    DO NOT ADD METHODS BELOW THIS LINE (pretty please)
  def _eager(self, pytmp=True):
    if not self._computed:
      # top-level call to execute all subparts of self._ast
      sb = self._ast._eager()
      if pytmp:
        h2o.rapids(ExprNode._collapse_sb(sb), self._id)
        sb = [self._id," "]
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
    if self._computed: sb += [self._id+" "]
    else:              sb += self._eager(True) if (len(gc.get_referrers(self)) >= H2OFrame.MAGIC_REF_COUNT) else self._eager(False)

  def _update(self):
    res = h2o.frame(self._id)["frames"][0]  # TODO: exclude here?
    self._nrows = res["rows"]
    self._ncols = len(res["columns"])
    self._col_names = [c["label"] for c in res["columns"]]
    self._computed=True
    self._ast=None
  #### DO NOT ADD ANY MEMBER METHODS HERE ####



# private static methods
def _py_tmp_key(): return unicode("py" + str(uuid.uuid4()))
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
def _is_str_list(l): return isinstance(l, (tuple, list)) and all([isinstance(i,(str,unicode)) for i in l])
def _is_num_list(l): return isinstance(l, (tuple, list)) and all([isinstance(i,(float,int  )) for i in l])
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
