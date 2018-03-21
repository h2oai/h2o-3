CREATE EXTERNAL TABLE IF NOT EXISTS AirlinesTest(
  fYear STRING ,
  fMonth STRING ,
  fDayofMonth STRING ,
  fDayOfWeek STRING ,
  DepTime INT ,
  ArrTime INT ,
  UniqueCarrier STRING ,
  Origin STRING ,
  Dest STRING ,
  Distance INT ,
  IsDepDelayed STRING ,
  IsDepDelayed_REC INT
)
COMMENT 'stefan test table'
ROW FORMAT DELIMITED
FIELDS TERMINATED BY ','
tblproperties ("skip.header.line.count"="1");

LOAD DATA INPATH '/tmp/AirlinesTest.csv' OVERWRITE INTO TABLE AirlinesTest;
