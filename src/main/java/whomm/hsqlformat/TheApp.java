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


		try {


			if (args.length <1 ) {
				System.out.println("Use: java -classpath hsqlformat.jar whomm.hsqlformat.TheApp thesqlfile.sql");
				System.exit(1);
			}

			String hsql = "";
			try {
				hsql = new String(Files.readAllBytes(Paths.get(args[0])));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String[] hsqls = hsql.split(";");
			for (String s : hsqls) {

				if (s.trim().equals("")) {
				}
				else if(s.trim().toLowerCase().startsWith("set ")) {
					System.out.println(s.trim() + ";\n");
				} else {
					final String dbType = JdbcConstants.HIVE; // 可以是ORACLE、POSTGRESQL、SQLSERVER、ODPS等
					List<SQLStatement> stmtList =  SQLUtils.parseStatements(s, dbType);
					System.out.println(SQLUtils.format(s,JdbcConstants.HIVE,SQLUtils.DEFAULT_LCASE_FORMAT_OPTION)+";");
				}

			}
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}



	}

}
