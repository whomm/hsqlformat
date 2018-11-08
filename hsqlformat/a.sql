--nihao
INSERT INTO TABLE app.app_es_onlineclass_teacherpcapp_csx_di
SELECT teacher.teacher_id, to_date(MIN(log_teacher.create_time)) AS min_login_date
	, substr(MIN(log_teacher.create_time), 12, 8) AS min_login_time
	, to_date(MAX(log_teacher.create_time)) AS max_login_date
	, substr(MAX(log_teacher.create_time), 12, 8) AS max_login_time
	, to_date(MAX(sys1.create_time)) AS max_class_date
	, substr(MAX(sys1.create_time), 12, 8) AS max_class_time
FROM (
	SELECT xid AS teacher_id,
	m  `date`
	FROM stg.stg_vipkid_teacher_da
	WHERE pt = 1231232
		AND life_cycle = 'REGULAR'
) teacher
	JOIN (
		SELECT get_json_object(json_str, '$.teacherId') AS teacher_id, date_time AS create_time
		FROM dwd.dwd_log_teacher_portal_di
		WHERE pt >= 20180303
			AND pt <= sdfsdff
			AND log_event = 'page_view'
			AND log_url IN ('https://t.vipkid.com.cn/account/board', 'https://t.vipkid.com.cn/classrooms')
			AND log_user_agent LIKE '%VIPKIDTEACHER%'
	) log_teacher
	ON teacher.teacher_id = log_teacher.teacher_id
	LEFT JOIN (
		SELECT online_class_id, classroom, scheduled_date_time, finish_type, teacher_id
		FROM edw.edw_qos_first_supplier_da
		WHERE pt >= 20180303
			AND pt <= sdfsdfsdf
	) class
	ON teacher.teacher_id = class.teacher_id
	LEFT JOIN (
		SELECT classroom, create_time, browser
		FROM stg.stg_qos_online_class_user_sys_info_da
		WHERE pt = sdfsdf
			AND role = 'TEACHER'
			AND browser = 'VIPKIDTEACHER'
	) sys1
	ON class.classroom = sys1.classroom
GROUP BY teacher.teacher_id

