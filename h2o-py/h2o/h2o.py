# -*- encoding: utf-8 -*-
"""
h2o -- module for using H2O services.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import os
import subprocess
import tempfile
import warnings
import webbrowser

from .backend import H2OConnection
from .backend import H2OConnectionConf
from .backend import H2OLocalServer
from .base import Keyed
from .estimators import create_estimator
from .estimators.generic import H2OGenericEstimator
from .exceptions import H2OError, H2ODeprecationWarning
from .estimators.gbm import H2OGradientBoostingEstimator
from .estimators.glm import H2OGeneralizedLinearEstimator
from .estimators.xgboost import H2OXGBoostEstimator
from .estimators.infogram import H2OInfogram
from .estimators.deeplearning import H2OAutoEncoderEstimator, H2ODeepLearningEstimator
from .estimators.extended_isolation_forest import H2OExtendedIsolationForestEstimator
from .exceptions import H2OConnectionError, H2OValueError
from .expr import ExprNode
from .frame import H2OFrame
from .grid.grid_search import H2OGridSearch
from .job import H2OJob
from .model.model_base import ModelBase
from .utils.compatibility import *  # NOQA
from .utils.config import H2OConfigReader
from .utils.metaclass import deprecated_fn
from .utils.shared_utils import check_frame_id, gen_header, py_tmp_key, quoted
from .utils.typechecks import assert_is_type, assert_satisfies, BoundInt, BoundNumeric, I, is_type, numeric, U

# enable h2o deprecation warnings by default to ensure that users get notified in interactive mode, without being too annoying
warnings.filterwarnings("once", category=H2ODeprecationWarning)
# An IPython deprecation warning is triggered after h2o.init(). Remove this once the deprecation has been resolved
# warnings.filterwarnings('ignore', category=DeprecationWarning, module='.*/IPython/.*')

h2oconn = None  # type: H2OConnection


def connect(server=None, url=None, ip=None, port=None,
            https=None, verify_ssl_certificates=None, cacert=None,
            auth=None, proxy=None, cookies=None, verbose=True, config=None, strict_version_check=False):
    """
    Connect to an existing H2O server, remote or local.

    There are two ways to connect to a server: either pass a ``server`` parameter containing an instance of
    an H2OLocalServer, or specify ``ip`` and ``port`` of the server that you want to connect to.

    :param server: An H2OLocalServer instance to connect to (optional).
    :param url: Full URL of the server to connect to (can be used instead of ``ip`` + ``port`` + ``https``).
    :param ip: The ip address (or host name) of the server where H2O is running.
    :param port: Port number that H2O service is listening to.
    :param https: Set to True to connect via https:// instead of http://.
    :param verify_ssl_certificates: When using https, setting this to False will disable SSL certificates verification.
    :param cacert: Path to a CA bundle file or a directory with certificates of trusted CAs (optional).
    :param auth: Either a (username, password) pair for basic authentication, an instance of h2o.auth.SpnegoAuth
                 or one of the requests.auth authenticator objects.
    :param proxy: Proxy server address.
    :param cookies: Cookie (or list of) to add to request
    :param verbose: Set to False to disable printing connection status messages.
    :param config: Connection configuration object encapsulating connection parameters.
    :param strict_version_check: If True, an error will be raised if the client and server versions don't match.
    :returns: the new :class:`H2OConnection` object.

    :examples:

    >>> import h2o
    >>> ipA = "127.0.0.1"
    >>> portN = "54321"
    >>> urlS = "http://127.0.0.1:54321"
    >>> connect_type=h2o.connect(ip=ipA, port=portN, verbose=True)
    # or
    >>> connect_type2 = h2o.connect(url=urlS, https=True, verbose=True)

    """
    global h2oconn
    svc = _strict_version_check(strict_version_check, config=config)
    if config:
        if "connect_params" in config:
            h2oconn = _connect_with_conf(config["connect_params"], strict_version_check=svc)
        else:
            h2oconn = _connect_with_conf(config, strict_version_check=svc)
    else:
        h2oconn = H2OConnection.open(server=server, url=url, ip=ip, port=port, https=https,
                                     auth=auth, verify_ssl_certificates=verify_ssl_certificates, cacert=cacert,
                                     proxy=proxy, cookies=cookies,
                                     verbose=verbose, strict_version_check=svc)
        if verbose:
            h2oconn.cluster.show_status()
    return h2oconn


def api(endpoint, data=None, json=None, filename=None, save_to=None):
    """
    Perform a REST API request to a previously connected server.

    This function is mostly for internal purposes, but may occasionally be useful for direct access to
    the backend H2O server. It has same parameters as :meth:`H2OConnection.request <h2o.backend.H2OConnection.request>`.

    The list of available endpoints can be obtained using::
    
        endpoints = [' '.join([r.http_method, r.url_pattern]) for r in h2o.api("GET /3/Metadata/endpoints").routes]
    
    For each route, the available parameters (passed as data or json) can be obtained using::
    
        parameters = {f.name: f.help for f in h2o.api("GET /3/Metadata/schemas/{route.input_schema}").fields}
    
    :examples:

    >>> res = h2o.api("GET /3/NetworkTest")
    >>> res["table"].show()
    """
    # type checks are performed in H2OConnection class
    _check_connection()
    return h2oconn.request(endpoint, data=data, json=json, filename=filename, save_to=save_to)


def connection():
    """Return the current :class:`H2OConnection` handler.

    :examples:

    >>> temp = h2o.connection()
    >>> temp
    """
    return h2oconn


def init(url=None, ip=None, port=None, name=None, https=None, cacert=None, insecure=None, username=None, password=None,
         cookies=None, proxy=None, start_h2o=True, nthreads=-1, ice_root=None, log_dir=None, log_level=None,
         max_log_file_size=None, enable_assertions=True, max_mem_size=None, min_mem_size=None, strict_version_check=None, 
         ignore_config=False, extra_classpath=None, jvm_custom_args=None, bind_to_localhost=True, **kwargs):
    """
    Attempt to connect to a local server, or if not successful start a new server and connect to it.

    :param url: Full URL of the server to connect to (can be used instead of ``ip`` + ``port`` + ``https``).
    :param ip: The ip address (or host name) of the server where H2O is running.
    :param port: Port number that H2O service is listening to.
    :param name: Cluster name. If None while connecting to an existing cluster it will not check the cluster name.
                 If set then will connect only if the target cluster name matches. If no instance is found and decides to start a local
                 one then this will be used as the cluster name or a random one will be generated if set to None.
    :param https: Set to True to connect via https:// instead of http://.
    :param cacert: Path to a CA bundle file or a directory with certificates of trusted CAs (optional).
    :param insecure: When using https, setting this to True will disable SSL certificates verification.
    :param username: Username and
    :param password: Password for basic authentication.
    :param cookies: Cookie (or list of) to add to each request.
    :param proxy: Proxy server address.
    :param start_h2o: If False, do not attempt to start an h2o server when connection to an existing one failed.
    :param nthreads: "Number of threads" option when launching a new h2o server.
    :param ice_root: Directory for temporary files for the new h2o server.
    :param log_dir: Directory for H2O logs to be stored if a new instance is started. Ignored if connecting to an existing node.
    :param log_level: The logger level for H2O if a new instance is started. One of:
    
        - TRACE
        - DEBUG
        - INFO
        - WARN
        - ERRR
        - FATA

        Default is INFO. Ignored if connecting to an existing node.
    :param max_log_file_size: Maximum size of INFO and DEBUG log files. The file is rolled over after a specified size has been reached. (The default is 3MB. Minimum is 1MB and maximum is 99999MB)
    :param enable_assertions: Enable assertions in Java for the new h2o server.
    :param max_mem_size: Maximum memory to use for the new h2o server. Integer input will be evaluated as gigabytes.  Other units can be specified by passing in a string (e.g. "160M" for 160 megabytes).

        - **Note:** If ``max_mem_size`` is not defined, then the amount of memory that H2O allocates will be determined by the default memory of the Java Virtual Machine (JVM). This amount depends on the Java version, but it will generally be 25% of the machine's physical memory.
    :param min_mem_size: Minimum memory to use for the new h2o server. Integer input will be evaluated as gigabytes.  Other units can be specified by passing in a string (e.g. "160M" for 160 megabytes).
    :param strict_version_check: If True, an error will be raised if the client and server versions don't match.
    :param ignore_config: Indicates whether a processing of a .h2oconfig file should be conducted or not. Default value is False.
    :param extra_classpath: List of paths to libraries that should be included on the Java classpath when starting H2O from Python.
    :param kwargs: (all other deprecated attributes)
    :param jvm_custom_args: Customer, user-defined argument's for the JVM H2O is instantiated in. Ignored if there is an instance of H2O already running and the client connects to it.
    :param bind_to_localhost: A flag indicating whether access to the H2O instance should be restricted to the local machine (default) or if it can be reached from other computers on the network.


    :examples:

    >>> import h2o
    >>> h2o.init(ip="localhost", port=54323)

    """
    global h2oconn
    assert_is_type(url, str, None)
    assert_is_type(ip, str, None)
    assert_is_type(port, int, str, None)
    assert_is_type(name, str, None)
    assert_is_type(https, bool, None)
    assert_is_type(insecure, bool, None)
    assert_is_type(username, str, None)
    assert_is_type(password, str, None)
    assert_is_type(cookies, str, [str], None)
    assert_is_type(proxy, {str: str}, None)
    assert_is_type(start_h2o, bool, None)
    assert_is_type(nthreads, int)
    assert_is_type(ice_root, str, None)
    assert_is_type(log_dir, str, None)
    assert_is_type(log_level, str, None)
    assert_satisfies(log_level, log_level in [None, "TRACE", "DEBUG", "INFO", "WARN", "ERRR", "FATA"])
    assert_is_type(enable_assertions, bool)
    assert_is_type(max_mem_size, int, str, None)
    assert_is_type(min_mem_size, int, str, None)
    assert_is_type(strict_version_check, bool, None)
    assert_is_type(extra_classpath, [str], None)
    assert_is_type(jvm_custom_args, [str], None)
    assert_is_type(bind_to_localhost, bool)
    assert_is_type(kwargs, {"proxies": {str: str}, "max_mem_size_GB": int, "min_mem_size_GB": int,
                            "force_connect": bool, "as_port": bool})

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
    svc = _strict_version_check(strict_version_check)
    verify_ssl_certificates = not insecure

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
        svc = _strict_version_check(strict_version_check, config=config)
        # Note: `verify_ssl_certificates` is never None at this point => use `insecure` to check for None/default input)
        if insecure is None and "init.verify_ssl_certificates" in config:
            verify_ssl_certificates = config["init.verify_ssl_certificates"].lower() != "false"
        if cacert is None:
            if "init.cacert" in config:
                cacert = config["init.cacert"]

    assert_is_type(verify_ssl_certificates, bool)

    if not start_h2o:
        print("Warning: if you don't want to start local H2O server, then use of `h2o.connect()` is preferred.")
    try:
        h2oconn = H2OConnection.open(url=url, ip=ip, port=port, name=name, https=https,
                                     verify_ssl_certificates=verify_ssl_certificates, cacert=cacert,
                                     auth=auth, proxy=proxy, cookies=cookies, verbose=True,
                                     msgs=("Checking whether there is an H2O instance running at {url}",
                                           "connected.", "not found."),
                                     strict_version_check=svc)
    except H2OConnectionError:
        # Backward compatibility: in init() port parameter really meant "baseport" when starting a local server...
        if port and not str(port).endswith("+") and not kwargs.get("as_port", False):
            port = str(port) + "+"
        if not start_h2o: raise
        if ip and not (ip == "localhost" or ip == "127.0.0.1"):
            raise H2OConnectionError('Can only start H2O launcher if IP address is localhost.')
        if https:
            raise H2OConnectionError('Starting local server is not available with https enabled. You may start local'
                                     ' instance of H2O with https manually '
                                     '(https://docs.h2o.ai/h2o/latest-stable/h2o-docs/welcome.html#new-user-quick-start).')
        hs = H2OLocalServer.start(nthreads=nthreads, enable_assertions=enable_assertions, max_mem_size=mmax,
                                  min_mem_size=mmin, ice_root=ice_root, log_dir=log_dir, log_level=log_level,
                                  max_log_file_size=max_log_file_size, port=port, name=name,
                                  extra_classpath=extra_classpath, jvm_custom_args=jvm_custom_args,
                                  bind_to_localhost=bind_to_localhost)
        h2oconn = H2OConnection.open(server=hs, https=https, verify_ssl_certificates=verify_ssl_certificates,
                                     cacert=cacert, auth=auth, proxy=proxy, cookies=cookies, verbose=True,
                                     strict_version_check=svc)
    h2oconn.cluster.timezone = "UTC"
    h2oconn.cluster.show_status()


def resume(recovery_dir=None):
    """
    Triggers auto-recovery resume - this will look into configured recovery dir and resume and
    tasks that were interrupted by unexpected cluster stopping.

    :param recovery_dir: A path to where cluster recovery data is stored, if blank, will use cluster's configuration.
    """

    params = {
        "recovery_dir": recovery_dir
    }
    api(endpoint="POST /3/Recovery/resume", data=params)


def lazy_import(path, pattern=None):
    """
    Import a single file or collection of files.

    :param path: A path to a data file (remote or local).
    :param pattern: Character string containing a regular expression to match file(s) in the folder.
    :returns: either a :class:`H2OFrame` with the content of the provided file, or a list of such frames if
        importing multiple files.

    :examples:

    >>> iris = h2o.lazy_import("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    
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
                na_strings=None, skipped_columns=None, quotechar=None, escapechar=None):
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
          list of date time formats:

              - "yyyy-MM-dd" (date),
              - "yyyy MM dd" (date),
              - "dd-MMM-yy" (date),
              - "dd MMM yy" (date).
              - "HH:mm:ss" (time),
              - "HH:mm:ss:SSS" (time),
              - "HH:mm:ss:SSSnnnnnn" (time),
              - "HH.mm.ss" (time),
              - "HH.mm.ss.SSS" (time),
              - "HH.mm.ss.SSSnnnnnn" (time).
              
          Times can also contain "AM" or "PM".      
    :param na_strings: A list of strings, or a list of lists of strings (one list per column), or a dictionary
        of column names to strings which are to be interpreted as missing values.
    :param skipped_columns: an integer lists of column indices to skip and not parsed into the final frame from the import file.
    :param quotechar: A hint for the parser which character to expect as quoting character. Only single quote, double quote or None (default) are allowed. None means automatic detection.
    :param escapechar: (Optional) One ASCII character used to escape other characters.

    :returns: a new :class:`H2OFrame` instance.

    :examples:
    
    >>> iris_df = h2o.upload_file("~/Desktop/repos/h2o-3/smalldata/iris/iris.csv")
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
    assert_is_type(quotechar, None, U("'", '"'))
    assert (skipped_columns==None) or isinstance(skipped_columns, list), \
        "The skipped_columns should be an list of column names!"
    assert_is_type(escapechar, None, I(str, lambda s: len(s) == 1))

    check_frame_id(destination_frame)
    if path.startswith("~"):
        path = os.path.expanduser(path)
    return H2OFrame()._upload_parse(path, destination_frame, header, sep, col_names, col_types, na_strings, skipped_columns,
                                    quotechar, escapechar)


