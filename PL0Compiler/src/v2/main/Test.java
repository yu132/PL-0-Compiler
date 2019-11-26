package v2.main;

public class Test {
	
	private static String	namef			= "testCode\\breakcontinue\\";
	
	private static String	codeFileName	= "example";
	
	public static void main(String[] args) {
		//MainCompiler.getPcode("testCode\\list\\bubble sort.txt", "testCode\\list\\out2.txt");
		
		MainCompiler.getTokens(namef + codeFileName + ".txt", namef + "tokens.txt");
		
		MainCompiler.getPcode(namef + codeFileName + ".txt", namef + "pcode.txt");
		
		MainCompiler.run(10000, namef + codeFileName + ".txt");
		
		//MainCompiler.run(300, namef + "bubble sort.txt", namef + "data.txt", namef + "out3.txt");
		
		//		Random r = new Random();
		//		for (int i = 0; i < 100; ++i) {
		//			System.out.print(r.nextInt(1000) + " ");
		//		}
	}
	
	
}
