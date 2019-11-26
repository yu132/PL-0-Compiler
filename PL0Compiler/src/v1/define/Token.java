package v1.define;

/**
 * 词法分析后产生的Token
 * 
 * 每个Token包括类别，行和值ֵ
 * 
 * @author 87663
 *
 */
public class Token {
	private SymType	type; //token的类别
	private int		line; //token所在行，错误处理使用
	private int		index;//token的开始位置在某行的第几位
	private String	value; //token的值，只有标识符和常量有值
	
	public Token(SymType type, int line, int index, String value) {
		super();
		this.type	= type;
		this.line	= line;
		this.index	= index;
		this.value	= value;
	}
	
	public SymType getType() {
		return type;
	}
	
	public int getLine() {
		return line;
	}
	
	public int getIndex() {
		return index;
	}
	
	public String getValue() {
		return value;
	}
	
	
}
