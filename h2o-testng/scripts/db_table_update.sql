-- create procedure
DELIMITER $$
DROP PROCEDURE IF EXISTS updateMSE$$
CREATE PROCEDURE updateMSE()
BEGIN
  DECLARE done INT DEFAULT FALSE;
  DECLARE p_testcase_id VARCHAR(125);
  DECLARE p_auc_result VARCHAR(125);
  DECLARE p_auc_time DATETIME;
  DECLARE cur1 CURSOR FOR 
    SELECT test_case_id,date,result 
    FROM h2o.TestNG 
    WHERE metric_type = 'AUC';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

  OPEN cur1;
  SET SQL_SAFE_UPDATES=0;

  read_loop: LOOP
    FETCH cur1 INTO p_testcase_id, p_auc_time, p_auc_result;
    
    IF done THEN
      LEAVE read_loop;
    END IF;
    
    UPDATE h2o.TestNG
    SET auc_result = p_auc_result
    WHERE metric_type = 'MSE'
		AND test_case_id = p_testcase_id
        -- AND TIMESTAMP(date) BETWEEN TIMESTAMP(p_auc_time) AND DATE_ADD(TIMESTAMP(p_auc_time),INTERVAL -1 MINUTE)
        AND TIMESTAMP(date) <= TIMESTAMP(p_auc_time)
        AND TIMESTAMP(date) >= DATE_ADD(TIMESTAMP(p_auc_time),INTERVAL -1 MINUTE)
        -- AND date = p_auc_time
        ;

  END LOOP;

  SET SQL_SAFE_UPDATES=1;
  CLOSE cur1;
END $$
DELIMITER ;

-- create back up table
CREATE TABLE IF NOT EXISTS TestNG_backup
(
test_case_id		 VARCHAR(125),
training_frame_id	 VARCHAR(125),
validation_frame_id	 VARCHAR(125),
metric_type			 VARCHAR(125),
result				 VARCHAR(125),
date				 datetime,
interpreter_version	 VARCHAR(125),
machine_name		 VARCHAR(125),
total_hosts			 INT,
cpus_per_hosts		 INT,
total_nodes			 INT,
source				 VARCHAR(125),
parameter_list		 VARCHAR(1024),
git_hash_number		 VARCHAR(125),
tuned_or_defaults	 VARCHAR(125)
);

-- back up data
INSERT INTO TestNG_backup
SELECT * FROM TestNG;

-- add auc_result column in TestNG table
ALTER TABLE TestNG ADD COLUMN auc_result DOUBLE AFTER validation_frame_id;

-- update auc result
CALL updateMSE();

-- add mse_column in TestNG table
ALTER TABLE TestNG ADD COLUMN mse_result DOUBLE AFTER validation_frame_id;

SET SQL_SAFE_UPDATES=0;
-- update mse result
UPDATE TestNG
SET mse_result = result
;
-- delete the auc row
DELETE FROM TestNG
WHERE metric_type = 'AUC';

SET SQL_SAFE_UPDATES=1;

-- drop metric_type and result column
ALTER TABLE TestNG DROP COLUMN result;
ALTER TABLE TestNG DROP COLUMN metric_type;

-- finish
SELECT * FROM TestNG;

