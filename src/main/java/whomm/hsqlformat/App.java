package whomm.hsqlformat;

/**
 * Hello world!
 *
 */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import whomm.hsqlformat.hive.parse.FormatDriver;


public class App {

	

	
	public static void main(String[] args) {

		try {
			
			
			if (args.length <1 ) {
				System.out.println("Use: java -classpath hsqlformat.jar whomm.hsqlformat.App thesqlfile.sql");
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

				FormatDriver pd = new FormatDriver();
				if (s.trim().equals("")) {
					
				}
				else if(s.trim().toLowerCase().startsWith("set ")) {
					System.out.println(s.trim() + ";\n");
				} else {
					System.out.println(pd.Format(s, new StringBuilder()).toString() + ";\n");
				}
				
			}

		} catch (whomm.hsqlformat.hive.parse.ParseException e) {
			// TODO Auto-generated catch block
			System.out.println(e.getMessage());
			System.exit(1);
		}

	}
	
	
	/*
	public static void main(String[] args) {

		try {
			
			
		

			String hsql = "";
			try {
				hsql = new String(Files.readAllBytes(Paths.get("./f.sql")));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String[] hsqls = hsql.split(";");
			for (String s : hsqls) {

				FormatDriver pd = new FormatDriver();
				if (s.trim().equals("")) {
					
				}
				else if(s.trim().toLowerCase().startsWith("set ")) {
					System.out.println(s.trim() + ";\n");
				} else {
					System.out.println(pd.Format(s, new StringBuilder()).toString() + ";\n");
				}
				
			}

		} catch (whomm.hsqlformat.hive.parse.ParseException e) {
			// TODO Auto-generated catch block
			System.out.println(e.getMessage());
			System.exit(1);
		}
		

	}*/
	

}
