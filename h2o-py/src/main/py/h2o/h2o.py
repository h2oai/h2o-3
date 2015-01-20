"""
This module implements the communication REST layer for the python <-> H2O connection.
"""

import urllib
from connection import H2OConnection as h2oConn
from job import H2OJob
from frame import H2OFrame
from frame import H2OVec
from expr import Expr


def import_file(path):
    """
    Import a single file.
    :param path: A path to a data file (remote or local)
    :return: Return an H2OFrame.
    """
    j = h2oConn.do_safe_get_json(url_suffix="ImportFiles", params={'path': path})
    if j['fails']: raise ValueError("ImportFiles of " + path + " failed on " + j['fails'])
    return j['keys'][0]


def import_frame(path=None, vecs=None):
    """
    Import a frame.
    :param path:
    :return:
    """
    return H2OFrame(vecs=vecs) if vecs else H2OFrame(remote_fname=path)


def parse_setup(rawkey):
    """
    Unable to use 'requests.params=' syntax because it flattens array parameters,
    but ParseSetup really expects a real array of Keys.
    :param rawkey:
    :return: A ParseSetup "object"
    """
    j = h2oConn.do_safe_post_json(url_suffix="ParseSetup", params={'srcs': [rawkey]})
    if not j['isValid']: raise ValueError("ParseSetup not Valid", j)
    return j


def parse(setup, h2o_name):
    """
    Trigger a parse; blocking; removeFrame just keep the Vec keys.
    :param setup: The result of calling parse_setup
    :param h2o_name: The name of the H2O Frame on the back end.
    :return: Return a new parsed object
    """
    # Parse parameters (None values provided by setup)
    p = {'delete_on_done': True,
         'blocking': True,
         'removeFrame': True,
         'hex': h2o_name,
         'ncols': None,
         'sep': None,
         'columnNames': None,
         'pType': None,
         'checkHeader': None,
         'singleQuotes': None,
         }

    # update the parse parameters with the parse_setup values
    p.update({k: v for k, v in setup.iteritems() if k in p})

    # Extract only 'name' from each src in the array of srcs
    p['srcs'] = [src['name'] for src in setup['srcs']]
    # Request blocking parse
    # TODO: POST vs GET
    j = H2OJob(h2oConn.do_safe_post_json(url_suffix="Parse", params=p)).poll()
    return j.jobs


def remove(key):
    """
    Remove a key from H2O.
    :param key: The key pointing to the object to be removed.
    :return: void
    """
    h2oConn.do_safe_rest(url_suffix="Remove", params={"key": key}, method="DELETE")


def rapids(expr):
    """
    Fire off a Rapids expression
    :param expr: The rapids expression (ascii string)
    :return: The JSON response of the Rapids execution.
    """
    return h2oConn.do_safe_post_json(url_suffix="Rapids",
                                     params={"ast": urllib.quote(expr)})


def frame(key):
    """
    Retrieve metadata for a key that points to a Frame.
    :param key: A pointer to a Frame in H2O.
    :return: Meta information on the Frame.
    """

    return h2oConn.do_safe_get_json(url_suffix="Frames", params={"key": key})


def connect(ip="localhost", port=54321):
    """
    Initiate an H2O connection to the specified ip and port
    :param ip: An IP address, default is "localhost"
    :param port: A port, default is 54321
    :return: None
    """
    h2oConn(ip=ip, port=port)
    return None


# def GBM(self,distribution,shrinkage,ntrees,interaction_depth,x,train_frame,test_frame=None):
#     p = {'loss':distribution,'learn_rate':shrinkage,'ntrees':ntrees,'max_depth':interaction_depth,'variable_importance':False,'response_column':x,'training_frame':train_frame}
#     if test_frame: p['validation_frame'] = test_frame
#     j = self._doJob(self._doSafeGet(self.buildURL("GBM",p)))
#     j = self._doSafeGet(self.buildURL("3/Models/"+j['dest']['name'],{}))
#     return j['models'][0]
#
# def DeepLearning(self,x,train_frame,test_frame=None,**kwargs):
#     kwargs['response_column'] = x
#     kwargs['training_frame'] = train_frame
#     if test_frame: kwargs['validation_frame'] = test_frame
#     j = self._doJob(self._doSafeGet(self.buildURL("DeepLearning",kwargs)))
#     j = self._doSafeGet(self.buildURL("3/Models/"+j['dest']['name'],{}))
#     return j['models'][0]
