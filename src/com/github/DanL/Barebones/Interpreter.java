package com.github.DanL.Barebones;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;

public class Interpreter {
	
	private HashMap<String, Integer> vars = new HashMap<String, Integer>();
	
	private String[] tokens;
	
	private List<Integer> stack = new ArrayList<Integer>();
	
	private List<String> condStack = new ArrayList<String>();
	
	/**
	 * Takes a file, and loads the file into memory as tokens.
	 * @param readCodeFrom - File to read from.
	 * @throws FileNotFoundException - We can't find the file we're trying to read from.
	 */
	public Interpreter(File readCodeFrom) throws FileNotFoundException {
		Scanner s = null;
		String src = "";
		try {
		    s = new Scanner(readCodeFrom);
		    while (s.hasNextLine()) {
		    	src += s.nextLine();
		    }
		    s.close();
		    tokens = src.split(";");
		}
		catch (Exception e) {
			if (s != null) {
				s.close();
			}
			throw e;
		}
	}
	
	public void printTrace() {
		String name;
		int val;
		for (Entry<String, Integer> varPair: vars.entrySet()) {
			name = varPair.getKey(); val = varPair.getValue();
			System.out.println(name + " = " + val);
		}
	}
	
	/**
	 * Find the next instance of any of the instructions in instructions.
	 * @param startFrom - The index to begin from.
	 * @param instructions - An array of instructions to look for.
	 * @return - The next instance of any of the instructions.
	 */
	private int findNext(int startFrom, String... instructions) {
		String[] branch = {"if", "while"};
		HashSet<String> needsTerminate = new HashSet<String>(Arrays.asList(branch));
		HashSet<String> lookingFor = new HashSet<String>(Arrays.asList(instructions));
		int depth = 0; //If depth is not zero, ignore instructions. Depth is only reduced by end instructions.
		startFrom++; //So we don't instantly "find" the instruction we're on.
		for (;startFrom < tokens.length;startFrom++) {
			String ci = tokens[startFrom].trim().toLowerCase();
			if (ci.startsWith("#")) { //Comment, so ignore it.
				continue;
			}
			String[] split = ci.split(" ");
			String inst = split[0];
			if (depth == 0 && lookingFor.contains(inst)) {
				return startFrom; //Found it.
			}
			else if (needsTerminate.contains(inst)) {
				depth++;
			}
			if (depth != 0) {
				//Non-zero depth, so if this isn't an end, ignore it.
				if (inst.contentEquals("end")) {
					depth--;
				}
			}
		}
		return -1; //Didn't find anything.
		
	}
	
	/**
	 * Runs the code we've had loaded.
	 * 
	 * @param printTraceSteps - If true, print out variables at every step. If false, only print at the end.
	 * @throws InvalidInstructionException - We encountered something that didn't parse right.
	 */
	public void execute(boolean printTraceSteps) throws InvalidInstructionException {
		//We now just step through the code one instruction at a time and run it.
		int pointer = 0; //This can go forward and backwards, so don't use a for loop.
		int maxPointer = tokens.length;
		List<Boolean> isIfResolved = new ArrayList<Boolean>();
		String ci;
		String inst;
		String var;
		String[] split;
		while (pointer < maxPointer) {
			ci = tokens[pointer].trim().toLowerCase();
			if (ci.startsWith("#")) { //Comment, so ignore it.
				pointer++;
				continue;
			}
			if (printTraceSteps) {
				System.out.println(ci);
			}
			split = ci.split(" ");
			inst = split[0];
			if (inst.contentEquals("end")) {
				//Look at the end of the condStack.
				int stackEnd = condStack.size() - 1;
				String latestCondition = condStack.get(stackEnd);
				if (vars.getOrDefault(latestCondition, 0) == 0) {
					//Equals zero, so pop both this and the other stack.
					condStack.remove(stackEnd);
					stack.remove(stackEnd);
				}
				else {
					//Otherwise, don't pop, but instead set pointer to whatever the final value on our stack is.
					pointer = stack.get(stackEnd);
				}
				//Return to the top of the loop, but first make sure to run the post-instruction stuff (explained below)
				pointer++;
				if (printTraceSteps) {
					printTrace();
				}
				continue;
			}
			//Add the else and endif instructions here.
			else if (inst.contentEquals("else")) {
				if (isIfResolved.get(isIfResolved.size() - 1)){
					pointer = findNext(pointer, "endif"); //Aren't looking for any other control structures.
				}
				pointer++; //Add one to the pointer so we don't get stuck.
				continue; //Required (as below) because otherwise we get an error on split[1];
			}
			else if (inst.contentEquals("endif")) {
				//Remove the last boolean from our list.
				isIfResolved.remove(isIfResolved.size() - 1);
				pointer++;
				continue;
			}
			var = split[1];
			if (inst.contentEquals("if")) {
				if (vars.getOrDefault(var, 0) == 0) {
					//Zero, so skip.
					pointer = findNext(pointer, "endif", "elif", "else");
					isIfResolved.add(false);
					continue;
				}
				else {
					//Running this code, but we need to ensure that the next elif doesn't run.
					isIfResolved.add(true);
				}
			}
			else if (inst.contentEquals("elif")) {
				if (isIfResolved.get(isIfResolved.size() - 1) || vars.getOrDefault(var, 0) == 0) {
					//Either condition is false or we've already dealt with this.
					pointer = findNext(pointer, "endif", "elif", "else");
					continue;
				}
				else {
					//Running this code, but we need to ensure that the next elif doesn't run.
					isIfResolved.add(true);
				}
			}
			else if (inst.contentEquals("while")) {
				//While loops are annoying.
				//First off, save the current pointer onto the pointer stack.
				stack.add(pointer);
				//Also, add the variable we are watching onto our other stack.
				condStack.add(var);
			}
			else if (inst.contentEquals("clear")) {
				vars.put(var, 0);
			}
			else if (inst.contentEquals("incr")) {
				vars.put(var, vars.getOrDefault(var, 0) + 1); //Increments a variable
			}
			else if (inst.contentEquals("decr")) {
				vars.put(var, vars.getOrDefault(var, 0) - 1); //Decrements a variable
			}
			else {
				throw new InvalidInstructionException(ci, pointer);
			}
			//Do two things here
			//1) Add 1 to the pointer.
			pointer++;
			//2) If enabled, print out a variable trace.
			if (printTraceSteps) {
				printTrace();
			}
		}
		//Program end.
		printTrace();
	}
	
	/**
	 * Times the program's execution.
	 * @param debugOutput - Should we print variable values at every step, or just at the end?
	 * @return - The time in milliseconds to run the program.
	 * @throws InvalidInstructionException - if an instruction couldn't be parsed.
	 */
	public long executeTimed(boolean debugOutput) throws InvalidInstructionException {
		long timeNow = Instant.now().toEpochMilli();
		execute(debugOutput);
		return Instant.now().toEpochMilli() - timeNow;
	}
}

class InvalidInstructionException extends Throwable{

	/**
	 * Eclipse asked me to.
	 */
	private static final long serialVersionUID = -1674109805049593132L;
	
	private String line;
	private int lineNumber;
	
	public InvalidInstructionException(String ci, int pointer) {
		line = ci;
		lineNumber = pointer + 1; 
	}

	public void printFailedLine() {
		System.out.println("The following instruction on line " + lineNumber + " failed to parse! Instruction: " + line);
	}
	
}
