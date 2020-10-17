package com.github.DanL.Barebones;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
	 * Runs the code we've had loaded.
	 * 
	 * @param printTraceSteps - If true, print out variables at every step. If false, only print at the end.
	 * @throws InvalidInstructionException - We encountered something that didn't parse right.
	 */
	public void execute(boolean printTraceSteps) throws InvalidInstructionException {
		//We now just step through the code one instruction at a time and run it.
		int pointer = 0; //This can go forward and backwards, so don't use a for loop.
		int maxPointer = tokens.length;
		boolean skipping = false; //If this is true, ignore any code we come across.
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
			if (skipping) {
				if (printTraceSteps) {
					System.out.println("Skipped.");
				}
				
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
			var = split[1];
			if (inst.contentEquals("if")) {
				if (vars.getOrDefault(var, 0) == 0) {
					//Zero, so skip.
					skipping = true;
					pointer++;
					continue;
				}
				else {
					//Do not skip.
				}
			}
			if (inst.contentEquals("while")) {
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
