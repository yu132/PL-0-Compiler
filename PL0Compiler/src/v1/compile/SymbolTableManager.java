package v1.compile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import v1.define.Symbol;
import v1.define.SymbolType;

public class SymbolTableManager {
	
	/**
	 * 符号表层List	包含当前所有过程的符号表
	 * 
	 * 符号表数组[] 常量、变量、过程
	 * 
	 * 符号表Map 符号名:符号对象
	 */
	private List<Map<String, Symbol>[]>	table	= new ArrayList<>();
	
	private List<Symbol>				all		= new ArrayList<>();
	
	public int nowIndex() {
		return all.size();
	}
	
	public Symbol LastProcedure() {
		for (int i = all.size() - 1; i >= 0; i--) {
			if (all.get(i).getType() == SymbolType.PRO) {
				return all.get(i);
			}
		}
		return null;
	}
	
	public boolean addSymbol(Symbol symbol) {
		int					type	= symbol.getType().ordinal();
		
		Map<String, Symbol>	tab		= table.get(table.size() - 1)[type];
		
		if (symbol.getType() == SymbolType.PRO) {
			if (!tab.containsKey(symbol.getName())) {
				tab.put(symbol.getName(), symbol);
				all.add(symbol);
				return true;
			}
		} else {
			Map<String, Symbol>	consts	= table.get(table.size() - 1)[0];
			Map<String, Symbol>	vars	= table.get(table.size() - 1)[1];
			if (!consts.containsKey(symbol.getName()) && !vars.containsKey(symbol.getName())) {
				tab.put(symbol.getName(), symbol);
				all.add(symbol);
				return true;
			}
		}
		return false;
	}
	
	public Symbol getSymbol(String symbolName, SymbolType type) {
		int typeIndex = type.ordinal();
		for (int i = table.size() - 1; i >= 0; --i) {
			Map<String, Symbol> tab = table.get(i)[typeIndex];
			if (tab.containsKey(symbolName)) {
				return tab.get(symbolName);
			}
		}
		return null;
	}
	
	public void deleteTable() {
		table.remove(table.size() - 1);
	}
	
	@SuppressWarnings("unchecked")
	public void addTable() {
		Map<String, Symbol>[] temp = new Map[3];
		for (int i = 0; i < 3; ++i) {
			temp[i] = new HashMap<>();
		}
		table.add(temp);
	}
}
