package v1.define;

/**
 * 符号（内容）种类
 */
public enum SymType {
	//begin, end, if, then, else, const, procedure, var, do, while, call, read, write, odd, repeat, until
	BEG, END, IF, THEN, ELS, CON, PROC, VAR, DO, WHI, CAL, REA, WRI, ODD, REP, UNT,
	//=, <, <=, >=, >, <>, +, -, *, /
	EQU, LIT, LEQ, GRE, GEQ, NEQ, ADD, SUB, MUL, DIV,
	//标识符， 常量
	SYM, CONST,
	//:=, ',' , ';', '.', '(', ')'
	CEQU, COMMA, SEMIC, POINT, LBR, RBR,
	//:
	COL,
	//end of file
	EOF;
}
