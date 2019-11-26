package v1.define;

/**
 * 符号表中的符号项
 * 
 * @author 87663
 *
 */
public class Symbol {
	private SymbolType	type;           //表示常量、变量或过程
	private int			value;          //表示常量或变量的值
	private int			level;          //嵌套层次
	private int			address;        //相对于所在嵌套过程基地址的地址
	private String		name;           //变量、常量或过程名
	
	public Symbol(SymbolType type, int value, int level, int address, String name) {
		super();
		this.type		= type;
		this.value		= value;
		this.level		= level;
		this.address	= address;
		this.name		= name;
	}
	
	public SymbolType getType() {
		return type;
	}
	
	public int getValue() {
		return value;
	}
	
	public int getLevel() {
		return level;
	}
	
	public int getAddress() {
		return address;
	}
	
	public String getName() {
		return name;
	}
	
	public void setType(SymbolType type) {
		this.type = type;
	}
	
	public void setValue(int value) {
		this.value = value;
	}
	
	public void setLevel(int level) {
		this.level = level;
	}
	
	public void setAddress(int address) {
		this.address = address;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	
	
}
