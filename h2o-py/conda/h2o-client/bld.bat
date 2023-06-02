"%PYTHON%" setup.py install --client --single-version-externally-managed --record=record.txt
if errorlevel 1 exit 1
