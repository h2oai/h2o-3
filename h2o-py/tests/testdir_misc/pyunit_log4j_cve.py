import sys
sys.path.insert(1,"../../")
import socket
import h2o
from tests import pyunit_utils
import time
import threading


def malicious_log():
    print("Waiting 3s before sending malicious logging call ${jndi:ldap://127.0.0.1:50001/}")
    time.sleep(3)
    print("Calling log_and_echo")
    h2o.log_and_echo('${jndi:ldap://127.0.0.1:50001/}')
    print("Finished log_and_echo")
    time.sleep(3)
    print("Sending poison pill to close the connection")
    send_poison_pill()


def close_quietly(sock):
    try:
        sock.close()
    except OSError as oe:
        print(oe)


def send_poison_pill():
    sock = None
    for res in socket.getaddrinfo("localhost", 50001, socket.AF_INET, socket.SOCK_STREAM):
        af, socktype, proto, canonname, sa = res
        try:
            sock = socket.socket(af, socktype, proto)
        except OSError as oe:
            print(oe)
            sock = None
            continue
        try:
            sock.connect(sa)
        except OSError as oe:
            close_quietly(sock)
            print(oe)
            sock = None
            continue
        break
    if sock is not None:
        close_quietly(sock)


def test_log4j_cve():
    if sys.version_info[0] < 3:
        print("Skipping log24j_cve test on Python 2.x")
        return

    # Open TCP socket to listen on localhost:50001
    sock = None
    for res in socket.getaddrinfo("localhost", 50001, socket.AF_INET,
                                  socket.SOCK_STREAM, 0, socket.AI_PASSIVE):
        af, socktype, proto, canonname, sa = res
        try:
            sock = socket.socket(af, socktype, proto)
        except OSError:
            sock = None
            continue
        try:
            sock.bind(sa)
            sock.listen(1)
        except OSError:
            close_quietly(sock)
            sock = None
            continue
        break
    assert sock is not None

    t = threading.Thread(target=malicious_log)
    t.start()

    # check that we are not receiving any data on this socket
    # if someone just connects (and sends no data) we assume that it originated from this test (the "poison pill")
    # (log4j request would always also send data) 
    conn, _ = sock.accept()
    with conn:
        data = conn.recv(10)
        # if we do receive any data - it was H2O's log4j that called us
        called_by_log4j = len(data) > 0
        conn.close()
    close_quietly(sock)
        
    assert not called_by_log4j


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_log4j_cve)
else:
    test_log4j_cve()
