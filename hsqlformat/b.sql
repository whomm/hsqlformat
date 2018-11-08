;CREATE external TABLE if not exists app.app_es_onlineclass_teacherpcapp_csx_di (
    teacher_id    string comment "Teacher_ID"
    ,min_login_date   string comment "First_Login_Date"
         , min_login_time   string comment "First_Login_Time"
    ,
    max_login_date      string comment "Last_Login_Date"
    ,max_login_time string comment "Last_Login_Time"
    ,ax_class_date   string comment "Last_Class_Date"
    ,max_class_time   string comment "Last_Class_Time"
)
STORED BY 'org.elasticsearch.hadoop.hive.EsStorageHandler'
TBLPROPERTIES(
'es.resource' = 'app_es_onlineclass_teacherpcapp_csx_di/app_es_onlineclass_teacherpcapp_csx_di',
'es.nodes'='10.0.12.108,10.0.11.110,10.0.12.110',
'es.port'='6335',
'http.port'='6335',
'es.mapping.id' = 'teacher_id',
'es.write.operation'='upsert'
);



create external table if not exists dwd.dwd_contract_order_receipt_status_log_da
(
    `id` int comment "自增ID，不为空"
    ,`create_time` string comment "创建时间 默认值为：CURRENT_TIMESTAMP"
    ,`order_id` int comment "对应pack_order表的id"
    ,`status` tinyint comment "状态 默认值为：0：apply 1：reject 2：print 3：posted"
    ,`op_user` string comment "操作人Id 均为NULL"
) comment "订单状态 开发人: liyanyan@vipkid.com.cn"
partitioned by (pt string,good string) 
ROW FORMAT DELIMITED
FIELDS TERMINATED BY '\u0001'
COLLECTION ITEMS TERMINATED BY '\u0002'
MAP KEYS TERMINATED BY '\u0003'
STORED AS ORCFILE
LOCATION '/bigdata_dw/dwd/dwd_contract_order_receipt_status_log_da';