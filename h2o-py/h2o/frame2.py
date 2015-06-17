# -*- coding: utf-8 -*-
# import numpy    no numpy cuz windoz
import collections, csv, itertools, os, re, tempfile, uuid, copy, urllib,sys,urllib2
import h2o
from connection import H2OConnection
from ast import *

class H2OFrame(H2OObj):

  def __init__(self, python_obj=None, file_path=None, raw_id=None):
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
    self.remote_fname = file_path
    self._id = ""
    self._rows=None
    self._cols=None

    if python_obj is not None: self._upload_python_object(python_obj)
    elif file_path is not None: self._import_parse()
    elif raw_id: self._handle_text_key(raw_id, None)
    else: raise ValueError("H2OFrame instances require a python object, a file path, or a raw import file identifier.")

  def _import_parse(self):
    rawkey = h2o.import_file(self.remote_fname)
    setup = h2o.parse_setup(rawkey)
    parse = h2o.parse(setup, H2OFrame.py_tmp_key())  # create a new key
    self._id = parse["job"]["dest"]["name"]
    rows = self._rows = parse['rows']
    cols = self._cols = parse['column_names']
    thousands_sep = h2o.H2ODisplay.THOUSANDS
    if isinstance(self.remote_fname, str):
      print "Imported {}. Parsed {} rows and {} cols".format(self.remote_fname,thousands_sep.format(rows), thousands_sep.format(len(cols)))
    else:
      h2o.H2ODisplay([["File"+str(i+1),f] for i,f in enumerate(self.remote_fname)],None, "Parsed {} rows and {} cols".format(thousands_sep.format(rows), thousands_sep.format(len(cols))))

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
    csv_writer.writeheader()            # write the header
    csv_writer.writerows(data_to_write) # write the data
    tmp_file.close()                    # close the streams
    self._upload_raw_data(tmp_path, header) # actually upload the data to H2O
    os.remove(tmp_path)                     # delete the tmp file

  def _handle_text_key(self, text_key, column_names):
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
    self._cols = cols = parse['column_names'] if parse["column_names"] else ["C" + str(x) for x in range(1,len(parse['vec_ids'])+1)]
    # set the rows
    self._rows = rows = parse['rows']
    thousands_sep = h2o.H2ODisplay.THOUSANDS
    print "Uploaded {} into cluster with {} rows and {} cols".format(text_key, thousands_sep.format(rows), thousands_sep.format(len(cols)))

  def _upload_raw_data(self, tmp_file_path, column_names):
    # file upload info is the normalized path to a local file
    fui = {"file": os.path.abspath(tmp_file_path)}
    # create a random name for the data
    dest_key = H2OFrame.py_tmp_key()
    # do the POST -- blocking, and "fast" (does not real data upload)
    H2OConnection.post_json("PostFile", fui, destination_frame=dest_key)
    # actually parse the data and setup self._vecs
    self._handle_text_key(dest_key, column_names)

  def __iter__(self):
    """
    Allows for list comprehensions over an H2OFrame

    :return: An iterator over the H2OFrame
    """
    raise ValueError("iterable: unimpl")

  def col_names(self):
    """
    Retrieve the column names (one name per H2OVec) for this H2OFrame.

    :return: A character list[] of column names.
    """
    return self._cols

  def names(self):
    """
    Retrieve the column names (one name per H2OVec) for this H2OFrame.

    :return: A character list[] of column names.
    """
    return self.col_names()

  def nrow(self):
    """
    Get the number of rows in this H2OFrame.

    :return: The number of rows in this dataset.
    """
    return self._rows

  def ncol(self):
    """
    Get the number of columns in this H2OFrame.

    :return: The number of columns in this H2OFrame.
    """
    return len(self._cols)

  def filterNACols(self, frac=0.2):
    """
    Filter columns with prportion of NAs >= frac.
    :param frac: Fraction of NAs in the column.
    :return: A  list of column indices.
    """
    return ExprNode("filterNACols", self, frac)  # eager me return [ list of columns ]

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
    nrows = min(self.nrow(), rows)
    ncols = min(self.ncol(), cols)
    colnames = self.names()[0:ncols]
    head = self[0:10,:]
    res = head.as_data_frame(False)
    print "First {} rows and first {} columns: ".format(nrows, ncols)
    h2o.H2ODisplay(res,["Row ID"]+colnames)

  def tail(self, rows=10, cols=200, **kwargs):
    """
    Analgous to R's `tail` call on a data.frame. Display a digestible chunk of the H2OFrame starting from the end.

    :param rows: Number of rows to display.
    :param cols: Number of columns to display.
    :param kwargs: Extra arguments passed from other methods.
    :return: None
    """
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
    raise NotImplementedError
    # if self._vecs is None or self._vecs == []:
    #   raise ValueError("Frame Removed")
    # thousands_sep = h2o.H2ODisplay.THOUSANDS
    # print "Rows:", thousands_sep.format(len(self._vecs[0])), "Cols:", thousands_sep.format(len(self))
    # headers = [vec._name for vec in self._vecs]
    # table = [
    #   self._row('type', None),
    #   self._row('mins', 0),
    #   self._row('mean', None),
    #   self._row('maxs', 0),
    #   self._row('sigma', None),
    #   self._row('zero_count', None),
    #   self._row('missing_count', None)
    # ]
    # chunk_summary_tmp_key = H2OFrame.send_frame(self)
    # chunk_dist_sum = h2o.frame(chunk_summary_tmp_key)["frames"][0]
    # dist_summary = chunk_dist_sum["distribution_summary"]
    # chunk_summary = chunk_dist_sum["chunk_summary"]
    # h2o.removeFrameShallow(chunk_summary_tmp_key)
    # chunk_summary.show()
    # dist_summary.show()
    # h2o.H2ODisplay(table, [""] + headers, "Column-by-Column Summary")

  # def __repr__(self):
  #   if self._vecs is None or self._vecs == []:
  #     raise ValueError("Frame Removed")
  #   self.show()
  #   return ""

  def as_data_frame(self, use_pandas=True):
    url = 'http://' + H2OConnection.ip() + ':' + str(H2OConnection.port()) + "/3/DownloadDataset?frame_id=" + urllib.quote(id) + "&hex_string=false"
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
    for i,v in enumerate(self._cols):
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
    if isinstance(item, (int,str,list)): return ExprNode("cols", self, item)  # just columns
    elif isinstance(item, tuple):
      rows = item[0]
      if isinstance(rows, slice):
        if all([a is None for a in [rows.start,rows.step,rows.stop]]): return ExprNode("cols",self, item[1])  # fr[:,cols] -> reallyjust a column slice
      return ExprNode("rows", ExprNode("cols", self, item[1]), item[0])

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
    return ExprNode("cbind", self, data)

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

  # generic reducers (min, max, sum, var)
  def min(self):
    """
    :return: The minimum value of all frame entries
    """
    return ExprNode("min", self)

  def max(self):
    """
    :return: The maximum value of all frame entries
    """
    return ExprNode("max", self)

  def sum(self):
    """
    :return: The sum of all frame entries
    """
    return ExprNode("sum", self)

  def var(self,na_rm=False,use="everything"):
    """
    :param na_rm: True or False to remove NAs from computation.
    :param use: One of "everything", "complete.obs", or "all.obs".
    :return: The covariance matrix of the columns in this H2OFrame.
    """
    return ExprNode("var", self,na_rm,use)


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


def _is_list_of_lists(o): return any(isinstance(l, (list, tuple)) for l in o)


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
