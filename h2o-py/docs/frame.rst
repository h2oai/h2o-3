H2OFrame
========

A H2OFrame represents a 2D array of data where each column is uniformly typed.

The data may be local, or it may be in an H2O cluster. The data are loaded from a CSV
file or the data are loaded from a native python data structure, and is either a
python-process-local file or a cluster-local file, or a list of H2OVec objects.

Loading Data From A CSV File
----------------------------

  H2O's parser supports data of various formats coming from various sources.
  Briefly, these formats are:

     * SVMLight
     * CSV (data may delimited by any of the 128 ASCII characters)
     * XLS

  Data sources may be:
     * NFS / Local File / List of Files
     * HDFS
     * URL
     * A Directory (with many data files inside at the *same* level -- no support for
                   recursive import of data)
     * S3/S3N
     * Native Language Data Structure (c.f. the subsequent section)
  .. code-block:: python

    trainFrame = h2o.import_frame(path="hdfs://192.168.1.10/user/data/data_test.csv")
    #or
    trainFrame = h2o.import_frame(path="~/data/data_test.csv")

Loading Data From A Python Object
---------------------------------

  It is possible to transfer the data that are stored in python data structures to
  H2O by using the H2OFrame constructor and the `python_obj` argument. (Note that if
  the `python_obj` argument is not `None`, then additional arguments are ignored).

  The following types are permissible for `python_obj`:

    * :class:`tuple` ()
    * :class:`list`  []
    * :class:`dict`  {}
    * :mod:`collections.OrderedDict`

  The type of `python_obj` is inspected by performing an `isinstance` call. A
  ValueError will be raised if the type of `python_obj` is not one of the above
  types. Notably, sets, byte arrays, and un-contained types are not permissible.

  In the subsequent sections, each data type will be discussed in detail. Each
  discussion will be couched in terms of the "source" representation (the python
  object) and the "target" representation (the H2O object). Concretely, the topics
  of discussion will be on the following: Headers, Data Types, Number of Rows,
  Number of Columns, and Missing Values.

  Aside: Why is Pandas' DataFrame not a permissible type?

      There are two reason that Pandas' DataFrame objects are not included.
      First, it is desirable to keep the number of dependencies to a minimum, and it
      is difficult to justify the inclusion of the Pandas module as a dependency if
      its raison d'être is tied to this small detail of transferring data from
      python to H2O.

      Second, Pandas objects are simple wrappers of numpy arrays together with some
      meta data; therefore if one was adequately motivated, then the transfer of
      data from a Pandas DataFrame to an H2O Frame could readily be achieved.


  In what follows, H2OFrame and Frame will be used synonymously. Technically, an
  H2OFrame is the object-pointer that resides in the python VM and points to a Frame
  object inside of the H2O JVM. Similarly, H2OFrame, Frame, and H2O Frame will all
  refer to the same kind of object. In general, though, the context is from the
  python VM, unless otherwise specified.

Loading A Python Tuple
++++++++++++++++++++++

      Essentially, the tuple is an immutable list. This immutability does not map to
      the H2OFrame. So pythonistas be ware!

      The restrictions on what goes inside the tuple are fairly relaxed, but if they
      are too unusual, a ValueError will be raised.

      A tuple looks as follows:

          (i1, i2, i3, ..., iN)

      Restrictions are really on the types of the individual `iJ` (1 <= J <= N).

      If `iJ` is {} for some J, then a ValueError will be raised.

      If `iJ` is a () (tuple) or [] (list), then `iJ` must be a () or [] for all J;
      otherwise a ValueError will be raised.

      If `iJ` is a () or [], and if it is in fact a nested () or nested [], then a
      ValueError will be raised. In other words, only a single level of nesting is
      valid, all internal arrays must be flat -- H2O will not flatten them for you.

      If `iJ` is not a () or [], then it must be of type string or a non-complex
      numeric type (float or int). In other words, if `iJ` is not a tuple, list,
      string, float, or int, for some J, then a ValueError will be raised.

      Some acceptable inputs are:
          * Example A: (1,2,3)
          * Example B: ((1,2,3), (4,5,6), ("cat", "dog"))
          * Example C: ((1,2,3), [4,5,6], ["blue", "yellow"], (321.239, "green","hi"))
          * Example D: (3284.123891, "dog", 89)

      Note that it is perfectly fine to mix () and [] within a tuple.

      Onward.

      Headers, Columns, Rows, Data Types, and Missing Values:

      The form of the H2OFrame is as follows:

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
      but it is possible to specify a header with a python dictionary, see below
      for details).

      Headers:

          Since no header row can be specified for this case, H2O will generate a
          column header on your behalf and the column header will look like this:

              C1, C2, C3, ..., CN

          Notably, these columns have a 1-based indexing (i.e. the 0th column is
          "C1").

      Rows and Columns and Missing Data:

          The shape of the H2OFrame is determined by the two factors:
              the number of arrays nested in the ()
              the number of items in each array

          If there are no nested arrays (as in Example A and Example D above), then
          the resulting H2OFrame will have shape (rows x cols):

              1 x len(tuple)

          (i.e. a Frame with a single row).

          If there are nested arrays (as in Example B and Example C above), then
          (given the rules stated above) the resulting H2OFrame will have ROWS equal
          to the number of arrays nested within and COLUMNS equal to the maximum sub
          array:

              max( [len(l) for l in tuple] ) x len(tuple)

          Note that this handles the issue with ragged sub arrays by assuming that
          shorter sub arrays will pad themselves with NA (missing values) at the end
          so that they become the correct length.

          Because the Frame is uniformly typed, mixing and matching data types
          within a column may produce unexpected results. Please read up on the H2O
          parser for details on how a column type is determined for a column of
          initially mixed type.

Loading A Python List
+++++++++++++++++++++

      The same discussion applies for lists as it does for tuples. Lists are mutable
      objects so there is no semantic difference regarding mutability between an
      H2OFrame and a list (as there is for a tuple).

      Additionally, a list [] is ordered (as is a tuple ()) and the data appearing
      within

Loading A Python Dictionary Or collections.OrderedDict
++++++++++++++++++++++++++++++++++++++++++++++++++++++

      Each entry in the {} is expected to represent a single column. Keys in the {}
      must be character strings following the pattern: ^[\a-\z\A-\Z_][\a-z\A-\Z\0-\9_.]*$
      without restriction on length. That is a valid column name may begin with any
      letter (capital or not) or an "_", it can then be followed by any number of
      letters, digits, "_"s, or "."s.

      Values in the {} may be a flat [], a flat (), or a single int, float, or
      string value. Nested [] and () will raise a ValueError. This is the only
      additional restriction on [] and () that applies in this context.

      Note that the built-in dict does not provide any guarantees on ordering. This
      has implications on the order of columns in the eventual H2OFrame, since they
      may be written out of order from which they were initially put into the dict.

      collections.OrderedDict will preserve the order of the key-value pairs in
      which they were entered.

:mod:`frame` Module
===================

H2OFrame Class
--------------

.. autoclass:: h2o.frame.H2OFrame
    :members:
    :undoc-members:
    :show-inheritance:

H2OVec Class
------------

.. autoclass:: h2o.frame.H2OVec
    :members:
    :undoc-members:
    :show-inheritance: