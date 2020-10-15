import struct

with open(input("Enter file to check> "), "rb") as f:
    data = f.read()
    
header = data[:10]
instructions = data[10:]

asciiHead, memSize, progSize = struct.unpack(">4sHI", header)

print("FILE type: %s." % asciiHead)
print("Mem. size = %i.\nProg. size= %i." % (memSize, progSize))

def interpretInstruction(byteInst, num):
    inst, var, pointer = struct.unpack(">BHI", byteInst)
    if inst == 0x00:
        inst = "CLER"
    elif inst == 0xB0:
        inst = "GOTO"
    elif inst == 0x80:
        inst = "INCR"
    elif inst == 0x40:
        inst = "DECR"
    else:
        print("Invalid instruction byte of %i!" % inst)
        return
    if pointer == 0: #Not a GOTO
        print("%i: %s %i" % (num, inst, var))
    else:
        print("%i: %s %i if %i not zero." % (num, inst, pointer, var))
        
for i in range(0, progSize, 7):
    interpretInstruction(instructions[i:i+7], i)