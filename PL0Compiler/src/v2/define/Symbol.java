package v2.define;

import java.util.HashMap;
import java.util.Map;

/**
 * 符号表中的符号项
 * 
 * @author 87663
 *
 */
public class Symbol {
	private SymbolType			type;           //表示常量、变量或过程
	private int					value;          //表示常量或变量的值，数组表示数组的层级总数
	private int					level;          //嵌套层次
	private int					address;        //相对于所在嵌套过程基地址的地址
	private String				name;           //变量、常量或过程名
	
	//附加信息，当前存在该域的有数组预计算值和数组长度表
	private Map<String, Object>	additionalInformation;
	
	public void putAdditionalInformation(String field, Object mesg) {
		if (additionalInformation == null) {
			additionalInformation = new HashMap<>();
		}
		additionalInformation.put(field, mesg);
	}
	
	public Object getAdditionalInformation(String field) {
		if (additionalInformation == null) {
			return null;
		}
		return additionalInformation.get(field);
	}
	
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
	
	public void setType(SymbolType type) {
		this.type = type;
	}
	
	public int getValue() {
		return value;
	}
	
	public void setValue(int value) {
		this.value = value;
	}
	
	public int getLevel() {
		return level;
	}
	
	public void setLevel(int level) {
		this.level = level;
	}
	
	public int getAddress() {
		return address;
	}
	
	public void setAddress(int address) {
		this.address = address;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	
	
}
