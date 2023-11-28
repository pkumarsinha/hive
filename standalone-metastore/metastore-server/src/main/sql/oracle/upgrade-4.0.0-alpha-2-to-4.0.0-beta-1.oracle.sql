SELECT 'Upgrading MetaStore schema from 4.0.0-alpha-2 to 4.0.0-beta-1' AS Status from dual;

-- HIVE-26221
ALTER TABLE TAB_COL_STATS ADD HISTOGRAM BLOB;
ALTER TABLE PART_COL_STATS ADD HISTOGRAM BLOB;

-- HIVE-26719
ALTER TABLE COMPACTION_QUEUE ADD CQ_NUMBER_OF_BUCKETS INTEGER;
ALTER TABLE COMPLETED_COMPACTIONS ADD CC_NUMBER_OF_BUCKETS INTEGER;

-- HIVE-26735
ALTER TABLE COMPACTION_QUEUE ADD CQ_ORDER_BY VARCHAR(4000);
ALTER TABLE COMPLETED_COMPACTIONS ADD CC_ORDER_BY VARCHAR(4000);

-- HIVE-26704
CREATE TABLE MIN_HISTORY_WRITE_ID (
  MH_TXNID NUMBER(19) NOT NULL REFERENCES TXNS (TXN_ID),
  MH_DATABASE VARCHAR2(128) NOT NULL,
  MH_TABLE VARCHAR2(256) NOT NULL,
  MH_WRITEID NUMBER(19) NOT NULL
);

-- HIVE-27165
DROP INDEX TAB_COL_STATS_IDX;
CREATE INDEX TAB_COL_STATS_IDX ON TAB_COL_STATS (DB_NAME, TABLE_NAME, COLUMN_NAME, CAT_NAME);
DROP INDEX PCS_STATS_IDX;
CREATE INDEX PCS_STATS_IDX ON PART_COL_STATS (DB_NAME,TABLE_NAME,COLUMN_NAME,PARTITION_NAME,CAT_NAME);

-- HIVE-27186
ALTER TABLE METASTORE_DB_PROPERTIES ADD PROPERTYCONTENT BLOB;

-- HIVE-27457
UPDATE "SDS"
    SET "SDS"."INPUT_FORMAT" = 'org.apache.hadoop.hive.kudu.KuduInputFormat',
        "SDS"."OUTPUT_FORMAT" = 'org.apache.hadoop.hive.kudu.KuduOutputFormat'
    WHERE "SDS"."SD_ID" IN (
        SELECT "TBL_ID" FROM "TABLE_PARAMS" WHERE "PARAM_VALUE" LIKE '%KuduStorageHandler%'
    );
UPDATE "SERDES"
    SET "SERDES"."SLIB" = 'org.apache.hadoop.hive.kudu.KuduSerDe'
    WHERE "SERDE_ID" IN (
        SELECT "SDS"."SERDE_ID"
            FROM "TBLS"
            LEFT JOIN "SDS" ON "TBLS"."SD_ID" = "SDS"."SD_ID"
            WHERE "TBL_ID" IN (SELECT "TBL_ID" FROM "TABLE_PARAMS" WHERE "PARAM_VALUE" LIKE '%KuduStorageHandler%')
    );

-- These lines need to be last.  Insert any changes above.
UPDATE VERSION SET SCHEMA_VERSION='4.0.0-beta-1', VERSION_COMMENT='Hive release version 4.0.0-beta-1' where VER_ID=1;
SELECT 'Finished upgrading MetaStore schema from 4.0.0-alpha-2 to 4.0.0-beta-1' AS Status from dual;