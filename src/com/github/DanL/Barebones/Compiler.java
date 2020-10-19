package com.github.DanL.Barebones;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
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
	
	private byte[] numToBytes(Short s) {
		byte[] res = new byte[2];
		res[0] = (byte) ((s >> 8) & 0xff);
		res[1] = (byte) (s & 0xff);
		return res;
	}
	
	private byte[] numToBytes(Integer i) {
		byte[] res = new byte[4];
		res[0] = (byte) ((i >> 24) & 0xff);
		res[1] = (byte) ((i >> 16) & 0xff);
		res[2] = (byte) ((i >> 8) & 0xff);
		res[3] = (byte) (i & 0xff);
		return res;
	}
	
	private static final byte[] header = {0x42, 0x4f, 0x4e, 0x45}; 
	
	
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
			if (inst.contentEquals("incr")) {
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
