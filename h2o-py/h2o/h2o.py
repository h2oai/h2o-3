# -*- encoding: utf-8 -*-
"""
h2o -- module for using H2O services.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import logging
import os
import warnings
import webbrowser
import types


from h2o.backend import H2OConnection
from h2o.backend import H2OConnectionConf
from h2o.backend import H2OLocalServer
from h2o.exceptions import H2OConnectionError, H2OValueError
from h2o.utils.config import H2OConfigReader
from h2o.utils.shared_utils import check_frame_id, deprecated, gen_header, py_tmp_key, quoted, urlopen
from h2o.utils.typechecks import assert_is_type, assert_satisfies, BoundInt, BoundNumeric, I, is_type, numeric, U
from .estimators.deeplearning import H2OAutoEncoderEstimator
from .estimators.deeplearning import H2ODeepLearningEstimator
from .estimators.deepwater import H2ODeepWaterEstimator
from .estimators.estimator_base import H2OEstimator
from .estimators.xgboost import H2OXGBoostEstimator
from .estimators.gbm import H2OGradientBoostingEstimator
from .estimators.glm import H2OGeneralizedLinearEstimator
from .estimators.glrm import H2OGeneralizedLowRankEstimator
from .estimators.kmeans import H2OKMeansEstimator
from .estimators.naive_bayes import H2ONaiveBayesEstimator
from .estimators.pca import H2OPrincipalComponentAnalysisEstimator
from .estimators.random_forest import H2ORandomForestEstimator
from .estimators.stackedensemble import H2OStackedEnsembleEstimator
from .estimators.word2vec import H2OWord2vecEstimator
from .expr import ExprNode
from .frame import H2OFrame
from .grid.grid_search import H2OGridSearch
from .job import H2OJob
from .model.model_base import ModelBase
from .transforms.decomposition import H2OSVD
from .utils.debugging import *  # NOQA
from .utils.compatibility import *  # NOQA
from .utils.compatibility import PY3

logging.basicConfig()

# An IPython deprecation warning is triggered after h2o.init(). Remove this once the deprecation has been resolved
warnings.filterwarnings('ignore', category=DeprecationWarning, module='.*/IPython/.*')


h2oconn = None  # type: H2OConnection

def connect(server=None, url=None, ip=None, port=None, https=None, verify_ssl_certificates=None, auth=None,
            proxy=None,cookies=None, verbose=True, config=None):
    """
    Connect to an existing H2O server, remote or local.

    There are two ways to connect to a server: either pass a `server` parameter containing an instance of
    an H2OLocalServer, or specify `ip` and `port` of the server that you want to connect to.

    :param server: An H2OLocalServer instance to connect to (optional).
    :param url: Full URL of the server to connect to (can be used instead of `ip` + `port` + `https`).
    :param ip: The ip address (or host name) of the server where H2O is running.
    :param port: Port number that H2O service is listening to.
    :param https: Set to True to connect via https:// instead of http://.
    :param verify_ssl_certificates: When using https, setting this to False will disable SSL certificates verification.
    :param auth: Either a (username, password) pair for basic authentication, or one of the requests.auth
                 authenticator objects.
    :param proxy: Proxy server address.
    :param cookies: Cookie (or list of) to add to request
    :param verbose: Set to False to disable printing connection status messages.
    :param connection_conf: Connection configuration object encapsulating connection parameters.
    :returns: the new :class:`H2OConnection` object.
    """
    global h2oconn
    if config:
        if "connect_params" in config:
            h2oconn = _connect_with_conf(config["connect_params"])
        else:
            h2oconn = _connect_with_conf(config)
    else:
        h2oconn = H2OConnection.open(server=server, url=url, ip=ip, port=port, https=https,
                                     auth=auth, verify_ssl_certificates=verify_ssl_certificates,
                                     proxy=proxy,cookies=cookies,
                                     verbose=verbose)
        if verbose:
            h2oconn.cluster.show_status()
    return h2oconn


def api(endpoint, data=None, json=None, filename=None, save_to=None):
    """
    Perform a REST API request to a previously connected server.

    This function is mostly for internal purposes, but may occasionally be useful for direct access to
    the backend H2O server. It has same parameters as :meth:`H2OConnection.request <h2o.backend.H2OConnection.request>`.
    """
    # type checks are performed in H2OConnection class
    _check_connection()
    return h2oconn.request(endpoint, data=data, json=json, filename=filename, save_to=save_to)



def connection():
    """Return the current :class:`H2OConnection` handler."""
    return h2oconn


def version_check():
    """Used to verify that h2o-python module and the H2O server are compatible with each other."""
    from .__init__ import __version__ as ver_pkg
    ci = h2oconn.cluster
    if not ci:
        raise H2OConnectionError("Connection not initialized. Did you run h2o.connect()?")
    ver_h2o = ci.version
    if ver_pkg == "SUBST_PROJECT_VERSION": ver_pkg = "UNKNOWN"
    if str(ver_h2o) != str(ver_pkg):
        branch_name_h2o = ci.branch_name
        build_number_h2o = ci.build_number
        if build_number_h2o is None or build_number_h2o == "unknown":
            raise H2OConnectionError(
                "Version mismatch. H2O is version {0}, but the h2o-python package is version {1}. "
                "Upgrade H2O and h2o-Python to latest stable version - "
                "http://h2o-release.s3.amazonaws.com/h2o/latest_stable.html"
                "".format(ver_h2o, ver_pkg))
        elif build_number_h2o == "99999":
            raise H2OConnectionError(
                "Version mismatch. H2O is version {0}, but the h2o-python package is version {1}. "
                "This is a developer build, please contact your developer."
                "".format(ver_h2o, ver_pkg))
        else:
            raise H2OConnectionError(
                "Version mismatch. H2O is version {0}, but the h2o-python package is version {1}. "
                "Install the matching h2o-Python version from - "
                "http://h2o-release.s3.amazonaws.com/h2o/{2}/{3}/index.html."
                "".format(ver_h2o, ver_pkg, branch_name_h2o, build_number_h2o))
    # Check age of the install
    if ci.build_too_old:
        print("Warning: Your H2O cluster version is too old ({})! Please download and install the latest "
              "version from http://h2o.ai/download/".format(ci.build_age))


def init(url=None, ip=None, port=None, https=None, insecure=None, username=None, password=None,
         cookies=None, proxy=None, start_h2o=True, nthreads=-1, ice_root=None, enable_assertions=True,
         max_mem_size=None, min_mem_size=None, strict_version_check=None, ignore_config=False,
         extra_classpath=None, **kwargs):
    """
    Attempt to connect to a local server, or if not successful start a new server and connect to it.

    :param url: Full URL of the server to connect to (can be used instead of `ip` + `port` + `https`).
    :param ip: The ip address (or host name) of the server where H2O is running.
    :param port: Port number that H2O service is listening to.
    :param https: Set to True to connect via https:// instead of http://.
    :param insecure: When using https, setting this to True will disable SSL certificates verification.
    :param username: Username and
    :param password: Password for basic authentication.
    :param cookies: Cookie (or list of) to add to each request.
    :param proxy: Proxy server address.
    :param start_h2o: If False, do not attempt to start an h2o server when connection to an existing one failed.
    :param nthreads: "Number of threads" option when launching a new h2o server.
    :param ice_root: Directory for temporary files for the new h2o server.
    :param enable_assertions: Enable assertions in Java for the new h2o server.
    :param max_mem_size: Maximum memory to use for the new h2o server.
    :param min_mem_size: Minimum memory to use for the new h2o server.
    :param strict_version_check: If True, an error will be raised if the client and server versions don't match.
    :param ignore_config: Indicates whether a processing of a .h2oconfig file should be conducted or not. Default value is False.
    :param extra_classpath: List of paths to libraries that should be included on the Java classpath when starting H2O from Python.
    :param kwargs: (all other deprecated attributes)
    """
    global h2oconn
    assert_is_type(url, str, None)
    assert_is_type(ip, str, None)
    assert_is_type(port, int, str, None)
    assert_is_type(https, bool, None)
    assert_is_type(insecure, bool, None)
    assert_is_type(username, str, None)
    assert_is_type(password, str, None)
    assert_is_type(cookies, str, [str], None)
    assert_is_type(proxy, {str: str}, None)
    assert_is_type(start_h2o, bool, None)
    assert_is_type(nthreads, int)
    assert_is_type(ice_root, str, None)
    assert_is_type(enable_assertions, bool)
    assert_is_type(max_mem_size, int, str, None)
    assert_is_type(min_mem_size, int, str, None)
    assert_is_type(strict_version_check, bool, None)
    assert_is_type(extra_classpath, [str], None)
    assert_is_type(kwargs, {"proxies": {str: str}, "max_mem_size_GB": int, "min_mem_size_GB": int,
                            "force_connect": bool})

    def get_mem_size(mmint, mmgb):
        if not mmint:  # treat 0 and "" as if they were None
            if mmgb is None: return None
            return mmgb << 30
        if is_type(mmint, int):
            # If the user gives some small number just assume it's in Gigabytes...
            if mmint < 1000: return mmint << 30
            return mmint
        if is_type(mmint, str):
            last = mmint[-1].upper()
            num = mmint[:-1]
            if not (num.isdigit() and last in "MGT"):
                raise H2OValueError("Wrong format for a *_memory_size argument: %s (should be a number followed by "
                                    "a suffix 'M', 'G' or 'T')" % mmint)
            if last == "T": return int(num) << 40
            if last == "G": return int(num) << 30
            if last == "M": return int(num) << 20

    scheme = "https" if https else "http"
    proxy = proxy[scheme] if proxy is not None and scheme in proxy else \
        kwargs["proxies"][scheme] if "proxies" in kwargs and scheme in kwargs["proxies"] else None
    mmax = get_mem_size(max_mem_size, kwargs.get("max_mem_size_GB"))
    mmin = get_mem_size(min_mem_size, kwargs.get("min_mem_size_GB"))
    auth = (username, password) if username and password else None
    check_version = True
    verify_ssl_certificates = True

    # Apply the config file if ignore_config=False
    if not ignore_config:
        config = H2OConfigReader.get_config()
        if url is None and ip is None and port is None and https is None and "init.url" in config:
            url = config["init.url"]
        if proxy is None and "init.proxy" in config:
            proxy = config["init.proxy"]
        if cookies is None and "init.cookies" in config:
            cookies = config["init.cookies"].split(";")
        if auth is None and "init.username" in config and "init.password" in config:
            auth = (config["init.username"], config["init.password"])
        if strict_version_check is None:
            if "init.check_version" in config:
                check_version = config["init.check_version"].lower() != "false"
            elif os.environ.get("H2O_DISABLE_STRICT_VERSION_CHECK"):
                check_version = False
        else:
            check_version = strict_version_check
        if insecure is None:
            if "init.verify_ssl_certificates" in config:
                verify_ssl_certificates = config["init.verify_ssl_certificates"].lower() != "false"
            else:
                verify_ssl_certificates = not insecure

    if not start_h2o:
        print("Warning: if you don't want to start local H2O server, then use of `h2o.connect()` is preferred.")
    try:
        h2oconn = H2OConnection.open(url=url, ip=ip, port=port, https=https,
                                     verify_ssl_certificates=verify_ssl_certificates,
                                     auth=auth, proxy=proxy,cookies=cookies, verbose=True,
                                     _msgs=("Checking whether there is an H2O instance running at {url}",
                                            "connected.", "not found."))
    except H2OConnectionError:
        # Backward compatibility: in init() port parameter really meant "baseport" when starting a local server...
        if port and not str(port).endswith("+"):
            port = str(port) + "+"
        if not start_h2o: raise
        if ip and not (ip == "localhost" or ip == "127.0.0.1"):
            raise H2OConnectionError('Can only start H2O launcher if IP address is localhost.')
        hs = H2OLocalServer.start(nthreads=nthreads, enable_assertions=enable_assertions, max_mem_size=mmax,
                                  min_mem_size=mmin, ice_root=ice_root, port=port, extra_classpath=extra_classpath)
        h2oconn = H2OConnection.open(server=hs, https=https, verify_ssl_certificates=not insecure,
                                     auth=auth, proxy=proxy,cookies=cookies, verbose=True)
    if check_version:
        version_check()
    h2oconn.cluster.timezone = "UTC"
    h2oconn.cluster.show_status()


def lazy_import(path, pattern=None):
    """
    Import a single file or collection of files.

    :param path: A path to a data file (remote or local).
    :param pattern: Character string containing a regular expression to match file(s) in the folder.
    :returns: either a :class:`H2OFrame` with the content of the provided file, or a list of such frames if
        importing multiple files.
    """
    assert_is_type(path, str, [str])
    assert_is_type(pattern, str, None)
    paths = [path] if is_type(path, str) else path
    return _import_multi(paths, pattern)


def _import_multi(paths, pattern):
    assert_is_type(paths, [str])
    assert_is_type(pattern, str, None)
    j = api("POST /3/ImportFilesMulti", {"paths": paths, "pattern": pattern})
    if j["fails"]: raise ValueError("ImportFiles of '" + ".".join(paths) + "' failed on " + str(j["fails"]))
    return j["destination_frames"]


def upload_file(path, destination_frame=None, header=0, sep=None, col_names=None, col_types=None,
                na_strings=None):
    """
    Upload a dataset from the provided local path to the H2O cluster.

    Does a single-threaded push to H2O. Also see :meth:`import_file`.

    :param path: A path specifying the location of the data to upload.
    :param destination_frame:  The unique hex key assigned to the imported file. If none is given, a key will
        be automatically generated.
    :param header: -1 means the first line is data, 0 means guess, 1 means first line is header.
    :param sep: The field separator character. Values on each line of the file are separated by
        this character. If not provided, the parser will automatically detect the separator.
    :param col_names: A list of column names for the file.
    :param col_types: A list of types or a dictionary of column names to types to specify whether columns
        should be forced to a certain type upon import parsing. If a list, the types for elements that are
        one will be guessed. The possible types a column may have are:

        - "unknown" - this will force the column to be parsed as all NA
        - "uuid"    - the values in the column must be true UUID or will be parsed as NA
        - "string"  - force the column to be parsed as a string
        - "numeric" - force the column to be parsed as numeric. H2O will handle the compression of the numeric
          data in the optimal manner.
        - "enum"    - force the column to be parsed as a categorical column.
        - "time"    - force the column to be parsed as a time column. H2O will attempt to parse the following
          list of date time formats: (date) "yyyy-MM-dd", "yyyy MM dd", "dd-MMM-yy", "dd MMM yy", (time)
          "HH:mm:ss", "HH:mm:ss:SSS", "HH:mm:ss:SSSnnnnnn", "HH.mm.ss" "HH.mm.ss.SSS", "HH.mm.ss.SSSnnnnnn".
          Times can also contain "AM" or "PM".
    :param na_strings: A list of strings, or a list of lists of strings (one list per column), or a dictionary
        of column names to strings which are to be interpreted as missing values.

    :returns: a new :class:`H2OFrame` instance.

    :examples:
        >>> frame = h2o.upload_file("/path/to/local/data")
    """
    coltype = U(None, "unknown", "uuid", "string", "float", "real", "double", "int", "numeric",
                "categorical", "factor", "enum", "time")
    natype = U(str, [str])
    assert_is_type(path, str)
    assert_is_type(destination_frame, str, None)
    assert_is_type(header, -1, 0, 1)
    assert_is_type(sep, None, I(str, lambda s: len(s) == 1))
    assert_is_type(col_names, [str], None)
    assert_is_type(col_types, [coltype], {str: coltype}, None)
    assert_is_type(na_strings, [natype], {str: natype}, None)
    check_frame_id(destination_frame)
    if path.startswith("~"):
        path = os.path.expanduser(path)
    return H2OFrame()._upload_parse(path, destination_frame, header, sep, col_names, col_types, na_strings)


def import_file(path=None, destination_frame=None, parse=True, header=0, sep=None, col_names=None, col_types=None,
                na_strings=None, pattern=None):
    """
    Import a dataset that is already on the cluster.

    The path to the data must be a valid path for each node in the H2O cluster. If some node in the H2O cluster
    cannot see the file, then an exception will be thrown by the H2O cluster. Does a parallel/distributed
    multi-threaded pull of the data. The main difference between this method and :func:`upload_file` is that
    the latter works with local files, whereas this method imports remote files (i.e. files local to the server).
    If you running H2O server on your own maching, then both methods behave the same.

    :param path: path(s) specifying the location of the data to import or a path to a directory of files to import
    :param destination_frame: The unique hex key assigned to the imported file. If none is given, a key will be
        automatically generated.
    :param parse: If True, the file should be parsed after import. If False, then a list is returned containing the file path.
    :param header: -1 means the first line is data, 0 means guess, 1 means first line is header.
    :param sep: The field separator character. Values on each line of the file are separated by
        this character. If not provided, the parser will automatically detect the separator.
    :param col_names: A list of column names for the file.
    :param col_types: A list of types or a dictionary of column names to types to specify whether columns
        should be forced to a certain type upon import parsing. If a list, the types for elements that are
        one will be guessed. The possible types a column may have are:

        - "unknown" - this will force the column to be parsed as all NA
        - "uuid"    - the values in the column must be true UUID or will be parsed as NA
        - "string"  - force the column to be parsed as a string
        - "numeric" - force the column to be parsed as numeric. H2O will handle the compression of the numeric
          data in the optimal manner.
        - "enum"    - force the column to be parsed as a categorical column.
        - "time"    - force the column to be parsed as a time column. H2O will attempt to parse the following
          list of date time formats: (date) "yyyy-MM-dd", "yyyy MM dd", "dd-MMM-yy", "dd MMM yy", (time)
          "HH:mm:ss", "HH:mm:ss:SSS", "HH:mm:ss:SSSnnnnnn", "HH.mm.ss" "HH.mm.ss.SSS", "HH.mm.ss.SSSnnnnnn".
          Times can also contain "AM" or "PM".
    :param na_strings: A list of strings, or a list of lists of strings (one list per column), or a dictionary
        of column names to strings which are to be interpreted as missing values.
    :param pattern: Character string containing a regular expression to match file(s) in the folder if `path` is a
        directory.

    :returns: a new :class:`H2OFrame` instance.

    :examples:
        >>> # Single file import
        >>> iris = import_file("h2o-3/smalldata/iris.csv")
        >>> # Return all files in the folder iris/ matching the regex r"iris_.*\.csv"
        >>> iris_pattern = h2o.import_file(path = "h2o-3/smalldata/iris",
        ...                                pattern = "iris_.*\.csv")
    """
    coltype = U(None, "unknown", "uuid", "string", "float", "real", "double", "int", "numeric",
                "categorical", "factor", "enum", "time")
    natype = U(str, [str])
    assert_is_type(path, str, [str])
    assert_is_type(pattern, str, None)
    assert_is_type(destination_frame, str, None)
    assert_is_type(parse, bool)
    assert_is_type(header, -1, 0, 1)
    assert_is_type(sep, None, I(str, lambda s: len(s) == 1))
    assert_is_type(col_names, [str], None)
    assert_is_type(col_types, [coltype], {str: coltype}, None)
    assert_is_type(na_strings, [natype], {str: natype}, None)
    check_frame_id(destination_frame)
    patharr = path if isinstance(path, list) else [path]
    if any(os.path.split(p)[0] == "~" for p in patharr):
        raise H2OValueError("Paths relative to a current user (~) are not valid in the server environment. "
                            "Please use absolute paths if possible.")
    if not parse:
        return lazy_import(path, pattern)
    else:
        return H2OFrame()._import_parse(path, pattern, destination_frame, header, sep, col_names, col_types, na_strings)


def import_sql_table(connection_url, table, username, password, columns=None, optimize=True):
    """
    Import SQL table to H2OFrame in memory.

    Assumes that the SQL table is not being updated and is stable.
    Runs multiple SELECT SQL queries concurrently for parallel ingestion.
    Be sure to start the h2o.jar in the terminal with your downloaded JDBC driver in the classpath::

        java -cp <path_to_h2o_jar>:<path_to_jdbc_driver_jar> water.H2OApp

    Also see :func:`import_sql_select`.
    Currently supported SQL databases are MySQL, PostgreSQL, MariaDB, and Netezza. Support for Oracle 12g and Microsoft SQL
    Server is forthcoming.

    :param connection_url: URL of the SQL database connection as specified by the Java Database Connectivity (JDBC)
        Driver. For example, "jdbc:mysql://localhost:3306/menagerie?&useSSL=false"
    :param table: name of SQL table
    :param columns: a list of column names to import from SQL table. Default is to import all columns.
    :param username: username for SQL server
    :param password: password for SQL server
    :param optimize: optimize import of SQL table for faster imports. Experimental.

    :returns: an :class:`H2OFrame` containing data of the specified SQL table.

    :examples:
        >>> conn_url = "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
        >>> table = "citibike20k"
        >>> username = "root"
        >>> password = "abc123"
        >>> my_citibike_data = h2o.import_sql_table(conn_url, table, username, password)
    """
    assert_is_type(connection_url, str)
    assert_is_type(table, str)
    assert_is_type(username, str)
    assert_is_type(password, str)
    assert_is_type(columns, [str], None)
    assert_is_type(optimize, bool)
    p = {"connection_url": connection_url, "table": table, "username": username, "password": password, "optimize": optimize}
    if columns:
        p["columns"] = ", ".join(columns)
    j = H2OJob(api("POST /99/ImportSQLTable", data=p), "Import SQL Table").poll()
    return get_frame(j.dest_key)


def import_sql_select(connection_url, select_query, username, password, optimize=True):
    """
    Import the SQL table that is the result of the specified SQL query to H2OFrame in memory.

    Creates a temporary SQL table from the specified sql_query.
    Runs multiple SELECT SQL queries on the temporary table concurrently for parallel ingestion, then drops the table.
    Be sure to start the h2o.jar in the terminal with your downloaded JDBC driver in the classpath::

      java -cp <path_to_h2o_jar>:<path_to_jdbc_driver_jar> water.H2OApp

    Also see h2o.import_sql_table. Currently supported SQL databases are MySQL, PostgreSQL, and MariaDB. Support
    for Oracle 12g and Microsoft SQL Server is forthcoming.

    :param connection_url: URL of the SQL database connection as specified by the Java Database Connectivity (JDBC)
        Driver. For example, "jdbc:mysql://localhost:3306/menagerie?&useSSL=false"
    :param select_query: SQL query starting with `SELECT` that returns rows from one or more database tables.
    :param username: username for SQL server
    :param password: password for SQL server
    :param optimize: optimize import of SQL table for faster imports. Experimental.

    :returns: an :class:`H2OFrame` containing data of the specified SQL query.

    :examples:
        >>> conn_url = "jdbc:mysql://172.16.2.178:3306/ingestSQL?&useSSL=false"
        >>> select_query = "SELECT bikeid from citibike20k"
        >>> username = "root"
        >>> password = "abc123"
        >>> my_citibike_data = h2o.import_sql_select(conn_url, select_query,
        ...                                          username, password)
    """
    assert_is_type(connection_url, str)
    assert_is_type(select_query, str)
    assert_is_type(username, str)
    assert_is_type(password, str)
    assert_is_type(optimize, bool)
    p = {"connection_url": connection_url, "select_query": select_query, "username": username, "password": password,
         "optimize": optimize}
    j = H2OJob(api("POST /99/ImportSQLTable", data=p), "Import SQL Table").poll()
    return get_frame(j.dest_key)


def parse_setup(raw_frames, destination_frame=None, header=0, separator=None, column_names=None,
                column_types=None, na_strings=None):
    """
    Retrieve H2O's best guess as to what the structure of the data file is.

    During parse setup, the H2O cluster will make several guesses about the attributes of
    the data. This method allows a user to perform corrective measures by updating the
    returning dictionary from this method. This dictionary is then fed into `parse_raw` to
    produce the H2OFrame instance.

    :param raw_frames: a collection of imported file frames
    :param destination_frame: The unique hex key assigned to the imported file. If none is given, a key will
        automatically be generated.
    :param header: -1 means the first line is data, 0 means guess, 1 means first line is header.
    :param separator: The field separator character. Values on each line of the file are separated by
        this character. If not provided, the parser will automatically detect the separator.
    :param column_names: A list of column names for the file.
    :param column_types: A list of types or a dictionary of column names to types to specify whether columns
        should be forced to a certain type upon import parsing. If a list, the types for elements that are
        one will be guessed. The possible types a column may have are:

        - "unknown" - this will force the column to be parsed as all NA
        - "uuid"    - the values in the column must be true UUID or will be parsed as NA
        - "string"  - force the column to be parsed as a string
        - "numeric" - force the column to be parsed as numeric. H2O will handle the compression of the numeric
          data in the optimal manner.
        - "enum"    - force the column to be parsed as a categorical column.
        - "time"    - force the column to be parsed as a time column. H2O will attempt to parse the following
          list of date time formats: (date) "yyyy-MM-dd", "yyyy MM dd", "dd-MMM-yy", "dd MMM yy", (time)
          "HH:mm:ss", "HH:mm:ss:SSS", "HH:mm:ss:SSSnnnnnn", "HH.mm.ss" "HH.mm.ss.SSS", "HH.mm.ss.SSSnnnnnn".
          Times can also contain "AM" or "PM".

    :param na_strings: A list of strings, or a list of lists of strings (one list per column), or a dictionary
        of column names to strings which are to be interpreted as missing values.

    :returns: a dictionary containing parse parameters guessed by the H2O backend.
    """
    coltype = U(None, "unknown", "uuid", "string", "float", "real", "double", "int", "numeric",
                "categorical", "factor", "enum", "time")
    natype = U(str, [str])
    assert_is_type(raw_frames, str, [str])
    assert_is_type(destination_frame, None, str)
    assert_is_type(header, -1, 0, 1)
    assert_is_type(separator, None, I(str, lambda s: len(s) == 1))
    assert_is_type(column_names, [str], None)
    assert_is_type(column_types, [coltype], {str: coltype}, None)
    assert_is_type(na_strings, [natype], {str: natype}, None)
    check_frame_id(destination_frame)

    # The H2O backend only accepts things that are quoted
    if is_type(raw_frames, str): raw_frames = [raw_frames]

    # temporary dictionary just to pass the following information to the parser: header, separator
    kwargs = {"check_header": header, "source_frames": [quoted(frame_id) for frame_id in raw_frames]}
    if separator:
        kwargs["separator"] = ord(separator)

    j = api("POST /3/ParseSetup", data=kwargs)
    if "warnings" in j and j["warnings"]:
        for w in j["warnings"]:
            warnings.warn(w)
    # TODO: really should be url encoding...
    # TODO: clean up all this
    if destination_frame:
        j["destination_frame"] = destination_frame
    if column_names is not None:
        if not isinstance(column_names, list): raise ValueError("col_names should be a list")
        if len(column_names) != len(j["column_types"]): raise ValueError(
            "length of col_names should be equal to the number of columns: %d vs %d"
            % (len(column_names), len(j["column_types"])))
        j["column_names"] = column_names
    if column_types is not None:
        if isinstance(column_types, dict):
            # overwrite dictionary to ordered list of column types. if user didn't specify column type for all names,
            # use type provided by backend
            if j["column_names"] is None:  # no colnames discovered! (C1, C2, ...)
                j["column_names"] = gen_header(j["number_columns"])
            if not set(column_types.keys()).issubset(set(j["column_names"])): raise ValueError(
                "names specified in col_types is not a subset of the column names")
            idx = 0
            column_types_list = []
            for name in j["column_names"]:
                if name in column_types:
                    column_types_list.append(column_types[name])
                else:
                    column_types_list.append(j["column_types"][idx])
                idx += 1
            column_types = column_types_list
        elif isinstance(column_types, list):
            if len(column_types) != len(j["column_types"]): raise ValueError(
                "length of col_types should be equal to the number of columns")
            column_types = [column_types[i] if column_types[i] else j["column_types"][i] for i in
                            range(len(column_types))]
        else:  # not dictionary or list
            raise ValueError("col_types should be a list of types or a dictionary of column names to types")
        j["column_types"] = column_types
    if na_strings is not None:
        if isinstance(na_strings, dict):
            # overwrite dictionary to ordered list of lists of na_strings
            if not j["column_names"]: raise ValueError("column names should be specified")
            if not set(na_strings.keys()).issubset(set(j["column_names"])): raise ValueError(
                "names specified in na_strings is not a subset of the column names")
            j["na_strings"] = [[] for _ in range(len(j["column_names"]))]
            for name, na in na_strings.items():
                idx = j["column_names"].index(name)
                if is_type(na, str): na = [na]
                for n in na: j["na_strings"][idx].append(quoted(n))
        elif is_type(na_strings, [[str]]):
            if len(na_strings) != len(j["column_types"]):
                raise ValueError("length of na_strings should be equal to the number of columns")
            j["na_strings"] = [[quoted(na) for na in col] if col is not None else [] for col in na_strings]
        elif isinstance(na_strings, list):
            j["na_strings"] = [[quoted(na) for na in na_strings]] * len(j["column_types"])
        else:  # not a dictionary or list
            raise ValueError(
                "na_strings should be a list, a list of lists (one list per column), or a dictionary of column "
                "names to strings which are to be interpreted as missing values")

    # quote column names and column types also when not specified by user
    if j["column_names"]: j["column_names"] = list(map(quoted, j["column_names"]))
    j["column_types"] = list(map(quoted, j["column_types"]))
    return j


def parse_raw(setup, id=None, first_line_is_header=0):
    """
    Parse dataset using the parse setup structure.

    :param setup: Result of ``h2o.parse_setup()``
    :param id: an id for the frame.
    :param first_line_is_header: -1, 0, 1 if the first line is to be used as the header

    :returns: an :class:`H2OFrame` object.
    """
    assert_is_type(setup, dict)
    assert_is_type(id, str, None)
    assert_is_type(first_line_is_header, -1, 0, 1)
    check_frame_id(id)
    if id:
        setup["destination_frame"] = id
    if first_line_is_header != (-1, 0, 1):
        if first_line_is_header not in (-1, 0, 1): raise ValueError("first_line_is_header should be -1, 0, or 1")
        setup["check_header"] = first_line_is_header
    fr = H2OFrame()
    fr._parse_raw(setup)
    return fr


def assign(data, xid):
    """
    (internal) Assign new id to the frame.

    :param data: an H2OFrame whose id should be changed
    :param xid: new id for the frame.
    :returns: the passed frame.
    """
    assert_is_type(data, H2OFrame)
    assert_is_type(xid, str)
    assert_satisfies(xid, xid != data.frame_id)
    check_frame_id(xid)
    data._ex = ExprNode("assign", xid, data)._eval_driver(False)
    data._ex._cache._id = xid
    data._ex._children = None
    return data


def deep_copy(data, xid):
    """
    Create a deep clone of the frame ``data``.

    :param data: an H2OFrame to be cloned
    :param xid: (internal) id to be assigned to the new frame.
    :returns: new :class:`H2OFrame` which is the clone of the passed frame.
    """
    assert_is_type(data, H2OFrame)
    assert_is_type(xid, str)
    assert_satisfies(xid, xid != data.frame_id)
    check_frame_id(xid)
    duplicate = data.apply(lambda x: x)
    duplicate._ex = ExprNode("assign", xid, duplicate)._eval_driver(False)
    duplicate._ex._cache._id = xid
    duplicate._ex._children = None
    return duplicate


def get_model(model_id):
    """
    Load a model from the server.

    :param model_id: The model identification in H2O

    :returns: Model object, a subclass of H2OEstimator
    """
    assert_is_type(model_id, str)
    model_json = api("GET /3/Models/%s" % model_id)["models"][0]
    algo = model_json["algo"]
    if algo == "svd":            m = H2OSVD()
    elif algo == "pca":          m = H2OPrincipalComponentAnalysisEstimator()
    elif algo == "drf":          m = H2ORandomForestEstimator()
    elif algo == "naivebayes":   m = H2ONaiveBayesEstimator()
    elif algo == "kmeans":       m = H2OKMeansEstimator()
    elif algo == "glrm":         m = H2OGeneralizedLowRankEstimator()
    elif algo == "glm":          m = H2OGeneralizedLinearEstimator()
    elif algo == "gbm":          m = H2OGradientBoostingEstimator()
    elif algo == "deepwater":    m = H2ODeepWaterEstimator()
    elif algo == "xgboost":      m = H2OXGBoostEstimator()
    elif algo == "word2vec":     m = H2OWord2vecEstimator()
    elif algo == "deeplearning":
        if model_json["output"]["model_category"] == "AutoEncoder":
            m = H2OAutoEncoderEstimator()
        else:
            m = H2ODeepLearningEstimator()
    elif algo == "stackedensemble": m = H2OStackedEnsembleEstimator()
    else:
        raise ValueError("Unknown algo type: " + algo)
    m._resolve_model(model_id, model_json)
    return m


def get_grid(grid_id):
    """
    Return the specified grid.

    :param grid_id: The grid identification in h2o

    :returns: an :class:`H2OGridSearch` instance.
    """
    assert_is_type(grid_id, str)
    grid_json = api("GET /99/Grids/%s" % grid_id)
    models = [get_model(key["name"]) for key in grid_json["model_ids"]]
    # get first model returned in list of models from grid search to get model class (binomial, multinomial, etc)
    first_model_json = api("GET /3/Models/%s" % grid_json["model_ids"][0]["name"])["models"][0]
    gs = H2OGridSearch(None, {}, grid_id)
    gs._resolve_grid(grid_id, grid_json, first_model_json)
    gs.models = models
    hyper_params = {param: set() for param in gs.hyper_names}
    for param in gs.hyper_names:
        for model in models:
            if isinstance(model.full_parameters[param]["actual_value"], list):
                hyper_params[param].add(model.full_parameters[param]["actual_value"][0])
            else:
                hyper_params[param].add(model.full_parameters[param]["actual_value"])

    hyper_params = {str(param): list(vals) for param, vals in hyper_params.items()}
    gs.hyper_params = hyper_params
    gs.model = model.__class__()
    return gs


def get_frame(frame_id, **kwargs):
    """
    Obtain a handle to the frame in H2O with the frame_id key.

    :param str frame_id: id of the frame to retrieve.
    :returns: an :class:`H2OFrame` object
    """
    assert_is_type(frame_id, str)
    return H2OFrame.get_frame(frame_id, **kwargs)


def no_progress():
    """
    Disable the progress bar from flushing to stdout.

    The completed progress bar is printed when a job is complete so as to demarcate a log file.
    """
    H2OJob.__PROGRESS_BAR__ = False


def show_progress():
    """Enable the progress bar (it is enabled by default)."""
    H2OJob.__PROGRESS_BAR__ = True


def enable_expr_optimizations(flag):
    """Enable expression tree local optimizations."""
    ExprNode.__ENABLE_EXPR_OPTIMIZATIONS__ = flag


def is_expr_optimizations_enabled():
    return ExprNode.__ENABLE_EXPR_OPTIMIZATIONS__


def log_and_echo(message=""):
    """
    Log a message on the server-side logs.

    This is helpful when running several pieces of work one after the other on a single H2O
    cluster and you want to make a notation in the H2O server side log where one piece of
    work ends and the next piece of work begins.

    Sends a message to H2O for logging. Generally used for debugging purposes.

    :param message: message to write to the log.
    """
    assert_is_type(message, str)
    api("POST /3/LogAndEcho", data={"message": str(message)})


def remove(x):
    """
    Remove object(s) from H2O.

    :param x: H2OFrame, H2OEstimator, or string, or a list of those things: the object(s) or unique id(s)
        pointing to the object(s) to be removed.
    """
    item_type = U(str, H2OFrame, H2OEstimator)
    assert_is_type(x, item_type, [item_type])
    if not isinstance(x, list): x = [x]
    for xi in x:
        if isinstance(xi, H2OFrame):
            xi_id = xi._ex._cache._id  # String or None
            if xi_id is None: return  # Lazy frame, never evaluated, nothing in cluster
            rapids("(rm {})".format(xi_id))
            xi._ex = None
        elif isinstance(xi, H2OEstimator):
            api("DELETE /3/DKV/%s" % xi.model_id)
            xi._id = None
        else:
            # string may be a Frame key name part of a rapids session... need to call rm thru rapids here
            try:
                rapids("(rm {})".format(xi))
            except:
                api("DELETE /3/DKV/%s" % xi)


def remove_all():
    """Remove all objects from H2O."""
    api("DELETE /3/DKV")


def rapids(expr):
    """
    Execute a Rapids expression.

    :param expr: The rapids expression (ascii string).

    :returns: The JSON response (as a python dictionary) of the Rapids execution.
    """
    assert_is_type(expr, str)
    return ExprNode.rapids(expr)


def ls():
    """List keys on an H2O Cluster."""
    return H2OFrame._expr(expr=ExprNode("ls")).as_data_frame(use_pandas=True)


def frame(frame_id):
    """
    Retrieve metadata for an id that points to a Frame.

    :param frame_id: the key of a Frame in H2O.

    :returns: dict containing the frame meta-information.
    """
    assert_is_type(frame_id, str)
    return api("GET /3/Frames/%s" % frame_id)


def frames():
    """
    Retrieve all the Frames.

    :returns: Meta information on the frames
    """
    return api("GET /3/Frames")


def download_pojo(model, path="", get_jar=True, jar_name=""):
    """
    Download the POJO for this model to the directory specified by path; if path is "", then dump to screen.

    :param model: the model whose scoring POJO should be retrieved.
    :param path: an absolute path to the directory where POJO should be saved.
    :param get_jar: retrieve the h2o-genmodel.jar also (will be saved to the same folder ``path``).
    :param jar_name: Custom name of genmodel jar.
    :returns: location of the downloaded POJO file.
    """
    assert_is_type(model, ModelBase)
    assert_is_type(path, str)
    assert_is_type(get_jar, bool)

    if not model.have_pojo:
        raise H2OValueError("Export to POJO not supported")

    if path == "":
        java_code = api("GET /3/Models.java/%s" % model.model_id)
        print(java_code)
        return None
    else:
        filename = api("GET /3/Models.java/%s" % model.model_id, save_to=path)
        if get_jar:
            if jar_name == "":
                api("GET /3/h2o-genmodel.jar", save_to=os.path.join(path, "h2o-genmodel.jar"))
            else:
                api("GET /3/h2o-genmodel.jar", save_to=os.path.join(path, jar_name))
        return filename


def download_csv(data, filename):
    """
    Download an H2O data set to a CSV file on the local disk.

    Warning: Files located on the H2O server may be very large! Make sure you have enough
    hard drive space to accommodate the entire file.

    :param data: an H2OFrame object to be downloaded.
    :param filename: name for the CSV file where the data should be saved to.
    """
    assert_is_type(data, H2OFrame)
    assert_is_type(filename, str)
    url = h2oconn.make_url("DownloadDataset", 3) + "?frame_id={}&hex_string=false".format(data.frame_id)
    with open(filename, "wb") as f:
        f.write(urlopen()(url).read())


def download_all_logs(dirname=".", filename=None):
    """
    Download H2O log files to disk.

    :param dirname: a character string indicating the directory that the log file should be saved in.
    :param filename: a string indicating the name that the CSV file should be. Note that the saved format is .zip, so the file name must include the .zip extension.

    :returns: path of logs written in a zip file.

    :examples: The following code will save the zip file `'autoh2o_log.zip'` in a directory that is one down from where you are currently working into a directory called `your_directory_name`. (Please note that `your_directory_name` should be replaced with the name of the directory that you've created and that already exists.)

        >>> h2o.download_all_logs(dirname='./your_directory_name/', filename = 'autoh2o_log.zip')

    """
    assert_is_type(dirname, str)
    assert_is_type(filename, str, None)
    url = "%s/3/Logs/download" % h2oconn.base_url
    opener = urlopen()
    response = opener(url)

    if not os.path.exists(dirname): os.mkdir(dirname)
    if filename is None:
        if PY3:
            headers = [h[1] for h in response.headers._headers]
        else:
            headers = response.headers.headers
        for h in headers:
            if "filename=" in h:
                filename = h.split("filename=")[1].strip()
                break
    path = os.path.join(dirname, filename)
    response = opener(url).read()

    print("Writing H2O logs to " + path)
    with open(path, "wb") as f:
        f.write(response)
    return path


def save_model(model, path="", force=False):
    """
    Save an H2O Model object to disk. (Note that ensemble binary models can now be saved using this method.)

    :param model: The model object to save.
    :param path: a path to save the model at (hdfs, s3, local)
    :param force: if True overwrite destination directory in case it exists, or throw exception if set to False.

    :returns: the path of the saved model

    :examples:
        >>> path = h2o.save_model(my_model, dir=my_path)
    """
    assert_is_type(model, ModelBase)
    assert_is_type(path, str)
    assert_is_type(force, bool)
    path = os.path.join(os.getcwd() if path == "" else path, model.model_id)
    return api("GET /99/Models.bin/%s" % model.model_id, data={"dir": path, "force": force})["dir"]


def load_model(path):
    """
    Load a saved H2O model from disk. (Note that ensemble binary models can now be loaded using this method.)

    :param path: the full path of the H2O Model to be imported.

    :returns: an :class:`H2OEstimator` object

    :examples:
        >>> path = h2o.save_model(my_model, dir=my_path)
        >>> h2o.load_model(path)
    """
    assert_is_type(path, str)
    res = api("POST /99/Models.bin/%s" % "", data={"dir": path})
    return get_model(res["models"][0]["model_id"]["name"])


def export_file(frame, path, force=False, parts=1):
    """
    Export a given H2OFrame to a path on the machine this python session is currently connected to.

    :param frame: the Frame to save to disk.
    :param path: the path to the save point on disk.
    :param force: if True, overwrite any preexisting file with the same path
    :param parts: enables export to multiple 'part' files instead of just a single file.
        Convenient for large datasets that take too long to store in a single file.
        Use parts=-1 to instruct H2O to determine the optimal number of part files or
        specify your desired maximum number of part files. Path needs to be a directory
        when exporting to multiple files, also that directory must be empty.
        Default is ``parts = 1``, which is to export to a single file.
    """
    assert_is_type(frame, H2OFrame)
    assert_is_type(path, str)
    assert_is_type(force, bool)
    assert_is_type(parts, int)
    H2OJob(api("POST /3/Frames/%s/export" % (frame.frame_id), data={"path": path, "num_parts": parts, "force": force}),
           "Export File").poll()


def cluster():
    """Return :class:`H2OCluster` object describing the backend H2O cloud."""
    return h2oconn.cluster if h2oconn else None



def create_frame(frame_id=None, rows=10000, cols=10, randomize=True,
                 real_fraction=None, categorical_fraction=None, integer_fraction=None,
                 binary_fraction=None, time_fraction=None, string_fraction=None,
                 value=0, real_range=100, factors=100, integer_range=100,
                 binary_ones_fraction=0.02, missing_fraction=0.01,
                 has_response=False, response_factors=2, positive_response=False,
                 seed=None, seed_for_column_types=None):
    """
    Create a new frame with random data.

    Creates a data frame in H2O with real-valued, categorical, integer, and binary columns specified by the user.

    :param frame_id: the destination key. If empty, this will be auto-generated.
    :param rows: the number of rows of data to generate.
    :param cols: the number of columns of data to generate. Excludes the response column if has_response is True.
    :param randomize: If True, data values will be randomly generated. This must be True if either
        categorical_fraction or integer_fraction is non-zero.
    :param value: if randomize is False, then all real-valued entries will be set to this value.
    :param real_range: the range of randomly generated real values.
    :param real_fraction: the fraction of columns that are real-valued.
    :param categorical_fraction: the fraction of total columns that are categorical.
    :param factors: the number of (unique) factor levels in each categorical column.
    :param integer_fraction: the fraction of total columns that are integer-valued.
    :param integer_range: the range of randomly generated integer values.
    :param binary_fraction: the fraction of total columns that are binary-valued.
    :param binary_ones_fraction: the fraction of values in a binary column that are set to 1.
    :param time_fraction: the fraction of randomly created date/time columns.
    :param string_fraction: the fraction of randomly created string columns.
    :param missing_fraction: the fraction of total entries in the data frame that are set to NA.
    :param has_response: A logical value indicating whether an additional response column should be prepended to the
        final H2O data frame. If set to True, the total number of columns will be ``cols + 1``.
    :param response_factors: if has_response is True, then this variable controls the type of the "response" column:
        setting response_factors to 1 will generate real-valued response, any value greater or equal than 2 will
        create categorical response with that many categories.
    :param positive_reponse: when response variable is present and of real type, this will control whether it
        contains positive values only, or both positive and negative.
    :param seed: a seed used to generate random values when ``randomize`` is True.
    :param seed_for_column_types: a seed used to generate random column types when ``randomize`` is True.

    :returns: an :class:`H2OFrame` object
    """
    t_fraction = U(None, BoundNumeric(0, 1))
    assert_is_type(frame_id, str, None)
    assert_is_type(rows, BoundInt(1))
    assert_is_type(cols, BoundInt(1))
    assert_is_type(randomize, bool)
    assert_is_type(value, numeric)
    assert_is_type(real_range, BoundNumeric(0))
    assert_is_type(real_fraction, t_fraction)
    assert_is_type(categorical_fraction, t_fraction)
    assert_is_type(integer_fraction, t_fraction)
    assert_is_type(binary_fraction, t_fraction)
    assert_is_type(time_fraction, t_fraction)
    assert_is_type(string_fraction, t_fraction)
    assert_is_type(missing_fraction, t_fraction)
    assert_is_type(binary_ones_fraction, t_fraction)
    assert_is_type(factors, BoundInt(1))
    assert_is_type(integer_range, BoundInt(1))
    assert_is_type(has_response, bool)
    assert_is_type(response_factors, None, BoundInt(1))
    assert_is_type(positive_response, bool)
    assert_is_type(seed, int, None)
    assert_is_type(seed_for_column_types, int, None)
    check_frame_id(frame_id)

    if randomize and value:
        raise H2OValueError("Cannot set data to a `value` if `randomize` is true")

    if (categorical_fraction or integer_fraction) and not randomize:
        raise H2OValueError("`randomize` should be True when either categorical or integer columns are used.")

    # The total column fraction that the user has specified explicitly. This sum should not exceed 1. We will respect
    # all explicitly set fractions, and will auto-select the remaining fractions.
    frcs = [real_fraction, categorical_fraction, integer_fraction, binary_fraction, time_fraction, string_fraction]
    wgts = [0.5, 0.2, 0.2, 0.1, 0.0, 0.0]
    sum_explicit_fractions = sum(0 if f is None else f for f in frcs)
    count_explicit_fractions = sum(0 if f is None else 1 for f in frcs)
    remainder = 1 - sum_explicit_fractions
    if sum_explicit_fractions >= 1 + 1e-10:
        raise H2OValueError("Fractions of binary, integer, categorical, time and string columns should add up "
                            "to a number less than 1.")
    elif sum_explicit_fractions >= 1 - 1e-10:
        # The fractions already add up to almost 1. No need to do anything (the server will absorb the tiny
        # remainder into the real_fraction column).
        pass
    else:
        # sum_explicit_fractions < 1  =>  distribute the remainder among the columns that were not set explicitly
        if count_explicit_fractions == 6:
            raise H2OValueError("Fraction of binary, integer, categorical, time and string columns add up to a "
                                "number less than 1.")
        # Each column type receives a certain part (proportional to column's "weight") of the remaining fraction.
        sum_implicit_weights = sum(wgts[i] if frcs[i] is None else 0 for i in range(6))
        for i, f in enumerate(frcs):
            if frcs[i] is not None: continue
            if sum_implicit_weights == 0:
                frcs[i] = remainder
            else:
                frcs[i] = remainder * wgts[i] / sum_implicit_weights
            remainder -= frcs[i]
            sum_implicit_weights -= wgts[i]
    for i, f in enumerate(frcs):
        if f is None:
            frcs[i] = 0
    real_fraction, categorical_fraction, integer_fraction, binary_fraction, time_fraction, string_fraction = frcs

    parms = {"dest": frame_id if frame_id else py_tmp_key(append=h2oconn.session_id),
             "rows": rows,
             "cols": cols,
             "randomize": randomize,
             "categorical_fraction": categorical_fraction,
             "integer_fraction": integer_fraction,
             "binary_fraction": binary_fraction,
             "time_fraction": time_fraction,
             "string_fraction": string_fraction,
             # "real_fraction" is not provided, the backend computes it as 1 - sum(5 other fractions)
             "value": value,
             "real_range": real_range,
             "factors": factors,
             "integer_range": integer_range,
             "binary_ones_fraction": binary_ones_fraction,
             "missing_fraction": missing_fraction,
             "has_response": has_response,
             "response_factors": response_factors,
             "positive_response": positive_response,
             "seed": -1 if seed is None else seed,
             "seed_for_column_types": -1 if seed_for_column_types is None else seed_for_column_types,
             }
    H2OJob(api("POST /3/CreateFrame", data=parms), "Create Frame").poll()
    return get_frame(parms["dest"])


def interaction(data, factors, pairwise, max_factors, min_occurrence, destination_frame=None):
    """
    Categorical Interaction Feature Creation in H2O.

    Creates a frame in H2O with n-th order interaction features between categorical columns, as specified by
    the user.

    :param data: the H2OFrame that holds the target categorical columns.
    :param factors: factor columns (either indices or column names).
    :param pairwise: If True, create pairwise interactions between factors (otherwise create one
        higher-order interaction). Only applicable if there are 3 or more factors.
    :param max_factors: Max. number of factor levels in pair-wise interaction terms (if enforced, one extra
        catch-all factor will be made).
    :param min_occurrence: Min. occurrence threshold for factor levels in pair-wise interaction terms
    :param destination_frame: a string indicating the destination key. If empty, this will be auto-generated by H2O.

    :returns: :class:`H2OFrame`
    """
    assert_is_type(data, H2OFrame)
    assert_is_type(factors, [str, int])
    assert_is_type(pairwise, bool)
    assert_is_type(max_factors, int)
    assert_is_type(min_occurrence, int)
    assert_is_type(destination_frame, str, None)
    factors = [data.names[n] if is_type(n, int) else n for n in factors]
    parms = {"dest": py_tmp_key(append=h2oconn.session_id) if destination_frame is None else destination_frame,
             "source_frame": data.frame_id,
             "factor_columns": [quoted(f) for f in factors],
             "pairwise": pairwise,
             "max_factors": max_factors,
             "min_occurrence": min_occurrence,
             }
    H2OJob(api("POST /3/Interaction", data=parms), "Interactions").poll()
    return get_frame(parms["dest"])


def as_list(data, use_pandas=True, header=True):
    """
    Convert an H2O data object into a python-specific object.

    WARNING! This will pull all data local!

    If Pandas is available (and use_pandas is True), then pandas will be used to parse the
    data frame. Otherwise, a list-of-lists populated by character data will be returned (so
    the types of data will all be str).

    :param data: an H2O data object.
    :param use_pandas: If True, try to use pandas for reading in the data.
    :param header: If True, return column names as first element in list

    :returns: List of lists (Rows x Columns).
    """
    assert_is_type(data, H2OFrame)
    assert_is_type(use_pandas, bool)
    assert_is_type(header, bool)
    return H2OFrame.as_data_frame(data, use_pandas=use_pandas, header=header)


def demo(funcname, interactive=True, echo=True, test=False):
    """
    H2O built-in demo facility.

    :param funcname: A string that identifies the h2o python function to demonstrate.
    :param interactive: If True, the user will be prompted to continue the demonstration after every segment.
    :param echo: If True, the python commands that are executed will be displayed.
    :param test: If True, `h2o.init()` will not be called (used for pyunit testing).

    :example:
        >>> import h2o
        >>> h2o.demo("gbm")
    """
    import h2o.demos as h2odemo
    assert_is_type(funcname, str)
    assert_is_type(interactive, bool)
    assert_is_type(echo, bool)
    assert_is_type(test, bool)

    demo_function = getattr(h2odemo, funcname, None)
    if demo_function and type(demo_function) is type(demo):
        demo_function(interactive, echo, test)
    else:
        print("Demo for %s is not available." % funcname)


def load_dataset(relative_path):
    """Imports a data file within the 'h2o_data' folder."""
    assert_is_type(relative_path, str)
    h2o_dir = os.path.split(__file__)[0]
    for possible_file in [os.path.join(h2o_dir, relative_path),
                          os.path.join(h2o_dir, "h2o_data", relative_path),
                          os.path.join(h2o_dir, "h2o_data", relative_path + ".csv")]:
        if os.path.exists(possible_file):
            return upload_file(possible_file)
    # File not found -- raise an error!
    raise H2OValueError("Data file %s cannot be found" % relative_path)


def make_metrics(predicted, actual, domain=None, distribution=None):
    """
    Create Model Metrics from predicted and actual values in H2O.

    :param H2OFrame predicted: an H2OFrame containing predictions.
    :param H2OFrame actuals: an H2OFrame containing actual values.
    :param domain: list of response factors for classification.
    :param distribution: distribution for regression.
    """
    assert_is_type(predicted, H2OFrame)
    assert_is_type(actual, H2OFrame)
    # assert predicted.ncol == 1, "`predicted` frame should have exactly 1 column"
    assert actual.ncol == 1, "`actual` frame should have exactly 1 column"
    assert_is_type(distribution, str, None)
    assert_satisfies(actual.ncol, actual.ncol == 1)
    if domain is None and any(actual.isfactor()):
        domain = actual.levels()[0]
    res = api("POST /3/ModelMetrics/predictions_frame/%s/actuals_frame/%s" % (predicted.frame_id, actual.frame_id),
              data={"domain": domain, "distribution": distribution})
    return res["model_metrics"]


def flow():
    """
    Open H2O Flow in your browser.

    """
    webbrowser.open(connection().base_url, new = 1)


def _put_key(file_path, dest_key=None, overwrite=True):
    """
    Upload given file into DKV and save it under give key as raw object.

    :param dest_key:  name of destination key in DKV
    :param file_path:  path to file to upload
    :return: key name if object was uploaded successfully
    """
    ret = api("POST /3/PutKey?destination_key={}&overwrite={}".format(dest_key if dest_key else '', overwrite),
              filename=file_path)
    return ret["destination_key"]


def _create_zip_file(dest_filename, *content_list):
    from .utils.shared_utils import InMemoryZipArch
    with InMemoryZipArch(dest_filename) as zip_arch:
        for filename, file_content in content_list:
            zip_arch.append(filename, file_content)
    return dest_filename


def _default_source_provider(obj):
    import inspect
    # First try to get source code via inspect
    try:
        return '    '.join(inspect.getsourcelines(obj)[0])
    except (OSError, TypeError):
        # It seems like we are in interactive shell and
        # we do not have access to class source code directly
        # At this point we can:
        # (1) get IPython history and find class definition, or
        # (2) compose body of class from methods, since it is still possible to get
        #     method body
        class_def = "class {}:\n".format(obj.__name__)
        for name, member in inspect.getmembers(obj):
            if inspect.ismethod(member):
                class_def += inspect.getsource(member)
        return class_def

def upload_custom_metric(func, func_file="metrics.py", func_name=None, class_name=None, source_provider=None):
    """
    Upload given metrics function into H2O cluster.

    The metrics can have different representation:
      - method
      - class: needs to inherit from water.udf.CFunc2 and implement method apply(actual, predict)
      returning double
      - string: the same as in class case, but the class is given as a string

    :param func:  metrics representation: string, class, function
    :param func_file:  internal name of file to save given metrics representation
    :param func_name:  name for h2o key under which the given metric is saved
    :param class_name: name of class wrapping the metrics function
    :param source_provider: a function which provides a source code for given function
    :return: reference to uploaded metrics function
    """
    import tempfile
    import inspect

    # Use default source provider
    if not source_provider:
        source_provider = _default_source_provider

    # The template wraps given metrics representation
    _CFUNC_CODE_TEMPLATE = """# Generated code
import water.udf.CMetricFunc as MetricFunc

# User given metric function as a class implementing
# 3 methods defined by interface CMetricFunc
{}

# Generated user metric which satisfies the interface
# of Java MetricFunc
class {}Wrapper({}, MetricFunc, object):
    pass

"""

    assert_satisfies(func, inspect.isclass(func) or isinstance(func, str),
                     "The argument func needs to be string or class !")
    assert_satisfies(func_file, func_file is not None,
                     "The argument func_file is missing!")
    assert_satisfies(func_file, func_file.endswith('.py'),
                     "The argument func_file needs to end with '.py'")
    code = None
    derived_func_name = None
    module_name = func_file[:-3]
    if isinstance(func, str):
        assert_satisfies(class_name, class_name is not None,
                         "The argument class_name is missing! " +
                         "It needs to reference the class in given string!")
        derived_func_name = "metrics_{}".format(class_name)
        code = str
    else:
        assert_satisfies(func, inspect.isclass(func), "The parameter `func` should be str or class")
        for method in ['map', 'reduce', 'metric']:
            assert_satisfies(func, method in func.__dict__, "The class `func` needs to define method `{}`".format(method))

        assert_satisfies(class_name, class_name is None,
                         "If class is specified then class_name parameter needs to be None")

        class_name = "{}.{}Wrapper".format(module_name, func.__name__)
        derived_func_name = "metrics_{}".format(func.__name__)
        code = _CFUNC_CODE_TEMPLATE.format(source_provider(func), func.__name__, func.__name__)

    # If the func name is not given, use whatever we can derived from given definition
    if not func_name:
        func_name = derived_func_name
    # Saved into jar file
    tmpdir = tempfile.mkdtemp(prefix="h2o-func")
    func_arch_file = _create_zip_file("{}/func.jar".format(tmpdir), (func_file, code))
    # Upload into K/V
    dest_key = _put_key(func_arch_file, dest_key=func_name)
    # Reference
    return "python:{}={}".format(dest_key, class_name)


#-----------------------------------------------------------------------------------------------------------------------
# Private
#-----------------------------------------------------------------------------------------------------------------------

def _check_connection():
    if not h2oconn or not h2oconn.cluster:
        raise H2OConnectionError("Not connected to a cluster. Did you run `h2o.connect()`?")

def _connect_with_conf(conn_conf):
    conf = conn_conf
    if isinstance(conn_conf, dict):
        conf = H2OConnectionConf(config=conn_conf)
    assert_is_type(conf, H2OConnectionConf)

    return connect(url = conf.url, verify_ssl_certificates = conf.verify_ssl_certificates,
                   auth = conf.auth, proxy = conf.proxy,cookies = conf.cookies, verbose = conf.verbose)

#-----------------------------------------------------------------------------------------------------------------------
#  ALL DEPRECATED METHODS BELOW
#-----------------------------------------------------------------------------------------------------------------------

# Deprecated since 2015-10-08
@deprecated("Deprecated, use ``h2o.import_file()``.")
def import_frame():
    """Deprecated."""
    import_file()

# Deprecated since 2015-10-08
@deprecated("Deprecated (converted to a private method).")
def parse():
    """Deprecated."""
    pass

# Deprecated since 2016-08-04
@deprecated("Deprecated, use ``h2o.cluster().show_status()``.")
def cluster_info():
    """Deprecated."""
    _check_connection()
    cluster().show_status()

# Deprecated since 2016-08-04
@deprecated("Deprecated, use ``h2o.cluster().show_status(True)``.")
def cluster_status():
    """Deprecated."""
    _check_connection()
    cluster().show_status(True)

# Deprecated since 2016-08-04
@deprecated("Deprecated, use ``h2o.cluster().shutdown()``.")
def shutdown(prompt=False):
    """Deprecated."""
    _check_connection()
    cluster().shutdown(prompt)

# Deprecated since 2016-08-04
@deprecated("Deprecated, use ``h2o.cluster().network_test()``.")
def network_test():
    """Deprecated."""
    _check_connection()
    cluster().network_test()

# Deprecated since 2016-08-04
@deprecated("Deprecated, use ``h2o.cluster().timezone``.")
def get_timezone():
    """Deprecated."""
    _check_connection()
    return cluster().timezone

# Deprecated since 2016-08-04
@deprecated("Deprecated, set ``h2o.cluster().timezone`` instead.")
def set_timezone(value):
    """Deprecated."""
    _check_connection()
    cluster().timezone = value

# Deprecated since 2016-08-04
@deprecated("Deprecated, use ``h2o.cluster().list_timezones()``.")
def list_timezones():
    """Deprecated."""
    _check_connection()
    return cluster().list_timezones()
