package whomm.hsqlformat.hive.parse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CharStream;
import org.antlr.runtime.CommonToken;
import org.antlr.runtime.NoViableAltException;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.Token;
import org.antlr.runtime.TokenRewriteStream;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeAdaptor;
import org.antlr.runtime.tree.TreeAdaptor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FormatDriver.
 *
 */
public class FormatDriver {

	// 注释节点和输出位置
	public class CommentsWhere {
		public Token Comment; // 这个是评论节点
		public Token Where; // 这个是应该放到哪里
		@Override
		public String toString(){
			String w = "";
			if(this.Where != null) {
				w=this.Where.toString();
			}
			return "\n["+this.Comment.toString()+"\n" +w+"]";
		}
	}

	public Deque<CommentsWhere> AllCommentsDeq;

	public Deque<ASTNode> allnodelist;

	public static int TABSPACE = 4;

	private static final Logger LOG = LoggerFactory.getLogger("hive.ql.parse.FormatDriver");

	public class ANTLRNoCaseStringStream extends ANTLRStringStream {

		public ANTLRNoCaseStringStream(String input) {
			super(input);
		}

		@Override
		public int LA(int i) {

			int returnChar = super.LA(i);
			if (returnChar == CharStream.EOF) {
				return returnChar;
			} else if (returnChar == 0) {
				return returnChar;
			}

			return Character.toUpperCase((char) returnChar);
		}
	}

	public class HiveLexerX extends HiveLexer {

		private final ArrayList<ParseError> errors;

		public HiveLexerX() {
			super();
			errors = new ArrayList<ParseError>();
		}

		public HiveLexerX(CharStream input) {
			super(input);
			errors = new ArrayList<ParseError>();
		}

		@Override
		public void displayRecognitionError(String[] tokenNames, RecognitionException e) {

			errors.add(new ParseError(this, e, tokenNames));
		}

		@Override
		public String getErrorMessage(RecognitionException e, String[] tokenNames) {
			String msg = null;

			if (e instanceof NoViableAltException) {
				@SuppressWarnings("unused")
				NoViableAltException nvae = (NoViableAltException) e;
				// for development, can add
				// "decision=<<"+nvae.grammarDecisionDescription+">>"
				// and "(decision="+nvae.decisionNumber+") and
				// "state "+nvae.stateNumber
				msg = "character " + getCharErrorDisplay(e.c) + " not supported here";
			} else {
				msg = super.getErrorMessage(e, tokenNames);
			}

			return msg;
		}

		public ArrayList<ParseError> getErrors() {
			return errors;
		}

	}

	public class MyCommonTreeAdaptor extends CommonTreeAdaptor {

		private HiveParser ps = null;

		public MyCommonTreeAdaptor(HiveParser ps) {
			this.ps = ps;
		}

		@Override
		public Object create(Token payload) {
			return new ASTNode(payload);
		}

		@Override
		public Object dupNode(Object t) {

			return create(((CommonTree) t).token);
		};

		@Override
		public Token createToken(int tokenType, String text) {

			CommonToken ct = new CommonToken(tokenType, text);

			/*
			 * if (LOG.isDebugEnabled()) { LOG.debug("create token: " + text);
			 * LOG.debug(((Token) ps.xstarttokens.peek()).toString()); }
			 */

			Token t = (Token) ps.xstarttokens.peek();
			ct.setLine(t.getLine());
			ct.setCharPositionInLine(t.getCharPositionInLine());
			return ct;
		}

		@Override
		public Object dupTree(Object t, Object parent) {
			// Overriden to copy start index / end index, that is needed through
			// optimization,
			// e.g., for masking/filtering
			ASTNode astNode = (ASTNode) t;
			ASTNode astNodeCopy = (ASTNode) super.dupTree(t, parent);
			astNodeCopy.setTokenStartIndex(astNode.getTokenStartIndex());
			astNodeCopy.setTokenStopIndex(astNode.getTokenStopIndex());
			return astNodeCopy;
		}

		@Override
		public Object errorNode(TokenStream input, Token start, Token stop, RecognitionException e) {
			return new ASTErrorNode(input, start, stop, e);
		};
	};

	public ASTNode parse(String command) throws ParseException {
		

		HiveLexerX lexer = new HiveLexerX(new ANTLRNoCaseStringStream(command));
		TokenRewriteStream tokens = new TokenRewriteStream(lexer);

		tokens.fill();
		List<Token> alltokens = (List<Token>) tokens.getTokens();

		this.AllCommentsDeq = new LinkedList<CommentsWhere>();
		for (Token t : alltokens) {
			if (t.getChannel() == lexer.COMMENTS) {

				CommentsWhere cw = new CommentsWhere();
				cw.Comment = t;
				cw.Where = null;
				AllCommentsDeq.addLast(cw);
			}
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("Parsing command: " + command);
			LOG.debug("All comments:"+AllCommentsDeq.toString());
		}
		

		HiveParser parser = new HiveParser(tokens);
		MyCommonTreeAdaptor adaptor = new MyCommonTreeAdaptor(parser);
		parser.setTreeAdaptor(adaptor);
		HiveParser.statement_return r = null;
		try {
			r = parser.statement();
		} catch (RecognitionException e) {
			e.printStackTrace();
			throw new ParseException(parser.errors);
		}

		if (lexer.getErrors().size() == 0 && parser.errors.size() == 0) {
			LOG.debug("Parse Completed");
		} else if (lexer.getErrors().size() != 0) {
			throw new ParseException(lexer.getErrors());
		} else {
			throw new ParseException(parser.errors);
		}

		ASTNode tree = (ASTNode) r.getTree();
		tree.setUnknownTokenBoundaries();
		return tree;
	}

	private void UpdateCommentsWhere(ASTNode tree) {

		allnodelist = new LinkedList<ASTNode>();
		SortAllnodes(tree);
		for (CommentsWhere i : AllCommentsDeq) {

			ASTNode last = null;
			for (ASTNode j : allnodelist) {

				if (j.getLine() < i.Comment.getLine() || (j.getLine() == i.Comment.getLine()
						&& j.getCharPositionInLine() <= i.Comment.getCharPositionInLine())) {

					if (last != null) {
						i.Where = last.getToken();
					}

					break;
				}

				last = j;

			}

		}
	}

