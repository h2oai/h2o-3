"""
An H2OConnection represents the latest active handle to a cloud. No more than a single
H2OConnection object will be active at any one time.
"""

import requests
import sys
import time
from two_dim_table import H2OTwoDimTable

__H2OCONN__ = None                   # the single active connection to H2O cloud
__H2O_REST_API_VERSION__ = "LATEST"  # const for the version of the rest api


# Python has no private classes; abuse the abc package to fake it.
class H2OConnection(object):
  """
  H2OConnection is a class that represents a connection to the H2O cluster.
  It is specified by an IP address and a port number.

  Objects of type H2OConnection are not instantiated directly!

  This class contains static methods for performing the common REST methods
  GET, POST, and DELETE.
  """

  def __init__(self, ip="localhost", port=54321, size=1):
    """
    Instantiate the package handle to the H2O cluster.
    :param ip: An IP address, default is "localhost"
    :param port: A port, default is 54321
    :return: None
    """
    if not (isinstance(port, int) and 0 <= port <= sys.maxint):
       raise ValueError("Port out of range, "+port)
    global __H2OCONN__
    self._ip = ip
    self._port = port
    self._session_id = None
    self._rest_version = __H2O_REST_API_VERSION__
    __H2OCONN__ = self
    cld = self._connect(size)
    self._session_id = self.get_session_id()
    ncpus = sum([n['num_cpus'] for n in cld['nodes']])
    mmax = sum([n['max_mem'] for n in cld['nodes']])
    print "Connected to cloud '" + cld['cloud_name'] + "' size", cld['cloud_size'], "ncpus", ncpus, "maxmem", get_human_readable_size(mmax), "session_id", self._session_id

  def _connect(self,size):
    """
    Does not actually "connect", instead simply tests that the cluster can be reached,
    is of a certain size, and is taking basic status commands.
    :param size: The number of H2O instances in the cloud.
    :return: The JSON response from a "stable" cluster.
    """
    max_retries = 5
    retries = 0
    while True:
      retries += 1
      try:
        cld = H2OConnection.get_json(url_suffix="Cloud")
        if not cld['cloud_healthy']:
          raise ValueError("Cluster reports unhealthy status", cld)
        if cld['cloud_size'] >= size and cld['consensus']:
          return cld
      except EnvironmentError:
        pass
      # Cloud too small or voting in progress; sleep; try again
      time.sleep(0.1)
      if retries > max_retries:
        raise EnvironmentError("Max retries exceeded. Could not establish link to the H2O cloud @ " + str(self._ip) + ":" + str(self._port))

  @staticmethod
  def get_session_id():
      return H2OConnection.get_json(url_suffix="InitID")["session_key"]

  @staticmethod
  def rest_version(): return __H2OCONN__._rest_version

  @staticmethod
  def session_id(): return __H2OCONN__._session_id

  @staticmethod
  def port(): return __H2OCONN__._port

  @staticmethod
  def ip(): return __H2OCONN__._ip

  @staticmethod
  def current_connection(): return __H2OCONN__

  @staticmethod
  def check_conn():
    if not __H2OCONN__:
      raise EnvironmentError("No active connection to an H2O cluster.  Try calling `h2o.init()`")
    return __H2OCONN__

  """
  Below is the REST implementation layer:
      _attempt_rest -- GET, POST, DELETE

      _do_raw_rest

      get
      post
      get_json
      post_json

  All methods are static and rely on an active __H2OCONN__ object.
  """

  @staticmethod
  def get(url_suffix, **kwargs):
    return __H2OCONN__._do_raw_rest(url_suffix, "GET", None, **kwargs)

  @staticmethod
  def post(url_suffix, file_upload_info=None, **kwargs):
    return __H2OCONN__._do_raw_rest(url_suffix, "POST", file_upload_info, **kwargs)

  @staticmethod
  def delete(url_suffix, **kwargs):
    return __H2OCONN__._do_raw_rest(url_suffix, "DELETE", None, **kwargs)

  @staticmethod
  def get_json(url_suffix, **kwargs):
    return __H2OCONN__._rest_json(url_suffix, "GET", None, **kwargs)

  @staticmethod
  def post_json(url_suffix, file_upload_info=None, **kwargs):
    return __H2OCONN__._rest_json(url_suffix, "POST", file_upload_info, **kwargs)

  def _rest_json(self, url_suffix, method, file_upload_info, **kwargs):
    raw_txt = self._do_raw_rest(url_suffix, method, file_upload_info, **kwargs)
    return self._process_tables(raw_txt.json())

  # Massage arguments into place, call _attempt_rest
  def _do_raw_rest(self, url_suffix, method, file_upload_info=None, **kwargs):
    if not url_suffix:
      raise ValueError("No url suffix supplied.")
    url = "http://{}:{}/{}/{}".format(self._ip,self._port,self._rest_version,url_suffix + ".json")

    query_string = ""
    for k,v in kwargs.iteritems():
      if isinstance(v, list):
        x = '['
        x += ','.join([str(l).encode("utf-8") for l in v])
        x += ']'
      else:
        x = str(v).encode("utf-8")
      query_string += k+"="+x+"&"
    query_string = query_string[:-1] # Remove trailing extra &

    post_body = ""
    if not file_upload_info:
      if method == "POST":
        post_body = query_string
      elif query_string != '':
        url = "{}?{}".format(url, query_string)
    else:
      if not method == "POST":
        raise ValueError("Received file upload info and expected method to be POST. Got: " + str(method))
      if query_string != '':
        url = "{}?{}".format(url, query_string)

    begin_time_seconds = time.time()
    http_result = self._attempt_rest(url, method, post_body, file_upload_info)
    end_time_seconds = time.time()
    elapsed_time_seconds = end_time_seconds - begin_time_seconds
    elapsed_time_millis = elapsed_time_seconds * 1000
    if not http_result.ok:
      raise EnvironmentError("h2o-py got an unexpected HTTP status code:\n {} {} (method = {}; url = {})"
                             .format(http_result.status_code,http_result.reason,method,url))

    # TODO: is.logging? -> write to logs
    # print "Time to perform REST call (millis): " + str(elapsed_time_millis)
    return http_result

  # Low level request call
  def _attempt_rest(self, url, method, post_body, file_upload_info):
    try:
      if method == "GET":
        return requests.get(url)
      elif file_upload_info:
        files = {file_upload_info["file"] : open(file_upload_info["file"], "rb")}
        return requests.post(url, files=files)
      elif method == "POST":
        return requests.post(url, params=post_body)
      elif method == "DELETE":
        return requests.delete(url)
      else:
        raise ValueError("Unknown HTTP method " + method)

    except requests.ConnectionError as e:
      raise EnvironmentError("h2o-py encountered an unexpected HTTP error:\n {}".format(e.message))

    return http_result

  # TODO:
  # @staticmethod
  # def _process_matrices(x=None):
  #     if x:
  #         if isinstance(x, "dict"):
  #
  #     return x

  @staticmethod
  def _process_tables(x=None):
      elts = ["tableHeader", "rowHeaders", "colHeaders",
              "colTypes", "colFormats", "cellValues"]

      if x:
          if isinstance(x, dict):
              have_table = all([True if i in elts else False for i in x.keys()])
              have_table &= len(x) == len(elts)
              if have_table:
                  tbl = x["cellValues"]
                  tbl = H2OTwoDimTable(x["rowHeaders"], x["colHeaders"], x["colTypes"],
                                       x["tableHeader"], x["cellValues"],
                                       x["colFormats"])
                  x = tbl
              else:
                  for k in x:
                      x[k] = H2OConnection._process_tables(x[k])
          if isinstance(x, list):
              for it in range(len(x)):
                  x[it] = H2OConnection._process_tables(x[it])
      return x

def get_human_readable_size(num):
    exp_str = [(0, 'B'), (10, 'KB'), (20, 'MB'), (30, 'GB'), (40, 'TB'), (50, 'PB'), ]
    i = 0
    rounded_val = 0
    while i + 1 < len(exp_str) and num >= (2 ** exp_str[i + 1][0]):
        i += 1
        rounded_val = round(float(num) / 2 ** exp_str[i][0], 2)
    return '%s %s' % (rounded_val, exp_str[i][1])