def import_file(path=None, destination_frame=None, parse=True, header=0, sep=None, col_names=None, col_types=None,
                na_strings=None, pattern=None, skipped_columns=None, custom_non_data_line_markers=None,
                partition_by=None, quotechar=None, escapechar=None):
    """
    Import a dataset that is already on the cluster.

    The path to the data must be a valid path for each node in the H2O cluster. If some node in the H2O cluster
    cannot see the file, then an exception will be thrown by the H2O cluster. Does a parallel/distributed
    multi-threaded pull of the data. The main difference between this method and :func:`upload_file` is that
    the latter works with local files, whereas this method imports remote files (i.e. files local to the server).
    If you running H2O server on your own machine, then both methods behave the same.

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
    :param partition_by: Names of the column the persisted dataset has been partitioned by.

        - "unknown" - this will force the column to be parsed as all NA
        - "uuid"    - the values in the column must be true UUID or will be parsed as NA
        - "string"  - force the column to be parsed as a string
        - "numeric" - force the column to be parsed as numeric. H2O will handle the compression of the numeric
          data in the optimal manner.
        - "enum"    - force the column to be parsed as a categorical column.
        - "time"    - force the column to be parsed as a time column. H2O will attempt to parse the following
          list of date time formats:
          
              - "yyyy-MM-dd" (date),
              - "yyyy MM dd" (date),
              - "dd-MMM-yy" (date),
              - "dd MMM yy" (date), 
              - "HH:mm:ss" (time),
              - "HH:mm:ss:SSS" (time),
              - "HH:mm:ss:SSSnnnnnn" (time),
              - "HH.mm.ss" (time),
              - "HH.mm.ss.SSS" (time),
              - "HH.mm.ss.SSSnnnnnn"(time).
              
          Times can also contain "AM" or "PM".
    :param na_strings: A list of strings, or a list of lists of strings (one list per column), or a dictionary
        of column names to strings which are to be interpreted as missing values.
    :param pattern: Character string containing a regular expression to match file(s) in the folder if `path` is a
        directory.  
    :param skipped_columns: an integer list of column indices to skip and not parsed into the final frame from the import file.
    :param custom_non_data_line_markers: If a line in imported file starts with any character in given string it will NOT be imported. Empty string means all lines are imported, None means that default behaviour for given format will be used
    :param quotechar: A hint for the parser which character to expect as quoting character. Only single quote, double quote or None (default) are allowed. None means automatic detection.
    :param escapechar: (Optional) One ASCII character used to escape other characters.

    :returns: a new :class:`H2OFrame` instance.

    :examples:
    
    >>> birds = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/birds.csv")
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
    assert_is_type(partition_by, None, [str], str)
    assert_is_type(quotechar, None, U("'", '"'))
    assert_is_type(escapechar, None, I(str, lambda s: len(s) == 1))
    assert isinstance(skipped_columns, (type(None), list)), "The skipped_columns should be an list of column names!"
    check_frame_id(destination_frame)
    patharr = path if isinstance(path, list) else [path]
    if any(os.path.split(p)[0] == "~" for p in patharr):
        raise H2OValueError("Paths relative to a current user (~) are not valid in the server environment. "
                            "Please use absolute paths if possible.")
    if not parse:
        return lazy_import(path, pattern)
    else:
        return H2OFrame()._import_parse(path, pattern, destination_frame, header, sep, col_names, col_types, na_strings,
                                        skipped_columns, custom_non_data_line_markers, partition_by, quotechar, escapechar)


def load_grid(grid_file_path, load_params_references=False):
    """
    Loads previously saved grid with all its models from the same folder

    :param grid_file_path: A string containing the path to the file with grid saved.
     Grid models are expected to be in the same folder.
    :param load_params_references: If true will attemt to reload saved objects referenced by grid parameters
      (e.g. training frame, calibration frame), will fail if grid was saved without referenced objects.

    :return: An instance of H2OGridSearch

    :examples:

    >>> from collections import OrderedDict
    >>> from h2o.grid.grid_search import H2OGridSearch
    >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
    >>> train = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    # Run GBM Grid Search
    >>> ntrees_opts = [1, 3]
    >>> learn_rate_opts = [0.1, 0.01, .05]
    >>> hyper_parameters = OrderedDict()
    >>> hyper_parameters["learn_rate"] = learn_rate_opts
    >>> hyper_parameters["ntrees"] = ntrees_opts
    >>> export_dir = pyunit_utils.locate("results")
    >>> gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters)
    >>> gs.train(x=list(range(4)), y=4, training_frame=train)
    >>> grid_id = gs.grid_id
    >>> old_grid_model_count = len(gs.model_ids)
    # Save the grid search to the export directory
    >>> saved_path = h2o.save_grid(export_dir, grid_id)
    >>> h2o.remove_all();
    >>> train = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    # Load the grid searcht-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    >>> grid = h2o.load_grid(saved_path)
    >>> grid.train(x=list(range(4)), y=4, training_frame=train)
    """

    assert_is_type(grid_file_path, str)
    response = api(
        "POST /3/Grid.bin/import", 
        {"grid_path": grid_file_path, "load_params_references": load_params_references}
    )
    return get_grid(response["name"])


def save_grid(grid_directory, grid_id, save_params_references=False, export_cross_validation_predictions=False):
    """
    Export a Grid and it's all its models into the given folder

    :param grid_directory: A string containing the path to the folder for the grid to be saved to.
    :param grid_id: A character string with identification of the Grid in H2O.
    :param save_params_references: True if objects referenced by grid parameters
      (e.g. training frame, calibration frame) should also be saved. 
    :param export_cross_validation_predictions: A boolean flag indicating whether the models exported from the grid 
      should be saved with CV Holdout Frame predictions. Default is not to export the predictions.

    :examples:

    >>> from collections import OrderedDict
    >>> from h2o.grid.grid_search import H2OGridSearch
    >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
    >>> train = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    # Run GBM Grid Search
    >>> ntrees_opts = [1, 3]
    >>> learn_rate_opts = [0.1, 0.01, .05]
    >>> hyper_parameters = OrderedDict()
    >>> hyper_parameters["learn_rate"] = learn_rate_opts
    >>> hyper_parameters["ntrees"] = ntrees_opts
    >>> export_dir = pyunit_utils.locate("results")
    >>> gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters)
    >>> gs.train(x=list(range(4)), y=4, training_frame=train)
    >>> grid_id = gs.grid_id
    >>> old_grid_model_count = len(gs.model_ids)
    # Save the grid search to the export directory
    >>> saved_path = h2o.save_grid(export_dir, grid_id)
    >>> h2o.remove_all();
    >>> train = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    # Load the grid search
    >>> grid = h2o.load_grid(saved_path)
    >>> grid.train(x=list(range(4)), y=4, training_frame=train)
    """
    assert_is_type(grid_directory, str)
    assert_is_type(grid_id, str)
    assert_is_type(save_params_references, bool)
    assert_is_type(export_cross_validation_predictions, bool)
    params = {
        "grid_directory": grid_directory,
        "save_params_references": save_params_references,
        "export_cross_validation_predictions": export_cross_validation_predictions
    }
    api("POST /3/Grid.bin/" + grid_id + "/export", params)
    return grid_directory + "/" + grid_id


def import_hive_table(database=None, table=None, partitions=None, allow_multi_format=False):
    """
    Import Hive table to H2OFrame in memory.

    Make sure to start H2O with Hive on classpath. Uses hive-site.xml on classpath to connect to Hive.
    When database is specified as jdbc URL uses Hive JDBC driver to obtain table metadata. then
    uses direct HDFS access to import data.

    :param database: Name of Hive database (default database will be used by default), can be also a JDBC URL.
    :param table: name of Hive table to import
    :param partitions: a list of lists of strings - partition key column values of partitions you want to import.
    :param allow_multi_format: enable import of partitioned tables with different storage formats used. WARNING:
        this may fail on out-of-memory for tables with a large number of small partitions.

    :returns: an :class:`H2OFrame` containing data of the specified Hive table.

    :examples:
    
    >>> basic_import = h2o.import_hive_table("default",
    ...                                      "table_name")
    >>> jdbc_import = h2o.import_hive_table("jdbc:hive2://hive-server:10000/default",
    ...                                      "table_name")
    >>> multi_format_enabled = h2o.import_hive_table("default",
    ...                                              "table_name",
    ...                                              allow_multi_format=True)
    >>> with_partition_filter = h2o.import_hive_table("jdbc:hive2://hive-server:10000/default",
    ...                                               "table_name",
    ...                                               [["2017", "02"]])
    """
    assert_is_type(database, str, None)
    assert_is_type(table, str)
    assert_is_type(partitions, [[str]], None)
    p = { "database": database, "table": table, "partitions": partitions, "allow_multi_format": allow_multi_format }
    j = H2OJob(api("POST /3/ImportHiveTable", data=p), "Import Hive Table").poll()
    return get_frame(j.dest_key)


def import_sql_table(connection_url, table, username, password, columns=None, optimize=True, 
                     fetch_mode=None, num_chunks_hint=None):
    """
    Import SQL table to H2OFrame in memory.

    Assumes that the SQL table is not being updated and is stable.
    Runs multiple SELECT SQL queries concurrently for parallel ingestion.
    Be sure to start the h2o.jar in the terminal with your downloaded JDBC driver in the classpath::

        java -cp <path_to_h2o_jar>:<path_to_jdbc_driver_jar> water.H2OApp

    Also see :func:`import_sql_select`.
    Currently supported SQL databases are MySQL, PostgreSQL, MariaDB, Hive, Oracle and Microsoft SQL.

    :param connection_url: URL of the SQL database connection as specified by the Java Database Connectivity (JDBC)
        Driver. For example::

            "jdbc:mysql://localhost:3306/menagerie?&useSSL=false"
            
    :param table: name of SQL table
    :param columns: a list of column names to import from SQL table. Default is to import all columns.
    :param username: username for SQL server
    :param password: password for SQL server
    :param optimize: DEPRECATED. Ignored - use ``fetch_mode`` instead. Optimize import of SQL table for faster imports.
    :param fetch_mode: Set to DISTRIBUTED to enable distributed import. Set to SINGLE to force a sequential read by a single node
        from the database.
    :param num_chunks_hint: Desired number of chunks for the target Frame.

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
    assert_is_type(fetch_mode, str, None)
    assert_is_type(num_chunks_hint, int, None)
    p = {"connection_url": connection_url, "table": table, "username": username, "password": password,
         "fetch_mode": fetch_mode, "num_chunks_hint": num_chunks_hint}
    if columns:
        p["columns"] = ", ".join(columns)
    j = H2OJob(api("POST /99/ImportSQLTable", data=p), "Import SQL Table").poll()
    return get_frame(j.dest_key)


