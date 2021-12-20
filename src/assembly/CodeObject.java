package assembly;

import java.io.StringWriter;
import java.util.Collection;
import assembly.instructions.Instruction;
import compiler.Scope;
import compiler.Scope.SymbolTableEntry;

/**
 * Code object class. 
 * 
 * Stores the list of {@link Instruction}s representing the code for a particular
 * subtree, as well as the location of the register where the result of that code
 * is stored (if any). Also tracks with <code>lval</code> whether the temporary
 * holds an lval (an address) or an rval (data).
 */
public class CodeObject {
	InstructionList code;
	String temp; //temporary where result of current code is stored
	Scope.Type type; //type of value stored in temp if rval, type of value in address if lval
	boolean lval; //true if lvalue, false if rvalue
	SymbolTableEntry ste; //null if there is no variable, non-null if there is a variable
	
	// Only used for condition variables!
	// left and right temp
	boolean isflt;
	boolean isderef;
	String ltemp;
	String rtemp;
	String optype;

	public enum OpType {
		EQ,
		NE,
		LT,
		LE,
		GT,
		GE,
	}

	public OpType op;

	public OpType getOpFromString(String s) {
		switch (s) {
		case "<=" : return OpType.LE;
		case "<" : return OpType.LT;
		case ">=" : return OpType.GE;
		case ">" : return OpType.GT;
		case "==" : return OpType.EQ;
		case "!=" : return OpType.NE;
		default : throw new Error ("Unrecognized op type");
		}
	}

	public OpType getReversedOp(OpType ops) {
		switch (ops) {
			case LE : return OpType.GT;
			case LT : return OpType.GE;
			case GE : return OpType.LT;
			case GT : return OpType.LE;
			case EQ : return OpType.NE;
			case NE : return OpType.EQ;
			default : throw new Error ("Bad op type");
		}
	}

	public OpType getOp() {
		return op;
	}

	public void setOp(OpType op) {
		this.op = op;
	}

	public CodeObject() {
		this(null);
		isflt = false;
		isderef = false;
	}

	public CodeObject(SymbolTableEntry ste) {
		this.ste = ste;
		if (ste != null) 
			this.type = ste.getType();
		else
			this.type = null;
		code = new InstructionList();
		isderef = false;

	}
	
	public String toString() {
		StringWriter sw = new StringWriter();
		
		sw.write(";Current temp: " + temp + "\n");
		sw.write(";IR Code: \n");
		
		sw.write(code.toString());
		
		return sw.toString();
	}
	
	public Collection<Instruction> getCode() {
		return code;
	}

	public boolean isVar() {
		return (ste != null);
	}

	public SymbolTableEntry getSTE() {
		return ste;
	}

	public Scope.Type getType() {
		return type;
	}

	public boolean isFlt() {
		return isflt;
	}

	public boolean isderef() {
		return isderef;
	}

}
