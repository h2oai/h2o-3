Data
----

**How should I format my SVMLight data before importing?**

The data must be formatted as a sorted list of unique integers, the column indices must be >= 1, and the columns must be in ascending order.

--------------

**Does H2O provide native sparse support?**

Sparse data is supported natively by loading a sparse matrix from an SVMLight file. In addition, H2O includes a direct conversion of a sparse matrix to an H2O Frame in Python via the ``h2o.H2OFrame()`` method and in R via the ``as.h2o()`` function. For sparse data, H2O writes a sparse matrix to SVMLight format and then loads it back in using ``h2o.import_file`` in Python or ``h2o.importFile`` with ``parse_type=SVMLight`` in R.

In R, a sparse matrix is specified using ``Matrix::sparseMatrix`` along with the boolean flag, ``sparse=TRUE``. For example:

  ::

    data <- rep(0, 100)
    data[(1:10)^2] <- 1:10 * pi
    m <- matrix(data, ncol = 20, byrow = TRUE)
    m <- Matrix::Matrix(m, sparse = TRUE)
    h2o.matrix <- as.h2o(m, "sparse_matrix")

In Python, a sparse matrix is specified using ``scipy.parse``. For example:

  ::

    import scipy.sparse as sp
    A = sp.csr_matrix([[1, 2, 0, 5.5], [0, 0, 3, 6.7], [4, 0, 5, 0]])
    fr = h2o.H2OFrame(A)
    A = sp.lil_matrix((1000, 1000))
    A.setdiag(10)
    for i in range(999):
        A[i, i + 1] = -3
        A[i + 1, i] = -2
    fr = h2o.H2OFrame(A)

--------------

**What date and time formats does H2O support?**

H2O is set to auto-detect two major date/time formats. Because many date
time formats are ambiguous (e.g. 01/02/03), general date time detection
is not used.

The first format is for dates formatted as yyyy-MM-dd. Year is a
four-digit number, the month is a two-digit number ranging from 1 to 12,
and the day is a two-digit value ranging from 1 to 31. This format can
also be followed by a space and then a time (specified below).

The second date format is for dates formatted as dd-MMM-yy. Here the day
must be one or two digits with a value ranging from 1 to 31. The month
must be either a three-letter abbreviation or the full month name but is
not case sensitive. The year must be either two or four digits. In
agreement with `POSIX <https://en.wikipedia.org/wiki/POSIX>`__
standards, two-digit dates >= 69 are assumed to be in the 20th century
(e.g. 1969) and the rest are part of the 21st century. This date format
can be followed by either a space or colon character and then a time.
The '-' between the values is optional.

Times are specified as HH:mm:ss. HH is a two-digit hour and must be a
value between 0-23 (for 24-hour time) or 1-12 (for a twelve-hour clock).
mm is a two-digit minute value and must be a value between 0-59. ss is a
two-digit second value and must be a value between 0-59. This format can
be followed with either milliseconds, nanoseconds, and/or the cycle
(i.e. AM/PM). If milliseconds are included, the format is HH:mm:ss:SSS.
If nanoseconds are included, the format is HH:mm:ss:SSSnnnnnn. H2O only
stores fractions of a second up to the millisecond, so accuracy may be
lost. Nanosecond parsing is only included for convenience. Finally, a
valid time can end with a space character and then either "AM" or "PM".
For this format, the hours must range from 1 to 12. Within the time, the
':' character can be replaced with a '.' character.

--------------

**How does H2O handle name collisions/conflicts in the dataset?**

If there is a name conflict (for example, column 48 isn't named, but C48
already exists), then the column name in concatenated to itself until a
unique name is created. So for the previously cited example, H2O will
try renaming the column to C48C48, then C48C48C48, and so on until an
unused name is generated.

--------------

**What types of data columns does H2O support?**

Currently, H2O supports:

-  float (any IEEE double)
-  integer (up to 64bit, but compressed according to actual range)
-  factor (same as integer, but with a String mapping, often handled
   differently in the algorithms)
-  time (same as 64bit integer, but with a time-since-Unix-epoch
   interpretation)
-  UUID (128bit integer, no math allowed)
-  String

--------------

**I am trying to parse a Gzip data file containing multiple files, but
it does not parse as quickly as the uncompressed files. Why is this?**

Parsing Gzip files is not done in parallel, so it is sequential and uses
only one core. Other parallel parse compression schemes are on the
roadmap.
