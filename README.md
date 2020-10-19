# Barebones language
Challenge 3 of the University of Southampton Space Cadets program.

The barebones language is based on four instructions:

Set a variable to zero.

`clear var;`

Add one to a variable.

`incr var;`
  
Subtract one from a variable.

`decr var;`
  
Loop until var is 0.

`while var not 0 do; ...end;`

In addition, this version of the language has some extra features that the main language does not.
Note that programs compiled with these features will still work on the compiler from Challenge 2: they use the same 4 statments but organised differently.


1) Comments: any text that starts with a `#` is a comment until the next `;` and will be ignored.

2) If/Elif/Else constructs: the syntax is: `if var not 0 do; ... elif var not 0 do; ... else do; ... end;`
  
This project is an interpreter for this very simple language.

There is also a compiler built in (see the `-c` flag) and bytecode executor (the program automatically figures out which one to use).

Running the program:
`java -jar Challenge-3.jar filename` 


Important notes:
The interpreter is fairly forgiving: if a variable doen't exist, it is assumed to equal zero.

Command line switches:
`-v` = Turns the extra printing OFF (this will only print at the end).
`-t` = Time the execution of a script.
`-c` = Compile the input file into bytecode and saves it
`-j` = Just-In-Time compiler (compiles the input and runs the bytecode).

NOTE: These switches cannot be combined in the way you'd expect (so `-vt` is invalid, it must be specified as `-v -t`).
