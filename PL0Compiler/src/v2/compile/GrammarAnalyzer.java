package v2.compile;

import static v2.define.Operator.*;
import static v2.define.SymType.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import v2.define.Operator;
import v2.define.Pcode;
import v2.define.SymType;
import v2.define.Symbol;
import v2.define.SymbolType;
import v2.define.Token;

/**
 * 完成语法分析和代码生成的程序
 * 
 * @author 87663
 *
 */
public class GrammarAnalyzer {
	
	/* TODO 
	 * 
	 * 一元运算      
	 * else if
	 * 为赋值语句添加运算如+= -= *=等
	 * 
	 */
	
	//需要的Tokens
	private List<Token>				tokens;
	
	//生成的代码集合
	private List<Pcode>				pcodeTable;
	
	//符号表
	private SymbolTableManager		symTable;
	
	//专门用来处理break和Continue的数据结构
	private BreakContinueHandler	bcHandle;
	
	//错误信息
	private List<String>			errorMesgs;
	
	//是否发生错误
	private boolean					error;
	
	//当前的Token指针
	private int						tokenIndex;
	
	//程序层数
	private int						level;
	
	//当前层所使用的栈的大小，即下一个需要使用栈的变量应该占据的栈的位置
	private int						address;
	
	//是否已经完成
	private boolean					finish;
	
	private boolean					arrayOutOfBoundCheck	= true;
	
