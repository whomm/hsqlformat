package whomm.hsqlformat;

/**
 * Hello world!
 *
 */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;

import org.apache.hadoop.hive.ql.lib.Node;
import org.apache.hadoop.hive.ql.parse.ASTNode;
import org.apache.hadoop.hive.ql.parse.HiveParser;
import org.apache.hadoop.hive.ql.parse.ParseException;
import org.apache.hadoop.hive.ql.parse.ParseUtils;
import org.apache.commons.lang3.StringUtils;



public class App 
{
	public static int TABSPACE = 4;

	public static boolean CreateFormat(ASTNode node, StringBuilder sb, int tabLength, String appendbef, String appendend) {
		
		
		switch(node.getType()) {
		
		
		//创建表
		case HiveParser.TOK_CREATETABLE:
		{
			sb.append(StringUtils.repeat(" ", tabLength * App.TABSPACE));
			List<Node> c = node.getChildren();
			Collections.sort(c, new Comparator<Node>() {
	            public int compare(Node o1, Node o2) {
	            	return Integer.compare(((ASTNode)o1).getTokenStartIndex() , ((ASTNode)o2).getTokenStartIndex());
	            }
	        });
			
			sb.append("create");
        	for(Node i : c) {
        		
        		switch(((ASTNode)i).getType()) {
        		//
        		case HiveParser.TOK_LIKETABLE:
        			break;
        		//是否外表
        		case HiveParser.KW_EXTERNAL:
        			sb.append(" external talbe");
        			break;
        		//是否存在
        		case HiveParser.TOK_IFNOTEXISTS:
        			sb.append(" if not exists ");
        			break;
        		//表注释
        		case HiveParser.TOK_TABLECOMMENT:
        			sb.append("comment ");
        			for(Node i1 : i.getChildren()) {
        	        	sb.append(((ASTNode)i1).getText());
        			}
        			sb.append("\n");
        			break;
        			
        		//存储handler
        		case HiveParser.TOK_STORAGEHANDLER:
        		
        			sb.append("STORED BY ");
        			for (Node i1 : i.getChildren()) {
        				sb.append(((ASTNode)i1).getText());
        			}
        			sb.append("\n");
        			break;
        		
        		//分区列表
        		case HiveParser.TOK_TABLEPARTCOLS:
        			CreateFormat((ASTNode)i,sb,tabLength,"","");
        			break;
        		
        		//行格式
        		case HiveParser.TOK_TABLEROWFORMAT:	
        			sb.append("ROW FORMAT DELIMITED \n");
        			for (Node i1 : i.getChildren()) {
        				CreateFormat((ASTNode)i1,sb,tabLength,"","");
        			}
        			
        			break;
        		
        		//存储格式
        		case HiveParser.TOK_FILEFORMAT_GENERIC:
        			sb.append("STORED AS ");
        			for(Node i1 : i.getChildren()) {
        	        	sb.append(((ASTNode)i1).getText());
        			}
        			sb.append("\n");
        			break;
        		//存储位置
        		case HiveParser.TOK_TABLELOCATION:
        			sb.append("LOCATION ");
        			for(Node i1 : i.getChildren()) {
        	        	sb.append(((ASTNode)i1).getText());
        			}
        			sb.append("\n");
        			break;
        			
        		default:
        			CreateFormat((ASTNode)i,sb,tabLength,"","");
        			
        		}
        		
        		
        	}
        	
        	sb.append('\n');
			return true;
		}
		
		
		//行属性里面的分隔符
		case HiveParser.TOK_SERDEPROPS:
		{
			List<Node> c = node.getChildren();
        	for(Node i : c) {
        		sb.append(StringUtils.repeat(" ", (tabLength+1) * App.TABSPACE));
        		switch(((ASTNode)i).getType()) {
        		case HiveParser.TOK_TABLEROWFORMATFIELD:
        			sb.append("FIELDS TERMINATED BY"+ ((ASTNode)i.getChildren().get(0)).getText() + "\n");
        			break;
        		case HiveParser.TOK_TABLEROWFORMATCOLLITEMS:
        			sb.append("COLLECTION ITEMS TERMINATED BY"+ ((ASTNode)i.getChildren().get(0)).getText() + "\n");
        			break;
        		case HiveParser.TOK_TABLEROWFORMATMAPKEYS:
        			sb.append("MAP KEYS TERMINATED BY "+ ((ASTNode)i.getChildren().get(0)).getText() + "\n");
        		
        			break;
        		default:
        			sb.append("<Row FOMAT TODO:>\n");
                	sb.append(((ASTNode)node).getToken());
                	sb.append(((ASTNode)node).getText());
        		}
        		
        	}
        	
			return true;
		}
		
		
		//表名称
		case HiveParser.TOK_TABNAME:
		{
			
			List<Node> c = node.getChildren();
        	for(Node i : c) {
        		sb.append(((ASTNode)i).getText()+ ".");
        	}
        	sb.deleteCharAt(sb.length() - 1);
        	return true;
		}
		
		//表格属性列
		case HiveParser.TOK_TABLEPROPERTIES:
		{
			sb.append("TBLPROPERTIES(\n");
			
			List<Node> c0 = node.getChildren();
			for(Node c : c0) {
				if(((ASTNode)c).getType() == HiveParser.TOK_TABLEPROPLIST) {
					int no = 0;
		        	for(Node i : c.getChildren()) {
		        		if(no != 0) {
		        			CreateFormat((ASTNode)i, sb, tabLength+1,",","");
		        		} else {
		        			
		        			CreateFormat((ASTNode)i, sb, tabLength+1," ","");
		        		}
		        		
		        		no++;
		        	}
					
				}
			}
        	sb.append(StringUtils.repeat(" ", tabLength * App.TABSPACE));
			sb.append(")\n");
			
			return true;
		}
		
		
		//表格属性属性
		case HiveParser.TOK_TABLEPROPERTY:
		{
			
			sb.append(StringUtils.repeat(" ", tabLength * App.TABSPACE));
			sb.append(appendbef);
			List<Node> c = node.getChildren();
			int count = 0;
        	for(Node i : c) {
        		if (count>0) {
        			sb.append(" = ");
        		}
        		sb.append(((ASTNode)i).getText());
        		count++;
        	}
        	sb.append(appendend);
        	sb.append('\n');
			return true;
		}
		
		//分区列表
		case HiveParser.TOK_TABLEPARTCOLS:
		{
			sb.append("partitioned by (\n");
			List<Node> c = node.getChildren();
			int no = 0;
        	for(Node i : c) {
        		
        		if(no != 0) {
        			CreateFormat((ASTNode)i, sb, tabLength+1,",","");
        		} else {
        			CreateFormat((ASTNode)i, sb, tabLength+1," ","");
        		}
        		
        		no++;
        	}
			sb.append(")\n");
			
			return true;
		}
		//表格列数组
		case HiveParser.TOK_TABCOLLIST:
		{
			sb.append("(\n");
			List<Node> c = node.getChildren();
			int no = 0;
        	for(Node i : c) {
        		
        		if(no != 0) {
        			CreateFormat((ASTNode)i, sb, tabLength+1,",","");
        		} else {
        			CreateFormat((ASTNode)i, sb, tabLength+1," ","");
        		}
        		
        		no++;
        	}
			sb.append(")\n");
			
			return true;
		}
		//建表的具体列
		case HiveParser.TOK_TABCOL:
		{
			sb.append(StringUtils.repeat(" ", tabLength * App.TABSPACE));
			sb.append(appendbef);
			List<Node> c = node.getChildren();
        	for(Node i : c) {
        		switch(((ASTNode)i).getType()) {
        		//列名称
        		case HiveParser.Identifier: 
	        		{
	        			String name = ((ASTNode)i).getText();
	        			if(name.charAt(0) != '`') {
	        				sb.append('`');
	        				sb.append(name);
	        				sb.append('`');
	        			} else {
	        				sb.append(name);
	        			}
	        		}
        			break;
        			
        		//列类型
        		case HiveParser.TOK_STRING:
        			sb.append(" string");
        			break;
        		case HiveParser.TOK_INT:
        			sb.append(" int");
        			break;
        		case HiveParser.TOK_BIGINT:
        			sb.append(" bigint");
        			break;
        		case HiveParser.TOK_TINYINT:
        			sb.append(" tinyint");
        			break;
        			
        		//注释
        		case HiveParser.StringLiteral:
        			sb.append(" comment "+((ASTNode)i).getText());
        			break;
        		default:
        			
        			sb.append("<COLUMETYPE TODO:>");
        			sb.append(((ASTNode)i));
        			break;
        		}
        		
        	}
        	sb.append(appendend); 	
        	sb.append('\n');
        	return true;

		}
		
		
			
        	
			
        default:	
        
        	sb.append("<FOMAT TODO:>\n");
        	sb.append(((ASTNode)node).getToken());
        	sb.append(((ASTNode)node).getText());
        	
			return false;
		}
		
	}
	