	private void SortAllnodes(ASTNode tree) {

		allnodelist.addFirst(tree);
		List<Node> c = tree.getChildren();
		if (c != null) {
			Collections.sort(c, new Comparator<Node>() {
				public int compare(Node o1, Node o2) {
					return Integer.compare(((ASTNode) o1).getTokenStartIndex(), ((ASTNode) o2).getTokenStartIndex());
				}
			});

			for (Node i : c) {
				SortAllnodes((ASTNode) i);
			}
		}

	}

	private static String KeywordFormat(String key, boolean keyup) {

		if (keyup) {
			return key.toUpperCase();
		}
		return key.toLowerCase();
	}

	private boolean NeedRemoveLastEnter(StringBuilder sb) {
		// 空行 或 上一行是注释 不要删除\n
		if (sb.length() > 1 && sb.charAt(sb.length() - 1) == '\n' && sb.charAt(sb.length() - 2) == '\n') {
			// 空行
			return false;
		}
		if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {

			int lastcommetline = sb.lastIndexOf("--");
			if (lastcommetline >= 0) {
				String lastline = sb.substring(lastcommetline, sb.length());
				int entercount = 0;
				for (char a : lastline.toCharArray()) {
					if (a == '\n') {
						entercount++;
					}

				}
				if (entercount == 1) {// 最后一行是注释
					return false;

				}

			}

		}

		return true;
	}

