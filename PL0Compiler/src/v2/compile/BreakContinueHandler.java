package v2.compile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import v2.define.Pcode;

public class BreakContinueHandler {
	
	private List<String>				flags					= new ArrayList<>();
	
	private Map<String, Integer>		continueFlagAddresses	= new HashMap<>();
	
	private Map<String, List<Pcode>>	continueFlagCodes		= new HashMap<>();
	
	private List<Pcode>					continueCodes			= new ArrayList<>();
	
	private Map<String, List<Pcode>>	breakFlagCodes			= new HashMap<>();
	
	private List<Pcode>					breakCodes				= new ArrayList<>();
	
	public boolean addFlag(String flag, int nowCodeAddress) {
		if (continueFlagAddresses.containsKey(flag)) {
			return false;
		}
		continueFlagAddresses.put(flag, nowCodeAddress);
		continueFlagCodes.put(flag, new ArrayList<>());
		breakFlagCodes.put(flag, new ArrayList<>());
		flags.add(flag);
		return true;
	}
	
	public void addNoNameFlag(int nowCodeAddress) {
		String uuidFlag = UUID.randomUUID().toString();
		continueFlagAddresses.put(uuidFlag, nowCodeAddress);
		continueFlagCodes.put(uuidFlag, new ArrayList<>());
		breakFlagCodes.put(uuidFlag, new ArrayList<>());
		flags.add(uuidFlag);
	}
	
	private boolean existFlag(String flag) {
		return continueFlagAddresses.containsKey(flag);
	}
	
	public void removeFlag(int breakToCodeAddress) {
		String		flag					= flags.remove(flags.size() - 1);
		
		int			continueToCodeAddress	= continueFlagAddresses.remove(flag);
		
		List<Pcode>	continuePcodes			= continueFlagCodes.remove(flag);
		for (Pcode pcode : continuePcodes) {
			pcode.setA(continueToCodeAddress);
		}
		for (Pcode pcode : continueCodes) {
			pcode.setA(continueToCodeAddress);
		}
		continueCodes.clear();
		
		List<Pcode> breakPcodes = breakFlagCodes.remove(flag);
		for (Pcode pcode : breakPcodes) {
			pcode.setA(breakToCodeAddress);
		}
		for (Pcode pcode : breakCodes) {
			pcode.setA(breakToCodeAddress);
		}
		breakCodes.clear();
	}
	
	public void addContinueCode(Pcode continueCode) {
		continueCodes.add(continueCode);
	}
	
	public boolean addContinueFlagCode(String flag, Pcode continueCode) {
		if (!existFlag(flag)) {
			return false;
		}
		continueFlagCodes.get(flag).add(continueCode);
		return true;
	}
	
	public void addBreakCode(Pcode breakCode) {
		breakCodes.add(breakCode);
	}
	
	public boolean addBreakFlagCode(String flag, Pcode breakCode) {
		if (!existFlag(flag)) {
			return false;
		}
		breakFlagCodes.get(flag).add(breakCode);
		return true;
	}
	
}
