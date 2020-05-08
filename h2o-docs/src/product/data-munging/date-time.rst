Working with Date/Time Columns
------------------------------

H2O is set to auto-detect two major date/time formats. Because many date time formats are ambiguous (e.g. 01/02/03), general date time detection is not used.

Date Formats
~~~~~~~~~~~~

The first format is for dates formatted as ``yyyy-MM-dd``. Year is a four-digit number, the month is a two-digit number ranging from 01 to 12, and the day is a two-digit value ranging from 01 to 31. This format can also be followed by a space and then a time (specified below).

The second date format is for dates formatted as ``dd-MMM-yy``. Here the day must be one or two digits with a value ranging from 1 to 31. The month must be either a three-letter abbreviation or the full month name, but is not case sensitive. The year must be either two or four digits. In agreement with `POSIX <https://en.wikipedia.org/wiki/POSIX>`__ standards, two-digit dates >= 69 are assumed to be in the 20th century (e.g. 1969) and the rest are part of the 21st century. This date format can be followed by either a space or colon character and then a time. The ‘-‘ between the values is optional.

Time Formats
~~~~~~~~~~~~

Times are specified as ``HH:mm:ss``. ``HH`` is a two-digit hour and must be a value between 00-23 (for 24-hour time) or 01-12 (for a twelve-hour clock). ``mm`` is a two-digit minute value and must be a value between 00-59. ``ss`` is a two-digit second value and must be a value between 00-59. This format can be followed with either milliseconds, nanoseconds, and/or the cycle (i.e. AM/PM). 

If milliseconds are included, the format is ``HH:mm:ss:SSS``. If nanoseconds are included, the format is ``HH:mm:ss:SSSnnnnnn``. H2O only stores fractions of a second up to the millisecond, so accuracy may be lost. Nanosecond parsing is only included for convenience. Finally, a valid time can end with a space character and then either “AM” or “PM”. For this format, the hours must range from 01 to 12. Within the time, the ‘:’ character can be replaced with a ‘.’ character.

Examples
~~~~~~~~

Example 1: Convert a frame with strings/categoricals into the date format.

.. tabs::
   .. code-tab:: r R

       library(h2o)
       h2o.init()

       hdf <- h2o.importFile("https://s3.amazonaws.com/h2o-public-test-data/smalldata/jira/v-11-eurodate.csv")
       h2o.describe(hdf[5])

       hdf[,5] <- h2o.as_date(hdf[,5], "%d.%m.%y %H:%M")
       h2o.describe(hdf)


   .. code-tab:: python

       import h2o
       h2o.init()

       # Import the v-11-eurodate.csv file 
       # In this file, column 5 is a string column
       df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/jira/v-11-eurodate.csv")
       df["ds5"].describe()

       # Convert column 5 to a date column
       df2 = df["ds5"].as_date("%d.%m.%y %H:%M")
       df2["ds5"].describe()

