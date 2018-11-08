package whomm.hsqlformat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.util.JdbcConstants;
import com.alibaba.druid.sql.ast.SQLStatement;
import java.util.List;

public final class TheApp {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
        
		String hsql="";
		try {
			hsql = new String(Files.readAllBytes(Paths.get("./a.sql")));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String[] hsqls = hsql.split(";");
		for( String s:hsqls) {
			System.out.println(s);
			
			
			
			final String dbType = JdbcConstants.HIVE; // 可以是ORACLE、POSTGRESQL、SQLSERVER、ODPS等
			List<SQLStatement> stmtList =  SQLUtils.parseStatements(s, dbType);
			System.out.println(stmtList.toString());
			
			System.out.println(SQLUtils.format(s,JdbcConstants.HIVE,SQLUtils.DEFAULT_LCASE_FORMAT_OPTION)+";");  
		}
		
	}

}
