package v2.define;

/**
 * 符号（内容）种类
 */
public enum SymType {
	
	//V1
	
	//begin, end, if, then, else, const, procedure, var, do, while, call, read, write, odd, repeat, until
	BEG, END, IF, THEN, ELS, CON, PROC, VAR, DO, WHI, CAL, REA, WRI, ODD, REP, UNT,
	//=, <, <=, >=, >, <>, +, -, *, /
	EQU, LIT, LEQ, GRE, GEQ, NEQ, ADD, SUB, MUL, DIV,
	//标识符， 常量
	SYM, NUMBER,
	//:=, ',' , ';', '.', '(', ')'
	CEQU, COMMA, SEMIC, POINT, LBR, RBR,
	//:
	COL,
	//end of file
	EOF,
	
	
	//V2
	LSB,// '['
	RSB,// ']'
	
	LIST,//list 数组声明
	
	EXIT,//exit 退出程序指令
	
	TRUE,//真
	FALSE,//假
	
	AND,//与
	OR,//或
	NOT,//非
	
	SAND,//短路与
	SOR,//短路或
	
	RETPRO,//提前返回指令
	
	FOR,//for循环
	WHEN,//当什么条件运行
	CHANGE,//进行什么修改
	
	PASS,//空语句
	
	FLAG,//循环标志
	BREAK,//循环跳出
	CONTINUE,//循环跳过
	
	MOD,//求模运算符
	
	BITWISE_AND,//按位与
	BITWISE_OR,//按位或
	BITWISE_XOR,//按位异或
	BITWISE_NOT,//按位取反
	
	BITWISE_SHIFT_LEFT,//按位左移
	BITWISE_SHIFT_RIGHT,//按位右移
	
	;
	
	public static SymType getByName(String name) {
		switch (name) {
			case "BEGIN":
				return BEG;
			case "END":
				return END;
			case "IF":
				return IF;
			case "THEN":
				return THEN;
			case "ELSE":
				return ELS;
			case "CONST":
				return CON;
			case "PROCEDURE":
				return PROC;
			case "VAR":
				return VAR;
			case "DO":
				return DO;
			case "WHILE":
				return WHI;
			case "CALL":
				return CAL;
			case "READ":
				return REA;
			case "WRITE":
				return WRI;
			case "ODD":
				return ODD;
			case "REPEAT":
				return REP;
			case "UNTIL":
				return UNT;
			case "LIST":
				return LIST;
			case "EXIT":
				return EXIT;
			case "TRUE":
				return TRUE;
			case "FLASE":
				return FALSE;
			case "AND":
				return AND;
			case "OR":
				return OR;
			case "NOT":
				return NOT;
			case "SAND":
				return SAND;
			case "SOR":
				return SOR;
			case "RETPRO":
				return RETPRO;
			case "FOR":
				return FOR;
			case "WHEN":
				return WHEN;
			case "CHANGE":
				return CHANGE;
			case "PASS":
				return PASS;
			case "FLAG":
				return FLAG;
			case "BREAK":
				return BREAK;
			case "CONTINUE":
				return CONTINUE;
			default:
				throw new RuntimeException("Unkown reserved word");
		}
	}
}
