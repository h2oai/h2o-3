# Data: Parse

Once data are ingested, they are available to H2O, but are
not yet in a format that H2O can process. Converting the data to
an H2O usable format is called parsing.

## Parser Behavior

The data type in each column must be consistent. For example, when
data are alpha-coded categorical, all entries must be alpha or
alpha numeric. If numeric entries are detected by the parser, the
column will not be processed. It will register all entries as
NA. This is also true when NA entries are included in columns
consisting of numeric data. Columns of alpha coded categorical
variables containing NA entries will register NA as a distinct
factor level. When missing data are coded as periods or dots in the
original data set those entries are converted to zero.

*In general options can be left in default and the parser just works.*

**Parser Type**
Drop down menu allows users to specify whether data are formatted as
CSV, XLS, or SVMlight. This option is best left in default - the
parser recognizes data formats with rare exception.

**Separator**
A list of common separators is given, however, this option is best
left in default.

**Header**
Checkbox to be checked if the first line of the file being parsed is
a header (includes column names or indices).

**Header From File**
Specify a file key if the header for the data to be parsed is found
in another file that has already been imported to H2O.

**Exclude**
A comma separated list of columns to be omitted from parse.

**Source Key**
The file key associated with the imported data to be parsed.

**Destination Key**
An optional user specified name for the parsed data to be referenced
later in modeling. If left in default a destination key will
automatically be assigned to be "original file name.hex".

**Preview**
Auto-generated preview of parsed data.

**Delete on done**
A checkbox indicating whether imported data should be deleted when
parsed. In general, this option is recommended, as retaining data will take
memory resources, but not aid in modeling because unparsed data
can't be acted on by H2O.
