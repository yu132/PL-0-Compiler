package v1.compile;

import static v1.define.Operator.*;
import static v1.define.Pcode.*;
import static v1.define.SymType.*;

import java.util.ArrayList;
import java.util.List;

import v1.define.Operator;
import v1.define.Pcode;
import v1.define.SymType;
import v1.define.Symbol;
import v1.define.SymbolType;
import v1.define.Token;

/**
 * 完成语法分析和代码生成的程序
 * 
 * @author 87663
 *
 */
public class Block {
	
	//需要的Tokens
	private List<Token>			tokens;
	
	//生成的代码集合
	private List<Pcode>			pcodeTable;
	
	//符号表
	private SymbolTableManager	symTable;
	
	//错误信息
	private List<String>		errorMesgs;
	
	//是否发生错误
	private boolean				error;
	
	//当前的Token指针
	private int					tokenIndex;
	
	//程序层数
	private int					level;
	
	//当前层所使用的栈的大小，即下一个需要使用栈的变量应该占据的栈的位置
	private int					address;
	
	//是否已经完成
	private boolean				finish;
	
	public Block(List<Token> tokens) {
		super();
		this.tokens		= tokens;
		this.pcodeTable	= new ArrayList<>();
		this.errorMesgs	= new ArrayList<>();
		this.error		= false;
		this.tokenIndex	= 0;
		this.level		= 0;
		this.address	= 0;
		this.finish		= false;
		
		this.symTable	= new SymbolTableManager();
		symTable.addTable();
	}
	
	//获取生成的Pcode
	public List<Pcode> getPcode() {
		if (!finish) {
			doAnalysis();
			finish = true;
		}
		return new ArrayList<>(pcodeTable);
	}
	
	//程序编译过程中是否发生错误
	public boolean isError() {
		return error;
	}
	
	//获取错误信息
	public List<String> getErrorMesgs() {
		return new ArrayList<>(errorMesgs);
	}
	
	//进行程序语法分析并生成Pcode
	private void doAnalysis() {
		program();
	}
	
	//<主程序>::=<分程序>.
	private void program() {
		block();//“<分程序>”
		if (nowTokenType() == POINT) {// “.”
			advance();//跳过“.”
			if (nowTokenType() != EOF) {//若不是文件尾，有多余字符
				errorHandle(1);
			}
		} else {//缺少“.”
			errorHandle(2);
		}
	}
	
	//<分程序>::=[<常量说明部分>][<变量说明部分>][<过程说明部分>]<语句>
	private void block() {
		int addressTemp = address;//保存之前的address值
		addressReset();
		
		Symbol	procedure	= symTable.LastProcedure();//当前分程序所在过程
		
		Pcode	jmp			= code(JMP, 0, 0);//跳跃的地址之后回填，先设为0
		pcodeTable.add(jmp);//跳过声明部分，因为声明部分是子过程的内容
		
		conDeclare();//“[<常量说明部分>]”
		varDeclare();//“[<变量说明部分>]”
		proc();//“[<过程说明部分>]”
		
		jmp.setA(nowCodeAddress());//回填当前地址
		
		//申请分配栈空间，大小即address的大小，常量和变量声明会增加该大小
		pcodeTable.add(code(INT, 0, address));
		
		if (procedure != null) {//不是主函数，那么该过程的起始语句的地址要保存在符号表中
								//起始语句即上一句INT指令
			procedure.setValue(nowCodeAddress() - 1);
		}
		
		statement();//“<语句>”
		
		pcodeTable.add(code(OPR, 0, 0));//表示过程调用结束，返回调用点
		
		address = addressTemp;
	}
	
	//<常量说明部分>::=const <常量定义>{,<常量定义>};
	private void conDeclare() {
		if (nowTokenType() == CON) { //"CONST" 
			advance();//跳过“CONST”
			conDefine();//“<常量定义>”
			
			while (nowTokenType() == COMMA) {//“,”
				advance();//跳过“,”
				conDefine();//“<常量定义>”
			}
			
			semic();//“;”
		}
	}
	
