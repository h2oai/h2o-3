#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys
sys.path.insert(1,"../../")
import h2o
import socket
from tests import pyunit_utils



def init_proxy():
    conn = h2o.h2o.connection()
    local_if_host = socket.gethostbyname(socket.gethostname())
    print(conn._proxies)
    if("localhost" in conn._base_url or "127.0.0.1" in conn.base_url or local_if_host in conn._base_url):
        assert len(conn._proxies) == 2
        assert conn._proxies == {'http': None, 'https': None}
    else:
        assert len(conn._proxies) == 0
        assert conn._proxies is None



if __name__ == "__main__":
    pyunit_utils.standalone_test(init_proxy)
else:
    init_proxy()
