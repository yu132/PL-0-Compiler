package v2.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

import v2.compile.GrammarAnalyzer;
import v2.compile.LexicalAnalyzer;
import v2.compile.PL0VM;

public class MainCompiler {
	
	public static void getTokens(String inputFileName, String outputFileName) {
		try {
			outList(new PrintStream(new FileOutputStream(new File(outputFileName))),
					new LexicalAnalyzer(getInput(inputFileName)).getTokens());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean sameTokens(String inputFileName1, String inputFileName2) {
		return new LexicalAnalyzer(getInput(inputFileName1)).getTokens()
				.equals(new LexicalAnalyzer(getInput(inputFileName2)).getTokens());
	}
	
	public static void getPcode(String inputFileName, String outputFileName) {
		try {
			outList(new PrintStream(new FileOutputStream(new File(outputFileName))),
					new GrammarAnalyzer(new LexicalAnalyzer(getInput(inputFileName)).getTokens())
							.getPcode());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public static void run(String inputFileName) {
		new PL0VM(new GrammarAnalyzer(new LexicalAnalyzer(getInput(inputFileName)).getTokens())
				.getPcode()).run();
	}
	
	public static void run(int stackSize, String inputFileName) {
		new PL0VM(stackSize,
				new GrammarAnalyzer(new LexicalAnalyzer(getInput(inputFileName)).getTokens())
						.getPcode()).run();
	}
	
	public static void run(int stackSize, String inputFileName, String inputStreamFile,
			String onputStreamFile) {
		try {
			InputStream		in	= new FileInputStream(new File(inputStreamFile));
			OutputStream	out	= new FileOutputStream(new File(onputStreamFile));
			
			new PL0VM(stackSize,
					new GrammarAnalyzer(new LexicalAnalyzer(getInput(inputFileName)).getTokens())
							.getPcode(),
					in, out).run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static <T> void outList(PrintStream out, List<T> list) {
		for (T each : list) {
			out.println(each);
		}
	}
	
	private static char[] getInput(String inputFileName) {
		try {
			InputStream	is		= new FileInputStream(inputFileName);
			int			iAvail	= is.available();
			byte[]		bytes	= new byte[iAvail];
			is.read(bytes);
			is.close();
			String codes = new String(bytes, "UTF-8");
			return codes.toCharArray();
		} catch (Exception e) {
			return null;
		}
	}
	
}