	//<常量定义>::=<标识符>=<无符号整数>
	private void conDefine() {
		if (nowTokenType() != SYM) {//不是以标识符开头，缺少标识符
			errorHandle(4);
		}
		String constName = nowToken().getValue();//获取标识符名字
		if (symTable.getSymbol(constName, SymbolType.CONST) != null) {//若该常量标识符已存在
			errorHandle(7);
		}
		
		advance();//跳过标识符
		
		if (nowTokenType() == EQU || nowTokenType() == CEQU) {//“=”或“:=”
			
			if (nowTokenType() == CEQU) {//常量赋值中使用了错误的赋值号
				errorHandle(6);
			}
			advance();//跳过赋值号
			if (nowTokenType() == CONST) {//是数字
				int constNum = Integer.parseInt(nowToken().getValue());
				symTable.addSymbol(new Symbol(SymbolType.CONST, constNum,
						level, address, constName));//加入常量符号
				advance();//跳过数字
			} else {//缺少常量赋值用的数字
				errorHandle(8);
			}
		} else {//常量定义中缺少赋值号
			errorHandle(5);
		}
	}
	
	//<变量说明部分>::=var<标识符>{,<标识符>};
	private void varDeclare() {
		if (nowTokenType() == VAR) {//“VAR”
			
			do {
				advance();//跳过“VAR”或者“,”
				if (nowTokenType() != SYM) {//不是标识符
					errorHandle(4);
				}
				String varName = nowToken().getValue();//获取声明变量标识符的名称
				
				//已经存在该名称的常量标识符，因此也不能声明这种变量
				if (symTable.getSymbol(varName, SymbolType.CONST) != null) {
					errorHandle(9);
				}
				
				boolean success = symTable.addSymbol(new Symbol(SymbolType.VAR,
						0, level, address, varName));//加入变量符号
				
				if (!success) {//标识符重定义
					errorHandle(9);
				}
				
				addressAdvance();//栈上的位置加一，因为当前位置被这个变量占据
				advance();//跳过标识符
				
			} while (nowTokenType() == COMMA);//如果是“,” 那么后面肯定还定义了别的变量
			
			semic();//“;”
		}
	}
	
	//<过程说明部分>::=<过程首部><分程序>;{<过程说明部分>}
	//<过程首部>::=procedure<标识符>;
	//即<过程说明部分>::=procedure<标识符>;<分程序>;{<过程说明部分>}
	private void proc() {
		if (nowTokenType() == PROC) {
			advance();//跳过“PROCEDURE”
			
			if (nowTokenType() != SYM) {//不是以标识符开头，缺少标识符
				errorHandle(4);
			}
			
			String	procName	= nowToken().getValue();//获取声明过程标识符的名称
			
			boolean	success		= symTable.addSymbol(new Symbol(SymbolType.PRO,
					0, level, address, procName));//加入过程符号
			
			if (!success) {//标识符重定义
				errorHandle(10);
			}
			addressAdvance();//栈上地址加一，当前位置用于存储当前过程
			advance();//跳过“<标识符>”
			
			semic();//“;”
			
			levelAdvance();//进入分程序，深度加一
			
			symTable.addTable();//为该过程生成一张新的表
			
			block();//“<分程序>”
			
			symTable.addTable();//该过程的语法分析和代码生成已经结束，销毁该表
			
			levelGoBack();//退出分程序，深度减一
			
			semic();//“;”
			
			proc();//“{<过程说明部分>}”
		}
	}
	