def import_sql_select(connection_url, select_query, username, password, optimize=True,
                      use_temp_table=None, temp_table_name=None, fetch_mode=None, num_chunks_hint=None):
    """
    Import the SQL table that is the result of the specified SQL query to H2OFrame in memory.

    Creates a temporary SQL table from the specified sql_query.
    Runs multiple SELECT SQL queries on the temporary table concurrently for parallel ingestion, then drops the table.
    Be sure to start the h2o.jar in the terminal with your downloaded JDBC driver in the classpath::

      java -cp <path_to_h2o_jar>:<path_to_jdbc_driver_jar> water.H2OApp

    Also see ``h2o.import_sql_table``. Currently supported SQL databases are MySQL, PostgreSQL, MariaDB, Hive, Oracle 
    and Microsoft SQL Server.

    :param connection_url: URL of the SQL database connection as specified by the Java Database Connectivity (JDBC)
        Driver. For example, "jdbc:mysql://localhost:3306/menagerie?&useSSL=false"
    :param select_query: SQL query starting with `SELECT` that returns rows from one or more database tables.
    :param username: username for SQL server
    :param password: password for SQL server
    :param optimize: DEPRECATED. Ignored - use ``fetch_mode`` instead. Optimize import of SQL table for faster imports.
    :param use_temp_table: whether a temporary table should be created from ``select_query``
    :param temp_table_name: name of temporary table to be created from ``select_query``
    :param fetch_mode: Set to DISTRIBUTED to enable distributed import. Set to SINGLE to force a sequential read by a single node
        from the database.
    :param num_chunks_hint: Desired number of chunks for the target Frame.

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
    assert_is_type(use_temp_table, bool, None)
    assert_is_type(temp_table_name, str, None)
    assert_is_type(fetch_mode, str, None)
    assert_is_type(num_chunks_hint, int, None)
    p = {"connection_url": connection_url, "select_query": select_query, "username": username, "password": password,
         "use_temp_table": use_temp_table, "temp_table_name": temp_table_name, "fetch_mode": fetch_mode,
         "num_chunks_hint": num_chunks_hint}
    j = H2OJob(api("POST /99/ImportSQLTable", data=p), "Import SQL Table").poll()
    return get_frame(j.dest_key)


def parse_setup(raw_frames, destination_frame=None, header=0, separator=None, column_names=None,
                column_types=None, na_strings=None, skipped_columns=None, custom_non_data_line_markers=None,
                partition_by=None, quotechar=None, escapechar=None):
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
    :param column_names: A list of column names for the file. If ``skipped_columns`` are specified, only list column names
         of columns that are not skipped.
    :param column_types: A list of types or a dictionary of column names to types to specify whether columns
        should be forced to a certain type upon import parsing. If a list, the types for elements that are
        one will be guessed. If ``skipped_columns`` are specified, only list column types of columns that are not skipped.
        The possible types a column may have are:

        - "unknown" - this will force the column to be parsed as all NA
        - "uuid"    - the values in the column must be true UUID or will be parsed as NA
        - "string"  - force the column to be parsed as a string
        - "numeric" - force the column to be parsed as numeric. H2O will handle the compression of the numeric
          data in the optimal manner.
        - "enum"    - force the column to be parsed as a categorical column.
        - "time"    - force the column to be parsed as a time column. H2O will attempt to parse the following
          list of date time formats:

          - "yyyy-MM-dd" (date),
          - "yyyy MM dd" (date),
          - "dd-MMM-yy" (date),
          - "dd MMM yy" (date), 
          - "HH:mm:ss" (time),
          - "HH:mm:ss:SSS" (time),
          - "HH:mm:ss:SSSnnnnnn" (time),
          - "HH.mm.ss" (time),
          - "HH.mm.ss.SSS" (time),
          - "HH.mm.ss.SSSnnnnnn" (time).
          
          Times can also contain "AM" or "PM".

    :param na_strings: A list of strings, or a list of lists of strings (one list per column), or a dictionary
        of column names to strings which are to be interpreted as missing values.
    :param skipped_columns: an integer lists of column indices to skip and not parsed into the final frame from the import file.
    :param custom_non_data_line_markers: If a line in imported file starts with any character in given string it will NOT be imported. Empty string means all lines are imported, None means that default behaviour for given format will be used
    :param partition_by: A list of columns the dataset has been partitioned by. None by default.
    :param quotechar: A hint for the parser which character to expect as quoting character. Only single quote, double quote or None (default) are allowed. None means automatic detection.
    :param escapechar: (Optional) One ASCII character used to escape other characters.

    :returns: a dictionary containing parse parameters guessed by the H2O backend.

    :examples:

    >>> col_headers = ["ID","CAPSULE","AGE","RACE",
    ...                "DPROS","DCAPS","PSA","VOL","GLEASON"]
    >>> col_types=['enum','enum','numeric','enum',
    ...            'enum','enum','numeric','numeric','numeric']
    >>> hex_key = "training_data.hex"
    >>> fraw = h2o.import_file(("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"),
    ...                         parse=False)
    >>> setup = h2o.parse_setup(fraw,
    ...                         destination_frame=hex_key,
    ...                         header=1,
    ...                         separator=',',
    ...                         column_names=col_headers,
    ...                         column_types=col_types,
    ...                         na_strings=["NA"])
    >>> setup
    """
    coltype = U(None, "unknown", "uuid", "string", "float", "real", "double", "int", "long", "numeric",
                "categorical", "factor", "enum", "time")
    natype = U(str, [str])
    assert_is_type(raw_frames, str, [str])
    assert_is_type(destination_frame, None, str)
    assert_is_type(header, -1, 0, 1)
    assert_is_type(separator, None, I(str, lambda s: len(s) == 1))
    assert_is_type(column_names, [str], None)
    assert_is_type(column_types, [coltype], {str: coltype}, None)
    assert_is_type(na_strings, [natype], {str: natype}, None)
    assert_is_type(partition_by, None, [str], str)
    assert_is_type(quotechar, None, U("'", '"'))
    assert_is_type(escapechar, None, I(str, lambda s: len(s) == 1))
    check_frame_id(destination_frame)

    # The H2O backend only accepts things that are quoted
    if is_type(raw_frames, str): raw_frames = [raw_frames]

    # temporary dictionary just to pass the following information to the parser: header, separator
    kwargs = {"check_header": header, "source_frames": [quoted(frame_id) for frame_id in raw_frames],
              "single_quotes": quotechar == "'"}
    if separator:
        kwargs["separator"] = ord(separator)
      
    if escapechar:
        kwargs["escapechar"] = ord(escapechar)

    if custom_non_data_line_markers is not None:
        kwargs["custom_non_data_line_markers"] = custom_non_data_line_markers
    if partition_by is not None:
        kwargs["partition_by"] = partition_by

    j = api("POST /3/ParseSetup", data=kwargs)
    if "warnings" in j and j["warnings"]:
        for w in j["warnings"]:
            warnings.warn(w)
    # TODO: really should be url encoding...
    if destination_frame:
        j["destination_frame"] = destination_frame

    parse_column_len = len(j["column_types"]) if skipped_columns is None else (len(j["column_types"])-len(skipped_columns))
    temp_column_names = j["column_names"] if j["column_names"] is not None else gen_header(j["number_columns"])
    use_type = [True]*len(temp_column_names)
    if skipped_columns is not None:
        use_type = [True]*len(temp_column_names)

        for ind in range(len(temp_column_names)):
            if ind in skipped_columns:
                use_type[ind]=False

    if column_names is not None:
        if not isinstance(column_names, list): raise ValueError("col_names should be a list")
        if (skipped_columns is not None) and len(skipped_columns)>0:
            if (len(column_names)) != parse_column_len:
                raise ValueError(
                    "length of col_names should be equal to the number of columns parsed: %d vs %d"
                    % (len(column_names), parse_column_len))
        else:
            if len(column_names) != len(j["column_types"]): raise ValueError(
                "length of col_names should be equal to the number of columns: %d vs %d"
                % (len(column_names), len(j["column_types"])))
        j["column_names"] = column_names
        counter = 0
        for ind in range(len(temp_column_names)):
            if use_type[ind]:
                temp_column_names[ind]=column_names[counter]
                counter = counter+1

    if column_types is not None: # keep the column types to include all columns
        if isinstance(column_types, dict):
            # overwrite dictionary to ordered list of column types. if user didn't specify column type for all names,
            # use type provided by backend
            if j["column_names"] is None:  # no colnames discovered! (C1, C2, ...)
                j["column_names"] = gen_header(j["number_columns"])
            if not set(column_types.keys()).issubset(set(j["column_names"])): raise ValueError(
                "names specified in col_types is not a subset of the column names")
            idx = 0
            column_types_list = []

            for name in temp_column_names: # column_names may have already been changed
                if name in column_types:
                    column_types_list.append(column_types[name])
                else:
                    column_types_list.append(j["column_types"][idx])
                idx += 1

            column_types = column_types_list

        elif isinstance(column_types, list):
            if len(column_types) != parse_column_len: raise ValueError(
                "length of col_types should be equal to the number of parsed columns")
            # need to expand it out to all columns, not just the parsed ones
            column_types_list = j["column_types"]
            counter = 0
            for ind in range(len(j["column_types"])):
                if use_type[ind] and column_types[counter] is not None:
                    column_types_list[ind]=column_types[counter]
                    counter=counter+1

            column_types = column_types_list
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

    if skipped_columns is not None:
        if isinstance(skipped_columns, list):
            j["skipped_columns"] = []
            for colidx in skipped_columns:
                if (colidx < 0): raise ValueError("skipped column index cannot be negative")
                j["skipped_columns"].append(colidx)

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

    :examples:

    >>> fraw = h2o.import_file(("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"),
    ...                         parse=False)
    >>> fhex = h2o.parse_raw(h2o.parse_setup(fraw),
    ...                      id='prostate.csv',
    ...                      first_line_is_header=0)
    >>> fhex.summary()
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

    :examples:

    >>> old_name = "prostate.csv"
    >>> new_name = "newProstate.csv"
    >>> training_data = h2o.import_file(("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"),
    ...                                  destination_frame=old_name)
    >>> temp=h2o.assign(training_data, new_name)

    """
    assert_is_type(data, H2OFrame)
    assert_is_type(xid, str)
    assert_satisfies(xid, xid != data.frame_id)
    check_frame_id(xid)
    data._ex = ExprNode("assign", xid, data)._eval_driver(None)
    data._ex._cache._id = xid
    data._ex._children = None
    return data


def deep_copy(data, xid):
    """
    Create a deep clone of the frame ``data``.

    :param data: an H2OFrame to be cloned
    :param xid: (internal) id to be assigned to the new frame.
    :returns: new :class:`H2OFrame` which is the clone of the passed frame.

    :examples:

    >>> training_data = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
    >>> new_name = "new_frame"
    >>> training_copy = h2o.deep_copy(training_data, new_name)
    >>> training_copy
    """
    assert_is_type(data, H2OFrame)
    assert_is_type(xid, str)
    assert_satisfies(xid, xid != data.frame_id)
    check_frame_id(xid)
    duplicate = data.apply(lambda x: x)
    duplicate._ex = ExprNode("assign", xid, duplicate)._eval_driver(None)
    duplicate._ex._cache._id = xid
    duplicate._ex._children = None
    return duplicate


def models():
    """
    Retrieve the IDs all the Models.

    :returns: Handles of all the models present in the cluster

    :examples:

    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> airlines["Year"]= airlines["Year"].asfactor()
    >>> airlines["Month"]= airlines["Month"].asfactor()
    >>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
    >>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
    >>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
    >>> model1 = H2OGeneralizedLinearEstimator(family="binomial")
    >>> model1.train(y=response, training_frame=airlines)
    >>> model2 = H2OXGBoostEstimator(family="binomial")
    >>> model2.train(y=response, training_frame=airlines)
    >>> model_list = h2o.get_models()
    """
    return [json["model_id"]["name"] for json in api("GET /3/Models")["models"]]


def get_model(model_id):
    """
    Load a model from the server.

    :param model_id: The model identification in H2O

    :returns: Model object, a subclass of H2OEstimator

    :examples:

    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> airlines["Year"]= airlines["Year"].asfactor()
    >>> airlines["Month"]= airlines["Month"].asfactor()
    >>> airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
    >>> airlines["Cancelled"] = airlines["Cancelled"].asfactor()
    >>> airlines['FlightNum'] = airlines['FlightNum'].asfactor()
    >>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
    ...               "DayOfWeek", "Month", "Distance", "FlightNum"]
    >>> response = "IsDepDelayed"
    >>> model = H2OGeneralizedLinearEstimator(family="binomial",
    ...                                       alpha=0,
    ...                                       Lambda=1e-5)
    >>> model.train(x=predictors,
    ...             y=response,
    ...             training_frame=airlines)
    >>> model2 = h2o.get_model(model.model_id)
    """
    assert_is_type(model_id, str)
    model_json = api("GET /3/Models/%s" % model_id)["models"][0]
    algo = model_json["algo"]
    # still some special handling for AutoEncoder: would be cleaner if we could get rid of this
    if algo == 'deeplearning' and model_json["output"]["model_category"] == "AutoEncoder":
        algo = 'autoencoder'

    m = create_estimator(algo)
    m._resolve_model(model_id, model_json)
    return m


def get_grid(grid_id):
    """
    Return the specified grid.

    :param grid_id: The grid identification in h2o

    :returns: an :class:`H2OGridSearch` instance.

    :examples:

    >>> from h2o.grid.grid_search import H2OGridSearch
    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> x = ["DayofMonth", "Month"]
    >>> hyper_parameters = {'learn_rate':[0.1,0.2],
    ...                     'max_depth':[2,3],
    ...                     'ntrees':[5,10]}
    >>> search_crit = {'strategy': "RandomDiscrete",
    ...                'max_models': 5,
    ...                'seed' : 1234,
    ...                'stopping_metric' : "AUTO",
    ...                'stopping_tolerance': 1e-2}
    >>> air_grid = H2OGridSearch(H2OGradientBoostingEstimator,
    ...                          hyper_params=hyper_parameters,
    ...                          search_criteria=search_crit)
    >>> air_grid.train(x=x,
    ...                y="IsDepDelayed",
    ...                training_frame=airlines,
    ...                distribution="bernoulli")
    >>> fetched_grid = h2o.get_grid(str(air_grid.grid_id))
    >>> fetched_grid
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
    Obtain a handle to the frame in H2O with the ``frame_id`` key.

    :param str frame_id: id of the frame to retrieve.
    :returns: an :class:`H2OFrame` object

    :examples:

    >>> from h2o.frame import H2OFrame
    >>> frame1 = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/titanic.csv")
    >>> frame2 = h2o.get_frame(frame1.frame_id)
    
    """
    assert_is_type(frame_id, str)
    return H2OFrame.get_frame(frame_id, **kwargs)


def no_progress():
    """
    Disable the progress bar from flushing to stdout.

    The completed progress bar is printed when a job is complete so as to demarcate a log file.

    :examples:

    >>> h2o.no_progress()
    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> x = ["DayofMonth", "Month"]
    >>> model = H2OGeneralizedLinearEstimator(family="binomial",
    ...                                       alpha=0,
    ...                                       Lambda=1e-5)
    >>> model.train(x=x, y="IsDepDelayed", training_frame=airlines)  
    """
    H2OJob.__PROGRESS_BAR__ = False


def show_progress():
    """Enable the progress bar (it is enabled by default).

    :examples:

    >>> h2o.no_progress()
    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> x = ["DayofMonth", "Month"]
    >>> model = H2OGeneralizedLinearEstimator(family="binomial",
    ...                                       alpha=0,
    ...                                       Lambda=1e-5)
    >>> model.train(x=x, y="IsDepDelayed", training_frame=airlines)
    >>> h2o.show_progress()
    >>> model.train(x=x, y="IsDepDelayed", training_frame=airlines)
    """
    H2OJob.__PROGRESS_BAR__ = True


def enable_expr_optimizations(flag):
    """Enable expression tree local optimizations.

    :examples:

    >>> h2o.enable_expr_optimizations(True)
    """
    ExprNode.__ENABLE_EXPR_OPTIMIZATIONS__ = flag


def is_expr_optimizations_enabled():
    """

    :examples:

    >>> h2o.enable_expr_optimizations(True)
    >>> h2o.is_expr_optimizations_enabled()
    >>> h2o.enable_expr_optimizations(False)
    >>> h2o.is_expr_optimizations_enabled()
    """
    return ExprNode.__ENABLE_EXPR_OPTIMIZATIONS__


def log_and_echo(message=""):
    """
    Log a message on the server-side logs.

    This is helpful when running several pieces of work one after the other on a single H2O
    cluster and you want to make a notation in the H2O server side log where one piece of
    work ends and the next piece of work begins.

    Sends a message to H2O for logging. Generally used for debugging purposes.

    :param message: message to write to the log.

    :examples:

    >>> ret = h2o.log_and_echo("Testing h2o.log_and_echo")
    """
    assert_is_type(message, str)
    api("POST /3/LogAndEcho", data={"message": str(message)})


def remove(x, cascade=True):
    """
    Remove object(s) from H2O.

    :param x: H2OFrame, H2OEstimator, or string, or a list of those things: the object(s) or unique id(s)
        pointing to the object(s) to be removed.
    :param cascade: boolean, if set to TRUE (default), the object dependencies (e.g. submodels) are also removed.

    :examples:

    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> h2o.remove(airlines)
    >>> airlines
    # Should receive error: "This H2OFrame has been removed."
    """
    item_type = U(str, Keyed)
    assert_is_type(x, item_type, [item_type])
    if not isinstance(x, list): x = [x]
    for xi in x:
        if isinstance(xi, H2OFrame):
            if xi.key is None: return  # Lazy frame, never evaluated, nothing in cluster
            rapids("(rm {})".format(xi.key))
            xi.detach()
        elif isinstance(xi, Keyed):
            api("DELETE /3/DKV/%s" % xi.key, data=dict(cascade=cascade))
            xi.detach()
        else:
            # string may be a Frame key name part of a rapids session... need to call rm thru rapids here
            try:
                rapids("(rm {})".format(xi))
            except:
                api("DELETE /3/DKV/%s" % xi, data=dict(cascade=cascade))


def remove_all(retained=None):
    """
    Removes all objects from H2O with possibility to specify models and frames to retain.
    Retained keys must be keys of models and frames only. For models retained, training and validation frames are retained as well.
    Cross validation models of a retained model are NOT retained automatically, those must be specified explicitely.
    
    :param retained: Keys of models and frames to retain

    :examples:

    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> gbm = H2OGradientBoostingEstimator(ntrees = 1)
    >>> gbm.train(x = ["Origin", "Dest"],
    ...           y = "IsDepDelayed",
    ...           training_frame=airlines)
    >>> h2o.remove_all([airlines.frame_id,
    ...                 gbm.model_id])
    """

    params = {"retained_keys": retained}
    api(endpoint="DELETE /3/DKV", data=params)


def rapids(expr):
    """
    Execute a Rapids expression.

    :param expr: The rapids expression (ascii string).

    :returns: The JSON response (as a python dictionary) of the Rapids execution.

    :examples:

    >>> rapidTime = h2o.rapids("(getTimeZone)")["string"]
    >>> print(str(rapidTime))
    """
    assert_is_type(expr, str)
    return ExprNode.rapids(expr)


def ls():
    """List keys on an H2O Cluster.

    :examples:

    >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    >>> h2o.ls()
    """
    return H2OFrame._expr(expr=ExprNode("ls")).as_data_frame(use_pandas=True)


def frame(frame_id):
    """
    Retrieve metadata for an id that points to a Frame.

    :param frame_id: the key of a Frame in H2O.

    :returns: dict containing the frame meta-information.

    :examples:

    >>> training_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv")
    >>> frame_summary = h2o.frame(training_data.frame_id)
    >>> frame_summary
    """
    assert_is_type(frame_id, str)
    return api("GET /3/Frames/%s" % frame_id)


def frames():
    """
    Retrieve all the Frames.

    :returns: Meta information on the frames

    :examples:

    >>> arrestsH2O = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv")
    >>> h2o.frames()
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

    :examples:

    >>> h2o_df = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
    >>> h2o_df['CAPSULE'] = h2o_df['CAPSULE'].asfactor()
    >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
    >>> binomial_fit = H2OGeneralizedLinearEstimator(family = "binomial")
    >>> binomial_fit.train(y = "CAPSULE",
    ...                    x = ["AGE", "RACE", "PSA", "GLEASON"],
    ...                    training_frame = h2o_df)
    >>> h2o.download_pojo(binomial_fit, path='', get_jar=False)
    """
    assert_is_type(model, ModelBase)
    assert_is_type(path, str)
    assert_is_type(get_jar, bool)

    if not model.have_pojo:
        raise H2OValueError("Export to POJO not supported")

    path = str(os.path.join(path, ''))
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

    :examples:

    >>> iris = h2o.load_dataset("iris")
    >>> h2o.download_csv(iris, "iris_delete.csv")
    >>> iris2 = h2o.import_file("iris_delete.csv")
    >>> iris2 = h2o.import_file("iris_delete.csv")
    """
    assert_is_type(data, H2OFrame)
    assert_is_type(filename, str)
    return api("GET /3/DownloadDataset?frame_id=%s&hex_string=false" % data.frame_id, save_to=filename)


