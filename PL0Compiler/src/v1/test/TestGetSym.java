package v1.test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import v1.compile.GetSym;
import v1.define.Token;

public class TestGetSym {
	public static List<Token> getTokens(String fileName) throws Exception {
		InputStream	is		= new FileInputStream(fileName);
		int			iAvail	= is.available();
		byte[]		bytes	= new byte[iAvail];
		is.read(bytes);
		is.close();
		String	codes	= new String(bytes, "UTF-8");
		GetSym	temp	= new GetSym(codes.toCharArray());
		return temp.getTokens();
	}
	
	public static void main(String[] args) throws Exception {
		for (Token token : getTokens("testCode\\example.txt")) {
			System.out.println(token.getType() + " " + token.getLine() + " " + token.getValue());
		}
	}
	
	
}