	//<语句>::=<赋值语句> | <条件语句> | <当循环语句> | <过程调用语句> 
	//			| <复合语句> | <读语句> | <写语句> | <空>
	private void statement() {
		switch (nowTokenType()) {
			
			//<条件语句>::=if<条件>then<语句>[else<语句>]
			/*
			 *	会生成如下结构的代码
			 *	
			 *	else<语句>存在：
			 *	a <条件>
			 *	b JPC ? e
			 *	c <语句>
			 *	d JMP ? f
			 *	e <语句>
			 *  f ?
			 *
			 *	else<语句>不存在：
			 *	a <条件>
			 *	b JPC ? d
			 *	c <语句>
			 *  d ?
			 */
			case IF: {
				advance();//跳过“IF”
				condition();//“<条件>”
				
				if (nowTokenType() != THEN) {//条件语句缺少“THEN”
					errorHandle(12);
				}
				
				Pcode jpc = code(JPC, 0, 0);//条件转移代码，需要回填
				pcodeTable.add(jpc);//用于跳过if后面的语句，不执行这些语句
				
				advance();//跳过“THEN”
				
				statement();//“<语句>”
				
				jpc.setA(nowCodeAddress());//回填条件转移代码，不满足则跳到ELSE或结尾处
				
				if (nowTokenType() == ELS) {//“[else<语句>]”
					Pcode jmp = code(JMP, 0, 0);//无条件转移代码，需要回填
					pcodeTable.add(jmp);//是用于上面if后的语句执行完成后，跳过else部分的语句
					
					advance();//跳过“ELSE”
					statement();//“<语句>”
					
					jmp.setA(nowCodeAddress());//回填无条件转移代码，跳到ELSE后的这部分语句
				}
				return;
			}
			
			//<当循环语句>::=while<条件>do<语句>
			/*
			 * 生成如下结构的代码：
			 * a <条件>
			 * b JPC ? f
			 * c <语句>
			 * d JMP ? a
			 * f ?
			 */
			case WHI: {
				advance();//跳过“WHILE”
				
				int firstLine = nowCodeAddress();//获取第一行代码所在的位置
				
				condition();//“<条件>”
				
				if (nowTokenType() != DO) {
					errorHandle(13);//WHILE循环缺少DO语句
				}
				
				advance();//跳过“DO”
				
				Pcode jpc = code(JPC, 0, 0);//条件转移代码，需要回填
				pcodeTable.add(jpc);//用于跳过do后面的语句，不执行这些语句
				
				statement();//“<语句>”
				
				Pcode jmp = code(JMP, 0, firstLine);//无条件转移代码
				pcodeTable.add(jmp);//跳回第一条语句，即条件语句的第一条语句
				
				jpc.setA(nowCodeAddress());//回填条件转移代码，跳到最后面
				
				return;
			}
			
			//<过程调用语句>::=call<标识符>
			/*
			 * 编译后生成代码：
			 * a CAL level-pro.getLevel() L
			 * 
			 * L <子程序>
			 * 
			 */
			case CAL: {
				
				advance();//跳过“CALL”
				
				if (nowTokenType() != SYM) {
					errorHandle(4);
				}
				
				String	proName	= nowToken().getValue();//获取过程标识符名称
				
				//获取过程符号表示
				Symbol	pro		= symTable.getSymbol(proName, SymbolType.PRO);
				
				if (pro == null) {//调用未定义的过程
					errorHandle(14);
				}
				
				//调用过程，这时A段为被调用过程的过程体（过程体之前一条指令）在目标程序区的入口地址
				pcodeTable.add(code(Operator.CAL, level - pro.getLevel(), pro.getValue()));
				
				advance();//跳过“<标识符>”
				
				return;
			}
			
			//<读语句>::=read'('<标识符>{,<标识符>}')'
			case REA: {
				advance();//跳过“READ”
				if (nowTokenType() != LBR) {//缺少“(”
					errorHandle(15);
				}
				
				do {
					advance();//跳过“(”或“,”
					if (nowTokenType() != SYM) {//不是以标识符开头，缺少标识符
						errorHandle(4);
					}
					String	varName	= nowToken().getValue();//获取标识符名字
					Symbol	var		= symTable.getSymbol(varName, SymbolType.VAR);//获取变量符号
					if (var == null) {//该变量不存在
						errorHandle(17);
					}
					if (symTable.getSymbol(varName, SymbolType.CONST) != null) {//修改的是个常量
						errorHandle(18);
					}
					pcodeTable.add(code(OPR, 0, 16));//从命令行中读取一个数到栈顶
					
					//将刚刚读进来的值存到变量单元中
					pcodeTable.add(code(STO, level - var.getLevel(), var.getAddress()));
					
					advance();//跳过“<标识符>”
				} while (nowTokenType() == COMMA);//出现“,” 后面肯定还有“<标识符>”
				
				rightBracket();//“)”
				return;
			}
			
			//<写语句>::=write '('<表达式>{,<表达式>}')'
			case WRI: {
				
				advance();//跳过“WRITE”
				if (nowTokenType() != LBR) {//缺少“(”
					errorHandle(15);
				}
				
				do {
					advance();//跳过“(”或“,”
					expression();//“<表达式>”
					pcodeTable.add(code(OPR, 0, 14));//栈顶值输出至屏幕
				} while (nowTokenType() == COMMA);//出现“,” 后面肯定还有“<表达式>”
				
				pcodeTable.add(code(OPR, 0, 15));//输出换行
				
				rightBracket();//“)”
				return;
			}
			
			//<复合语句>::=begin<语句>{;<语句>}end
			case BEG: {
				do {
					advance();//跳过“BEGIN”或“;”
					statement();//“<语句>”
				} while (nowTokenType() == SEMIC);//若还有“;” 那么肯定后面还有语句
				
				if (nowTokenType() != END) {//缺少“END”
					errorHandle(11);
				}
				advance();//跳过“END”
				return;
			}
			
			//<赋值语句>::=<标识符>:=<表达式>
			case SYM: {
				
				String	varName	= nowToken().getValue();//获取标识符名称
				Symbol	var		= symTable.getSymbol(varName, SymbolType.VAR);//获取标识符对应的符号
				if (var == null) {//该变量不存在
					errorHandle(17);
				}
				if (symTable.getSymbol(varName, SymbolType.CONST) != null) {//修改的是个常量
					errorHandle(18);
				}
				
				advance();//跳过“<标识符>”
				
				if (nowTokenType() == EQU) {//赋值表达式使用等号
					errorHandle(19);
				} else if (nowTokenType() == COL) {//赋值表达式中:=只有:
					errorHandle(20);
				} else if (nowTokenType() != CEQU) {//不是“:=”
					errorHandle(21);
				}
				
				advance();//跳过“:=”
				
				expression();//“<表达式>”
				
				//将刚刚读进来的值存到变量单元中
				pcodeTable.add(code(STO, level - var.getLevel(), var.getAddress()));
				
				return;
			}
			
			//<重复语句> ::= repeat<语句>{;<语句>}until<条件>
			/*
			 * 生成以下语句：
			 * a <语句>
			 * b <条件>
			 * c JPC 0 a
			 */
			case REP: {
				int firstLine = nowCodeAddress();//获取第一行的地址
				do {
					advance();//跳过“REPEAT”或“;”
					statement();//“<语句>”
				} while (nowTokenType() == SEMIC);//遇到“;”证明后面还有“<语句>”
				
				if (nowTokenType() != UNT) {//缺少“UNTIL”
					errorHandle(21);
				}
				advance();//跳过“UNTIL”
				condition();//“<条件>”
				
				//条件转移语句，若为假，则转移回第一行
				pcodeTable.add(code(JPC, 0, firstLine));
				return;
			}
			default://非法的语句开始符号
				errorHandle(22);
				return;
		}
	}
	
