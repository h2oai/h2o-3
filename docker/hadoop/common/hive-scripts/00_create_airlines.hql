CREATE EXTERNAL TABLE IF NOT EXISTS AirlinesTest(
  fYear VARCHAR(10) ,
  fMonth VARCHAR(10) ,
  fDayofMonth VARCHAR(20) ,
  fDayOfWeek VARCHAR(20) ,
  DepTime INT ,
  ArrTime INT ,
  UniqueCarrier STRING ,
  Origin STRING ,
  Dest STRING ,
  Distance INT ,
  IsDepDelayed STRING ,
  IsDepDelayed_REC INT
)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY ','
tblproperties ("skip.header.line.count"="1");

LOAD DATA INPATH '/tmp/AirlinesTest.csv' OVERWRITE INTO TABLE AirlinesTest;
