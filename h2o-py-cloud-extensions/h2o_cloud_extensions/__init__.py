# -*- encoding: utf-8 -*-

from .mlops.patch import *
from .settings import H2OCloudExtensionSettings
settings = H2OCloudExtensionSettings()

print("API of H2O-3 python client has been extended with cloud extensions.")