	//<条件>::=<表达式><关系运算符><表达式> | odd<表达式>
	private void condition() {
		if (nowTokenType() == ODD) {//“ODD”
			advance();//跳过“ODD”
			expression();//odd<表达式>
			pcodeTable.add(code(OPR, 0, 6));//栈顶元素的奇偶判断，结果值在栈顶
		} else {
			expression();//“<表达式>”
			
			SymType op = nowTokenType();//保存“<关系运算符>”
			
			advance();//跳过“<关系运算符>”
			
			expression();//“<表达式>”
			
			switch (op) {
				case EQU://等于
					//次栈顶与栈顶是否相等，退两个栈元素，结果值进栈
					pcodeTable.add(code(OPR, 0, 8));
					return;
				case NEQ://不等于
					//次栈顶与栈顶是否不等，退两个栈元素，结果值进栈
					pcodeTable.add(code(OPR, 0, 9));
					return;
				case LIT://小于
					//次栈顶是否小于栈顶，退两个栈元素，结果值进栈
					pcodeTable.add(code(OPR, 0, 10));
					return;
				case LEQ://小于等于
					//次栈顶是否小于等于栈顶，退两个栈元素，结果值进栈
					pcodeTable.add(code(OPR, 0, 13));
					return;
				case GRE://大于
					//次栈顶是否大于栈顶，退两个栈元素，结果值进栈
					pcodeTable.add(code(OPR, 0, 12));
					return;
				case GEQ://大于等于
					//次栈顶是否大于等于栈顶，退两个栈元素，结果值进栈
					pcodeTable.add(code(OPR, 0, 11));
					return;
				default://出现未知的<关系运算符>
					errorHandle(23);
					return;
			}
			
		}
	}
	