def download_all_logs(dirname=".", filename=None, container=None):
    """
    Download H2O log files to disk.

    :param dirname: a character string indicating the directory that the log file should be saved in.
    :param filename: a string indicating the name that the CSV file should be.
                     Note that the default container format is .zip, so the file name must include the .zip extension.
    :param container: a string indicating how to archive the logs, choice of "ZIP" (default) and "LOG":
    
                      - ZIP: individual log files archived in a ZIP package
                      - LOG: all log files will be concatenated together in one text file
                      
    :returns: path of logs written in a zip file.

    :examples: The following code will save the zip file `'h2o_log.zip'` in a directory that is one down from where you are currently working into a directory called `your_directory_name`. (Please note that `your_directory_name` should be replaced with the name of the directory that you've created and that already exists.)

    >>> h2o.download_all_logs(dirname='./your_directory_name/', filename = 'h2o_log.zip')

    """
    assert_is_type(dirname, str)
    assert_is_type(filename, str, None)
    assert_is_type(container, "ZIP", "LOG", None)
    type = "/%s" % container if container else ""

    def save_to(resp):
        path = os.path.join(dirname, filename if filename else h2oconn.save_to_detect(resp))
        print("Writing H2O logs to " + path)
        return path

    return api("GET /3/Logs/download%s" % type, save_to=save_to)


