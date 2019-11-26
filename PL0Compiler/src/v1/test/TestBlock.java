package v1.test;

import v1.compile.Block;
import v1.define.Pcode;

public class TestBlock {
	
	public static void main(String[] args) throws Exception {
		Block block = new Block(TestGetSym.getTokens("testCode\\example.txt"));
		for (Pcode code : block.getPcode()) {
			System.out.println(code.getF() + " " + code.getL() + " " + code.getA());
		}
	}
	
}