	//<表达式>::=[+|-]<项>{<加减运算符><项>}
	//<加减运算符>::=+|-
	private void expression() {
		SymType first = nowTokenType();
		if (first == ADD || first == SUB) {//“+”或“-”
			advance();//跳过“+”或“-”
		}
		
		term();//“<项>”
		
		if (first == SUB) {//若开头的是“-”
			pcodeTable.add(code(OPR, 0, 1));//栈顶元素取反
		}
		
		//还有“+”或“-”，那么肯定还有{<加减运算符><项>}
		while (nowTokenType() == ADD || nowTokenType() == SUB) {
			SymType op = nowTokenType();//获取“<加减运算符>”的类型
			advance();//跳过“+”或“-”
			term();//“<项>”
			if (op == ADD) {//“<加减运算符>”是“+”
				//次栈顶与栈顶相加，退两个栈元素，结果值进栈
				pcodeTable.add(code(OPR, 0, 2));
			} else {//SUB  “<加减运算符>”是“-”
				//次栈顶减去栈顶，退两个栈元素，结果值进栈
				pcodeTable.add(code(OPR, 0, 3));
			}
		}
	}
	
	//<项>::=<因子>{<乘除运算符><因子>}
	//<乘除运算符>::=*|/
	private void term() {
		factor();//“<因子>”
		
		//还有“*”或“/”，那么肯定还有{<乘除运算符><因子>}
		while (nowTokenType() == MUL || nowTokenType() == DIV) {
			SymType op = nowTokenType();//获取“<乘除运算符>”的类型
			advance();//跳过“*”或“/”
			factor();//“<因子>”
			if (op == MUL) {//“<乘除运算符>”是“*”
				//次栈顶乘以栈顶，退两个栈元素，结果值进栈
				pcodeTable.add(code(OPR, 0, 4));
			} else {//DIV   “<乘除运算符>”是“/”
				//次栈顶除以栈顶，退两个栈元素，结果值进栈
				pcodeTable.add(code(OPR, 0, 5));
			}
		}
	}
	
	//<因子>::=<标识符> | <无符号整数> | '('<表达式>')'
	private void factor() {
		switch (nowTokenType()) {
			case CONST://“CONST” 那么是“<无符号整数>”
				//将常量送到运行栈S的栈顶，这时A段为常量值。
				pcodeTable.add(code(Operator.LIT, 0, Integer.parseInt(nowToken().getValue())));
				advance();//跳过“<无符号整数>”
				return;
			case LBR://“(”  那么是“(<表达式>)'”
				advance();//跳过“(”
				expression();//“<表达式>”
				rightBracket();//“)”
				return;
			case SYM://“<标识符>”
				String symName = nowToken().getValue();//获取标识符的名称
				Symbol sym = symTable.getSymbol(symName, SymbolType.CONST);
				if (sym != null) {//该标识符标识一个常量
					//将常量送到运行栈S的栈顶，这时A段为常量值。
					pcodeTable.add(code(Operator.LIT, 0, sym.getValue()));
				} else {
					sym = symTable.getSymbol(symName, SymbolType.VAR);
					if (sym == null) {//该标识符既不是常量也不是变量
						errorHandle(17);
					}
					//将变量送到运行栈S的栈顶，这时A段为变量所在说明层中的相对位置。
					pcodeTable.add(code(Operator.LOD, level - sym.getLevel(), sym.getAddress()));
				}
				advance();//跳过“<标识符>”
				return;
			default://表达式开头符号异常，出现未知的因子
				errorHandle(24);
				break;
		}
	}
	
	private void semic() {
		if (nowTokenType() != SEMIC) {//缺少“;”
			errorHandle(3);
		}
		advance();//跳过“;”
	}
	/*
	private void leftBracket() {
		if (nowTokenType() != LBR) {//缺少“(”
			errorHandle(15);
		}
		advance();//跳过“(”
	}*/
	
	private void rightBracket() {
		if (nowTokenType() != RBR) {//缺少“)”
			errorHandle(16);
		}
		advance();//跳过“)”
	}
	
	/**
	 * @return 当前index指向的Token的类型
	 */
	private SymType nowTokenType() {
		return tokens.get(tokenIndex).getType();
	}
	
	/**
	 * @return 当前index指向的Token
	 */
	private Token nowToken() {
		return tokens.get(tokenIndex);
	}
	
	/**
	 * 移进操作
	 */
	private void advance() {
		++tokenIndex;
	}
	
	/**
	 * 层数改变之后，重设当前层的栈大小，前3位用于保存
	 * 
	 * 当前层基地址、上一层（调用层）基地址、pc
	 */
	private void addressReset() {
		address = 3;//表示当前层所用栈大小
	}
	