def save_model(model, path="", force=False, export_cross_validation_predictions=False, filename=None):
    """
    Save an H2O Model object to disk. (Note that ensemble binary models can now be saved using this method.)
    The owner of the file saved is the user by which H2O cluster was executed.

    :param model: The model object to save.
    :param path: a path to save the model at (hdfs, s3, local)
    :param force: if True overwrite destination directory in case it exists, or throw exception if set to False.
    :param export_cross_validation_predictions: logical, indicates whether the exported model
        artifact should also include CV Holdout Frame predictions.  Default is not to export the predictions.
    :param filename: a filename for the saved model

    :returns: the path of the saved model

    :examples:
        
    >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
    >>> h2o_df = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
    >>> my_model = H2OGeneralizedLinearEstimator(family = "binomial")
    >>> my_model.train(y = "CAPSULE",
    ...                x = ["AGE", "RACE", "PSA", "GLEASON"],
    ...                training_frame = h2o_df)
    >>> h2o.save_model(my_model, path='', force=True)
    """
    assert_is_type(model, ModelBase)
    assert_is_type(path, str)
    assert_is_type(force, bool)
    assert_is_type(export_cross_validation_predictions, bool)
    if filename is None:
        filename = model.model_id
    else:
        assert_is_type(filename, str)
    path = os.path.join(os.getcwd() if path == "" else path, filename)
    data = {"dir": path, "force": force, "export_cross_validation_predictions": export_cross_validation_predictions}
    return api("GET /99/Models.bin/%s" % model.model_id, data=data)["dir"]


