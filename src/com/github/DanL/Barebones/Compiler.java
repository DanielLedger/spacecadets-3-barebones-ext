package com.github.DanL.Barebones;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;

/**
 * Compiles a Barebones program into bytecode: this bytecode can then be run much quicker than the string code.
 * @author daniel
 *
 */
public class Compiler {
	
	public static boolean isCompiledCode(File f) throws FileNotFoundException {
		FileInputStream in = new FileInputStream(f);
		byte[] head = new byte[4];
		try {
			in.read(head);
			in.close();
		} catch (IOException e) {
			return false;
		}
		for (byte i = 0;i<4;i++) {
			if (head[i] != header[i]) {
				return false;
			}
		}
		return true;
	}
	
	private String[] tokens;
	
	//Stolen from the Interpreter class.
	/**
	 * Takes a file, and loads the file into memory as tokens.
	 * @param readCodeFrom - File to read from.
	 * @throws FileNotFoundException - We can't find the file we're trying to read from.
	 */
	public Compiler(File readCodeFrom) throws FileNotFoundException {
		Scanner s = null;
		String src = "clear c19d8376-bd42-47fa-94c3-aba77f40e2e8; incr c19d8376-bd42-47fa-94c3-aba77f40e2e8;"; //This variable has ID 0 and value 1, so will create a GOTO that always passes.
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
	
	static byte[] numToBytes(Short s) {
		byte[] res = new byte[2];
		res[0] = (byte) ((s >> 8) & 0xff);
		res[1] = (byte) (s & 0xff);
		return res;
	}
	
	static byte[] numToBytes(Integer i) {
		byte[] res = new byte[4];
		res[0] = (byte) ((i >> 24) & 0xff);
		res[1] = (byte) ((i >> 16) & 0xff);
		res[2] = (byte) ((i >> 8) & 0xff);
		res[3] = (byte) (i & 0xff);
		return res;
	}
	
	private static final byte[] header = {0x42, 0x4f, 0x4e, 0x45}; 
	
	/**
	 * Find the next instance of any of the instructions in instructions.
	 * @param startFrom - The index to begin from. We can convert this to a pointer by multiplying by 7.
	 * @param instructions - An array of instructions to look for.
	 * @return - The pointer increment to where the next instance of this data is.
	 */
	private int findNext(int startFrom, String... instructions) {
		String[] branch = {"if", "while"};
		HashSet<String> needsTerminate = new HashSet<String>(Arrays.asList(branch));
		HashSet<String> lookingFor = new HashSet<String>(Arrays.asList(instructions));
		int depth = 0; //If depth is not zero, ignore instructions. Depth is only reduced by end instructions.
		startFrom++; //So we don't instantly "find" the instruction we're on.
		int pointerIncrement = 0; //Since we're attempting to predict where code will be, we need to be clever with looking ahead.
		for (;startFrom < tokens.length;startFrom++) {
			String ci = tokens[startFrom].trim().toLowerCase();
			if (ci.startsWith("#")) { //Comment, so ignore it.
				continue;
			}
			String[] split = ci.split(" ");
			String inst = split[0];
			if (depth == 0 && lookingFor.contains(inst)) {
				return pointerIncrement; //Found it.
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
			if (inst.contentEquals("while") || inst.contentEquals("endif")) {
				//While and endif statements are removed.
				continue;
			}
			pointerIncrement += 7; //We add 7 bytes to the pointer.
			if (inst.contentEquals("if")){
				//If statements compile to 14 bytes, not 7.
				pointerIncrement += 7;
			}
			else if (inst.contentEquals("elif")) {
				//Elif compiles to a massive 21 bytes.
				pointerIncrement += 14;
			}
			
		}
		return -1; //Didn't find anything.
		
	}
	
	
	/**
	 * Creates a compiled binary stream: this stream can be shoved into a file or run directly.
	 * 
	 * FORMAT INFORMATION:
	 * Header - 0x42 0x4f 0x4e 0x45 - Ensures the file can be identified.
	 * Vars - 2 byte unsigned - How many variables need to be stored when executing.
	 * Length - 4 byte unsigned - How many bytes of source code follow.
	 * INSTRUCTION STRUCTURE:
	 * <inst>||<var>||<target (GOTO only), or zeroed>
	 * <08 b>||<16b>||<32b>
	 * For a size of exactly 7 bytes per instruction
	 * inst is one of these:
	 * - 0xb0 - GOTO
	 * - 0x80 - INC
	 * - 0x40 - DEC
	 * - 0x00 - CLEAR
	 * var is the number of the variable we're handling: for a GOTO we only follow the GOTO if it isn't zero.
	 * target is either the pointer we go to (for GOTO) or 0x00000000 for any other instruction. Pointer = bytes FROM FILE START.
	 * OPTIONAL:
	 * Symbol Table - Remainder of the file, maps internal variable numbers to names.
	 * Structured as:
	 * <var num>||0x3d||<name>||0x3b
	 * for each variable.
	 * @return The tokens we were given, compiled into bytes.
	 * @throws IOException 
	 */
	public byte[] compile(boolean verbose) throws IOException {
		ByteArrayOutputStream rawCode = new ByteArrayOutputStream();
		int length = 0;
		ArrayList<String> definedVars = new ArrayList<String>();
		ArrayList<Long> whileLoopPointers = new ArrayList<Long>();
		
		byte[] currentInstruction;
		for (String line: tokens) {
			if (line.trim().startsWith("#")) {
				continue; //Comment, so ignore.
			}
			currentInstruction = new byte[7];
			String[] parts = line.trim().toLowerCase().split(" ");
			String inst = parts[0];
			byte[] varID = new byte[2];
			if (parts.length > 1) {
				String var = parts[1];
				
				if (!definedVars.contains(var)) {
					definedVars.add(var);
				}
				varID = numToBytes((short) (definedVars.indexOf(var) & 0xffff));
			}
			//Note: +2 instructions means "skip the next instruction"
	        if (inst.contentEquals("if")) {
	        	//An if statement becomes GOTO <condition> <+2 instructions> GOTO <always> (next elif/else/endif). 
	        }
	        else if (inst.contentEquals("elif")) {
	        	//An elif statement compiles to the following mess: GOTO <always> <+2 instructions> GOTO <condition> <+2 instructions> GOTO <always> (next elif/else/endif).
	        	//The position of the elif is the middle of that vile mess.
	        }
	        else if (inst.contentEquals("else")) {
	        	//An else statement compiles to a tiny: GOTO <always> (next endif). However please note that the actual "position" of the else is the instruction after that.
	        }
	        else if (inst.contentEquals("endif")) {
	        	//This is removed at compile.
	        	continue;
	        }
	        else if (inst.contentEquals("incr")) {
				//Add the standard bytecode, nothing special.
				currentInstruction[0] = (byte) 0x80;
				currentInstruction[1] = varID[0]; currentInstruction[2] = varID[1];
				//Remaining 4 bytes are zeroed by default, so we don't need to touch them.
			}
			else if (inst.contentEquals("decr")) {
				//Add the standard bytecode, nothing special.
				currentInstruction[0] = (byte) 0x40;
				currentInstruction[1] = varID[0]; currentInstruction[2] = varID[1];
				//Remaining 4 bytes are zeroed by default, so we don't need to touch them.
			}
			else if (inst.contentEquals("clear")) {
				//Add the standard bytecode, nothing special.
				//First byte is also zeroed by default, so do nothing.
				currentInstruction[1] = varID[0]; currentInstruction[2] = varID[1];
				//Remaining 4 bytes are zeroed by default, so we don't need to touch them.
			}
			else if (inst.contentEquals("while")) {
				//The while instruction is parsed slightly weirdly: we remove it, but add a pointer
				//in whileLoopPointers, for the end instruction to come back to.
				long varId0 = varID[0];
				long varId1 = varID[1];
				long pointer = length + ((varId0 & 0xff) << 40) + ((varId1 & 0xff) << 32);
				if (verbose) {
					System.out.println("VarID: " + (varId0 << 40) + "," + ((varId1 & 0xff) << 32));
					System.out.println(pointer);
				}
				whileLoopPointers.add(pointer);
				//length += 7;
				continue; //Don't write anything to the byte stream.
			}
			else if (inst.contentEquals("end")) {
				//The strangest and most frustrating instruction:
				//1) Retrieve the most recent pointer and extract the target and variable ID from it.
				//2) Construct a GOTO instruction based on that.
				long lastPointer = whileLoopPointers.get(whileLoopPointers.size() - 1);
				whileLoopPointers.remove(lastPointer);
				if (verbose) {
					System.out.println("-----------");
					System.out.println(lastPointer);
				}
				varID[0] = (byte) ((lastPointer >> 40) & 0xff);
				varID[1] = (byte) ((lastPointer >> 32) & 0xff);
				byte[] target = numToBytes((int) (lastPointer & 0xffffffff)); //Update: it was not required. I can't do hex apparently.
				currentInstruction[0] = (byte) 0xb0;
				currentInstruction[1] = varID[0]; currentInstruction[2] = varID[1];
				for (byte i = 0; i < 4; i++) {
					currentInstruction[i + 3] = target[i];
				}
			}
			length += 7;
			if (verbose) {
				System.out.println("Writing the following bytes due to the token " + line);
				for (byte b: currentInstruction) {
					System.out.println(b);
				}
			}
			rawCode.write(currentInstruction);
		}
		ByteArrayOutputStream returnStream = new ByteArrayOutputStream();
		returnStream.write(header);
		returnStream.write(numToBytes((short) definedVars.size()));
		returnStream.write(numToBytes(length));
		returnStream.write(rawCode.toByteArray());
		return returnStream.toByteArray(); //Temporary
	}
}

/**
 * Represents an edit that must be applied to the compiled code before it is returned.
 * @author daniel
 */
class CodeEdit{
	byte[] newInstruction = new byte[7];
	
	void setInstruction(byte inst) {
		newInstruction[0] = inst;
	}
	
	void setVar(int varNum) {
		newInstruction[1] = (byte) ((varNum >> 8) & 0xff);
		newInstruction[2] = (byte) ((varNum) & 0xff);
	}
	
	void setPointer(int pointer) {
		byte[] pointerData = Compiler.numToBytes(pointer);
		for (int i = 0; i<4; i++) {
			newInstruction[i+3] = pointerData[i];
		}
	}
	
	byte[] getNewInstruction() {
		return newInstruction;
	}
}