	public GrammarAnalyzer(List<Token> tokens) {
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
		this.bcHandle	= new BreakContinueHandler();
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
	
	//<分程序>::=[<常量说明部分>][<变量说明部分>][<数组声明过程>][<过程说明部分>]<语句>
	private void block() {
		int addressTemp = address;//保存之前的address值
		addressReset();
		
		Symbol	procedure	= symTable.LastProcedure();//当前分程序所在过程
		
		//这里只有主过程才有必要跳过，子过程是不用跳过的，因为直接转跳到后面的语句了
		Pcode	jmp			= null;
		if (level == 0) {
			jmp = Pcode.code(JMP, 0, 0);//跳跃的地址之后回填，先设为0
			pcodeTable.add(jmp);//跳过声明部分，因为声明部分是子过程的内容
		}
		
		conDeclare();//“[<常量说明部分>]”
		varDeclare();//“[<变量说明部分>]”
		listDeclare();//“[<数组声明过程>]”
		proc();//“[<过程说明部分>]”
		
		if (level == 0) {
			jmp.setA(nowCodeAddress());//回填当前地址
		}
		
		if (procedure != null) {//不是主函数，那么该过程的起始语句的地址要保存在符号表中
			procedure.setValue(nowCodeAddress());//起始语句即下一句INT指令
		}
		
		//申请分配栈空间，大小即address的大小，常量和变量声明会增加该大小
		code(INT, 0, address);
		
		statement();//“<语句>”
		
		code(OPR, 0, 0);//表示过程调用结束，返回调用点
		
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
			if (nowTokenType() == NUMBER) {//是数字
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
	
	//<数组声明过程>::=list<数组声明标识符>{,<数组声明标识符>};
	private void listDeclare() {
		if (nowTokenType() == LIST) {//“VAR”
			
			do {
				advance();//跳过“LIST”或者“,”
				
				arraySymInDeclare();//“<数组声明标识符>”
				
			} while (nowTokenType() == COMMA);//如果是“,” 那么后面肯定还定义了别的数组
			
			semic();//“;”
		}
	}
	
	//<数组声明标识符>=<标识符>'['<数字>']'{'['<数字>']'}
	private void arraySymInDeclare() {
		if (nowTokenType() != SYM) {//不是标识符
			errorHandle(4);
		}
		String varName = nowToken().getValue();//获取声明变量标识符的名称
		
		//已经存在该名称的常量标识符，因此也不能声明这种变量
		if (symTable.getSymbol(varName, SymbolType.CONST) != null) {
			errorHandle(9);
		}
		
		if (symTable.getSymbol(varName, SymbolType.VAR) != null) {
			errorHandle(9);
		}
		
		//声明数组符号，长度之后再填
		Symbol	listSym	= new Symbol(SymbolType.VARLIST, 0, level, address, varName);
		
		boolean	success	= symTable.addSymbol(listSym);//加入变量数组符号
		
		if (!success) {//标识符重定义
			errorHandle(9);
		}
		
		advance();//跳过“<标识符>”
		
		int				listLevel				= 0;//数组的层级
		
		//预计算值，长度为listLevel+1
		//第零个表示数组总共的长度，即数组总共元素个数，第一个表示第二层所有的元素个数
		//第n-1个表示最后一层的元素个数，第n个为1，因为表示一个元素
		//这个和数组取值时有关，例如A[2][3][1][5]
		//数组的内部地址偏移就是2*p[1]+3*p[2]+1*p[3]+5*p[4]（p表示preCulArray）
		//通过预计算就可以很快的算出这个偏移量，在实际中就很方便
		List<Integer>	preCulArray				= new ArrayList<>();
		
		//数组每层的长度，长度为listLevel
		List<Integer>	arrayLengthOfEachLevel	= new ArrayList<>();
		
		listSym.putAdditionalInformation("list-preCul", preCulArray);
		listSym.putAdditionalInformation("list-lengthOfLevel", arrayLengthOfEachLevel);
		
		do {
			
			leftSquareBracket();//“[”
			
			if (nowTokenType() != NUMBER) {//数组定义中长度部分没有使用数字
				if (nowTokenType() == SYM) {//不是数字而是标识符
					errorHandle(26);
				} else {
					errorHandle(27);
				}
			}
			
			int listLengthOfLevel = Integer.parseInt(nowToken().getValue());//获取数组的长度
			
			arrayLengthOfEachLevel.add(listLengthOfLevel);//将每层的长度放入
			
			advance();//跳过“NUMBER”
			
			rightSquareBracket();//“]”
			
			++listLevel;//数组层数加一
			
		} while (nowTokenType() == LSB);//如果还遇到“[” 那么后面肯定还有一层
		
		
		//计算预计算数组长
		preCulArray.add(1);
		
		//每层的长度实际上就是上一层总长度乘以本层数字个数（即本层有几个上一层）
		for (int i = arrayLengthOfEachLevel.size() - 1; i >= 0; --i) {
			preCulArray.add(preCulArray.get(preCulArray.size() - 1) *
					arrayLengthOfEachLevel.get(i));//依次计算每层的长度
		}
		
		Collections.reverse(preCulArray);//反转，使得其顺序正确
		
		listSym.setValue(listLevel);//设置数组层级数
		
		addressAdvance(preCulArray.get(0));//栈上的位置加一段长度，因为这部分长度位置被这个数组占据
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
				complexConditionExperssion();//“<复杂条件表达式>”
				
				if (nowTokenType() != THEN) {//条件语句缺少“THEN”
					errorHandle(12);
				}
				
				Pcode jpc = Pcode.code(JPC, 0, 0);//条件转移代码，需要回填
				pcodeTable.add(jpc);//用于跳过if后面的语句，不执行这些语句
				
				advance();//跳过“THEN”
				
				statement();//“<语句>”
				
				jpc.setA(nowCodeAddress());//回填条件转移代码，不满足则跳到ELSE或结尾处
				
				if (nowTokenType() == ELS) {//“[else<语句>]”
					Pcode jmp = Pcode.code(JMP, 0, 0);//无条件转移代码，需要回填
					pcodeTable.add(jmp);//是用于上面if后的语句执行完成后，跳过else部分的语句
					
					advance();//跳过“ELSE”
					statement();//“<语句>”
					
					jmp.setA(nowCodeAddress());//回填无条件转移代码，跳到ELSE后的这部分语句
				}
				return;
			}
			
			//<当循环语句>::=while<条件> [flag<Flag标识符>] do<语句>
			/*
			 * 生成如下结构的代码：
			 * a <条件>
			 * b JPC ? f
			 * c <语句>
			 * d JMP ? a
			 * f ?
			 * 
			 * break -> jmp 0 f
			 * 
			 * continue -> jmp 0 a
			 */
			case WHI: {
				advance();//跳过“WHILE”
				
				int firstLine = nowCodeAddress();//获取第一行代码所在的位置
				
				complexConditionExperssion();//“<复杂条件表达式>”
				
				handleFlag(firstLine);//如果有flag，那么就进行处理，并且将continue应该返回的地址设置好
				
				if (nowTokenType() != DO) {
					errorHandle(13);//WHILE循环缺少DO语句
				}
				
				advance();//跳过“DO”
				
				Pcode jpc = Pcode.code(JPC, 0, 0);//条件转移代码，需要回填
				pcodeTable.add(jpc);//用于跳过do后面的语句，不执行这些语句
				
				statement();//“<语句>”
				
				Pcode jmp = Pcode.code(JMP, 0, firstLine);//无条件转移代码
				pcodeTable.add(jmp);//跳回第一条语句，即条件语句的第一条语句
				
				jpc.setA(nowCodeAddress());//回填条件转移代码，跳到最后面
				
				bcHandle.removeFlag(nowCodeAddress());//移除当前层的Flag，回填一系列和FLAG有关的代码
				
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
				code(Operator.CAL, level - pro.getLevel(), pro.getValue());
				
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
					
					String		varName	= nowToken().getValue();//获取标识符名称
					SymbolType	type;
					
					advance();//跳过“<标识符>”
					
					Symbol var = symTable.getSymbol(varName, SymbolType.VAR);//获取标识符对应的符号
					if (var == null) {//变量数组
						var = symTable.getSymbol(varName, SymbolType.VARLIST);
						if (var == null) {//该变量不存在
							errorHandle(17);
						}
						type = SymbolType.VARLIST;
						
						handleNDListBias(var);//处理多维数组，最后将数组偏移置于栈顶
						
					} else {//变量
						if (symTable.getSymbol(varName, SymbolType.CONST) != null) {//修改的是个常量
							errorHandle(18);
						}
						type = SymbolType.VAR;
					}
					
					code(OPR, 0, 16);//从命令行中读取一个数到栈顶
					
					if (type == SymbolType.VAR) {
						//将刚刚读进来的值存到变量单元中
						code(STO, level - var.getLevel(), var.getAddress());
					} else {
						//因为要让数组位移至于栈顶，而要赋的值于次栈，但是计算顺序是相反的，所以交换顺序
						code(SWAP, 0, 0);
						
						//通过上述<四则表达式>可以计算出一个数放在栈顶
						//该语句将这个栈顶的数加上原有地址形成偏移，然后向那个位置存数
						code(Operator.ARS, level - var.getLevel(), var.getAddress());
					}
					
				} while (nowTokenType() == COMMA);//出现“,” 后面肯定还有“<标识符>”
				
				rightBracket();//“)”
				return;
			}
			
			//<写语句>::=write '('<复杂状态表达式>{,<复杂状态表达式>}')'
			case WRI: {
				
				advance();//跳过“WRITE”
				if (nowTokenType() != LBR) {//缺少“(”
					errorHandle(15);
				}
				
				do {
					advance();//跳过“(”或“,”
					complexConditionExperssion();//<复杂状态表达式>
					code(OPR, 0, 14);//栈顶值输出至屏幕
				} while (nowTokenType() == COMMA);//出现“,” 后面肯定还有“<四则表达式>”
				
				code(OPR, 0, 15);//输出换行
				
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
			
			//<赋值语句>::=<标识符>|<数组标识符>:=<复杂状态表达式>
			case SYM: {
				assignmentStatement();
				return;
			}
			
			//<重复语句> ::= repeat[flag<>] do<语句>{;<语句>}until<条件>
			/*
			 * 生成以下语句：
			 * a <语句>
			 * b <条件>
			 * c JPC 0 a
			 * d ?
			 * 
			 * break -> jmp 0 d
			 * 
			 * continue -> jmp 0 a
			 */
			case REP: {
				int firstLine = nowCodeAddress();//获取第一行的地址
				
				advance();//跳过“PEREAT”
				
				handleFlag(firstLine);//如果有flag，那么就进行处理，并且将continue应该返回的地址设置好
				
				if (nowTokenType() != DO) {//缺少“DO”
					errorHandle(37);
				}
				
				do {
					advance();//跳过“DO”或“;”
					statement();//“<语句>”
				} while (nowTokenType() == SEMIC);//遇到“;”证明后面还有“<语句>”
				
				if (nowTokenType() != UNT) {//缺少“UNTIL”
					errorHandle(21);
				}
				advance();//跳过“UNTIL”
				complexConditionExperssion();//“<条件>”
				
				//条件转移语句，若为假，则转移回第一行
				code(JPC, 0, firstLine);
				
				bcHandle.removeFlag(nowCodeAddress());//移除当前层的Flag，回填一系列和FLAG有关的代码
				
				return;
			}
			
			//<停止程序语句>::= exit<复杂状态表达式>
			case EXIT: {
				advance();//跳过“EXIT”
				complexConditionExperssion();//<复杂状态表达式>
				code(Operator.EXIT, 0, 0);
				return;
			}
			
			//<提前终止语句>::= return
			case RETPRO: {
				code(Operator.OPR, 0, 0);//直接返回，过程没有返回值，因此直接返回即可
				return;
			}
			
			//<for循环语句>::=for <赋值语句>,{<赋值语句>} when <复杂条件表达式> change <赋值语句>,{<赋值语句>}  \
			//		[flag<Flag标识符>] do <语句>
			/*
			 * 形成的for循环语句结构如下：
			 * a <赋值语句>s
			 * b <复杂条件表达式>
			 * c jpc 0 j
			 * d jmp 0 h
			 * f <赋值语句>s
			 * g jmp 0 b
			 * h <语句>
			 * i jmp 0 f
			 * j ?
			 * 
			 * break -> jmp 0 j
			 * 
			 * continue -> jmp 0 f
			 * 
			 */
			case FOR: {
				do {
					advance();//跳过“FOR”或者“,”
					assignmentStatement();//“<赋值语句>”
				} while (nowTokenType() == COMMA);//如果出现“,” 那么肯定还有“<赋值语句>”
				
				if (nowTokenType() != WHEN) {//for循环中没有正确的出现when
					errorHandle(29);
				}
				advance();//跳过“WHEN”
				
				int first = nowCodeAddress();//when部分的第一句的地址
				
				complexConditionExperssion();//“<复杂条件表达式>”
				
				Pcode jpc = Pcode.code(JPC, 0, 0);//条件转移语句，等待回填
				pcodeTable.add(jpc);
				
				Pcode jmp = Pcode.code(JMP, 0, 0);//无条件转移语句，用于跳过change部分，先执行do后面的语句
				pcodeTable.add(jmp);
				
				int second = nowCodeAddress();//change部分第一句的地址
				
				if (nowTokenType() != CHANGE) {//for循环中没有正确的出现change
					errorHandle(30);
				}
				
				do {
					advance();//跳过“CHANGE”或者“,”
					assignmentStatement();//“<赋值语句>”
				} while (nowTokenType() == COMMA);//如果出现“,” 那么肯定还有“<赋值语句>”
				
				code(JMP, 0, first);//无条件转移语句，用于执行完change部分后，跳回开头
				
				jmp.setA(nowCodeAddress());//回填条件转移语句，用于跳过change部分，先执行do后面的语句
				
				handleFlag(second);//如果有flag，那么就进行处理，并且将continue应该返回的地址设置好
				
				if (nowTokenType() != DO) {//for循环中没有正确的出现do
					errorHandle(31);
				}
				
				advance();//跳过"DO"
				
				statement();//“<语句>”
				
				code(JMP, 0, second);//无条件转移语句，用于执行完do部分后，跳回change部分
				
				jpc.setA(nowCodeAddress());//回填条件转移语句，用于不符合条件，跳出for循环
				
				bcHandle.removeFlag(nowCodeAddress());//移除当前层的Flag，回填一系列和FLAG有关的代码
				
				return;
			}
			//<空语句>::=pass
			case PASS: {
				advance();//跳过“PASS”
				return;
			}
			
			//<循环跳出语句>::=break[<flag标识符>]
			case BREAK: {
				advance();//跳过“BREAK”
				
				Pcode jmp = Pcode.code(JMP, 0, 0);//生成无条件转移代码，用于转跳到循环结束的位置
				pcodeTable.add(jmp);
				
				if (nowTokenType() != SYM) {//没有flag标识符标记的跳出语句，表示跳出当前循环
					
					bcHandle.addBreakCode(jmp);//交给循环跳出跳过处理器负责回填
					
				} else {//有flag标识符标记，那么会跳出flag标记的那个循环
					
					String	flag	= nowToken().getValue();//获取当前FLAG标识符
					boolean	success	= bcHandle.addBreakFlagCode(flag, jmp);//交由循环跳出跳过处理器负责回填
					
					if (!success) {//使用未定义的循环FLAG标识符
						errorHandle(36);
					}
					
					advance();//跳过“<flag标识符>”
				}
				return;
			}
			
			//<循环跳过语句>::=continue[<flag标识符>]
			case CONTINUE: {
				advance();//跳过“CONTINUE”
				
				Pcode jmp = Pcode.code(JMP, 0, 0);//生成无条件转移代码，用于转跳到循环开始的位置
				pcodeTable.add(jmp);
				
				if (nowTokenType() != SYM) {//没有flag标识符标记的跳过语句，表示跳过当前循环
					
					bcHandle.addContinueCode(jmp);//交给循环跳出跳过处理器负责回填
					
				} else {//有flag标识符标记，那么会跳过flag标记的那个循环
					
					String	flag	= nowToken().getValue();//获取当前FLAG标识符
					boolean	success	= bcHandle.addContinueFlagCode(flag, jmp);//交由循环跳出跳过处理器负责回填
					
					if (!success) {//使用未定义的循环FLAG标识符
						errorHandle(36);
					}
					
					advance();//跳过“<flag标识符>”
				}
				return;
			}
			
			default://非法的语句开始符号
				errorHandle(22);
				return;
		}
	}
	
	//处理<Flag标识符>
	private void handleFlag(int firstLine) {
		if (nowTokenType() == FLAG) {//循环标志
			
			advance();//跳过“FLAG”
			
			if (nowTokenType() != SYM) {//不是<Flag标识符>
				errorHandle(34);
			}
			
			String	flagName	= nowToken().getValue();//获取<Flag标识符>
			
			boolean	success		= bcHandle.addFlag(flagName, firstLine);//添加FLAG标记
			
			if (!success) {//FLAG标识符重定义
				errorHandle(35);
			}
			
			advance();//跳过<Flag标识符>
		} else {
			bcHandle.addNoNameFlag(firstLine);//添加无名FLAG标记
		}
	}
	
	
	//<nd数组>='['<复杂条件表达式>']'{'['<复杂条件表达式>']'} 
	private void handleNDListBias(Symbol list) {
		
		int				listLevel		= 0;
		
		@SuppressWarnings("unchecked")
		List<Integer>	preCul			= (List<Integer>) list
				.getAdditionalInformation("list-preCul");
		
		@SuppressWarnings("unchecked")
		List<Integer>	listLevelLen	= (List<Integer>) list
				.getAdditionalInformation("list-lengthOfLevel");
		
		int				totalListLevel	= list.getValue();//数组总层级
		
		do {
			if (listLevel >= totalListLevel) {//数组层级超长
				errorHandle(32);
			}
			
			leftSquareBracket();//“[”
			complexConditionExperssion();//<复杂状态表达式>
			rightSquareBracket();//“]”
			
			if (arrayOutOfBoundCheck) {
				code(ARC, 0, listLevelLen.get(listLevel));//检查数组长度
			}
			
			code(Operator.LIT, 0, preCul.get(listLevel + 1));//装入预计算值
			code(OPR, 0, 4);//乘以下一层长度
			
			++listLevel;//数组层级加一
			
		} while (nowTokenType() == LSB);
		
		if (listLevel != totalListLevel) {//数组层级不足
			errorHandle(33);
		}
		
		if (arrayOutOfBoundCheck) {
			code(ARC, 0, preCul.get(0));//检查数组长度
		}
		
		for (; listLevel > 1; --listLevel) {//将这些预计算值加起来，得到总共的数组内部偏移
			code(OPR, 0, 2);
		}
	}
	
	
	//<赋值语句>::=<标识符>|<数组标识符>:=<复杂状态表达式>
	private void assignmentStatement() {
		String		varName	= nowToken().getValue();//获取标识符名称
		SymbolType	type;
		
		advance();//跳过“<标识符>”
		
		Symbol var = symTable.getSymbol(varName, SymbolType.VAR);//获取标识符对应的符号
		if (var == null) {//该变量不存在
			var = symTable.getSymbol(varName, SymbolType.VARLIST);
			if (var == null) {
				errorHandle(17);
			}
			type = SymbolType.VARLIST;
			
			handleNDListBias(var);//处理多维数组，最后将数组偏移置于栈顶
			
		} else {
			if (symTable.getSymbol(varName, SymbolType.CONST) != null) {//修改的是个常量
				errorHandle(18);
			}
			type = SymbolType.VAR;
		}
		
		if (nowTokenType() == EQU) {//赋值表达式使用等号
			errorHandle(19);
		} else if (nowTokenType() == COL) {//赋值表达式中:=只有:
			errorHandle(20);
		} else if (nowTokenType() != CEQU) {//不是“:=”
			errorHandle(21);
		}
		
		advance();//跳过“:=”
		
		complexConditionExperssion();//<复杂状态表达式>
		
		if (type == SymbolType.VAR) {
			//将刚刚读进来的值存到变量单元中
			code(STO, level - var.getLevel(), var.getAddress());
		} else {
			//因为要让数组位移至于栈顶，而要赋的值于次栈，但是计算顺序是相反的，所以交换顺序
			code(SWAP, 0, 0);
			
			//通过上述<复杂状态表达式>可以计算出一个数放在栈顶
			//该语句将这个栈顶的数加上原有地址形成偏移，然后向那个位置存数
			code(Operator.ARS, level - var.getLevel(), var.getAddress());
		}
		return;
	}
	
	//<复杂条件表达式>::=<或项>{OR|SCO<或项>}
	private void complexConditionExperssion() {
		orItem();//“<或项>”
		
		List<Pcode> scjpc = new ArrayList<>();
		
		while (true) {
			
			if (nowTokenType() != SOR) {
				for (Pcode jpc : scjpc) {
					jpc.setA(nowCodeAddress());
				}
				scjpc.clear();
				if (nowTokenType() != OR) {
					return;
				}
			} else {//SOR
				Pcode jpc = Pcode.code(JPNC, 0, 0);//等待第一个非短路与或者或项结束时进行回填
				scjpc.add(jpc);
				pcodeTable.add(jpc);
			}
			
			advance();//跳过“SOR”或“OR”
			orItem();//“<或项>”
			
			//将栈顶和次栈进行逻辑或操作，退两个栈元素，当两者都是0的时候，栈顶进入0，否则进入1
			code(OPR, 0, 19);
		}
	}
	
	//<或项>::=<与项>{AND|SCA<与项>}
	//短路就是从第一个跳过去
	private void orItem() {
		andItem();//“<与项>”
		
		List<Pcode> scjpc = new ArrayList<>();
		
		while (true) {
			
			if (nowTokenType() != SAND) {
				for (Pcode jpc : scjpc) {
					jpc.setA(nowCodeAddress());
				}
				scjpc.clear();
				if (nowTokenType() != AND) {
					return;
				}
			} else {//SAND
				Pcode jpc = Pcode.code(JPC, 0, 0);//等待第一个非短路与或者或项结束时进行回填
				scjpc.add(jpc);
				pcodeTable.add(jpc);
			}
			
			advance();//跳过“SAND”或“AND”
			andItem();//“<与项>”
			
			//将栈顶和次栈进行逻辑与操作，退两个栈元素，当两者都是1的时候，栈顶进入1，否则进入0
			code(OPR, 0, 18);
		}
	}
	
	//<与项>::=<非项>|<简单条件>
	private void andItem() {
		if (nowTokenType() == NOT) {
			notItem();//“<非项>”
		} else {
			simpleConditionExperssion();//“<简单条件表达式>”
		}
	}
	
	//<非项>::=NOT <简单条件>
	private void notItem() {
		advance();//跳过“NOT”
		simpleConditionExperssion();//“<简单条件表达式>”
		code(OPR, 0, 17);//将栈顶元素进行逻辑非运算，即非0变成0，0变成1
	}
	
	//<简单条件表达式>::=<位偏移表达式>[<关系运算符><位偏移表达式>] | odd<位偏移表达式> | TRUE | FALSE
	private void simpleConditionExperssion() {
		
		if (nowTokenType() == ODD) {//“ODD”
			advance();//跳过“ODD”
			bitshiftExpression();//odd<四则表达式>
			code(OPR, 0, 6);//栈顶元素的奇偶判断，结果值在栈顶
		} else if (nowTokenType() == TRUE) {
			advance();//跳过“TRUE”
			code(Operator.LIT, 0, 1);//真的本质就是计算出来不是0，所以用1表示真
		} else if (nowTokenType() == FALSE) {
			advance();//跳过“FALSE”
			code(Operator.LIT, 0, 0);//假的本质就是计算出来是0，所以用0表示假
		} else {
			bitshiftExpression();//“<四则表达式>”
			
			SymType op = nowTokenType();//保存“<关系运算符>”
			
			if (!(op == EQU || op == NEQ || op == SymType.LIT || op == LEQ || op == GRE
					|| op == GEQ)) {//如果没有关系运算符，那么[<关系运算符><四则表达式>]这部分不存在，返回
				return;
			}
			
			advance();//跳过“<关系运算符>”
			bitshiftExpression();//“<四则表达式>”
			
			switch (op) {
				case EQU://等于
					//次栈顶与栈顶是否相等，退两个栈元素，结果值进栈
					code(OPR, 0, 8);
					return;
				case NEQ://不等于
					//次栈顶与栈顶是否不等，退两个栈元素，结果值进栈
					code(OPR, 0, 9);
					return;
				case LIT://小于
					//次栈顶是否小于栈顶，退两个栈元素，结果值进栈
					code(OPR, 0, 10);
					return;
				case LEQ://小于等于
					//次栈顶是否小于等于栈顶，退两个栈元素，结果值进栈
					code(OPR, 0, 13);
					return;
				case GRE://大于
					//次栈顶是否大于栈顶，退两个栈元素，结果值进栈
					code(OPR, 0, 12);
					return;
				case GEQ://大于等于
					//次栈顶是否大于等于栈顶，退两个栈元素，结果值进栈
					code(OPR, 0, 11);
					return;
				default:
					return;
			}
			
		}
	}
	
	//<位偏移表达式>::=<位运算表达式>{<位偏移运算符><位运算表达式>} TODO
	//<位偏移运算符>::=>>|<<
	private void bitshiftExpression() {
		bitwiseExpression();//“<位运算表达式>”
		
		//还有“&”或“|”，那么肯定还有{<位偏移运算符><位运算表达式>}
		while (nowTokenType() == BITWISE_SHIFT_LEFT || nowTokenType() == BITWISE_SHIFT_RIGHT) {
			
			SymType op = nowTokenType();//获取“<位运算符>”的类型
			advance();//跳过“>>”或“<<”
			
			bitwiseExpression();//“<位运算表达式>”
			
			if (op == BITWISE_SHIFT_LEFT) {//“<位偏移运算符>”是“<<”
				//将次栈左移“栈顶”位，退两个栈元素，结果值进栈
				code(OPR, 0, 24);
			} else {//BITWISE_SHIFT_RIGHT “<位偏移运算符>”是“>>”
				//将次栈右移“栈顶”位，退两个栈元素，结果值进栈
				code(OPR, 0, 25);
			}
		}
	}
	
	//<位运算表达式>::=<四则表达式>{<位运算符><四则表达式>} TODO
	//<位运算符>::=&|‘|’|^
	private void bitwiseExpression() {
		fundamentalExpression();//“<四则表达式>”
		
		//还有“&”或“|”，那么肯定还有{<位运算符><四则表达式>}
		while (nowTokenType() == BITWISE_AND || nowTokenType() == BITWISE_OR) {
			
			SymType op = nowTokenType();//获取“<位运算符>”的类型
			advance();//跳过“&”或“|”
			
			fundamentalExpression();//“<四则表达式>”
			
			if (op == BITWISE_AND) {//“<位运算符>”是“&”
				//将栈顶和次栈进行按位与操作，退两个栈元素，结果值进栈
				code(OPR, 0, 21);
			} else if (op == BITWISE_OR) {//“<位运算符>”是“|”
				//将栈顶和次栈进行按位或操作，退两个栈元素，结果值进栈
				code(OPR, 0, 22);
			} else {//BITWISE_XOR “<位运算符>”是“^”
				//将栈顶和次栈进行按位异或操作，退两个栈元素，结果值进栈
				code(OPR, 0, 23);
			}
		}
	}
	
	
	//<四则表达式>::=[+|-]<项>{<加减运算符><项>}
	//<加减运算符>::=+|-
	private void fundamentalExpression() {
		SymType first = nowTokenType();
		if (first == ADD || first == SUB) {//“+”或“-”
			advance();//跳过“+”或“-”
		}
		
		term();//“<项>”
		
		if (first == SUB) {//若开头的是“-”
			code(OPR, 0, 1);//栈顶元素取反
		}
		
		//还有“+”或“-”，那么肯定还有{<加减运算符><项>}
		while (nowTokenType() == ADD || nowTokenType() == SUB) {
			SymType op = nowTokenType();//获取“<加减运算符>”的类型
			advance();//跳过“+”或“-”
			term();//“<项>”
			if (op == ADD) {//“<加减运算符>”是“+”
				//次栈顶与栈顶相加，退两个栈元素，结果值进栈
				code(OPR, 0, 2);
			} else {//SUB  “<加减运算符>”是“-”
				//次栈顶减去栈顶，退两个栈元素，结果值进栈
				code(OPR, 0, 3);
			}
		}
	}
	
	//<项>::=<因子>{<乘除模运算符><因子>}
	//<乘除模运算符>::=*|/|%
	private void term() {
		factor();//“<因子>”
		
		//还有“*”或“/”，那么肯定还有{<乘除运算符><因子>}
		while (nowTokenType() == MUL || nowTokenType() == DIV || nowTokenType() == MOD) {
			SymType op = nowTokenType();//获取“<乘除运算符>”的类型
			advance();//跳过“*”或“/”
			factor();//“<因子>”
			if (op == MUL) {//“<乘除运算符>”是“*”
				//次栈顶乘以栈顶，退两个栈元素，结果值进栈
				code(OPR, 0, 4);
			} else if (op == DIV) {//DIV   “<乘除运算符>”是“/”
				//次栈顶除以栈顶，退两个栈元素，结果值进栈
				code(OPR, 0, 5);
			} else {//MOD	 “<乘除运算符>”是“%”
				//次栈顶对栈顶求模，退两个栈元素，结果值进栈
				code(OPR, 0, 7);
			}
		}
	}
	
	//<因子>::=<标识符> |<数组标识符>| <无符号整数> | '('<复杂条件表达式>')'  TODO 另外还需补充一元运算
	//<数组标识符>::=<标识符>'['<四则表达式>']'
	private void factor() {
		switch (nowTokenType()) {
			case NUMBER://“NUMBER” 那么是“<无符号整数>”
				//将常量送到运行栈S的栈顶，这时A段为常量值。
				code(Operator.LIT, 0, Integer.parseInt(nowToken().getValue()));
				advance();//跳过“<无符号整数>”
				return;
			case LBR://“(”  那么是“(<复杂条件表达式>)'”
				advance();//跳过“(”
				complexConditionExperssion();//“<复杂条件表达式>”
				rightBracket();//“)”
				return;
			case SYM://“<标识符>”或“<数组标识符>”
				String symName = nowToken().getValue();//获取标识符的名称
				advance();//跳过“<标识符>”
				Symbol sym = symTable.getSymbol(symName, SymbolType.CONST);
				if (sym != null) {//该标识符标识一个常量
					//将常量送到运行栈S的栈顶，这时A段为常量值。
					code(Operator.LIT, 0, sym.getValue());
				} else {
					sym = symTable.getSymbol(symName, SymbolType.VAR);
					if (sym == null) {//该标识符既不是常量也不是变量,有可能是数组
						
						sym = symTable.getSymbol(symName, SymbolType.VARLIST);
						if (sym == null) {//也不是数组
							errorHandle(17);
						}
						
						handleNDListBias(sym);//处理多维数组，最后将数组偏移置于栈顶
						
						//该语句将这个栈顶的数加上原有地址形成偏移，从那个位置取数
						code(ARL, level - sym.getLevel(), sym.getAddress());
					} else {
						//将变量送到运行栈S的栈顶，这时A段为变量所在说明层中的相对位置。
						code(LOD, level - sym.getLevel(), sym.getAddress());
					}
				}
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
	
	private void leftSquareBracket() {
		if (nowTokenType() != LSB) {//缺少“[”
			errorHandle(25);
		}
		
		advance();//跳过“[”
	}
	
	private void rightSquareBracket() {
		if (nowTokenType() != RSB) {//缺少“]”
			errorHandle(28);
		}
		advance();//跳过“]”
	}
	
	private void code(Operator f, int l, int a) {
		pcodeTable.add(Pcode.code(f, l, a));
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
		System.out.println(nowToken().getType() + " " + nowToken().getValue() + lineIndexMesg());
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
	 * 当前栈被数组占用len长度的位置，向后移len位
	 */
	private void addressAdvance(int len) {
		address += len;
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
				System.err.println("Lack of \"END\" or lack of \";\" in last line"
						+ lineIndexMesg());
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
				System.err.println("Unkown \"token\"" + nowToken().getType()
						+ " in the start of a sentence or put \";\" before end" + lineIndexMesg());
				break;
			case 23://<条件语句>中出现未知的<关系运算符>
				System.err.println("Unkown \"token\"" + nowToken().getValue()
						+ " in the place of condition operate" + lineIndexMesg());
				break;
			case 24://表达式开头符号异常
				System.err.println("Unkown \"token\"" + nowToken().getValue()
						+ " in the start of a fundamentalExpression" + lineIndexMesg());
				break;
			case 25://缺少“[”
				break;
			case 26://数组定义中使用标识符
				break;
			case 27://数组定义中长度部分没有使用数字，且不是标识符
				break;
			case 28://缺少“]”
				break;
			case 29://for循环中没有正确的出现when
				break;
			case 30://for循环中没有正确的出现change
				break;
			case 31://for循环中没有正确的出现do
				break;
			case 32://数组层级超长
				break;
			case 33://数组层级不足
				break;
			case 34://循环FLAG声明中缺少FLAG标识符
				break;
			case 35://FLAG标识符重定义
				break;
			case 36://使用未定义的循环FLAG标识符
				break;
			case 37://repeat循环中缺少do
				break;
			case 38://
				break;
			case 39://
				break;
			case 40://
				break;
			case 41://
				break;
			case 42://
				break;
			case 43://
				break;
			case 44://
				break;
			case 45://
				break;
			case 46://
				break;
			case 47://
				break;
			case 48://
				break;
			case 49://
				break;
		}
		
		throw new RuntimeException("Error occur!");//出现严重错误，编译无法继续下去，退出并抛出异常
	}
	
	/**
	 * @return 错误出现的Token的行号和列号
	 */
	private String lineIndexMesg() {
		return " in line " + nowToken().getLine() + " index " + nowToken().getIndex();
	}
	
}