	/**
	 * 当前栈位置被占用，将栈的大小加一
	 */
	private void addressAdvance() {
		++address;
	}
	
	/**
	 * @return 当前代码的地址
	 */
	private int nowCodeAddress() {
		return pcodeTable.size();
	}
	
	/**
	 * 进入下一层子程序，层数加一
	 */
	private void levelAdvance() {
		++level;
	}
	
	/**
	 * 退出子程序，层数减一
	 */
	private void levelGoBack() {
		--level;
	}
	
	/**
	 * @return 错误出现的Token的行号和列号
	 */
	private String lineIndexMesg() {
		return " in line " + nowToken().getLine() + " index " + nowToken().getIndex();
	}
	
	private void errorHandle(int errorCode) {
		error = true;
		switch (errorCode) {
			case 1://“.”后有多余字符
				System.err.println("There are letters after \".\"" + lineIndexMesg());
				break;
			case 2://缺少“.”
				System.err.println("Lack of \".\"" + lineIndexMesg());
				break;
			case 3://缺少“;”
				System.err.println("Lack of \";\"" + lineIndexMesg());
				break;
			case 4://需要标识符的地方缺少标识符
				System.err.println("Lack of \"identifier\"" + lineIndexMesg());
				break;
			case 5://常量定义中缺少赋值号
				System.err.println("Lack of \"=\"" + lineIndexMesg());
				break;
			case 6://常量赋值中使用了错误的赋值号
				System.err.println("Use \":=\" instead of \"=\"" + lineIndexMesg());
				break;
			case 7://常量标识符重定义
				System.err.println("Declare \"const identifier\":\"" + nowToken().getValue()
						+ "\" for more than one times." + lineIndexMesg());
				break;
			case 8://缺少常量赋值用的数字
				System.err.println("Lack of \"number\"" + lineIndexMesg());
				break;
			case 9://变量标识符重定义
				System.err.println("Declare \"variable identifier\":\"" + nowToken().getValue()
						+ "\" for more than one times." + lineIndexMesg());
				break;
			case 10://过程标识符重定义
				System.err.println("Declare \"procedure identifier\":\"" + nowToken().getValue()
						+ "\" for more than one times." + lineIndexMesg());
				break;
			case 11://缺少“END”
				System.err.println("Lack of \"END\"" + lineIndexMesg());
				break;
			case 12://条件语句缺少“THEN”
				System.err.println("Lack of \"THEN\"" + lineIndexMesg());
				break;
			case 13://WHILE循环缺少DO语句
				System.err.println("Lack of \"DO\"" + lineIndexMesg());
				break;
			case 14://调用未定义的过程
				System.err.println("Call undeclared \"procedure identifier\":\""
						+ nowToken().getValue() + "\"" + lineIndexMesg());
				break;
			case 15://缺少“(”
				System.err.println("Lack of \"(\"" + lineIndexMesg());
				break;
			case 16://缺少“)”
				System.err.println("Lack of \")\"" + lineIndexMesg());
				break;
			case 17://使用未定义的变量或常量
				System.err.println("Use undeclared \"const or variable identifier\":\""
						+ nowToken().getValue() + "\"" + lineIndexMesg());
				break;
			case 18://修改常量的值
				System.err.println("Change value of \"const\"" + lineIndexMesg());
				break;
			case 19://赋值表达式使用等号
				System.err.println("Misuse \"=\" instead of \":=\"" + lineIndexMesg());
				break;
			case 20://赋值表达式中:=只有:
				System.err.println("Lack of \"=\" in \":=\"" + lineIndexMesg());
				break;
			case 21://repeat语句缺少until
				System.err.println("Lack of \"UNTIL\"" + lineIndexMesg());
				break;
			case 22://<语句>定义中出现非法的语句开始符号
				System.err.println("Unkown \"token\"" + nowToken().getValue()
						+ " in the start of a sentence" + lineIndexMesg());
				break;
			case 23://<条件语句>中出现未知的<关系运算符>
				System.err.println("Unkown \"token\"" + nowToken().getValue()
						+ " in the place of condition operate" + lineIndexMesg());
				break;
			case 24://表达式开头符号异常
				System.err.println("Unkown \"token\"" + nowToken().getValue()
						+ " in the start of a expression" + lineIndexMesg());
				break;
		}
		
		throw new RuntimeException("Error occur!");//出现严重错误，编译无法继续下去，退出并抛出异常
	}
}
