import certifi
import requests


version = tuple(map(int, certifi.__version__.split(".")))
assert version >= (2026, 10, 19), certifi.__version__
assert certifi.where()
assert requests.certs.where() == certifi.where()
print("certifi:", certifi.__version__)
print("certifi bundle:", certifi.where())
print("requests bundle:", requests.certs.where())