	public static StringBuilder mydump(ASTNode tr,StringBuilder sb) {
		Deque<ASTNode> stack = new ArrayDeque<ASTNode>();
		HashMap<ASTNode,Boolean> vistmap = new HashMap<ASTNode,Boolean>();
		stack.push(tr);
		int tabLength = 0;
		
		while (!stack.isEmpty()) {
			ASTNode next = stack.peek();
			
			
			if (vistmap.get(next) == null) {
				
				if(next.getType() == HiveParser.TOK_CREATETABLE) {
					 App.CreateFormat(next, sb, tabLength,"","");
					 return sb;
				}
				sb.append(StringUtils.repeat(" ", tabLength * App.TABSPACE));
				//if (! App.CreateFormat(next, sb, tabLength,"","")) 
				{
					sb.append("<FOMAT TODO:>\n");
		        	sb.append(next.getToken());
		        	sb.append(next.getText());
			        
			        if (next.getChildCount() > 0) {
			        	List<Node> c = next.getChildren();
			        	for(Node i : c) {
			        		stack.push((ASTNode)i);
			        	}
			        }
				}		        
		        tabLength++;
		        vistmap.put(next, true);
			} else {
				
				tabLength--;
				stack.pop();
				
			}
		}
		return sb;
	}
	
    public static void main( String[] args )
    {
    	
    	try {
    		
    		String hsql="";
			try {
				hsql = new String(Files.readAllBytes(Paths.get("./a.sql")));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		String[] hsqls = hsql.split(";");
    		for( String s:hsqls) {
    			
    			//s = s.replaceAll("\n", " ");
    			System.out.println(s);
    			ASTNode tr = ParseUtils.parse(s);
    			System.out.println(tr.dump());
        		System.out.println(App.mydump(tr,new StringBuilder()).toString());
    		}
    		
		}
    	catch (ParseException e) {
			// TODO Auto-generated catch block
			System.out.println(e.getMessage());
			System.exit(1);
		}
		
    }

	
}