def download_model(model, path="", export_cross_validation_predictions=False, filename=None):
    """
    Download an H2O Model object to the machine this python session is currently connected to.
    The owner of the file saved is the user by which python session was executed.

    :param model: The model object to download.
    :param path: a path to the directory where the model should be saved.
    :param export_cross_validation_predictions: logical, indicates whether the exported model
        artifact should also include CV Holdout Frame predictions.  Default is not to include the predictions.
    :param filename: a filename for the saved model

    :returns: the path of the downloaded model

    :examples:
    
    >>> from h2o.estimators.glm import H2OGeneralizedLinearEstimator
    >>> h2o_df = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
    >>> my_model = H2OGeneralizedLinearEstimator(family = "binomial")
    >>> my_model.train(y = "CAPSULE",
    ...                x = ["AGE", "RACE", "PSA", "GLEASON"],
    ...                training_frame = h2o_df)
    >>> h2o.download_model(my_model, path='')
    """
    assert_is_type(model, ModelBase)
    assert_is_type(path, str)
    assert_is_type(export_cross_validation_predictions, bool)
    if filename is None:
        filename = model.model_id
    else:
        assert_is_type(filename, str)
    path = os.path.join(os.getcwd() if path == "" else path, filename)
    return api("GET /3/Models.fetch.bin/%s" % model.model_id,
               data={"export_cross_validation_predictions": export_cross_validation_predictions}, 
               save_to=path)


def upload_model(path):
    """
    Upload a binary model from the provided local path to the H2O cluster.
    (H2O model can be saved in a binary form either by ``save_model()`` or by ``download_model()`` function.)
    
    :param path: A path on the machine this python session is currently connected to, specifying the location of the model to upload.
    
    :returns: a new :class:`H2OEstimator` object.
    """
    response = api("POST /3/PostFile.bin", filename=path)
    frame_key = response["destination_frame"]
    res = api("POST /99/Models.upload.bin/%s" % "", data={"dir": frame_key})
    return get_model(res["models"][0]["model_id"]["name"])


def load_model(path):
    """
    Load a saved H2O model from disk. (Note that ensemble binary models can now be loaded using this method.)

    :param path: the full path of the H2O Model to be imported.

    :returns: an :class:`H2OEstimator` object

    :examples:

    >>> training_data = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> predictors = ["Origin", "Dest", "Year", "UniqueCarrier",
    ...               "DayOfWeek", "Month", "Distance", "FlightNum"]
    >>> response = "IsDepDelayed"
    >>> model = H2OGeneralizedLinearEstimator(family="binomial",
    ...                                       alpha=0,
    ...                                       Lambda=1e-5)
    >>> model.train(x=predictors,
    ...             y=response,
    ...             training_frame=training_data)
    >>> h2o.save_model(model, path='', force=True)
    >>> h2o.load_model(model)
    """
    assert_is_type(path, str)
    res = api("POST /99/Models.bin/%s" % "", data={"dir": path})
    return get_model(res["models"][0]["model_id"]["name"])


def export_file(frame, path, force=False, sep=",", compression=None, parts=1, header=True, quote_header=True, parallel=False, format="csv"):
    """
    Export a given H2OFrame to a path on the machine this python session is currently connected to.

    :param frame: the Frame to save to disk.
    :param path: the path to the save point on disk.
    :param force: if True, overwrite any preexisting file with the same path.
    :param sep: field delimiter for the output file.
    :param compression: how to compress the exported dataset (default none; gzip, bzip2 and snappy available)
    :param parts: enables export to multiple 'part' files instead of just a single file.
        Convenient for large datasets that take too long to store in a single file.
        Use ``parts = -1`` to instruct H2O to determine the optimal number of part files or
        specify your desired maximum number of part files. Path needs to be a directory
        when exporting to multiple files, also that directory must be empty.
        Default is ``parts = 1``, which is to export to a single file.
    :param header: if True, write out column names in the header line.
    :param quote_header: if True, quote column names in the header.
    :param parallel: use a parallel export to a single file (doesn't apply when num_parts != 1, 
        might create temporary files in the destination directory).
    :param format: one of 'csv' or 'parquet'. Defaults to 'csv'. Export
        to parquet is multipart and H2O itself determines the optimal number
        of files (1 file per chunk).

    :examples:

    >>> h2o_df = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")
    >>> h2o_df['CAPSULE'] = h2o_df['CAPSULE'].asfactor()
    >>> rand_vec = h2o_df.runif(1234)
    >>> train = h2o_df[rand_vec <= 0.8]
    >>> valid = h2o_df[(rand_vec > 0.8) & (rand_vec <= 0.9)]
    >>> test = h2o_df[rand_vec > 0.9]
    >>> binomial_fit = H2OGeneralizedLinearEstimator(family = "binomial")
    >>> binomial_fit.train(y = "CAPSULE",
    ...                    x = ["AGE", "RACE", "PSA", "GLEASON"],
    ...                    training_frame = train, validation_frame = valid)
    >>> pred = binomial_fit.predict(test)
    >>> h2o.export_file(pred, "/tmp/pred.csv", force = True)
    """
    assert_is_type(frame, H2OFrame)
    assert_is_type(path, str)
    assert_is_type(sep, I(str, lambda s: len(s) == 1))
    assert_is_type(force, bool)
    assert_is_type(parts, int)
    assert_is_type(compression, str, None)
    assert_is_type(header, bool)
    assert_is_type(quote_header, bool)
    assert_is_type(parallel, bool)
    assert_is_type(format, str)
    H2OJob(api("POST /3/Frames/%s/export" % (frame.frame_id), 
               data={"path": path, "num_parts": parts, "force": force, 
                     "compression": compression, "separator": ord(sep),
                     "header": header, "quote_header": quote_header, "parallel": parallel, "format": format}), "Export File").poll()


def load_frame(frame_id, path, force=True):
    """
    Load frame previously stored in H2O's native format.

    This will load a data frame from file-system location. Stored data can be loaded only with a cluster of the same
    size and same version the the one which wrote the data. The provided directory must be accessible from all nodes 
    (HDFS, NFS). Provided frame_id must be the same as the one used when writing the data.
    
    :param frame_id: the frame ID of the original frame
    :param path: a filesystem location where to look for frame data
    :param force: overwrite an already existing frame (defaults to true)
    :returns: A Frame object.
    
    :examples:
    
    >>> iris = h2o.load_frame("iris_weather.hex", "hdfs://namenode/h2o_data")
    """
    H2OJob(api(
        "POST /3/Frames/load",
        data={"frame_id": frame_id, "dir": path, "force": force}
    ), "Load frame data").poll()
    return get_frame(frame_id)


def cluster():
    """Return :class:`H2OCluster` object describing the backend H2O cluster.

    :examples:

    >>> import h2o
    >>> h2o.init()
    >>> h2o.cluster()
    """
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
    :param cols: the number of columns of data to generate. Excludes the response column if ``has_response`` is True.
    :param randomize: If True, data values will be randomly generated. This must be True if either
        ``categorical_fraction`` or ``integer_fraction`` is non-zero.
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
    :param response_factors: if ``has_response`` is True, then this variable controls the type of the "response" column:
        setting ``response_factors`` to 1 will generate real-valued response, any value greater or equal than 2 will
        create categorical response with that many categories.
    :param positive_reponse: when response variable is present and of real type, this will control whether it
        contains positive values only, or both positive and negative.
    :param seed: a seed used to generate random values when ``randomize`` is True.
    :param seed_for_column_types: a seed used to generate random column types when ``randomize`` is True.

    :returns: an :class:`H2OFrame` object

    :examples:

    >>> dataset_params = {}
    >>> dataset_params['rows'] = random.sample(list(range(50,150)),1)[0]
    >>> dataset_params['cols'] = random.sample(list(range(3,6)),1)[0]
    >>> dataset_params['categorical_fraction'] = round(random.random(),1)
    >>> left_over = (1 - dataset_params['categorical_fraction'])
    >>> dataset_params['integer_fraction'] =
    ... round(left_over - round(random.uniform(0,left_over),1),1)
    >>> if dataset_params['integer_fraction'] + dataset_params['categorical_fraction'] == 1:
    ...     if dataset_params['integer_fraction'] >
    ...     dataset_params['categorical_fraction']:
    ...             dataset_params['integer_fraction'] =
    ...             dataset_params['integer_fraction'] - 0.1
    ...     else:   
    ...             dataset_params['categorical_fraction'] =
    ...             dataset_params['categorical_fraction'] - 0.1
    >>> dataset_params['missing_fraction'] = random.uniform(0,0.5)
    >>> dataset_params['has_response'] = False
    >>> dataset_params['randomize'] = True
    >>> dataset_params['factors'] = random.randint(2,5)
    >>> print("Dataset parameters: {0}".format(dataset_params))
    >>> distribution = random.sample(['bernoulli','multinomial',
    ...                               'gaussian','poisson','gamma'], 1)[0]
    >>> if   distribution == 'bernoulli': dataset_params['response_factors'] = 2
    ... elif distribution == 'gaussian':  dataset_params['response_factors'] = 1
    ... elif distribution == 'multinomial': dataset_params['response_factors'] = random.randint(3,5)
    ... else:
    ...     dataset_params['has_response'] = False
    >>> print("Distribution: {0}".format(distribution))
    >>> train = h2o.create_frame(**dataset_params)
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

    :examples:

    >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    >>> iris = iris.cbind(iris[4] == "Iris-setosa")
    >>> iris[5] = iris[5].asfactor()
    >>> iris.set_name(5,"C6")
    >>> iris = iris.cbind(iris[4] == "Iris-virginica")
    >>> iris[6] = iris[6].asfactor()
    >>> iris.set_name(6, name="C7")
    >>> two_way_interactions = h2o.interaction(iris,
    ...                                        factors=[4,5,6],
    ...                                        pairwise=True,
    ...                                        max_factors=10000,
    ...                                        min_occurrence=1)
    >>> from h2o.utils.typechecks import assert_is_type
    >>> assert_is_type(two_way_interactions, H2OFrame)
    >>> levels1 = two_way_interactions.levels()[0]
    >>> levels2 = two_way_interactions.levels()[1]
    >>> levels3 = two_way_interactions.levels()[2]
    >>> two_way_interactions
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

    If Pandas is available (and ``use_pandas`` is True), then pandas will be used to parse the
    data frame. Otherwise, a list-of-lists populated by character data will be returned (so
    the types of data will all be str).

    :param data: an H2O data object.
    :param use_pandas: If True, try to use pandas for reading in the data.
    :param header: If True, return column names as first element in list

    :returns: List of lists (Rows x Columns).

    :examples:

    >>> iris = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_wheader.csv")
    >>> from h2o.utils.typechecks import assert_is_type
    >>> res1 = h2o.as_list(iris, use_pandas=False)
    >>> assert_is_type(res1, list)
    >>> res1 = list(zip(*res1))
    >>> assert abs(float(res1[0][9]) - 4.4) < 1e-10 and abs(float(res1[1][9]) - 2.9) < 1e-10 and \
    ...     abs(float(res1[2][9]) - 1.4) < 1e-10, "incorrect values"
    >>> res1
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
    :param test: If True, ``h2o.init()`` will not be called (used for pyunit testing).

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
    """Imports a data file within the 'h2o_data' folder.

    :examples:

    >>> fr = h2o.load_dataset("iris")
    """
    assert_is_type(relative_path, str)
    h2o_dir = os.path.split(__file__)[0]
    for possible_file in [os.path.join(h2o_dir, relative_path),
                          os.path.join(h2o_dir, "h2o_data", relative_path),
                          os.path.join(h2o_dir, "h2o_data", relative_path + ".csv")]:
        if os.path.exists(possible_file):
            return upload_file(possible_file)
    # File not found -- raise an error!
    raise H2OValueError("Data file %s cannot be found" % relative_path)


