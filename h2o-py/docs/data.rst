:tocdepth: 2


Data In H2O
===========

An H2OFrame represents a 2D array of data where each column is uniformly typed.

The data may be local or it may be in an H2O cluster. The data are loaded from a CSV file
or from a native Python data structure, and is either a Python client-relative file, a
cluster-relative file, or a list of H2OVec objects.

Loading Data From A CSV File
----------------------------

Load data using either :mod:`h2o.import_file` or :mod:`h2o.upload_file`.

:mod:`h2o.import_file` uses cluster-relative names and ingests data in parallel.

:mod:`h2o.upload_file` uses Python client-relative names and single-threaded file upload from the client.

H2O's parser supports data of various formats from multiple sources.
The following formats are supported:

* ARFF
* CSV (data may delimited by any of the 128 ASCII characters; includes support for GZipped CSV)
* SVMLight
* XLS
* XLSX
* ORC (for Hadoop jobs; includes support for Hive files saved in ORC format)
* Avro version 1.8.0 (without multifile parsing or column type modification)
* Parquet


The following data sources are supported:

* NFS / Local File / List of Files
* HDFS
* URL
* A Directory (with many data files inside at the *same* level -- no support for recursive import of data)
* S3/S3N
* Native Language Data Structure (c.f. the subsequent section)
* Google Storage (gs://)

.. code-block:: python

    >>> trainFrame = h2o.import_file(path="hdfs://192.168.1.10/user/data/data_test.csv")
    #or
    >>> trainFrame = h2o.import_file(path="~/data/data_test.csv")

**Note**: When parsing a data file containing timestamps that do not include a timezone, the timestamps will be interpreted as UTC (GMT). You can override the parsing timezone using ``h2o.cluster().timezone``. For example:

.. code-block:: python

    h2o.cluster().timezone = "America/Los Angeles"

Loading Data From A Python Object
---------------------------------

To transfer the data that are stored in python data structures to H2O, use the H2OFrame
constructor and the :mod:`python_obj` argument. Additionally, :mod:`from_python` performs
the same function but provides a few more options for how H2O will parse the data.

The following types are permissible for `python_obj`:

* :class:`tuple` ()
* :class:`list`  []
* :class:`dict`  {}
* :mod:`collections.OrderedDict`
* :mod:`numpy.ndarray`
* :mod:`pandas.DataFrame`

The type of `python_obj` is inspected by performing an `isinstance` call. A ValueError
will be raised if the type of `python_obj` is not one of the above types. For example,
sets, byte arrays, and un-contained types are not permissible.

The subsequent sections discuss each data type in detail in terms of the "source"
representation (the python object) and the "target" representation (the H2O object).
Concretely, the topics of discussion will be on the following: Headers, Data Types,
Number of Rows, Number of Columns, and Missing Values.

In the following documentation, H2OFrame and Frame will be used synonymously. Technically,
an H2OFrame is the object-pointer that resides in the python VM and points to a Frame
object inside of the H2O JVM. Similarly, H2OFrame, Frame, and H2O Frame  all
refer to the same kind of object. In general, though, the context is from the
python VM, unless otherwise specified.

Loading A Python Tuple
++++++++++++++++++++++

Essentially, the tuple is an immutable list. This immutability does not map to
the H2OFrame. So Pythonistas beware!

The restrictions on what goes inside the tuple are fairly relaxed, but if they
are not recognized, a ValueError is raised.

A tuple is formatted as follows:

   (i1, i2, i3, ..., iN)

Restrictions are mainly on the types of the individual `iJ` (1 <= J <= N). Here `N` is the
number of rows in the column represented by this tuple.

If `iJ` is {} for some J, then a ValueError is raised. If `iJ` is a () (tuple) or []
(list), then `iJ` must be a () or [] for all J; otherwise a ValueError is raised. In other
words, any mixing of types will result in a

Additionally, only a single layer of nesting is allowed: if `iJ` is a () or [], and if it
contains any () or [], then a ValueError is raised.

If `iJ` is not a () or [], then it must be of type string or a non-complex
numeric type (float or int). In other words, if `iJ` is not a tuple, list,
string, float, or int, for some J, then a ValueError is raised.

Some examples of acceptable inputs are:
 * Example A: (1,2,3)
 * Example B: ((1,2,3), (4,5,6), ("cat", "dog"))
 * Example C: ((1,2,3), [4,5,6], ["blue", "yellow"], (321.239, "green","hi"))
 * Example D: (3284.123891, "dog", 89)

Note that it is perfectly fine to mix () and [] within a tuple.

Headers, Columns, Rows, Data Types, and Missing Values:

The format of the H2OFrame is as follows:

        +--------+--------+--------+-----+---------+
        | column1| column2| column3| ... | columnN |
        +========+========+========+=====+=========+
        |  a11,  |  a12,  | a13,   | ...,| a1N     |
        +--------+--------+--------+-----+---------+
        |  .,    |   .,   |   .,   | ...,| .       |
        +--------+--------+--------+-----+---------+
        |  .,    |   .,   |   .,   | ...,| .       |
        +--------+--------+--------+-----+---------+
        |  .,    |   .,   |   .,   | ...,| .       |
        +--------+--------+--------+-----+---------+
        |  aM1,  |  aM2,  |   aM3, | ...,| aMN     |
        +--------+--------+--------+-----+---------+

It looks exactly like an MxN matrix with an additional header "row". This
header cannot be specified when loading data from a () (or from a []
but it is possible to specify a header with a python dictionary (see below
for details).

**Headers:**

Since no header row can be specified for this case, H2O automatically generates a
column header in the following format:

 C1, C2, C3, ..., CN

Notably, these columns have a 1-based indexing (i.e. the 0th column is "C1").

**Rows, Columns, and Missing Data:**

The shape of the H2OFrame is determined by two factors:

- the number of arrays nested in the ()
- the number of items in each array

If there are no nested arrays (as in Example A and Example D above),
the resulting H2OFrame will have the following shape (rows x cols):

  len(tuple) x 1

(i.e. a Frame with a single column).

If there are nested arrays (as in Example B and Example C above), then
the resulting H2OFrame will have COLUMNS equal to the number of arrays nested within and
ROWS equal to the maximum sub-array:

    len(tuple) x max( [len(l) for l in tuple] )

Note that this addresses the issue with ragged sub-arrays by assuming that
shorter sub-arrays will pad themselves with NA (missing values) at the end
so that they become the correct length.

Because the Frame is uniformly typed, combining data types
within a column may produce unexpected results. Please read up on the H2O
parser for details on how a column type is determined for mixed-type columns. Also, as
stated above, you may use the :mod:`from_python` method to provide a set of column types.

Loading A Python List
+++++++++++++++++++++

The same principles that apply to tuples also apply to lists. Lists are mutable
objects, so there is no semantic difference regarding mutability between an
H2OFrame and a list (as there is for a tuple).

Additionally, a list [] is ordered the same way as a tuple (), with the data appearing
within the brackets.

Loading A Python Dictionary Or collections.OrderedDict
++++++++++++++++++++++++++++++++++++++++++++++++++++++

Each entry in the {} is expected to represent a single column. Keys in the {}
must be character strings following the pattern: ^[\a-\z\A-\Z_][\a-z\A-\Z\0-\9_.]*$
without restriction on length. A valid column name may begin with any
letter (capital or not) or an "_", followed by any number of
letters, digits, "_"s, or "."s.

Values in the {} may be a flat [], a flat (), or a single int, float, or
string value. Nested [] and () will raise a ValueError. This is the only
additional restriction on [] and () that applies in this context.

Note that the built-in dict does not provide any guarantees on ordering. This
has implications on the order of columns in the eventual H2OFrame, since they
may be written out of order from which they were initially put into the dict.

collections.OrderedDict preserves the order of the key-value pairs in which they were
entered.

Loading A numpy.ndarray Or A pandas.DataFrame
+++++++++++++++++++++++++++++++++++++++++++++
One or two dimensional :mod:`numpy.ndarray` objects can be converted to H2OFrames.
The implementation simply calls the `tolist()` method on the ndarray object. The same
principles that apply to lists are then applied to the result of the `tolist()` operation.

:mod:`pandas.DataFrame` objects can also be converted to H2OFrames. The implementation
simply calls the `values` method on the DataFrame object. The `values` method
returns an ndarray object, and the above-described ndarray transformation is then invoked,
so the rules for Python lists also apply here.

Setting S3 Credentials
-------------------------------
.. automodule:: h2o.persist.persist
   :members:


