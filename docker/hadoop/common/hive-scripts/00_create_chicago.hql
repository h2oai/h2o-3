CREATE EXTERNAL TABLE IF NOT EXISTS chicago(
  community_area_num INT,
  community_area_name STRING,
  pct_owned DOUBLE,
  pct_below DOUBLE,
  pct_16plus DOUBLE,
  pct_25plus DOUBLE,
  pct_yng_old DOUBLE,
  per_cpt_income INT,
  hardship_index BIGINT
)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY ','
tblproperties ("skip.header.line.count"="1");

LOAD DATA INPATH '/tmp/chicagoCensus.csv' OVERWRITE INTO TABLE chicago;
