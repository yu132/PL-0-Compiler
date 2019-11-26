package v2.compile;

import static v2.define.SymType.*;

import java.util.ArrayList;
import java.util.List;

import v2.define.SymType;
import v2.define.Token;

/**
 * 词法分析程序
 * 
 * @author 87663
 *
 */

public class LexicalAnalyzer {
	
	//保留字
	final static private String[]	reservedWords	= { "begin", "end", "if", "then", "else",
			"const", "procedure", "var", "do", "while", "call", "read", "write", "odd", "repeat",
			"until", "list", "exit", "true", "false", "and", "or", "not", "sand", "sor", "retpro",
			"for", "when", "change", "pass", "flag", "break", "continue" };
	
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
	
	public LexicalAnalyzer(char[] buffer) {
		super();
		
		if (buffer == null) {
			throw new NullPointerException("buffer should not be null");
		}
		
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
	
	public LexicalAnalyzer(char[] buffer, boolean strictCheck) {
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
					token(getByName(temp.toUpperCase()));
					return;
				}
			}
			
			//不是保留字，那么是标识符
			token(SYM, temp);
			return;
			
		} else if (isDigit()) {//是数字常量
			
			do {
				tokenStr.append(ch);
				getChar();
			} while (isDigit());
			
			goBackOne();
			
			token(NUMBER, tokenStr.toString());
			return;
			
		} else {
			switch (ch) {
				case '='://等号
					token(EQU);
					return;
				case '+'://加号
					token(ADD);
					return;
				case '-'://减号
					token(SUB);
					return;
				case '*'://乘号
					token(MUL);
					return;
				case '/'://除号
					token(DIV);
					return;
				case '<'://小于 小于等于 不等于
					getChar();
					if (ch == '=') {
						token(LEQ);
					} else if (ch == '>') {
						token(NEQ);
					} else if (ch == '<') {
						token(BITWISE_SHIFT_LEFT);
					} else {
						goBackOne();
						token(LIT);
					}
					return;
				case '>'://大于 大于等于
					getChar();
					if (ch == '=') {
						token(GEQ);
					} else if (ch == '>') {
						token(BITWISE_SHIFT_RIGHT);
					} else {
						goBackOne();
						token(GRE);
					}
					return;
				case ','://逗号
					token(COMMA);
					return;
				case ';'://分号
					token(SEMIC);
					return;
				case '.'://点号
					token(POINT);
					return;
				case '('://左括号
					getChar();
					if (ch == '*') {//是注释
						while (true) {//中间跳过字符
							getChar();
							if (ch == '*') {//如果遇到'*)' 那么就是注释的结尾
								getChar();
								if (ch == ')') {//如果是结尾就跳出，不是就继续判断
									break;
								}
							}
						}
						getToken();//因为刚刚遇到的是注释，而不是一个Token，因此我们必须
									//获得下一个token来满足这次调用需要的
					} else {
						goBackOne();
						token(LBR);
					}
					return;
				case ')'://右括号
					token(RBR);
					return;
				case ':'://赋值号
					getChar();
					if (ch == '=') {
						token(CEQU);
					} else {
						goBackOne();
						token(COL);
					}
					return;
				case '$':
					token(EOF);
					return;
				case '[':
					token(LSB);
					return;
				case ']':
					token(RSB);
					return;
				case '%':
					token(MOD);
					return;
				case '&':
					token(BITWISE_AND);
					return;
				case '|':
					token(BITWISE_OR);
					return;
				case '`':
					token(BITWISE_NOT);
					return;
				case '^':
					token(BITWISE_XOR);
					return;
				default:
					if (!strictCheck) {
						System.err.println(
								"Unkown char:\"" + ch + "\"in code, line " + line + " index "
										+ chIndexOfLine + " , ignored it");
						getToken();
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
	
	private void token(SymType symType) {
		token(symType, null);
	}
	
	private void token(SymType symType, String value) {
		token = new Token(symType, line, chIndexOfLine, value);
	}
	
	private void getChar() {
		if (ch == '\n') {
			++line;
			chIndexOfLine = 0;
		}
		if (chIndex < buffer.length) {
			ch = buffer[chIndex++];
			++chIndexOfLine;
		} else {
			ch	= '$';
			end	= true;
			++chIndex;
		}
		//	System.out.println(ch);
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