def make_metrics(predicted, actual, domain=None, distribution=None, weights=None, treatment=None, auc_type="NONE",
                 auuc_type="AUTO", auuc_nbins=-1):
    """
    Create Model Metrics from predicted and actual values in H2O.

    :param H2OFrame predicted: an H2OFrame containing predictions.
    :param H2OFrame actuals: an H2OFrame containing actual values.
    :param domain: list of response factors for classification.
    :param distribution: distribution for regression.
    :param H2OFrame weights: an H2OFrame containing observation weights (optional).
    :param H2OFrame treatment: an H2OFrame containing treatment information for uplift binomial classification only.
    :param auc_type: For multinomial classification you have to specify which type of agregated AUC/AUCPR 
           will be used to calculate this metric. Possibilities are:

               - MACRO_OVO
               - MACRO_OVR
               - WEIGHTED_OVO
               - WEIGHTED_OVR
               - NONE
               - AUTO 

           (OVO = One vs. One, OVR = One vs. Rest). Default is "NONE" (AUC and AUCPR are not calculated).
    :param auuc_type: For uplift binomial classification you have to specify which type of AUUC will be used to 
           calculate this metric. Choose from:

               - gini
               - lift
               - gain
               - AUTO (default, uses qini)
               
    :param auuc_nbins: For uplift binomial classification you have to specify number of bins to be used 
           for calculation the AUUC. Default is -1, which means 1000.
    :examples:

    >>> fr = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip")
    >>> fr["CAPSULE"] = fr["CAPSULE"].asfactor()
    >>> fr["RACE"] = fr["RACE"].asfactor()
    >>> response = "AGE"
    >>> predictors = list(set(fr.names) - {"ID", response})
    >>> for distr in ["gaussian", "poisson", "laplace", "gamma"]:
    ...     print("distribution: %s" % distr)
    ...     model = H2OGradientBoostingEstimator(distribution=distr,
    ...                                          ntrees=2,
    ...                                          max_depth=3,
    ...                                          min_rows=1,
    ...                                          learn_rate=0.1,
    ...                                          nbins=20)
    ...     model.train(x=predictors,
    ...                 y=response,
    ...                 training_frame=fr)
    ...     predicted = h2o.assign(model.predict(fr), "pred")
    ...     actual = fr[response]
    ...     m0 = model.model_performance(train=True)
    ...     m1 = h2o.make_metrics(predicted, actual, distribution=distr)
    ...     m2 = h2o.make_metrics(predicted, actual)
    >>> print(m0)
    >>> print(m1)
    >>> print(m2)
    """
    assert_is_type(predicted, H2OFrame)
    assert_is_type(actual, H2OFrame)
    assert_is_type(weights, H2OFrame, None)
    assert_is_type(treatment, H2OFrame, None)
    assert actual.ncol == 1, "`actual` frame should have exactly 1 column"
    assert_is_type(distribution, str, None)
    assert_satisfies(actual.ncol, actual.ncol == 1)
    assert_is_type(auc_type, str)
    allowed_auc_types = ["MACRO_OVO", "MACRO_OVR", "WEIGHTED_OVO", "WEIGHTED_OVR", "AUTO", "NONE"]
    assert auc_type in allowed_auc_types, "auc_type should be "+(" ".join([str(type) for type in allowed_auc_types]))
    if domain is None and any(actual.isfactor()):
        domain = actual.levels()[0]
    params = {"domain": domain, "distribution": distribution}
    if weights is not None:
        params["weights_frame"] = weights.frame_id
    if treatment is not None:
        params["treatment_frame"] = treatment.frame_id
        allowed_auuc_types = ["qini", "lift", "gain", "AUTO"]
        assert auuc_type in allowed_auuc_types, "auuc_type should be "+(" ".join([str(type) for type in allowed_auuc_types]))
        params["auuc_type"] = auuc_type
        assert auuc_nbins == -1 or auuc_nbins > 0, "auuc_nbis should be -1 or higner than 0."  
        params["auuc_nbins"] = auuc_nbins
    params["auc_type"] = auc_type    
    res = api("POST /3/ModelMetrics/predictions_frame/%s/actuals_frame/%s" % (predicted.frame_id, actual.frame_id),
              data=params)
    return res["model_metrics"]


def flow():
    """
    Open H2O Flow in your browser.

    :examples:

    >>> python
    >>> import h2o
    >>> h2o.init()
    >>> h2o.flow()

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


def _inspect_methods_separately(obj):
    import inspect
    class_def = "class {}:\n".format(obj.__name__)
    for name, member in inspect.getmembers(obj):
        if inspect.ismethod(member):
            class_def += inspect.getsource(member)
        elif inspect.isfunction(member):
            class_def += inspect.getsource(member)
    return class_def


def _default_source_provider(obj):
    import inspect
    # First try to get source code via inspect
    try:
        return '    '.join(inspect.getsourcelines(obj)[0])
    except (OSError, TypeError, IOError):
        # It seems like we are in interactive shell and
        # we do not have access to class source code directly
        # At this point we can:
        # (1) get IPython history and find class definition, or
        # (2) compose body of class from methods, since it is still possible to get
        #     method body
        return _inspect_methods_separately(obj)


def _default_custom_distribution_source_provider(obj):
    from h2o.utils.distributions import CustomDistributionGeneric
    if CustomDistributionGeneric in obj.mro():
        return _inspect_methods_separately(obj)
    else:
        return _default_source_provider(obj)
    

def upload_custom_metric(func, func_file="metrics.py", func_name=None, class_name=None, source_provider=None):
    """
    Upload given metrics function into H2O cluster.

    The metrics can have different representation:
      - class: needs to implement map(pred, act, weight, offset, model), reduce(l, r) and metric(l) methods
      - string: the same as in class case, but the class is given as a string

    :param func:  metric representation: string, class
    :param func_file:  internal name of file to save given metrics representation
    :param func_name:  name for h2o key under which the given metric is saved
    :param class_name: name of class wrapping the metrics function (when supplied as string)
    :param source_provider: a function which provides a source code for given function
    :return: reference to uploaded metrics function

    :examples:
    
    >>> class CustomMaeFunc:
    >>>     def map(self, pred, act, w, o, model):
    >>>         return [abs(act[0] - pred[0]), 1]
    >>>
    >>>     def reduce(self, l, r):
    >>>         return [l[0] + r[0], l[1] + r[1]]
    >>>
    >>>     def metric(self, l):
    >>>         return l[0] / l[1]
    >>>
    >>> custom_func_str = '''class CustomMaeFunc:
    >>>     def map(self, pred, act, w, o, model):
    >>>         return [abs(act[0] - pred[0]), 1]
    >>>
    >>>     def reduce(self, l, r):
    >>>         return [l[0] + r[0], l[1] + r[1]]
    >>>
    >>>     def metric(self, l):
    >>>         return l[0] / l[1]'''
    >>>
    >>>
    >>> h2o.upload_custom_metric(custom_func_str, class_name="CustomMaeFunc", func_name="mae")
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
        code = _CFUNC_CODE_TEMPLATE.format(func, class_name, class_name)
        derived_func_name = "metrics_{}".format(class_name)
        class_name = "{}.{}Wrapper".format(module_name, class_name)
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