	private void OutputComments(ASTNode node, StringBuilder sb) {
		try {
			if (AllCommentsDeq.size() > 0) {

				CommentsWhere firstcomment = AllCommentsDeq.getFirst();

				while (firstcomment.Where == null || firstcomment.Where == node.getToken()) {

					// 判断一下上一行是否是空行 或 注释 如果是不要删除\n
					if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n' && NeedRemoveLastEnter(sb)) {
						sb.delete(sb.length() - 1, sb.length());
						sb.append(StringUtils.repeat(" ", FormatDriver.TABSPACE));
						sb.append("--" + firstcomment.Comment.getText().replaceAll("^-*", ""));
						sb.append('\n');
					} else {

						sb.append(firstcomment.Comment.getText());
						sb.append('\n');
					}
					try {
						AllCommentsDeq.removeFirst();
						firstcomment = AllCommentsDeq.getFirst();
					} catch (NoSuchElementException e) {
						break;
					}

				}

			}

		} catch (NoSuchElementException e) {
			e.printStackTrace();
		}
	}

	public boolean CreateFormat(boolean KeyUpper, ASTNode node, StringBuilder sb, int tabLength,

			String appendbef, String appendend) {

		// 输出注释
		OutputComments(node, sb);

		switch (node.getType()) {

		// 创建表
		case HiveParser.TOK_CREATETABLE: {
			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			List<Node> c = node.getChildren();
			Collections.sort(c, new Comparator<Node>() {
				public int compare(Node o1, Node o2) {
					return Integer.compare(((ASTNode) o1).getTokenStartIndex(), ((ASTNode) o2).getTokenStartIndex());
				}
			});

			sb.append(KeywordFormat("create", KeyUpper));
			boolean puttable = false;
			for (Node i : c) {

				switch (((ASTNode) i).getType()) {
				//
				case HiveParser.TOK_LIKETABLE:
					break;
				// 是否外表
				case HiveParser.KW_EXTERNAL:
					sb.append(KeywordFormat(" external", KeyUpper));
					sb.append(KeywordFormat(" table", KeyUpper));
					puttable = true;
					break;
				// 是否存在
				case HiveParser.TOK_IFNOTEXISTS:

					if (!puttable) {
						sb.append(KeywordFormat(" table", KeyUpper));
						puttable = true;
					}
					sb.append(KeywordFormat(" if not exists ", KeyUpper));

					break;

				// 表名称
				case HiveParser.TOK_TABNAME: {
					if (!puttable) {
						sb.append(KeywordFormat(" table", KeyUpper));
						puttable = true;
					}
					sb.append(" ");
					List<Node> c1 = ((ASTNode) i).getChildren();
					for (Node i1 : c1) {
						sb.append(((ASTNode) i1).getText() + ".");
					}
					sb.deleteCharAt(sb.length() - 1);
					break;
				}
				// 表注释
				case HiveParser.TOK_TABLECOMMENT:
					sb.append(KeywordFormat("comment ", KeyUpper));
					for (Node i1 : i.getChildren()) {
						sb.append(((ASTNode) i1).getText());
					}
					sb.append("\n");
					break;

				// 存储handler
				case HiveParser.TOK_STORAGEHANDLER:

					sb.append(KeywordFormat("STORED BY ", KeyUpper));
					for (Node i1 : i.getChildren()) {
						sb.append(((ASTNode) i1).getText());
					}
					sb.append("\n");
					break;

				// 分区列表
				case HiveParser.TOK_TABLEPARTCOLS:
					CreateFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");
					break;

				// 行格式
				case HiveParser.TOK_TABLEROWFORMAT:
					sb.append(KeywordFormat("ROW FORMAT DELIMITED \n", KeyUpper));
					for (Node i1 : i.getChildren()) {
						CreateFormat(KeyUpper, (ASTNode) i1, sb, tabLength, "", "");
					}

					break;

				// 存储格式
				case HiveParser.TOK_FILEFORMAT_GENERIC:
					sb.append(KeywordFormat("STORED AS ", KeyUpper));
					for (Node i1 : i.getChildren()) {
						sb.append(((ASTNode) i1).getText());
					}
					sb.append("\n");
					break;
				// 存储位置
				case HiveParser.TOK_TABLELOCATION:
					sb.append(KeywordFormat("LOCATION ", KeyUpper));
					for (Node i1 : i.getChildren()) {
						sb.append(((ASTNode) i1).getText());
					}
					sb.append("\n");
					break;

				default:
					CreateFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");

				}

			}

			sb.append('\n');
			return true;
		}

		// 行属性里面的分隔符
		case HiveParser.TOK_SERDEPROPS: {
			List<Node> c = node.getChildren();
			for (Node i : c) {
				sb.append(StringUtils.repeat(" ", (tabLength + 1) * FormatDriver.TABSPACE));
				switch (((ASTNode) i).getType()) {
				case HiveParser.TOK_TABLEROWFORMATFIELD:
					sb.append(KeywordFormat("FIELDS TERMINATED BY", KeyUpper));
					sb.append(((ASTNode) i.getChildren().get(0)).getText() + "\n");
					break;
				case HiveParser.TOK_TABLEROWFORMATCOLLITEMS:
					sb.append(KeywordFormat("COLLECTION ITEMS TERMINATED BY", KeyUpper));
					sb.append(((ASTNode) i.getChildren().get(0)).getText() + "\n");
					break;
				case HiveParser.TOK_TABLEROWFORMATMAPKEYS:
					sb.append(KeywordFormat("MAP KEYS TERMINATED BY ", KeyUpper));
					sb.append(((ASTNode) i.getChildren().get(0)).getText() + "\n");

					break;
				default:
					sb.append("<ROW FOMAT TODO:>\n");
					sb.append(((ASTNode) node).getToken());
					sb.append(((ASTNode) node).getText());
				}

			}

			return true;
		}

		// 表格属性列
		case HiveParser.TOK_TABLEPROPERTIES: {
			sb.append(KeywordFormat("TBLPROPERTIES(\n", KeyUpper));

			List<Node> c0 = node.getChildren();
			for (Node c : c0) {
				if (((ASTNode) c).getType() == HiveParser.TOK_TABLEPROPLIST) {
					int no = 0;
					for (Node i : c.getChildren()) {
						if (no != 0) {
							CreateFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, ",", "");
						} else {

							CreateFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, " ", "");
						}

						no++;
					}

				}
			}
			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(")\n");

			return true;
		}

		// 表格属性属性
		case HiveParser.TOK_TABLEPROPERTY: {

			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(appendbef);
			List<Node> c = node.getChildren();
			int count = 0;
			for (Node i : c) {
				if (count > 0) {
					sb.append(" = ");
				}
				sb.append(((ASTNode) i).getText());
				count++;
			}
			sb.append(appendend);
			sb.append('\n');
			return true;
		}

		// 分区列表
		case HiveParser.TOK_TABLEPARTCOLS: {
			sb.append(KeywordFormat("partitioned by (\n", KeyUpper));
			List<Node> c = node.getChildren();
			int no = 0;
			for (Node i : c) {

				if (no != 0) {
					CreateFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, ",", "");
				} else {
					CreateFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, " ", "");
				}

				no++;
			}
			sb.append(")\n");

			return true;
		}
		// 表格列数组
		case HiveParser.TOK_TABCOLLIST: {
			sb.append("(\n");
			List<Node> c = node.getChildren();
			int no = 0;
			for (Node i : c) {

				if (no != 0) {
					CreateFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, ",", "");
				} else {
					CreateFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, " ", "");
				}

				no++;
			}
			sb.append(")\n");

			return true;
		}
		// 建表的具体列
		case HiveParser.TOK_TABCOL: {
			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(appendbef);
			List<Node> c = node.getChildren();
			for (Node i : c) {
				switch (((ASTNode) i).getType()) {
				// 列名称
				case HiveParser.Identifier: {
					String name = ((ASTNode) i).getText();
					if (name.charAt(0) != '`') {
						sb.append('`');
						sb.append(name);
						sb.append('`');
					} else {
						sb.append(name);
					}
				}
					break;

				// 列类型
					
				case HiveParser.TOK_DOUBLE:
					sb.append(KeywordFormat(" double", KeyUpper));
					break;
				case HiveParser.TOK_FLOAT:
					sb.append(KeywordFormat(" float", KeyUpper));
					break;
				case HiveParser.TOK_STRING:
					sb.append(KeywordFormat(" string", KeyUpper));
					break;
				case HiveParser.TOK_INT:
					sb.append(KeywordFormat(" int", KeyUpper));
					break;
				case HiveParser.TOK_BIGINT:
					sb.append(KeywordFormat(" bigint", KeyUpper));
					break;
				case HiveParser.TOK_SMALLINT:
					sb.append(KeywordFormat(" smallint", KeyUpper));
					break;
				case HiveParser.TOK_TINYINT:
					sb.append(KeywordFormat(" tinyint", KeyUpper));
					break;
				case HiveParser.TOK_DECIMAL:
					sb.append(KeywordFormat(" decimal(", KeyUpper)); {
					List<Node> c1 = ((ASTNode) i).getChildren();
					for (Node i1 : c1) {
						sb.append(((ASTNode) i1).getText() + ",");
					}
					sb.deleteCharAt(sb.length() - 1);
				}
					sb.append(')');
					break;

				// 注释
				case HiveParser.StringLiteral:
					sb.append(KeywordFormat(" comment ", KeyUpper));
					sb.append(((ASTNode) i).getText());
					break;
				// 数组
				case HiveParser.TOK_LIST:
					sb.append(KeywordFormat(" array<", KeyUpper)); {
					List<Node> c1 = ((ASTNode) i).getChildren();
					for (Node i1 : c1) {
						sb.append(HiveParser.xlate("KW" + ((ASTNode) i1).getText().substring(3)) + ",");
					}
					sb.deleteCharAt(sb.length() - 1);
				}
					sb.append('>');
					break;
				default:

					sb.append("<COLUMETYPE TODO:>");
					sb.append(((ASTNode) i));
					break;
				}

			}
			sb.append(appendend);
			sb.append('\n');
			return true;

		}

		default:

			sb.append("<CREATE FOMAT TODO:>\n");
			sb.append(((ASTNode) node).getToken());
			sb.append(((ASTNode) node).getText());

			return false;
		}

	}

	public boolean QueryFormat(boolean KeyUpper, ASTNode node, StringBuilder sb, int tabLength, String appendbef,
			String appendend) {

		// 输出注释
		OutputComments(node, sb);

		// 当前的二元操作符号
		String nowopration = "";

		switch (node.getType()) {

		// insert into
		case HiveParser.TOK_INSERT_INTO: {
			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(appendbef);

			sb.append("insert into \n");

			List<Node> c1 = ((ASTNode) node).getChildren();
			for (Node i1 : c1) {
				QueryFormat(KeyUpper, (ASTNode) i1, sb, tabLength + 1, " ", "");
			}

			sb.append(appendend);
			return true;
		}
		
		case HiveParser.KW_LOCAL :{
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append(" local ");
			
			sb.append(appendend);
			return true;
		}
		// table
		case HiveParser.TOK_TAB: {

			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(appendbef);
			sb.append("table ");
			List<Node> c1 = ((ASTNode) node).getChildren();
			for (Node i1 : c1) {
				QueryFormat(KeyUpper, (ASTNode) i1, sb, tabLength + 1, "", "\n");
			}

			sb.append(appendend);
			return true;
		}
		//具体partition
		case HiveParser.TOK_PARTVAL:{
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			List<Node> c = node.getChildren();
			boolean first = true;
			for (Node i : c) {

				if (!first) {
					sb.append(KeywordFormat("=", KeyUpper));
					QueryFormat(KeyUpper, (ASTNode) i, sb, 0, "", "");
				} else {
					QueryFormat(KeyUpper, (ASTNode) i, sb, 0, "", "");
				}

				first = false;
			}
			
			sb.append(appendend);
			
			return true;
		}
		//partitons 列表
		case HiveParser.TOK_PARTSPEC:{
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append(" partition(\n");
			List<Node> c = node.getChildren();

			boolean first = true;
			for (Node i : c) {

				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, first ? " " : ",", "\n");

				first = false;

			}
			sb.append(")");
			sb.append(appendend);
			return true;
		}
		//insert 目标
		case HiveParser.TOK_DESTINATION: {

			
			List<Node> c1 = ((ASTNode) node).getChildren();

			for (Node i1 : c1) {
				switch (((ASTNode) i1).getType()) {
				case HiveParser.TOK_TAB: {
					
					sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
					sb.append(appendbef);
					
					sb.append("insert overwrite table ");
					List<Node> c2 = ((ASTNode) i1).getChildren();
					for (Node i2  : c2) {
						QueryFormat(KeyUpper, (ASTNode) i2, sb, tabLength + 1, "", "");
					}
					sb.append("\n");
					sb.append(appendend);
					break;
				}
				case HiveParser.TOK_DIR: {
					
					List<Node> c2 = ((ASTNode) i1).getChildren();
					Collections.sort(c2, new Comparator<Node>() {
						public int compare(Node o1, Node o2) {
							return Integer.compare(((ASTNode) o1).getTokenStartIndex(), ((ASTNode) o2).getTokenStartIndex());
						}
					});
					
					if(c2.size() == 1 && ((ASTNode)c2.get(0)).getType() == HiveParser.TOK_TMP_FILE) {
						//临时文件运行需求
						return true;
					}
					
					sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
					sb.append(appendbef);
					
					sb.append("insert overwrite ");
					
					int index = 0;
					for (Node i2  : c2) {
						
						if (index == 1)
						{
							sb.append(" directory ");
						}
						switch (((ASTNode) i2).getType()) {
							
						
						default:
							QueryFormat(KeyUpper, (ASTNode) i2, sb, tabLength + 1, "", "");
							
						
						}
						index++;
					}
					sb.append("\n");
					sb.append(appendend);
					
					
					break;
				}
				default:
					QueryFormat(KeyUpper, (ASTNode) i1, sb, tabLength + 1, "", "\n");
				}
			}

			
			return true;
		}

		// 表引用-》表名称
		case HiveParser.TOK_TABREF: {

			List<Node> c = node.getChildren();
			int index = 0;
			for (Node i : c) {

				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");
				index++;
			}
			sb.append("\n");
			return true;
		}
		// 表名称
		case HiveParser.TOK_TABNAME: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);

			List<Node> c1 = ((ASTNode) node).getChildren();
			for (Node i1 : c1) {
				sb.append(((ASTNode) i1).getText() + ".");
			}
			sb.deleteCharAt(sb.length() - 1);

			sb.append(appendend);
			return true;
		}
		// select 列表
		case HiveParser.TOK_SELECTDI: {
			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(appendbef);

			sb.append(KeywordFormat("select distinct", KeyUpper));
			sb.append(KeywordFormat("\n", KeyUpper));
			List<Node> c = node.getChildren();
			boolean first = true;
			for (Node i : c) {

				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, first ? " " : ",", "");
				first = false;
			}

			sb.append(appendend);
			return true;
		}
		// select 列表
		case HiveParser.TOK_SELECT: {

			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(appendbef);

			sb.append(KeywordFormat(HiveParser.xlate("KW" + node.getText().substring(3)), KeyUpper));
			sb.append(KeywordFormat("\n", KeyUpper));
			List<Node> c = node.getChildren();
			boolean first = true;
			for (Node i : c) {

				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, first ? " " : ",", "");
				first = false;
			}

			sb.append(appendend);
			return true;
		}

		// distinct 的函数
		case HiveParser.TOK_FUNCTIONDI: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);

			List<Node> c0 = ((ASTNode) node).getChildren();
			boolean isfirst = true;
			boolean isfirstpar = true;
			for (Node i0 : c0) {

				if (isfirst) {

					sb.append(KeywordFormat(((ASTNode) i0).getText(), KeyUpper));
					sb.append("(");
					sb.append(KeywordFormat("DISTINCT ", KeyUpper));
					isfirst = false;
				} else {

					QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength, isfirstpar == false ? "," : "", "");
					isfirstpar = false;
				}

			}

			sb.append(")");

			sb.append(appendend);

			return true;
		}
		case HiveParser.TOK_FUNCTION: {

			// sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);

			List<Node> c0 = ((ASTNode) node).getChildren();
			boolean isfirst = true;
			boolean isfirstpar = true;

			String funcappendend = "";

			Node firstnode = c0.get(0);
			String funcname = ((ASTNode) firstnode).getText().toLowerCase();

			if (funcname.equals("row_number")) {
				for (Node i0 : c0) {

					if (isfirst) {
						sb.append(" row_number() ");
						isfirst = false;
					} else {

						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength + 1, isfirstpar == false ? "," : "", "");
						isfirstpar = false;
					}
				}
			}
			// 类型转换
			else if (funcname.equals("tok_int")) {

				funcappendend = " as int)";

				int i = 0;
				for (Node i0 : c0) {

					if (isfirst) {
						sb.append("cast(");
						isfirst = false;
					} else {
						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength + 1, isfirstpar == false ? "," : "", "");
						isfirstpar = false;
						i++;
					}
				}

				sb.append(funcappendend);

			} else if (funcname.equals("isnull") || funcname.equals("isnotnull")) {
				// is null

				if (funcname.equals("isnull")) {
					funcappendend = " is  null";
				} else if (funcname.equals("isnotnull")) {
					funcappendend = " is not null";
				}

				int i = 0;
				for (Node i0 : c0) {

					if (isfirst) {
						isfirst = false;
					} else {
						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength, isfirstpar == false ? "," : "", "");
						isfirstpar = false;
						i++;
					}
				}

				sb.append(funcappendend);

			} else if (funcname.equals("between")) {
				// in

				funcappendend = "";

				int i = 0;
				for (Node i0 : c0) {

					if (i == 0 || i==1) {

					} else if (i == 2) {
						sb.append(" ");
						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength, "", "");
						sb.append(" BETWEEN ");
					} else if (i == 3) {
						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength, "", "");
						sb.append(" and ");
					} else {
						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength, isfirstpar == false ? "" : "", "");
						isfirstpar = false;
					}
					i++;

				}

				sb.append(funcappendend);

			} else if (funcname.equals("in")) {
				// in

				funcappendend = ")";

				int i = 0;
				for (Node i0 : c0) {

					if (i == 0) {

					} else if (i == 1) {
						sb.append("\n");
						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength, "", "");
						sb.append(" in (");
					} else {
						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength, isfirstpar == false ? "," : "", "");
						isfirstpar = false;
					}
					i++;

				}

				sb.append(funcappendend);

			} else if (funcname.equals("when")) {

				//
				// 1） 把里面的所有的 换行去掉： case when 里面嵌套 case when 的情况会受到影响。另外里面如果有注释就会有语法错误
				// 2) 单独一个sb ， 然后每行append 偏移量 ，然后合并到当前的里面来。
				// 3) 最后选择了  case when 特殊处理 ，其他各个分支根据是否之前有换行 决定自己是否换行和缩进
				
				
				//这个tableng 是单独定义的
				int casetablength = (sb.length() - sb.lastIndexOf("\n"))/4+1;
				int i = 0;
				int clen = c0.size();
				for (Node i0 : c0) {

					if (isfirst) {
						if (sb.charAt(sb.length() - 1) == '\n') {
							// 新行的注意一下
							sb.append(StringUtils.repeat(" ", casetablength * FormatDriver.TABSPACE));
						} else {
							sb.append("\n");
							sb.append(StringUtils.repeat(" ", casetablength * FormatDriver.TABSPACE));
						}
						sb.append("case");
						isfirst = false;
					} else {
						if (i % 2 == 0 && i + 2 != clen) {
							sb.append("\n");
							sb.append(StringUtils.repeat(" ", (casetablength + 1) * FormatDriver.TABSPACE));
							sb.append("when ");

							QueryFormat(KeyUpper, (ASTNode) i0, sb, (casetablength + 2), "", "");
							i++;
							continue;
						}
						if (i % 2 == 0 && i + 2 == clen) {
							// 这个地方是是最后一个 需要用else ,间隔1个就到end了
							sb.append("\n");
							sb.append(StringUtils.repeat(" ", (casetablength + 1) * FormatDriver.TABSPACE));
							sb.append("else ");

							QueryFormat(KeyUpper, (ASTNode) i0, sb, (casetablength + 2), "", "");
							i++;
							continue;
						}

						if (i % 2 == 1) {
							sb.append("\n");
							sb.append(StringUtils.repeat(" ", (casetablength + 2) * FormatDriver.TABSPACE));
							sb.append("then ");

							QueryFormat(KeyUpper, (ASTNode) i0, sb, (casetablength + 3), "", "");
							i++;
							continue;

						}

						// System.out.println(((ASTNode) i0).getToken());
						QueryFormat(KeyUpper, (ASTNode) i0, sb, (casetablength + 1), "", "");
						i++;
					}

				}
				sb.append("\n");
				sb.append(StringUtils.repeat(" ", casetablength * FormatDriver.TABSPACE));
				sb.append("end");

			} else { // 常规的函数都能用这种形式

				funcappendend = ")";
				int i = 0;
				for (Node i0 : c0) {

					if (isfirst) {
						sb.append(KeywordFormat(getTokenName((ASTNode) i0), KeyUpper));
						sb.append("(");
						isfirst = false;
					} else {
						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength + 1, isfirstpar == false ? "," : "", "");
						isfirstpar = false;
						i++;
					}

				}

				sb.append(funcappendend);
			}

			sb.append(appendend);

			return true;
		}

		// select 下面直接的item

		case HiveParser.TOK_TABLE_OR_COL: {

			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);

			List<Node> c0 = ((ASTNode) node).getChildren();
			for (Node i0 : c0) {

				sb.append(((ASTNode) i0).getText());
			}

			sb.append(appendend);
			return true;
		}

		// select -> TOK_SELEXPR 里面跟着的 a.id 这样的
		case HiveParser.DOT: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}

			sb.append(appendbef);

			List<Node> c0 = ((ASTNode) node).getChildren();
			for (Node i0 : c0) {

				switch (((ASTNode) i0).getType()) {
				case HiveParser.Identifier: {
					sb.append(KeywordFormat(".", KeyUpper));

					sb.append(((ASTNode) i0).getText());
					break;
				}

				case HiveParser.TOK_TABLE_OR_COL: {
					List<Node> c1 = ((ASTNode) i0).getChildren();
					for (Node i1 : c1) {

						sb.append(((ASTNode) i1).getText());
					}
					break;
				}
				default:
					QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength, "", "");

				}
			}

			sb.append(appendend);
			return true;

		}

		// select item
		case HiveParser.TOK_SELEXPR: {

			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(appendbef);

			List<Node> c = node.getChildren();
			for (Node i : c) {

				switch (((ASTNode) i).getType()) {

				case HiveParser.StringLiteral:

					sb.append(((ASTNode) i).getText());

					break;

				case HiveParser.Identifier: {
					sb.append(KeywordFormat(" as ", KeyUpper));

					sb.append(((ASTNode) i).getText());

				}
					break;

				case HiveParser.TOK_TABLE_OR_COL: {
					List<Node> c0 = ((ASTNode) i).getChildren();
					for (Node i0 : c0) {

						sb.append(((ASTNode) i0).getText());
					}
					break;
				}

				// 这个是*号 什么都没有
				case HiveParser.TOK_SETCOLREF: {
					sb.append("*");
					break;
				}
				case HiveParser.TOK_ALLCOLREF: {
					sb.append("*");
					break;
				}

				default:

					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");
				}
			}
			sb.append("\n");
			sb.append(appendend);
			return true;
		}

		// from
		case HiveParser.TOK_FROM: {

			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));

			sb.append(KeywordFormat(HiveParser.xlate("KW" + node.getText().substring(3)), KeyUpper));
			sb.append(KeywordFormat("\n", KeyUpper));
			List<Node> c = node.getChildren();
			for (Node i : c) {
				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
			}
			return true;
		}

		// where
		case HiveParser.TOK_WHERE: {

			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(KeywordFormat(getTokenName((ASTNode) node), KeyUpper));
			// sb.append(KeywordFormat(HiveParser.xlate("KW" + node.getText().substring(3)),
			// KeyUpper));
			sb.append(KeywordFormat("\n", KeyUpper));
			List<Node> c = node.getChildren();
			for (Node i : c) {
				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
			}

			sb.append("\n");
			return true;

		}

		case HiveParser.STAR:

		case HiveParser.GREATERTHAN:

		case HiveParser.EQUAL:

		case HiveParser.GREATERTHANOREQUALTO:

		case HiveParser.LESSTHANOREQUALTO:

		case HiveParser.NOTEQUAL:

		case HiveParser.LESSTHAN:

		case HiveParser.KW_LIKE:

		case HiveParser.PLUS:
		case HiveParser.MINUS:
		case HiveParser.MOD:
		case HiveParser.BITWISEXOR:
		case HiveParser.DIVIDE: {

			if (node.getType() == HiveParser.STAR) {
				nowopration = "*";
			} else if (node.getType() == HiveParser.GREATERTHAN) {
				nowopration = ">";
			} else if (node.getType() == HiveParser.EQUAL) {
				nowopration = "=";
			} else if (node.getType() == HiveParser.GREATERTHANOREQUALTO) {
				nowopration = ">=";
			} else if (node.getType() == HiveParser.LESSTHANOREQUALTO) {
				nowopration = "<=";
			} else if (node.getType() == HiveParser.NOTEQUAL) {
				nowopration = "!="; // 这个地方有两个写法需要注意 <> 和 !=
			} else if (node.getType() == HiveParser.LESSTHAN) {
				nowopration = "<";
			} else if (node.getType() == HiveParser.KW_LIKE) {
				nowopration = " like ";
			} else if (node.getType() == HiveParser.PLUS) {
				nowopration = "+";
			} else if (node.getType() == HiveParser.MINUS) {
				nowopration = "-";
			} else if (node.getType() == HiveParser.MOD) {
				nowopration = "%";
			} else if (node.getType() == HiveParser.BITWISEXOR) {
				nowopration = "^";
			} else if (node.getType() == HiveParser.DIVIDE) {
				nowopration = "/";
			}

			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}

			// sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(appendbef);
			List<Node> c = node.getChildren();
			boolean first = true;
			for (Node i : c) {

				if (!first) {
					sb.append(KeywordFormat(nowopration, KeyUpper));
					QueryFormat(KeyUpper, (ASTNode) i, sb, 0, "", "");
				} else {
					QueryFormat(KeyUpper, (ASTNode) i, sb, 0, "", "");
				}

				first = false;
			}
			sb.append(appendend);
			return true;
		}
		case HiveParser.KW_OR: {
			if (!appendbef.equals("")) {
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
				sb.append(appendbef);
				sb.append("\n");
			}

			List<Node> c = node.getChildren();
			boolean first = true;
			for (Node i : c) {

				if (!first) {
					sb.append(KeywordFormat("\n", KeyUpper));
					sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
					sb.append(KeywordFormat("or", KeyUpper));
					sb.append(KeywordFormat("\n", KeyUpper));
					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
				} else {
					if (((ASTNode) i).getType() == HiveParser.KW_AND || ((ASTNode) i).getType() == HiveParser.KW_OR) {
						QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");
					} else {
						QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
					}
				}

				first = false;
			}
			sb.append("\n");
			if (!appendend.equals("")) {
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
				sb.append(appendend);
				sb.append("\n");
			}

			return true;
		}
		case HiveParser.KW_AND: {

			// sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(appendbef);

			List<Node> c = node.getChildren();
			boolean first = true;
			for (Node i : c) {

				if (!first) {
					sb.append(KeywordFormat("\n", KeyUpper));
					sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
					sb.append(KeywordFormat("and", KeyUpper));
					sb.append(KeywordFormat("\n", KeyUpper));

					if (((ASTNode) i).getType() == HiveParser.KW_OR) {
						QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "(", ")");
					} else {
						QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
					}

					// QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");

				} else {

					if (((ASTNode) i).getType() == HiveParser.KW_AND || ((ASTNode) i).getType() == HiveParser.KW_OR) {

						if (((ASTNode) i).getType() == HiveParser.KW_OR) {
							QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "(", ")");
						} else {
							QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");
						}

					} else {
						QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
					}

				}

				first = false;
			}
			sb.append(appendend);
			return true;
		}

		// row number -----------------------------
		case HiveParser.TOK_SORTBY: {

			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append(" sort by ");
			List<Node> c = node.getChildren();

			boolean first = true;
			for (Node i : c) {

				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, first ? "" : ",", "");

				first = false;

			}
			sb.append(appendend);
			return true;

		}
		case HiveParser.TOK_PARTITIONINGSPEC: {

			List<Node> c = node.getChildren();

			sb.append(" over( ");
			int index = 0;
			for (Node i : c) {
				// System.out.println(((ASTNode)i).getToken());

				switch (((ASTNode) i).getType()) {
				case HiveParser.TOK_DISTRIBUTEBY: {
					sb.append(" partition by ");
					List<Node> c0 = ((ASTNode) i).getChildren();

					index = 0;
					for (Node i0 : c0) {

						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength + 1, index == 0 ? " " : ",", "");
						index++;
					}

					break;
				}

				case HiveParser.TOK_ORDERBY: {
					sb.append(" order by ");
					List<Node> c0 = ((ASTNode) i).getChildren();
					index = 0;
					for (Node i0 : c0) {
						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength + 1, index == 0 ? " " : ",", "");
						index++;
					}

					break;
				}
				default:
					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
				}

			}
			sb.append(KeywordFormat(")", KeyUpper));
			return true;
		}
		case HiveParser.TOK_WINDOWSPEC: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			List<Node> c = node.getChildren();

			boolean first = true;
			for (Node i : c) {

				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");

				first = false;

			}
			sb.append(appendend);
			return true;
		}

		// row number end--------------------------------

		// 正则匹配
		case HiveParser.KW_REGEXP: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append(KeywordFormat(" REGEXP(", KeyUpper));
			List<Node> c = node.getChildren();

			boolean first = true;
			for (Node i : c) {

				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, first ? "" : ",", "");

				first = false;

			}
			sb.append(")\n");
			sb.append(appendend);
			return true;

		}
		// group by
		case HiveParser.TOK_GROUPBY: {
			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(KeywordFormat("group by", KeyUpper));
			sb.append("\n");
			List<Node> c = node.getChildren();

			int index = 0;
			for (Node i : c) {
				// System.out.println(((ASTNode)i).getToken());

				switch (((ASTNode) i).getType()) {
				case HiveParser.TOK_TABLE_OR_COL: {

					List<Node> c0 = ((ASTNode) i).getChildren();
					for (Node i0 : c0) {
						sb.append(StringUtils.repeat(" ", (tabLength + 1) * FormatDriver.TABSPACE));
						sb.append(index == 0 ? " " : ",");
						sb.append(((ASTNode) i0).getText());
						sb.append("\n");
					}

					break;
				}
				default:
					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, index == 0 ? " " : ",", "\n");
				}

				index++;

			}
			sb.append(KeywordFormat("\n", KeyUpper));
			return true;

		}

		// order by 孙 子节点
		case HiveParser.TOK_NULLS_LAST:
		case HiveParser.TOK_NULLS_FIRST: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			List<Node> c = node.getChildren();
			for (Node i : c) {
				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, appendbef, appendend);
			}
			sb.append(appendend);
			return true;
		}

		// order by 直接 子节点
		// asc
		case HiveParser.TOK_TABSORTCOLNAMEASC: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			List<Node> c = node.getChildren();
			int index = 0;
			for (Node i : c) {
				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
			}
			sb.append(KeywordFormat(" asc ", KeyUpper));
			sb.append(appendend);
			return true;
		}
		// desc
		case HiveParser.TOK_TABSORTCOLNAMEDESC: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			List<Node> c = node.getChildren();
			int index = 0;
			for (Node i : c) {
				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
			}
			sb.append(KeywordFormat(" desc ", KeyUpper));
			sb.append(appendend);
			return true;
		}

		// order by
		case HiveParser.TOK_ORDERBY: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append(KeywordFormat("order by", KeyUpper));
			sb.append("\n");
			List<Node> c0 = ((ASTNode) node).getChildren();
			int index = 0;
			for (Node i0 : c0) {
				QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength + 1, index == 0 ? " " : ",", "\n");
				index++;
			}
			sb.append(appendend);
			return true;
		}

		// limit
		case HiveParser.TOK_LIMIT: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append("limit\n");
			List<Node> c0 = ((ASTNode) node).getChildren();
			int index = 0;
			for (Node i0 : c0) {
				QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength + 1, index == 0 ? " " : ",", "\n");
				index++;
			}
			sb.append(appendend);
			return true;
		}

		// union all
		case HiveParser.TOK_UNIONALL: {

			List<Node> c = node.getChildren();
			boolean first = true;
			for (Node i : c) {

				if (!first) {
					sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
					sb.append(KeywordFormat("union all", KeyUpper));
					sb.append(KeywordFormat("\n", KeyUpper));
				}

				if (((ASTNode) i).getType() == HiveParser.TOK_UNIONALL) {
					// 如果是union all 嵌套的
					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");
				} else {
					// 如果是union all 具体的 子query 缩进加1
					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
				}

				first = false;

			}
			return true;
		}
		case HiveParser.TOK_FULLOUTERJOIN:
		case HiveParser.TOK_CROSSJOIN:
		case HiveParser.TOK_JOIN:
		case HiveParser.TOK_UNIQUEJOIN:

		case HiveParser.TOK_RIGHTOUTERJOIN:
		case HiveParser.TOK_LEFTSEMIJOIN:
		case HiveParser.TOK_LEFTOUTERJOIN: {
			// sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));

			// sb.append(KeywordFormat("left outer join ", KeyUpper));
			// sb.append(KeywordFormat("\n", KeyUpper));

			if (node.getType() == HiveParser.TOK_FULLOUTERJOIN) {
				nowopration = "full outer join";
			} else if (node.getType() == HiveParser.TOK_CROSSJOIN) {
				nowopration = "cross join";
			} else if (node.getType() == HiveParser.TOK_JOIN) {
				nowopration = "join";
			} else if (node.getType() == HiveParser.TOK_UNIQUEJOIN) {
				nowopration = "uniq join"; // 这个地方不知道是什么join
			} else if (node.getType() == HiveParser.TOK_RIGHTOUTERJOIN) {
				nowopration = "right out join";
			} else if (node.getType() == HiveParser.TOK_LEFTSEMIJOIN) {
				nowopration = "left semi join";
			} else if (node.getType() == HiveParser.TOK_LEFTOUTERJOIN) {
				nowopration = "left outer join ";
			}

			List<Node> c = node.getChildren();
			int index = 0;
			for (Node i : c) {

				if (index == 1) {
					sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
					sb.append(KeywordFormat(nowopration, KeyUpper));
					sb.append(KeywordFormat("\n", KeyUpper));
				}
				if (index == 2) {
					sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
					sb.append(KeywordFormat("on", KeyUpper));
					sb.append(KeywordFormat("\n", KeyUpper));
				}

				if (((ASTNode) i).getType() == HiveParser.TOK_LEFTOUTERJOIN
						|| ((ASTNode) i).getType() == HiveParser.TOK_JOIN) {

					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");
				} else {

					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "\n");
				}

				index++;

			}
			return true;
		}

		// 子查询
		case HiveParser.TOK_SUBQUERY: {
			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(KeywordFormat("(\n", KeyUpper));
			List<Node> c = node.getChildren();
			Collections.sort(c, new Comparator<Node>() {
				public int compare(Node o1, Node o2) {
					return Integer.compare(((ASTNode) o1).getTokenStartIndex(), ((ASTNode) o2).getTokenStartIndex());
				}
			});

			String subname = "";
			for (Node i : c) {
				switch (((ASTNode) i).getType()) {

				// 里面的具体query
				case HiveParser.TOK_QUERY: {
					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
					break;
				}
				// 最后一个别名
				case HiveParser.Identifier: {
					// 获取一下当前别名
					subname = ((ASTNode) i).getText();
					break;

				}
				default:
					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength + 1, "", "");
				}

			}

			sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			sb.append(") " + subname + "\n");
			return true;
		}

		// sub query 例如 x in (select * from a)
		case HiveParser.TOK_SUBQUERY_EXPR: {
			List<Node> c = node.getChildren();
			Collections.sort(c, new Comparator<Node>() {
				public int compare(Node o1, Node o2) {
					return Integer.compare(((ASTNode) o1).getTokenStartIndex(), ((ASTNode) o2).getTokenStartIndex());
				}
			});

			for (Node i : c) {
				if (((ASTNode) i).getType() != HiveParser.TOK_QUERY
						&& ((ASTNode) i).getType() != HiveParser.TOK_SUBQUERY_OP) {
					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");
				}

			}
			for (Node i : c) {

				if (((ASTNode) i).getType() == HiveParser.TOK_QUERY) {
					sb.append("\n");
					sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
					sb.append("(\n");
					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");

					sb.append("\n");
					sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
					sb.append(")\n");
					continue;
				}

				if (((ASTNode) i).getType() == HiveParser.TOK_SUBQUERY_OP) {
					List<Node> c0 = ((ASTNode) i).getChildren();
					for (Node i0 : c0) {
						QueryFormat(KeyUpper, (ASTNode) i0, sb, tabLength, "", "");
					}
					continue;
				}

			}

			return true;

		}
		// query
		case HiveParser.TOK_QUERY: {
			sb.append(appendbef);
			List<Node> c = node.getChildren();

			// 把里面的节点重新排一下序
			// insert 节点包含一些
			// from 节点包含一些
			List<Node> xx = new ArrayList<Node>();
			for (Node i : c) {

				switch (((ASTNode) i).getType()) {
				case HiveParser.TOK_INSERT: {
					List<Node> c0 = ((ASTNode) i).getChildren();
					xx.addAll(c0);
					break;
				}
				case HiveParser.TOK_FROM: {
					xx.add(i);
					break;
				}

				default:
					xx.add(i);
				}
			}
			Collections.sort(xx, new Comparator<Node>() {
				public int compare(Node o1, Node o2) {
					return Integer.compare(((ASTNode) o1).getTokenStartIndex(), ((ASTNode) o2).getTokenStartIndex());
				}
			});

			for (Node i : xx) {

				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");

			}
			sb.append(appendend);
			return true;

		}

		case HiveParser.KW_IN: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append(KeywordFormat(" in ", KeyUpper));

			sb.append(appendend);
			return true;

		}

		case HiveParser.KW_NOT: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append(KeywordFormat(" not ", KeyUpper));
			List<Node> c = node.getChildren();

			for (Node i : c) {

				QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");

			}
			sb.append(appendend);
			return true;

		}
		case HiveParser.TOK_NULL: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append(KeywordFormat(HiveParser.xlate("KW" + node.getText().substring(3)), KeyUpper));
			sb.append(appendend);
			return true;
		}
		// 这个地方是个数字
		case HiveParser.Number: {

			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);

			sb.append(((ASTNode) node).getText());

			sb.append(appendend);
			return true;
		}

		case HiveParser.Identifier: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append(node.getText());
			sb.append(appendend);
			return true;
		}

		case HiveParser.StringLiteral:
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);
			sb.append(((ASTNode) node).getText());
			sb.append(appendend);
			return true;

		// 数组下标取数据的情况 比如 select split(a,'-')[1] as xname, a2[1], a3['aaa'][1] from b;
		case HiveParser.LSQUARE: {
			if (sb.charAt(sb.length() - 1) == '\n') {
				// 新行的注意一下
				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));
			}
			sb.append(appendbef);

			List<Node> c = node.getChildren();
			boolean first = true;
			for (Node i : c) {

				if (!first) {
					sb.append("[");
					sb.append(((ASTNode) i).getText());
					sb.append("]");
				} else {
					QueryFormat(KeyUpper, (ASTNode) i, sb, tabLength, "", "");
				}

				first = false;

			}

			sb.append(appendend);
			return true;
		}
		default:
			sb.append("<QUERY FOMAT TODO:>");
			sb.append(((ASTNode) node).getToken());
			sb.append(((ASTNode) node).getText());
			sb.append("\n");

			return false;

		}

	}

	public StringBuilder Format(String sql, StringBuilder sb) throws ParseException {

		// 解析
		ASTNode tr = this.parse(sql);

		LOG.debug(tr.dump());
		// 更新注释位置
		this.UpdateCommentsWhere(tr);
		
		LOG.debug(this.AllCommentsDeq.toString());
		
		Deque<ASTNode> stack = new ArrayDeque<ASTNode>();
		HashMap<ASTNode, Boolean> vistmap = new HashMap<ASTNode, Boolean>();
		stack.push(tr);
		int tabLength = 0;

		while (!stack.isEmpty()) {
			ASTNode next = stack.peek();

			if (vistmap.get(next) == null) {

				sb.append(StringUtils.repeat(" ", tabLength * FormatDriver.TABSPACE));

				if (next.getType() == HiveParser.TOK_CREATETABLE) {
					CreateFormat(true, next, sb, tabLength, "", "");
					return sb;
				}

				if (next.getType() == HiveParser.TOK_QUERY) {
					QueryFormat(true, next, sb, tabLength, "", "");
					return sb;
				}

				if (!(next.getText() == null || next.getType() == HiveParser.EOF)) {
					sb.append("<dump FOMAT TODO:>\n");
					sb.append(next.getToken());
					sb.append(next.getText());
					sb.append("<dump FOMAT END:>\n");
				}

				if (next.getChildCount() > 0) {

					List<Node> c = next.getChildren();
					for (Node i : c) {
						stack.push((ASTNode) i);
					}
				}

				if (next.getToken() != null) {
					tabLength++;
				}

				vistmap.put(next, true);
			} else {
				if (next.getToken() != null) {
					tabLength--;
				}
				stack.pop();

			}
		}
		return sb;

	}

	public String getTokenName(ASTNode node) {

		String k = node.getText().replaceAll("^TOK", "KW");
		String n = HiveParser.getKeyStr(k);
		if (n != null) {
			return n;
		}
		return node.getText();

	}
}
