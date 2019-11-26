package v1.test;

import v1.compile.Block;
import v1.compile.PL0VM;

public class TestPL0VM {
	
	public static void main(String[] args) throws Exception {
		new PL0VM(new Block(TestGetSym.getTokens("testCode\\example.txt")).getPcode()).run();
		
		//		List<Pcode>	pcode	= new ArrayList<>();
		//		
		//		Scanner		scan	= new Scanner(new FileInputStream("testCode\\1.txt"));
		//		
		//		while (scan.hasNext())
		//			pcode.add(Pcode.code(Operator.valueOf(scan.next()), Integer.parseInt(scan.next()),
		//					Integer.parseInt(scan.next())));
		//		
		//		scan.close();
		//		
		//		new PL0VM(pcode).run();
	}
	
}
