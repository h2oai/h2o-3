# -*- coding: utf-8 -*-
# import numpy    no numpy cuz windoz
import collections, csv, itertools, os, re, tempfile, uuid, urllib2, sys, urllib,imp,copy,weakref,inspect,ast
from astfun import _bytecode_decompile_lambda
from expr import h2o,ExprNode
import gc
from group_by import GroupBy


# TODO: Automatically convert column names into Frame properties!


class H2OFrameWeakRefMixin:
  __refs__ = collections.defaultdict(list)
  def __init__(self):
    self.__refs__[self.__class__].append(weakref.ref(self))

  @classmethod
  def get_instance_ids(cls):
    for inst_ref in cls.__refs__[cls]:
      inst = inst_ref()
      if inst is not None:
        yield inst._id

  @classmethod
  def drop_instance(cls, inst):
    cls.__refs__[cls].remove(weakref.ref(inst))

class H2OFrame(H2OFrameWeakRefMixin):

  # Magical count-of-5:   (get 2 more when looking at it in debug mode)
  #  2 for _do_it frame, 2 for _do_it local dictionary list, 1 for parent
  MAGIC_REF_COUNT = 5 if sys.gettrace() is None else 7  # M = debug ? 7 : 5
  COUNTING = True
  dropped_instances = []  # keep track of dropped instances while not counting

  def __init__(self, python_obj=None, file_path=None, raw_id=None, expr=None, destination_frame="", header=(-1, 0, 1), separator="", column_names=None, column_types=None, na_strings=None):
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
    H2OFrameWeakRefMixin.__init__(self)
    self._id        = _py_tmp_key()  # gets overwritten if a parse happens
    self._keep      = False
    self._nrows     = None
    self._ncols     = None
    self._col_names = None
    self._types     = None
    self._computed  = False
    self._ast       = None
    self._data      = None  # any cached data

    kwargs = {k:v for k, v in locals().items() if k in ["destination_frame", "header", "separator", "column_names", "column_types", "na_strings"]}
    if expr is not None:         self._ast = expr
    elif python_obj is not None: self._upload_python_object(python_obj, **kwargs)
    elif file_path is not None:  self._import_parse(file_path, **kwargs)
    elif raw_id is not None:     self._handle_text_key(raw_id, **kwargs)
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
    types = [c["type"] for c in res["columns"]]
    fr._types = dict(zip(fr._col_names,types))
    return fr

  def __str__(self): return self._id

  def _import_parse(self, file_path, **kwargs):
    rawkey = h2o.lazy_import(file_path)
    setup = h2o.parse_setup(rawkey, **kwargs)
    parse = h2o._parse(setup)
    self._update_post_parse(parse)
    thousands_sep = h2o.H2ODisplay.THOUSANDS
    if isinstance(file_path, str): print "Imported {}. Parsed {} rows and {} cols".format(file_path,thousands_sep.format(self._nrows), thousands_sep.format(self._ncols))
    else:                          h2o.H2ODisplay([["File"+str(i+1),f] for i,f in enumerate(file_path)],None, "Parsed {} rows and {} cols".format(thousands_sep.format(self._nrows), thousands_sep.format(self._ncols)))

  def _upload_python_object(self, python_obj, **kwargs):
    """
    Properly handle native python data types. For a discussion of the rules and
    permissible data types please refer to the main documentation for H2OFrame.

    :param python_obj: A tuple, list, dict, collections.OrderedDict
    :param kwargs: Optional arguments for input into parse_setup(), such as column_names and column_types
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
    #csv_writer.writeheader()                     # write the header
    if not kwargs['column_names']: kwargs['column_names'] = header
    csv_writer.writerows(data_to_write)            # write the data
    tmp_file.close()                               # close the streams
    self._upload_raw_data(tmp_path, **kwargs)      # actually upload the data to H2O
    os.remove(tmp_path)                            # delete the tmp file

  def _handle_text_key(self, text_key, **kwargs):
    """
    Handle result of upload_file

    :param test_key: A key pointing to raw text to be parsed
    :param kwargs: Additional optional arguments for h2o.parse_setup(), such as column_names and column_types.
    :return: Part of the H2OFrame constructor.
    """
    # perform the parse setup
    setup = h2o.parse_setup(text_key, **kwargs)
    parse = h2o._parse(setup)
    self._update_post_parse(parse)
    thousands_sep = h2o.H2ODisplay.THOUSANDS
    print "Uploaded {} into cluster with {} rows and {} cols".format(text_key, thousands_sep.format(self._nrows), thousands_sep.format(self._ncols))

  def _upload_raw_data(self, tmp_file_path, **kwargs):
    fui = {"file": os.path.abspath(tmp_file_path)}                            # file upload info is the normalized path to a local file
    dest_key = _py_tmp_key()                                                  # create a random name for the data
    h2o.H2OConnection.post_json("PostFile", fui, destination_frame=dest_key)  # do the POST -- blocking, and "fast" (does not real data upload)
    self._handle_text_key(dest_key, **kwargs)                                 # actually parse the data and setup self._vecs

  def __iter__(self):
    """
    Allows for list comprehensions over an H2OFrame

    :return: An iterator over the H2OFrame
    """
    self._eager()
    ncol = self._ncols
    return (self[i] for i in range(ncol))

  def where(self):
	fr = H2OFrame(expr=ExprNode("h2o.which", self))
	idx = h2o.as_list(fr)["C1"]
	return [i-1 for i in idx]

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
  def __ne__  (self, i): return H2OFrame(expr=ExprNode("!=",  self,i))
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
  def __invert__(self): return H2OFrame(expr=ExprNode("!!", self))

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

  @property
  def columns(self):
    """
    Retrieve the column names for this H2OFrame.

    :return: A list of column names.
    """
    return self.col_names

  @property
  def col_names(self):
    """
    Retrieve the column names (one name per H2OVec) for this H2OFrame.

    :return: A character list[] of column names.
    """
    self._eager()
    return copy.deepcopy(self._col_names)

  @property
  def names(self,i=None):
    """
    Retrieve the column names (one name per H2OVec) for this H2OFrame.

    :return: A character list[] of column names.
    """
    self._eager()
    return self.col_names if i is None else self.columns[i]

  @property
  def nrow(self):
    """
    Get the number of rows in this H2OFrame.

    :return: The number of rows in this dataset.
    """
    self._eager()
    return self._nrows

  @property
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

  @property
  def dim(self):
    """
    Get the number of rows and columns in the H2OFrame.

    :return: The number of rows and columns in the H2OFrame as a list [rows, cols].
    """
    return [self.nrow, self.ncol]

  @property
  def shape(self):
    """
    Get the shape of the H2OFrame.

    :return: A tuple (nrow, ncol)
    """
    return (self.nrow, self.ncol)

  @property
  def types(self):
    """
    Get the column types of H2OFrame.

    :return: A dictionary of column_name-type pairs
    """
    return copy.deepcopy(self._types)

  @property
  def frame_id(self):
    """
    Get the frame name.

    :return: Get the name of this frame.
    """
    return self._id

  def unique(self):
    """
    Extract the unique values in the column.

    :return: A new H2OFrame of just the unique values in the column.
    """
    return H2OFrame(expr=ExprNode("unique", self))._frame()

  def show(self, use_pandas=True):
    """
    Used by the H2OFrame.__repr__ method to display a snippet of the data frame.

    :param use_pandas: Display a pandas style data frame (better for pretty printing wide datasets)
    :return: None
    """
    self.head(rows=10,cols=sys.maxint,show=True,use_pandas=use_pandas)  # all columns

  def head(self,rows=10,cols=200,show=True,use_pandas=False):
    """
    Analogous to R's `head` call on a data.frame. Display a digestible chunk of the H2OFrame starting from the beginning.

    :param rows: Number of rows starting from the topmost to display.
    :param cols: Number of columns starting from the leftmost to display.
    :param show: A flag specifying whether or not to display the output. If True, return None.
    :param use_pandas: A flag specifying whether or not to return a pandas DataFrame.
    :return: A local python object (a list of lists of strings, each list is a row, if use_pandas=False, otherwise a
    pandas DataFrame) containing this H2OFrame instance's data. If show = True, print only and return None.
    """
    self._eager()
    nrows = min(self.nrow, rows)
    ncols = min(self.ncol, cols)
    res = self[:nrows,:ncols].as_data_frame(use_pandas=use_pandas)
    if show:
      self._do_show(res,use_pandas)
    else:
      return res

  def _do_show(self,fr,use_pandas):
    print "H2OFrame with {} rows and {} columns: ".format(self.nrow, self.ncol)
    if use_pandas:
      import pandas
      pandas.options.display.max_rows=20
      if h2o.H2ODisplay._in_ipy():
        from IPython.display import display
        display(fr)
      else:
        print fr
    else:
      h2o.H2ODisplay(fr[1:],fr[0])

  def tail(self, rows=10, cols=200, show=True, use_pandas=False):
    """
    Analogous to R's `tail` call on a data.frame. Display a digestible chunk of the H2OFrame starting from the end.

    :param rows: Number of rows starting from the bottommost to display in their original order.
    :param cols: Number of columns starting from the leftmost to display.
    :param show: A flag specifying whether or not to display the output. If True, return None.
    :param use_pandas: A flag specifying whether or not to return a pandas DataFrame.
    :return: A local python object (a list of lists of strings, each list is a row, if use_pandas=False, otherwise a pandas
     DataFrame) containing this H2OFrame instance's data. If show = True, print only and return None.
    """
    self._eager()
    nrows = min(self.nrow, rows)
    ncols = min(self.ncol, cols)
    start_idx = self.nrow - nrows if rows is not None else 0
    res = self[start_idx:self.nrow,:ncols].as_data_frame(use_pandas=use_pandas)
    if show:
      self._do_show(res,use_pandas)
    else:
      return res

  def levels(self, col=None):
    """
    Get the factor levels for this frame and the specified column index.

    :param col: A column index in this H2OFrame.
    :return: a list of strings that are the factor levels for the column.
    """
    if self.ncol==1 or col is None:
      lol=h2o.as_list(H2OFrame(expr=ExprNode("levels", self))._frame(), False)[1:]
      levels=[level for l in lol for level in l] if self.ncol==1 else lol
    elif col is not None:
      lol=h2o.as_list(H2OFrame(expr=ExprNode("levels", ExprNode("cols", self, col)))._frame(),False)[1:]
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

  def set_level(self, level):
    """
    A method to set all column values to one of the levels.

    :param level: The level at which the column will be set (a string)
    :return: An H2OFrame with all entries set to the desired level
    """
    return H2OFrame(expr=ExprNode("setLevel", self, level))

  def set_levels(self, levels):
    """
    Works on a single categorical vector. New domains must be aligned with the old domains.
    This call has copy-on-write semantics.


    :param levels: A list of strings specifying the new levels. The number of new levels must match the number of
    old levels.
    :return: A new column with the domains.
    """
    return H2OFrame(expr=ExprNode("setDomain",self,levels))

  def set_names(self,names):
    """
    Change the column names to `names`.

    :param names: A list of strings equal to the number of columns in the H2OFrame.
    :return: None. Rename the column names in this H2OFrame.
    """
    h2o.rapids(ExprNode._collapse_sb(ExprNode("colnames=", self, range(self.ncol), names)._eager()), id=self._id)
    self._update()
    return self

  def set_name(self,col=None,name=None):
    """
    Set the name of the column at the specified index.

    :param col: Index of the column whose name is to be set. Or current name of the column to change.
    :param name: The new name of the column to set
    :return: the input frame
    """
    if isinstance(col, basestring): col = self._find_idx(col)  # lookup the name!
    if not isinstance(col, int) and self.ncol > 1: raise ValueError("`col` must be an index. Got: " + str(col))
    if self.ncol == 1: col = 0
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

    table = [type,mins,maxs,mean,sigma,zeros,miss]
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
    :param na_rm: True or False to remove NAs from computation.
    :return: The product of the column.
    """
    return H2OFrame(expr=ExprNode("prod.na" if na_rm else "prod",self))._scalar()

  def any(self,na_rm=False):
    """
    :param na_rm: True or False to remove NAs from computation.
    :return: True if any element is True in the column.
    """
    return H2OFrame(expr=ExprNode("any.na" if na_rm else "any",self))._scalar()

  def all(self):
    """
    :return: True if every element is True in the column.
    """
    return H2OFrame(expr=ExprNode("all",self))._scalar()

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
    is_char = all([isinstance(i,basestring) for i in cols])
    if is_char:
      cols = [self._find_idx(col) for col in cols]
    cols = sorted(cols)
    return H2OFrame(expr=ExprNode("removeVecs",self,cols))._frame()

  def kfold_column(self, n_folds=3, seed=-1):
    """
    Build a fold assignments column for cross-validation. This call will produce a column
    having the same data layout as the calling object.

    :param n_folds: Number of folds.
    :param seed:Seed for random numbers (affects sampling when balance_classes=T)
    :return: A column of fold IDs.
    """
    return H2OFrame(expr=ExprNode("kfold_column",self,n_folds,seed))._frame()

  def modulo_kfold_column(self, n_folds=3):
    """
    Build a fold assignments column for cross-validation. Rows are assigned a fold according
    to the current row number modulo n_folds.

    Parameters
    ----------
      n_folds : int
        The number of folds to build.

    :return: An H2OFrame holding a single column of the fold assignments.
    """
    return H2OFrame(expr=ExprNode("modulo_kfold_column",self,n_folds))._frame()

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

    :return: An H2OFrame holding a single column of the fold assignments.
    """
    return H2OFrame(expr=ExprNode("stratified_kfold_column",self,n_folds,seed))._frame()


  def structure(self):
    """
    Similar to R's str method: Compactly Display the Structure of this H2OFrame instance.

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

    :param use_pandas: A flag specifying whether or not to return a pandas DataFrame.
    :return: A local python object (a list of lists of strings, each list is a row, if use_pandas=False, otherwise a
    pandas DataFrame) containing this H2OFrame instance's data.
    """
    self._eager()
    url = 'http://' + h2o.H2OConnection.ip() + ':' + str(h2o.H2OConnection.port()) + "/3/DownloadDataset?frame_id=" + urllib.quote(self._id) + "&hex_string=false"
    response = urllib2.urlopen(url)
    if h2o.can_use_pandas() and use_pandas:
      import pandas
      df = pandas.read_csv(response,low_memory=False)
      time_cols = []
      category_cols = []
      if self.types is not None:
        for col_name in self.names:
          type = self.types[col_name]
          if type.lower() == 'time': time_cols.append(col_name)
          elif type.lower() == 'enum': category_cols.append(col_name)
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

  # Find a named H2OVec and return the zero-based index for it.  Error if name is missing
  def _find_idx(self,name):
    self._eager()
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
    if isinstance(item, (int,basestring,list)): return H2OFrame(expr=ExprNode("cols",self,item))  # just columns
    elif isinstance(item, slice):
      item = slice(item.start,min(self.ncol,item.stop))
      return H2OFrame(expr=ExprNode("cols",self,item))
    elif isinstance(item, H2OFrame): return H2OFrame(expr=ExprNode("rows",self,item))  # just rows
    elif isinstance(item, tuple):
      rows = item[0]
      cols = item[1]
      allrows = False
      allcols = False
      if isinstance(cols, slice):  allcols = all([a is None for a in [cols.start,cols.step,cols.stop]])
      if isinstance(rows, slice):  allrows = all([a is None for a in [rows.start,rows.step,rows.stop]])

      if allrows and allcols: return self                              # fr[:,:]    -> all rows and columns.. return self
      if allrows: return H2OFrame(expr=ExprNode("cols",self,item[1]))  # fr[:,cols] -> really just a column slice
      if allcols: return H2OFrame(expr=ExprNode("rows",self,item[0]))  # fr[rows,:] -> really just a row slices

      res = H2OFrame(expr=ExprNode("rows", ExprNode("cols",self,item[1]),item[0]))
      return res.flatten() if isinstance(item[0], (basestring,int)) and isinstance(item[1],(basestring,int)) else res


  def __setitem__(self, b, c):
    """
    Replace a column in an H2OFrame.

    :param b: A 0-based index or a column name.
    :param c: The vector that 'b' is replaced with.
    :return: Returns this H2OFrame.
    """
    col_expr=None
    row_expr=None
    colname=None

    if isinstance(b, basestring):
      if b in self.col_names: col_expr = self.col_names.index(b)
      else:
        col_expr = self._ncols
        colname = b
    elif isinstance(b, int): col_expr = b
    elif isinstance(b, tuple):
      col_expr = b[1]
      row_expr = b[0]
      if isinstance(col_expr, basestring):
        if col_expr not in self.col_names:
          colname = col_expr
          col_expr = self._ncols
      elif isinstance(col_expr, slice):
        if col_expr.start is None and col_expr.stop is None:
          col_expr = slice(0,self.ncol)
      elif col_expr==-1: col_expr = slice(0,self.ncol)
    elif isinstance(b, H2OFrame): row_expr = b
    else: row_expr = slice(0,self.nrow)

    if row_expr is None: row_expr = slice(0,self.nrow)
    if col_expr is None: col_expr = slice(0,self.ncol)

    src = c._frame() if isinstance(c,H2OFrame) else float("nan") if c is None else c
    expr = ExprNode("=",self,src,col_expr,row_expr) if colname is None else ExprNode("=",self,src,col_expr,row_expr,colname)
    h2o.rapids(ExprNode._collapse_sb(expr._eager()), self._id)
    self._update()

  def __int__(self):   return int(self._scalar())

  def __float__(self): return self._scalar()

  def __del__(self):
    if not H2OFrame.COUNTING:
      H2OFrame.dropped_instances.append(self._id)
      return
    if not self._keep and self._computed: h2o.remove(self)

  @staticmethod
  def del_dropped():
    live_frames = list(H2OFrame.get_instance_ids())
    dead_frames = H2OFrame.dropped_instances
    for fr in dead_frames:
      if fr not in live_frames:
        h2o.remove(fr)
    H2OFrame.dropped_instances = []

  def keep(self):
    self._keep = True
    return self

  def drop(self, i):
    """
    Returns a Frame with the column at index i dropped.

    :param i: Column to drop
    :return: Returns an H2OFrame
    """
    if isinstance(i, basestring): i = self._find_idx(i)
    return H2OFrame(expr=ExprNode("cols", self,-(i+1)))._frame()

  def pop(self,i):
    """
    Pop a colunn out of an H2OFrame.

    :param i: The index or name of the column to pop.
    :return: The column dropped from the frame.
    """
    if isinstance(i, basestring): i=self._find_idx(i)
    col = H2OFrame(expr=ExprNode("pop",self,i))._frame()
    if self._keep: col.keep()
    self._update()
    return col

  def __len__(self):
    """
    :return: Number of rows
    """
    return self.nrow

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

  def split_frame(self, ratios=None, destination_frames=None, seed=None):
    """
    Split a frame into distinct subsets of size determined by the given ratios.
    The number of subsets is always 1 more than the number of ratios given.

    :param ratios: The fraction of rows for each split.
    :param destination_frames: names of the split frames
    :param seed: Random seed
    :return: a list of frames
    """

    if ratios is None:
      ratios = [0.75]

    if len(ratios) < 1:
      raise ValueError("Ratios must have length of at least 1")

    if destination_frames is not None:
      if (len(ratios)+1) != len(destination_frames):
        raise ValueError("The number of provided destination_frames must be one more than the number of provided ratios")

    num_slices = len(ratios) + 1
    boundaries = []

    last_boundary = 0
    i = 0
    while i < num_slices-1:
      ratio = ratios[i]
      if ratio < 0:
        raise ValueError("Ratio must be greater than 0")
      boundary = last_boundary + ratio
      if boundary >= 1.0:
        raise ValueError("Ratios must add up to less than 1.0")
      boundaries.append(boundary)
      last_boundary = boundary
      i += 1

    splits = []
    tmp_runif = self.runif(seed)

    i = 0
    while i < num_slices:
      if i == 0:
        # lower_boundary is 0.0
        upper_boundary = boundaries[i]
        tmp_slice = self[(tmp_runif <= upper_boundary), :]
      elif i == num_slices-1:
        lower_boundary = boundaries[i-1]
        # upper_boundary is 1.0
        tmp_slice = self[(tmp_runif > lower_boundary), :]
      else:
        lower_boundary = boundaries[i-1]
        upper_boundary = boundaries[i]
        tmp_slice = self[((tmp_runif > lower_boundary) & (tmp_runif <= upper_boundary)), :]

      if destination_frames is None:
        splits.append(tmp_slice)
      else:
        destination_frame_id = destination_frames[i]
        tmp_slice2 = h2o.assign(tmp_slice, destination_frame_id)
        splits.append(tmp_slice2)

      i += 1

    return splits

  # ddply in h2o
  def ddply(self,cols,fun):
    """
    :param cols: Column names used to control grouping
    :param fun: Function to execute on each group.  Right now limited to textual Rapids expression
    :return: New frame with 1 row per-group, of results from 'fun'
    """
    return H2OFrame(expr=ExprNode("ddply", self, cols, fun))._frame()

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
    Impute a column in this H2OFrame.

    :param column: The column to impute
    :param method: How to compute the imputation value.
    :param combine_method: For even samples and method="median", how to combine quantiles.
    :param by: Columns to group-by for computing imputation value per groups of columns.
    :param inplace: Impute inplace?
    :return: the imputed frame.
    """
    if isinstance(column, basestring): column = self._find_idx(column)
    if isinstance(by, basestring):     by     = self._find_idx(by)
    return H2OFrame(expr=ExprNode("h2o.impute", self, column, method, combine_method, by, inplace))._frame()

  def merge(self, other, allLeft=True, allRite=False):
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
    :param seed: A random number used to select which entries to replace with missing values. Default of seed = -1 will automatically generate a seed in H2O.
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
  def min(self):
    """
    :return: The minimum value of all frame entries
    """
    return H2OFrame(expr=ExprNode("min", self))._scalar()

  def max(self):
    """
    :return: The maximum value of all frame entries
    """
    return H2OFrame(expr=ExprNode("max", self))._scalar()

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
    return H2OFrame(expr=ExprNode("mean", self, na_rm))._scalar()

  def median(self, na_rm=False):
    """
    :return: Median of this column.
    """
    return H2OFrame(expr=ExprNode("median", self, na_rm))._scalar()

  def var(self,y=None,use="everything"):
    """
    :param use: One of "everything", "complete.obs", or "all.obs".
    :return: The covariance matrix of the columns in this H2OFrame.
    """
    return H2OFrame(expr=ExprNode("var",self,self if y is None else y,use))._get()

  def sd(self):
    """
    :param na_rm: True or False to remove NAs from computation.
    :return: Standard deviation of the H2OVec elements.
    """
    return H2OFrame(expr=ExprNode("sd", self))._scalar()

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

    Parameters
    ----------
      pattern : str
        The split pattern.

    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("strsplit", self, pattern))

  def countmatches(self, pattern):
    """
    Split the strings in the target column on the given pattern

    Parameters
    ----------
      pattern : str
        The pattern to count matches on in each string.

    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("countmatches", self, pattern))

  def trim(self):
    """
    Trim the edge-spaces in a column of strings (only operates on frame with one column)

    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("trim", self))

  def length(self):
    """
    Create a column containing the length of the strings in the target column (only operates on frame with one column)

    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("length", self))


  def table(self, data2=None):
    """
    Parameters
    ----------
      data2 : H2OFrame
        Default is None, can be an optional single column to aggregate counts by.

    :return: An H2OFrame of the counts at each combination of factor levels
    """
    return H2OFrame(expr=ExprNode("table",self,data2) if data2 is not None else ExprNode("table",self))

  def hist(self, breaks="Sturges", plot=True, **kwargs):
    """
    Compute a histogram over a numeric column. If breaks=="FD", the MAD is used over the IQR in computing bin width.

    :param breaks: breaks Can be one of the following: A string: "Sturges", "Rice", "sqrt", "Doane", "FD", "Scott." A single number for the number of breaks splitting the range of the vec into number of breaks bins of equal width. Or, A vector of numbers giving the split points, e.g., c(-50,213.2123,9324834)
    :param plot: A logical value indicating whether or not a plot should be generated (default is TRUE).
    :return: if plot is True, then return None, else, an H2OFrame with these columns: breaks, counts, mids_true, mids, and density
    """
    frame = H2OFrame(expr=ExprNode("hist", self, breaks))._frame()
    total = frame["counts"].sum(True)
    densities = [(frame[i,"counts"]/total)*(1/(frame[i,"breaks"]-frame[i-1,"breaks"])) for i in range(1,frame["counts"].nrow)]
    densities.insert(0,0)
    densities_frame = H2OFrame(python_obj=[[d] for d in densities])
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
    return H2OFrame(expr=ExprNode("replacefirst", self, pattern, replacement, ignore_case))

  def gsub(self, pattern, replacement, ignore_case=False):
    """
    gsub performs replacement of all matches respectively.

    :param pattern:
    :param replacement:
    :param ignore_case:
    :return: H2OFrame
    """
    return H2OFrame(expr=ExprNode("replaceall", self, pattern, replacement, ignore_case))

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
    return H2OFrame(expr=ExprNode("toupper", self))

  def tolower(self):
    """
    Translate characters from upper to lower case for a particular column
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
    :param digits:
    :return: The rounded values in the H2OFrame to the specified number of significant digits.
    """
    return H2OFrame(expr=ExprNode("signif", self, digits))

  def round(self, digits=0):
    """
    :param digits:
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
    return H2OFrame(expr=ExprNode('h2o.random_stratified_split', self, test_frac, seed))._frame()

  def match(self, table, nomatch=0):
    """
    Makes a vector of the positions of (first) matches of its first argument in its second.

    :param table:
    :param nomatch:

    :return: H2OFrame of one boolean column
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

  def apply(self, fun=None, axis=0):
    """
    Apply a lambda expression to an H2OFrame.

    :param fun: A lambda expression to be applied per row or per column
    :param axis: 0: apply to each column; 1: apply to each row
    :return: An H2OFrame
    """
    if axis not in [0,1]:
      raise ValueError("margin must be either 0 (cols) or 1 (rows).")
    if fun is None:
      raise ValueError("No function to apply.")
    if isinstance(fun, type(lambda:0)) and fun.__name__ == (lambda:0).__name__:  # have lambda
      res = _bytecode_decompile_lambda(fun.func_code)
      return H2OFrame(expr=ExprNode("apply",self, 1+(axis==0),*res))
    else:
      raise ValueError("unimpl: not a lambda")


  # flow-coding result methods
  def _scalar(self):
    self._eager()  # scalar should be stashed into self._data
    if self._data is None:
      res = self.as_data_frame(use_pandas=False)[1:]
      if len(res)==1: return H2OFrame._get_scalar(res[0][0])
      else:
        return [H2OFrame._get_scalar(r[0]) for r in res]
    else:
      return H2OFrame._get_scalar(self._data)

  @staticmethod
  def _get_scalar(res):
    if res == '' or res=="NaN": return float("nan")
    if res == "TRUE": return True
    if res == "FALSE":return False
    try:    return float(res)
    except: return res

  def _get(self):
    self._eager()
    if self._data is None:
      return self._frame()
    return self._scalar()

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
        res = h2o.rapids(ExprNode._collapse_sb(sb), self._id)
        if "Scalar" in res["__meta"]["schema_name"] or "String" in res["__meta"]["schema_name"]:  # scalar return type, inline scalar (self._id is invalid! => so don't update it)
          sb = [str(res["scalar"])," "]
          self._data = res["scalar"]
        else:
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
    if self._computed:
      if self.dim == [1,1]:
        sb += [str(self._scalar()), " "]   # inline 1x1 H2OFrame here
      else:                 sb += [self._id+" "]
    else:              sb += self._eager(True) if (len(gc.get_referrers(self)) >= H2OFrame.MAGIC_REF_COUNT) else self._eager(False)

  def _update(self):
    res = h2o.frame(self._id)["frames"][0]  # TODO: exclude here?
    self._nrows = res["rows"]
    self._ncols = len(res["columns"])
    self._col_names = [c["label"] for c in res["columns"]]
    self._types = dict(zip(self._col_names, [c["type"] for c in res["columns"]]))
    self._computed=True
    self._ast=None

  def _update_post_parse(self, parse):
    self._id = parse["destination_frame"]["name"]
    self._computed=True
    self._nrows = int(H2OFrame(expr=ExprNode("nrow", self))._scalar())
    self._ncols = parse["number_columns"]
    self._col_names = parse['column_names'] if parse["column_names"] else ["C" + str(x) for x in range(1,self._ncols+1)]
    self._types = dict(zip(self._col_names,parse["column_types"]))
    self._keep = True

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
