package v1.compile;

import java.util.ArrayList;
import java.util.List;

import v1.define.SymType;
import v1.define.Token;

/**
 * 词法分析程序
 * 
 * @author 87663
 *
 */

public class GetSym {
	
	//保留字
	final static private String[]	reservedWords	= { "begin", "end", "if", "then", "else",
			"const", "procedure", "var", "do", "while", "call", "read", "write", "odd", "repeat",
			"until" };
	
	//分析的结果
	private List<Token>				tokens;
	
	//当前的字符
	private char					ch;
	
	//当前的字符位置
	private int						chIndex;
	
	//需要分析的字符流
	private char[]					buffer;
	
	//当前的行
	private int						line;
	
	//当前的列
	private int						chIndexOfLine;
	
	//当前的Token中的字符串
	private StringBuilder			tokenStr;
	
	//当前的Token
	private Token					token;
	
	//分析到字符流末尾
	private boolean					end;
	
	//分析结束
	private boolean					finish;
	
	//是否允许忽略未知字符，true表示不允许
	private boolean					strictCheck;
	
	public GetSym(char[] buffer) {
		super();
		this.tokens			= new ArrayList<>();
		this.ch				= 0;
		this.chIndex		= 0;
		this.buffer			= buffer;
		this.line			= 1;
		this.chIndexOfLine	= 1;
		this.tokenStr		= null;
		this.token			= null;
		this.end			= false;
		this.finish			= false;
		this.strictCheck	= false;
	}
	
	public GetSym(char[] buffer, boolean strictCheck) {
		super();
		this.tokens			= new ArrayList<>();
		this.ch				= 0;
		this.chIndex		= 0;
		this.buffer			= buffer;
		this.line			= 1;
		this.chIndexOfLine	= 1;
		this.tokenStr		= null;
		this.token			= null;
		this.end			= false;
		this.finish			= false;
		this.strictCheck	= strictCheck;
	}
	
	public List<Token> getTokens() {
		if (!finish) {
			doAnalysis();
			finish = true;
		}
		return new ArrayList<>(tokens);
	}
	
	private void doAnalysis() {
		while (!end) {
			getToken();
			tokens.add(token);
		}
	}
	
	private void getToken() {
		tokenStr = new StringBuilder(10);
		getChar();
		while (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
			if (ch == '\n') {
				++line;
				chIndexOfLine = 0;
			}
			getChar();
		}
		if (isLetter()) {//是字母，为保留字或变量名
			
			do {
				tokenStr.append(ch);
				getChar();
			} while (isLetter() || isDigit());
			
			goBackOne();
			
			String temp = tokenStr.toString();
			
			for (int i = 0; i < reservedWords.length; ++i) {
				if (temp.equals(reservedWords[i])) { //说明是保留字
					token = new Token(SymType.values()[i], line, chIndexOfLine, null);
					return;
				}
			}
			
			//不是保留字，那么是标识符
			token = new Token(SymType.SYM, line, chIndexOfLine, temp);
			return;
			
		} else if (isDigit()) {//是数字常量
			
			do {
				tokenStr.append(ch);
				getChar();
			} while (isDigit());
			
			goBackOne();
			
			token = new Token(SymType.CONST, line, chIndexOfLine, tokenStr.toString());
			return;
			
		} else {
			switch (ch) {
				case '='://等号
					token = new Token(SymType.EQU, line, chIndexOfLine, null);
					return;
				case '+'://加号
					token = new Token(SymType.ADD, line, chIndexOfLine, null);
					return;
				case '-'://减号
					token = new Token(SymType.SUB, line, chIndexOfLine, null);
					return;
				case '*'://乘号
					token = new Token(SymType.MUL, line, chIndexOfLine, null);
					return;
				case '/'://除号
					token = new Token(SymType.DIV, line, chIndexOfLine, null);
					return;
				case '<'://小于 小于等于 不等于
					getChar();
					if (ch == '=') {
						token = new Token(SymType.LEQ, line, chIndexOfLine, null);
					} else if (ch == '>') {
						token = new Token(SymType.NEQ, line, chIndexOfLine, null);
					} else {
						goBackOne();
						token = new Token(SymType.LIT, line, chIndexOfLine, null);
					}
					return;
				case '>'://大于 大于等于
					getChar();
					if (ch == '=') {
						token = new Token(SymType.GRE, line, chIndexOfLine, null);
					} else {
						goBackOne();
						token = new Token(SymType.GEQ, line, chIndexOfLine, null);
					}
					return;
				case ','://逗号
					token = new Token(SymType.COMMA, line, chIndexOfLine, null);
					return;
				case ';'://分号
					token = new Token(SymType.SEMIC, line, chIndexOfLine, null);
					return;
				case '.'://点号
					token = new Token(SymType.POINT, line, chIndexOfLine, null);
					return;
				case '('://左括号
					token = new Token(SymType.LBR, line, chIndexOfLine, null);
					return;
				case ')'://右括号
					token = new Token(SymType.RBR, line, chIndexOfLine, null);
					return;
				case ':'://赋值号
					getChar();
					if (ch == '=') {
						token = new Token(SymType.CEQU, line, chIndexOfLine, null);
					} else {
						goBackOne();
						token = new Token(SymType.COL, line, chIndexOfLine, null);
					}
					return;
				case '$':
					token = new Token(SymType.EOF, line, chIndexOfLine, null);
					return;
				default:
					if (!strictCheck) {
						System.err.println(
								"Unkown char:\"" + ch + "\"in code, line " + line + " index "
										+ chIndexOfLine + " , ignored it");
					} else {
						System.err.println(
								"Unkown char:\"" + ch + "\"in code, line " + line + " index "
										+ chIndexOfLine + ", stop complie");
						tokens = null;
						return;
					}
			}
		}
	}
	
	private void getChar() {
		if (chIndex < buffer.length) {
			ch = buffer[chIndex++];
			++chIndexOfLine;
		} else {
			ch	= '$';
			end	= true;
		}
	}
	
	private boolean isLetter() {
		return Character.isLetter(ch);
	}
	
	private boolean isDigit() {
		return Character.isDigit(ch);
	}
	
	//当前的字符不属于该token，那么就回退一格
	private void goBackOne() {
		--chIndex;
		--chIndexOfLine;
		end = false;
	}
	
}