def upload_custom_distribution(func, func_file="distributions.py", func_name=None, class_name=None, source_provider=None):
    import tempfile
    import inspect

    # Use default source provider
    if not source_provider:
        source_provider = _default_custom_distribution_source_provider

    # The template wraps given metrics representation
    _CFUNC_CODE_TEMPLATE = """# Generated code
import water.udf.CDistributionFunc as DistributionFunc

# User given metric function as a class implementing
# 4 methods defined by interface CDistributionFunc
{}

# Generated user distribution which satisfies the interface
# of Java DistributionFunc
class {}Wrapper({}, DistributionFunc, object):
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
        code = _CFUNC_CODE_TEMPLATE.format(func, class_name, class_name)
        derived_func_name = "distributions_{}".format(class_name)
        class_name = "{}.{}Wrapper".format(module_name, class_name)
    else:
        assert_satisfies(func, inspect.isclass(func), "The parameter `func` should be str or class")
        for method in ['link', 'init', 'gamma', 'gradient']:
            assert_satisfies(func, method in dir(func), "The class `func` needs to define method `{}`".format(method))
        assert_satisfies(class_name, class_name is None,
                         "If class is specified then class_name parameter needs to be None")

        class_name = "{}.{}Wrapper".format(module_name, func.__name__)
        derived_func_name = "distributions_{}".format(func.__name__)
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


def import_mojo(mojo_path, model_id=None):
    """
    Imports an existing MOJO model as an H2O model.
    
    :param mojo_path: Path to the MOJO archive on the H2O's filesystem
    :param model_id: Model ID, default is None
    :return: An H2OGenericEstimator instance embedding given MOJO

    :examples:

    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> model = H2OGradientBoostingEstimator(ntrees = 1)
    >>> model.train(x = ["Origin", "Dest"],
    ...             y = "IsDepDelayed",
    ...             training_frame=airlines)
    >>> original_model_filename = tempfile.mkdtemp()
    >>> original_model_filename = model.download_mojo(original_model_filename)
    >>> mojo_model = h2o.import_mojo(original_model_filename)
    """
    if mojo_path is None:
        raise TypeError("MOJO path may not be None")
    mojo_estimator = H2OGenericEstimator.from_file(mojo_path, model_id)
    return mojo_estimator


def upload_mojo(mojo_path, model_id=None):
    """
    Uploads an existing MOJO model from local filesystem into H2O and imports it as an H2O Generic Model. 

    :param mojo_path:  Path to the MOJO archive on the user's local filesystem
    :param model_id: Model ID, default None
    :return: An H2OGenericEstimator instance embedding given MOJO

    :examples:

    >>> from h2o.estimators import H2OGradientBoostingEstimator
    >>> airlines= h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
    >>> model = H2OGradientBoostingEstimator(ntrees = 1)
    >>> model.train(x = ["Origin", "Dest"],
    ...             y = "IsDepDelayed",
    ...             training_frame=airlines)
    >>> original_model_filename = tempfile.mkdtemp()
    >>> original_model_filename = model.download_mojo(original_model_filename)
    >>> mojo_model = h2o.upload_mojo(original_model_filename)
    """
    response = api("POST /3/PostFile", filename=mojo_path)
    frame_key = response["destination_frame"]
    mojo_estimator = H2OGenericEstimator(model_key=get_frame(frame_key), model_id=model_id)
    mojo_estimator.train()
    return mojo_estimator


def print_mojo(mojo_path, format="json", tree_index=None):
    """
    Generates string representation of an existing MOJO model. 

    :param mojo_path: Path to the MOJO archive on the user's local filesystem
    :param format: Output format. Possible values: json (default), dot, png 
    :param tree_index: Index of tree to print
    :return: An string representation of the MOJO for text output formats, 
        a path to a directory with the rendered images for image output formats
        (or a path to a file if only a single tree is outputted)  

    :example:

    >>> import json
    >>> from h2o.estimators.gbm import H2OGradientBoostingEstimator
    >>> prostate = h2o.import_file("http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv")
    >>> prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    >>> gbm_h2o = H2OGradientBoostingEstimator(ntrees = 5,
    ...                                        learn_rate = 0.1,
    ...                                        max_depth = 4,
    ...                                        min_rows = 10)
    >>> gbm_h2o.train(x = list(range(1,prostate.ncol)),
    ...               y = "CAPSULE",
    ...               training_frame = prostate)
    >>> mojo_path = gbm_h2o.download_mojo()
    >>> mojo_str = h2o.print_mojo(mojo_path)
    >>> mojo_dict = json.loads(mojo_str)
    """    
    assert_is_type(mojo_path, str)
    assert_is_type(format, str, None)
    assert_satisfies(format, format in [None, "json", "dot", "png"])
    assert_is_type(tree_index, int, None)

    ls = H2OLocalServer()
    jar = ls._find_jar()
    java = ls._find_java()
    if format is None:
        format = "json"
    is_image = format == "png"
    output_file = tempfile.mkstemp(prefix="mojo_output")[1]
    cmd = [java, "-cp", jar, "hex.genmodel.tools.PrintMojo", "--input", mojo_path, "--format", format, 
           "--output", output_file]
    if tree_index is not None:
        cmd += ["--tree", str(tree_index)]
    try:
        return_code = subprocess.call(cmd)
        if is_image:
            output = output_file
        else:
            with open(output_file, "r") as f:
                output = f.read()
            os.unlink(output_file)
    except OSError as e:
        traceback = getattr(e, "child_traceback", None)
        raise H2OError("Unable to print MOJO: %s" % e, traceback)
    if return_code == 0:
        return output
    else:
        raise H2OError("Unable to print MOJO: %s" % output)


def estimate_cluster_mem(ncols, nrows, num_cols=0, string_cols=0, cat_cols=0, time_cols=0, uuid_cols=0):
    """
    Computes an estimate for cluster memory usage in GB.
    
    Number of columns and number of rows are required. For a better estimate you can provide a counts of different
    types of columns in the dataset.

    :param ncols: (Required) total number of columns in a dataset. Integer, can't be negative
    :param nrows: (Required) total number of rows in a dataset. Integer, can't be negative 
    :param num_cols: number of numeric columns in a dataset. Integer, can't be negative.
    :param string_cols: number of string columns in a dataset. Integer, can't be negative.
    :param cat_cols: number of categorical columns in a dataset. Integer, can't be negative.
    :param time_cols: number of time columns in a dataset. Integer, can't be negative.
    :param uuid_cols: number of uuid columns in a dataset. Integer, can't be negative.
    :return: An memory estimate in GB.

    :example:

    >>> from h2o import estimate_cluster_mem
    >>> ### I will load a parquet file with 18 columns and 2 million lines
    >>> estimate_cluster_mem(18, 2000000)
    >>> ### I will load another parquet file with 16 columns and 2 million lines; I ask for a more precise estimate 
    >>> ### because I know 12 of 16 columns are categorical and 1 of 16 columns consist of uuids.
    >>> estimate_cluster_mem(18, 2000000, cat_cols=12, uuid_cols=1)
    >>> ### I will load a parquet file with 8 columns and 31 million lines; I ask for a more precise estimate 
    >>> ### because I know 4 of 8 columns are categorical and 4 of 8 columns consist of numbers.
    >>> estimate_cluster_mem(ncols=8, nrows=31000000, cat_cols=4, num_cols=4)
    
    """
    import math

    if ncols < 0:
        raise ValueError("ncols can't be a negative number")

    if nrows < 0:
        raise ValueError("nrows can't be a negative number")

    if num_cols < 0:
        raise ValueError("num_cols can't be a negative number")

    if string_cols < 0:
        raise ValueError("string_cols can't be a negative number")

    if cat_cols < 0:
        raise ValueError("cat_cols can't be a negative number")

    if time_cols < 0:
        raise ValueError("time_cols can't be a negative number")

    if uuid_cols < 0:
        raise ValueError("uuid_cols can't be a negative number")

    base_mem_requirement_mb = 32
    safety_factor = 4
    bytes_in_mb = 1024 * 1024
    bytes_in_gb = 1024 * bytes_in_mb

    known_cols = num_cols + string_cols + uuid_cols + cat_cols + time_cols

    if known_cols > ncols:
        raise ValueError("There can not be more specific columns then columns in total")

    unknown_cols = ncols - known_cols
    unknown_size = 8
    unknown_requirement = unknown_cols * nrows * unknown_size
    num_size = 8
    num_requirement = num_cols * nrows * num_size
    string_size = 128
    string_requirement = string_size * string_cols * nrows
    uuid_size = 16
    uuid_requirement = uuid_size * uuid_cols * nrows
    cat_size = 2
    cat_requirement = cat_size * cat_cols * nrows
    time_size = 8
    time_requirement = time_size * time_cols * nrows
    data_requirement = unknown_requirement + num_requirement + string_requirement + uuid_requirement + cat_requirement + time_requirement
    mem_req = (base_mem_requirement_mb * bytes_in_mb + data_requirement) * safety_factor / bytes_in_gb
    return math.ceil(mem_req)


# ----------------------------------------------------------------------------------------------------------------------
# Private
# ----------------------------------------------------------------------------------------------------------------------

def _check_connection():
    if not cluster():
        raise H2OConnectionError("Not connected to a cluster. Did you run `h2o.init()` or `h2o.connect()`?")
    
    
def _strict_version_check(force_version_check=None, config=None):
    if force_version_check is None:
        if config is not None and "init.check_version" in config:
            return config["init.check_version"].lower() != "false"
        else:
            return os.environ.get("H2O_DISABLE_STRICT_VERSION_CHECK", "false").lower() == "false"
    return force_version_check


def _connect_with_conf(conn_conf, **kwargs):
    conf = conn_conf
    if isinstance(conn_conf, dict):
        conf = H2OConnectionConf(config=conn_conf)
    assert_is_type(conf, H2OConnectionConf)
    return connect(url=conf.url, verify_ssl_certificates=conf.verify_ssl_certificates, cacert=conf.cacert,
                   auth=conf.auth, proxy=conf.proxy, cookies=conf.cookies, verbose=conf.verbose, **kwargs)


# ----------------------------------------------------------------------------------------------------------------------
# Deprecated functions
# ----------------------------------------------------------------------------------------------------------------------

# Deprecated since 2015-10-08
@deprecated_fn(replaced_by=import_file)
def import_frame():
    pass

# Deprecated since 2015-10-08
@deprecated_fn("Deprecated (converted to a private method).")
def parse():
    """Deprecated."""
    pass

# Deprecated since 2016-08-04
@deprecated_fn("Deprecated, use ``h2o.cluster().show_status()``.")
def cluster_info():
    """Deprecated."""
    _check_connection()
    cluster().show_status()

# Deprecated since 2016-08-04
@deprecated_fn("Deprecated, use ``h2o.cluster().show_status(True)``.")
def cluster_status():
    """Deprecated."""
    _check_connection()
    cluster().show_status(True)

# Deprecated since 2016-08-04
@deprecated_fn("Deprecated, use ``h2o.cluster().shutdown()``.")
def shutdown(prompt=False):
    """Deprecated."""
    _check_connection()
    cluster().shutdown(prompt)

# Deprecated since 2016-08-04
@deprecated_fn("Deprecated, use ``h2o.cluster().network_test()``.")
def network_test():
    """Deprecated."""
    _check_connection()
    cluster().network_test()

# Deprecated since 2016-08-04
@deprecated_fn("Deprecated, use ``h2o.cluster().timezone``.")
def get_timezone():
    """Deprecated."""
    _check_connection()
    return cluster().timezone

# Deprecated since 2016-08-04
@deprecated_fn("Deprecated, set ``h2o.cluster().timezone`` instead.")
def set_timezone(value):
    """Deprecated."""
    _check_connection()
    cluster().timezone = value

# Deprecated since 2016-08-04
@deprecated_fn("Deprecated, use ``h2o.cluster().list_timezones()``.")
def list_timezones():
    """Deprecated."""
    _check_connection()
    return cluster().list_timezones()

# Deprecated since 2021-07
@deprecated_fn("Deprecated, use ``h2o.cluster().check_version()`` instead.")
def version_check():
    _check_connection()
    cluster().check_version(strict=True)

