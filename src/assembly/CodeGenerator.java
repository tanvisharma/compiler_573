package assembly;

import java.util.List;

import compiler.Scope.SymbolTableEntry;
import ast.visitor.AbstractASTVisitor;

import ast.*;
import assembly.instructions.*;
import compiler.Scope;

public class CodeGenerator extends AbstractASTVisitor<CodeObject> {

	int intRegCount;
	int floatRegCount;
	static final public char intTempPrefix = 't';
	static final public char floatTempPrefix = 'f';
	
	int loopLabel;
	int elseLabel;
	int outLabel;

	String currFunc;
	
	public CodeGenerator() {
		loopLabel = 0;
		elseLabel = 0;
		outLabel = 0;
		intRegCount = 0;		
		floatRegCount = 0;
	}

	public int getIntRegCount() {
		return intRegCount;
	}

	public int getFloatRegCount() {
		return floatRegCount;
	}
	
	/**
	 * Generate code for Variables
	 * 
	 * Create a code object that just holds a variable
	 * 
	 * Important: add a pointer from the code object to the symbol table entry
	 *            so we know how to generate code for it later (we'll need to find
	 *            the address)
	 * 
	 * Mark the code object as holding a variable, and also as an lval
	 */
	@Override
	protected CodeObject postprocess(VarNode node) {
		
		Scope.SymbolTableEntry sym = node.getSymbol();
		
		CodeObject co = new CodeObject(sym);
		co.lval = true;
		co.type = node.getType();

		return co;
	}

	/** Generate code for IntLiterals
	 * 
	 * Use load immediate instruction to do this.
	 */
	@Override
	protected CodeObject postprocess(IntLitNode node) {
		CodeObject co = new CodeObject();
		
		//Load an immediate into a register
		//The li and la instructions are the same, but it's helpful to distinguish
		//for readability purposes.
		//li tmp' value
		Instruction i = new Li(generateTemp(Scope.InnerType.INT), node.getVal());

		co.code.add(i); //add this instruction to the code object
		co.lval = false; //co holds an rval -- data
		co.temp = i.getDest(); //temp is in destination of li
		co.type = node.getType();

		return co;
	}

	/** Generate code for FloatLiteras
	 * 
	 * Use load immediate instruction to do this.
	 */
	@Override
	protected CodeObject postprocess(FloatLitNode node) {
		CodeObject co = new CodeObject();
		
		//Load an immediate into a regisster
		//The li and la instructions are the same, but it's helpful to distinguish
		//for readability purposes.
		//li tmp' value
		Instruction i = new FImm(generateTemp(Scope.InnerType.FLOAT), node.getVal());

		co.code.add(i); //add this instruction to the code object
		co.lval = false; //co holds an rval -- data
		co.temp = i.getDest(); //temp is in destination of li
		co.type = node.getType();

		return co;
	}

	/**
	 * Generate code for binary operations.
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from left child
	 * Step 1a: if left child is an lval, add a load to get the data
	 * Step 2: add code from right child
	 * Step 2a: if right child is an lval, add a load to get the data
	 * Step 3: generate binary operation using temps from left and right
	 * 
	 * Don't forget to update the temp and lval fields of the code object!
	 * 	   Hint: where is the result stored? Is this data or an address?
	 * 
	 */
	@Override
	protected CodeObject postprocess(BinaryOpNode node, CodeObject left, CodeObject right) {

		CodeObject co = new CodeObject();
		
		/* FILL IN FROM STEP 2 */
		System.out.println("Calling rvalify from BinaryNode left");
		if (left.lval) {
			left = rvalify(left);
		}
		co.code.addAll(left.code);

		//STEP7
		System.out.println("Left type: "+left.type);
		if (left.getType().type == Scope.InnerType.INT) {
			if (right.getType().type == Scope.InnerType.FLOAT) {
				co.code.add(new ImovF(left.temp, generateTemp(Scope.InnerType.FLOAT) ));
				left.temp = co.code.getLast().getDest();
				left.type = right.type;
			}
		}

		System.out.println("Calling rvalify from BinaryNode right");
		if (right.lval) {
			right = rvalify(right);
		}
		co.code.addAll(right.code);

		System.out.println("Right type: "+right.type);
		if (right.getType().type == Scope.InnerType.INT) {
			if (left.getType().type == Scope.InnerType.FLOAT) {
				co.code.add(new ImovF(right.temp, generateTemp(Scope.InnerType.FLOAT)));
				right.temp = co.code.getLast().getDest();
				right.type = left.type;
			}
		}


		
		Instruction oper;


		switch(node.getOp()) {
			case ADD: 
				if (left.getType().type == Scope.InnerType.FLOAT) {
					oper = new FAdd(left.temp, right.temp, generateTemp(Scope.InnerType.FLOAT));
				} else { //Assuming int
					oper = new Add(left.temp, right.temp, generateTemp(Scope.InnerType.INT));
				}
				break;
			case SUB:
				if (left.getType().type == Scope.InnerType.FLOAT) {
					oper = new FSub(left.temp, right.temp, generateTemp(Scope.InnerType.FLOAT));
				} else { //Assuming float
					oper = new Sub(left.temp, right.temp, generateTemp(Scope.InnerType.INT));
				}
				break;
			case DIV:
				if (left.getType().type == Scope.InnerType.FLOAT) {
					oper = new FDiv(left.temp, right.temp, generateTemp(Scope.InnerType.FLOAT));
				} else { //Assuming float
					oper = new Div(left.temp, right.temp, generateTemp(Scope.InnerType.INT));
				}
				break;
			case MUL:
				if (left.getType().type == Scope.InnerType.FLOAT) {
					oper = new FMul(left.temp, right.temp, generateTemp(Scope.InnerType.FLOAT));
				} else { //Assuming float
					oper = new Mul(left.temp, right.temp, generateTemp(Scope.InnerType.INT));
				}
				break;
			default:
				throw new Error("Binary Operator not recognized");
		}

		co.code.add(oper);

		co.lval = false;
		co.temp = oper.getDest();
		// co.type = node.getType();
		co.type = left.type;

		return co;
	}

	/**
	 * Generate code for unary operations.
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from child expression
	 * Step 1a: if child is an lval, add a load to get the data
	 * Step 2: generate instruction to perform unary operation
	 * 
	 * Don't forget to update the temp and lval fields of the code object!
	 * 	   Hint: where is the result stored? Is this data or an address?
	 * 
	 */
	@Override
	protected CodeObject postprocess(UnaryOpNode node, CodeObject expr) {
		
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 2 */
		//Step 1
		if (expr.lval) {
			expr = rvalify(expr);
		}
		co.code.addAll(expr.code);

		Instruction i;

		switch (node.getOp()) {
			case NEG:
				if (expr.getType().type == Scope.InnerType.INT) {
					i = new Neg(expr.temp, generateTemp(expr.type.type));
				} else { //Assuming float
					i = new FNeg(expr.temp, generateTemp(expr.type.type));
				}
				break;
			default:
				throw new Error ("Unrecognized op type");
		}
		co.code.add(i);
		co.lval = false;
		co.type = expr.type;
		co.temp = i.getDest();

		return co;
	}

	//Step 7
	@Override
	protected CodeObject postprocess(TypeCastNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		return co;
	}


	/**
	 * Generate code for assignment statements
	 * 
	 * Step 0: create new code object
	 * Step 1: if LHS is a variable, generate a load instruction to get the address into a register
	 * Step 1a: add code from LHS of assignment (make sure it results in an lval!)
	 * Step 2: add code from RHS of assignment
	 * Step 2a: if right child is an lval, add a load to get the data
	 * Step 3: generate store
	 * 
	 * Hint: it is going to be easiest to just generate a store with a 0 immediate
	 * offset, and the complete store address in a register:
	 * 
	 * sw rhs 0(lhs)
	 */
	@Override
	protected CodeObject postprocess(AssignNode node, CodeObject left,
			CodeObject right) {
		
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 2 */
		//Step 1a
		InstructionList ileft = new InstructionList();
		if (left.isVar()) {
			if (!left.getSTE().isLocal()) {
				ileft = generateAddrFromVariable(left);
			}
		} else {
			ileft = left.code;
		}

		//Step 1b
		co.code.addAll(ileft);

		//Step 2
		System.out.println("Calling rvalify from AssignNode right");
		if (right.lval) {
			right = rvalify(right);
		}
		co.code.addAll(right.code);

		//Step 7
		Scope.InnerType lhs_type = left.getType().type;
		Scope.InnerType rhs_type = right.getType().type;
		if (rhs_type != lhs_type) {
			System.out.println("Dealing type mismatch in AssigNode...");
			if (lhs_type == Scope.InnerType.FLOAT) {
				co.code.add(new ImovF(right.temp, generateTemp(Scope.InnerType.FLOAT)));
				right.temp = co.code.getLast().getDest();
				right.type = left.type;
			}else if (lhs_type == Scope.InnerType.INT) {
				co.code.add(new FmovI(right.temp, generateTemp(Scope.InnerType.INT)));
				right.temp = co.code.getLast().getDest();
				right.type = left.type;
			} else {
				throw new Error("Type mismatch in assign");
			}
		}
		
		//Step 3
		Instruction sw = null;
		System.out.println("Type in AssignNOde: "+node.getType().type);
		switch(node.getType().type) {
			case INT:
				if (left.isderef()){
					System.out.println("left addr: "+left.temp);
					sw = new Sw(right.temp, left.temp, "0");

				}else {
					if (!left.getSTE().isLocal()) {
						sw = new Sw(right.temp, ileft.getLast().getDest(), "0");
					} else {
						sw = new Sw(right.temp, "fp", left.getSTE().addressToString());
					}
				}

				break;

			case PTR:
				
				if (!left.isderef()){
					if (!left.getSTE().isLocal()) {
						sw = new Sw(right.temp, ileft.getLast().getDest(), "0");
					} else {
						sw = new Sw(right.temp, "fp", left.getSTE().addressToString());
					}
				}else{ //when a sub-pointer coming from multi d ptr
					sw = new Sw(right.temp, left.temp, "0");
				}

				break;

			case FLOAT:
				if (left.isderef()){
					sw = new Fsw(right.temp, left.temp, "0"); //left.temp is a deref address

				} else if (!left.getSTE().isLocal()) {
					sw = new Fsw(right.temp, ileft.getLast().getDest(), "0");
				} else {
					sw = new Fsw(right.temp, "fp", left.getSTE().addressToString());
				}
				break;
			default:
				System.out.println("Type error in assign node "+node.getType().type);
				throw new Error("Shouldn't read into other variable");
		}
		
		co.code.add(sw);
		
		return co;
	}

	/**
	 * Add together all the lists of instructions generated by the children
	 */
	@Override
	protected CodeObject postprocess(StatementListNode node,
			List<CodeObject> statements) {
		CodeObject co = new CodeObject();
		//add the code from each individual statement
		for (CodeObject subcode : statements) {
			co.code.addAll(subcode.code);
		}
		co.type = null; //set to null to trigger errors
		return co;
	}
	
	/**
	 * Generate code for read
	 * 
	 * Step 0: create new code object
	 * Step 1: add code from VarNode (make sure it's an lval)
	 * Step 2: generate GetI instruction, storing into temp
	 * Step 3: generate store, to store temp in variable
	 */
	@Override
	protected CodeObject postprocess(ReadNode node, CodeObject var) {
		
		//Step 0
		CodeObject co = new CodeObject();

		//Generating code for read(id)
		assert(var.getSTE() != null); //var had better be a variable

		InstructionList il = new InstructionList();
		switch(node.getType().type) {
			case INT: 
				//Code to generate if INT:
				//geti tmp
				//if var is global: la tmp', <var>; sw tmp 0(tmp')
				//if var is local: sw tmp offset(fp)
				Instruction geti = new GetI(generateTemp(Scope.InnerType.INT));
				il.add(geti);
				InstructionList store = new InstructionList();
				if (var.getSTE().isLocal()) {
					store.add(new Sw(geti.getDest(), "fp", String.valueOf(var.getSTE().addressToString())));
				} else {
					store.addAll(generateAddrFromVariable(var));
					store.add(new Sw(geti.getDest(), store.getLast().getDest(), "0"));
				}
				il.addAll(store);
				break;
			case FLOAT:
				//Code to generate if FLOAT:
				//getf tmp
				//if var is global: la tmp', <var>; fsw tmp 0(tmp')
				//if var is local: fsw tmp offset(fp)
				Instruction getf = new GetF(generateTemp(Scope.InnerType.FLOAT));
				il.add(getf);
				InstructionList fstore = new InstructionList();
				if (var.getSTE().isLocal()) {
					fstore.add(new Fsw(getf.getDest(), "fp", String.valueOf(var.getSTE().addressToString())));
				} else {
					fstore.addAll(generateAddrFromVariable(var));
					fstore.add(new Fsw(getf.getDest(), fstore.getLast().getDest(), "0"));
				}
				il.addAll(fstore);
				break;
			default:
				throw new Error("Shouldn't read into other variable");
		}
		
		co.code.addAll(il);

		co.lval = false; //doesn't matter
		co.temp = null; //set to null to trigger errors
		co.type = null; //set to null to trigger errors

		return co;
	}

	/**
	 * Generate code for print
	 * 
	 * Step 0: create new code object
	 * 
	 * If printing a string:
	 * Step 1: add code from expression to be printed (make sure it's an lval)
	 * Step 2: generate a PutS instruction printing the result of the expression
	 * 
	 * If printing an integer:
	 * Step 1: add code from the expression to be printed
	 * Step 1a: if it's an lval, generate a load to get the data
	 * Step 2: Generate PutI that prints the temporary holding the expression
	 */
	@Override
	protected CodeObject postprocess(WriteNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		//generating code for write(expr)

		//for strings, we expect a variable
		if (node.getWriteExpr().getType().type == Scope.InnerType.STRING) {
			//Step 1:
			assert(expr.getSTE() != null);
			
			System.out.println("; generating code to print " + expr.getSTE());

			//Get the address of the variable
			InstructionList addrCo = generateAddrFromVariable(expr);
			co.code.addAll(addrCo);

			//Step 2:
			Instruction write = new PutS(addrCo.getLast().getDest());
			co.code.add(write);
		} else {
			//Step 1a:
			//if expr is an lval, load from it
			if (expr.lval == true) {
				expr = rvalify(expr);
			}
			
			//Step 1:
			co.code.addAll(expr.code);

			//Step 2:
			//if type of writenode is int, use puti, if float, use putf
			Instruction write = null;
			switch(node.getWriteExpr().getType().type) {
			case STRING: throw new Error("Shouldn't have a STRING here");
			case INT: 
			case PTR: //should work the same way for pointers
				write = new PutI(expr.temp); break;
			case FLOAT: write = new PutF(expr.temp); break;
			default: throw new Error("WriteNode has a weird type");
			}

			co.code.add(write);
		}

		co.lval = false; //doesn't matter
		co.temp = null; //set to null to trigger errors
		co.type = null; //set to null to trigger errors

		return co;
	}

	/**
	 * FILL IN FROM STEP 3
	 * 
	 * Generating an instruction sequence for a conditional expression
	 * 
	 * Implement this however you like. One suggestion:
	 *
	 * Create the code for the left and right side of the conditional, but defer
	 * generating the branch until you process IfStatementNode or WhileNode (since you
	 * do not know the labels yet). Modify CodeObject so you can save the necessary
	 * information to generate the branch instruction in IfStatementNode or WhileNode
	 * 
	 * Alternate idea 1:
	 * 
	 * Don't do anything as part of CodeGenerator. Create a new visitor class
	 * that you invoke *within* your processing of IfStatementNode or WhileNode
	 * 
	 * Alternate idea 2:
	 * 
	 * Create the branch instruction in this function, then tweak it as necessary in
	 * IfStatementNode or WhileNode
	 * 
	 * Hint: you may need to preserve extra information in the returned CodeObject to
	 * make sure you know the type of branch code to generate (int vs float)
	 */
	@Override
	protected CodeObject postprocess(CondNode node, CodeObject left, CodeObject right) {
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 3*/
		// rvalify left and right nodes as needed
		if (left.lval) {
			left = rvalify(left);
		}

		if (right.lval) {
			right = rvalify(right);
		}


		// Load info about the code object!
		co.isflt = true;
		if (left.getType().type == Scope.InnerType.INT) {
			co.isflt = false;
		}

		// Load info about the op into the code object
		co.optype = node.getStringFromOp(node.getOp());
		co.setOp(co.getOpFromString(co.optype));

		// Load the left and right register address into the temps.
		co.ltemp = left.temp;
		co.rtemp = right.temp;

		co.code.addAll(left.code);
		co.code.addAll(right.code);

		return co;
	}

	/**
	 * FILL IN FROM STEP 3
	 * 
	 * Step 0: Create code object
	 * 
	 * Step 1: generate labels
	 * 
	 * Step 2: add code from conditional expression
	 * 
	 * Step 3: create branch statement (if not created as part of step 2)
	 * 			don't forget to generate correct branch based on type
	 * 
	 * Step 4: generate code
	 * 		<cond code>
	 *		<flipped branch> elseLabel
	 *		<then code>
	 *		j outLabel
	 *		elseLabel:
	 *		<else code>
	 *		outLabel:
	 *
	 * Step 5 insert code into code object in appropriate order.
	 */
	@Override
	protected CodeObject postprocess(IfStatementNode node, CodeObject cond, CodeObject tlist, CodeObject elist) {
		//Step 0:
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 3*/
		CodeObject tmpo = new CodeObject();
		
		// Step 1:
		String tempVar = generateTemp(Scope.InnerType.INT);
		String elseLabel = generateElseLabel();
		String outLabel = generateOutLabel();

		// Step 2:
		co.code.addAll(cond.code);

		Instruction oper;
		// Step3
		switch(cond.getReversedOp(cond.getOp())) {
			case EQ: 
			if (cond.isflt == false) {
				oper = new Beq(cond.ltemp, cond.rtemp, elseLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				// Generate a new temp for result
				oper = new Feq(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
			}
			break;
			case NE: 
			if (cond.isflt == false) {
				oper = new Bne(cond.ltemp, cond.rtemp, elseLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				// Generate a new temp for result
				oper = new Feq(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
				oper = new Bne(tempVar, "x0", elseLabel);
				tmpo.code.add(oper);
			}
			break;
			case LT: 
			if (cond.isflt == false) {
				oper = new Blt(cond.ltemp, cond.rtemp, elseLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				oper = new Flt(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
			}
			break;
			case LE: 
			if (cond.isflt == false) {
				oper = new Ble(cond.ltemp, cond.rtemp, elseLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				oper = new Fle(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
			}
			break;
			case GT: 
			if (cond.isflt == false) {
				oper = new Bgt(cond.ltemp, cond.rtemp, elseLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				oper = new Fle(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
				oper = new Bne(tempVar, "x0", elseLabel);
				tmpo.code.add(oper);
			}
			break;
			case GE: 
			if (cond.isflt == false) {
				oper = new Bge(cond.ltemp, cond.rtemp, elseLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				oper = new Flt(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
				oper = new Beq(tempVar, "x0", elseLabel);
				tmpo.code.add(oper);
			}
			break;

			default:
				throw new Error("Comparation OP not recognized");
		}

		// Step 4:
		tmpo.code.addAll(tlist.code);
		oper = new J(outLabel);
		tmpo.code.add(oper);

		oper = new Label(elseLabel);
		tmpo.code.add(oper);

		tmpo.code.addAll(elist.code);

		oper = new Label(outLabel);
		tmpo.code.add(oper);

		// Step 5
		co.code.addAll(tmpo.code);

		return co;
	}

		/**
	 * FILL IN FROM STEP 3
	 * 
	 * Step 0: Create code object
	 * 
	 * Step 1: generate labels
	 * 
	 * Step 2: add code from conditional expression
	 * 
	 * Step 3: create branch statement (if not created as part of step 2)
	 * 			don't forget to generate correct branch based on type
	 * 
	 * Step 4: generate code
	 * 		loopLabel:
	 *		<cond code>
	 *		<flipped branch> outLabel
	 *		<body code>
	 *		j loopLabel
	 *		outLabel:
	 *
	 * Step 5 insert code into code object in appropriate order.
	 */
	@Override
	protected CodeObject postprocess(WhileNode node, CodeObject cond, CodeObject slist) {
		//Step 0:
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 3*/
		CodeObject tmpo = new CodeObject();

		// Step 1:
		String tempVar = generateTemp(Scope.InnerType.INT);
		String loopLabel = generateLoopLabel() ;
		String outLabel = generateOutLabel();


		Instruction oper;

		// loopLabel to loop back to!
		oper = new Label(loopLabel);
		co.code.add(oper);

		// Step 2:
		co.code.addAll(cond.code);


		// Step3
		switch(cond.getReversedOp(cond.getOp())) {
			case EQ: 
			if (cond.isflt == false) {
				oper = new Beq(cond.ltemp, cond.rtemp, outLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				// Generate a new temp for result
				oper = new Feq(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
			}
			break;
			case NE: 
			if (cond.isflt == false) {
				oper = new Bne(cond.ltemp, cond.rtemp, outLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				// Generate a new temp for result
				oper = new Feq(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
				oper = new Bne(tempVar, "x0", outLabel);
				tmpo.code.add(oper);
			}
			break;
			case LT: 
			if (cond.isflt == false) {
				oper = new Blt(cond.ltemp, cond.rtemp, outLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				oper = new Flt(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
			}
			break;
			case LE: 
			if (cond.isflt == false) {
				oper = new Ble(cond.ltemp, cond.rtemp, outLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				oper = new Fle(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
			}
			break;
			case GT: 
			if (cond.isflt == false) {
				oper = new Bgt(cond.ltemp, cond.rtemp, outLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				oper = new Fle(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
				oper = new Bne(tempVar, "x0", outLabel);
				tmpo.code.add(oper);
			}
			break;
			case GE: 
			if (cond.isflt == false) {
				oper = new Bge(cond.ltemp, cond.rtemp, outLabel);
				tmpo.code.add(oper);
			} else { //Assuming float
				oper = new Flt(cond.ltemp, cond.rtemp, tempVar);
				tmpo.code.add(oper);
				oper = new Bne(tempVar, "x0", outLabel);
				tmpo.code.add(oper);
			}
			break;

			default:
				throw new Error("Comparation OP not recognized");
		}

		// Step 4:
		tmpo.code.addAll(slist.code);
		oper = new J(loopLabel);
		tmpo.code.add(oper);

		oper = new Label(outLabel);
		tmpo.code.add(oper);

		// Step 5
		co.code.addAll(tmpo.code);

		return co;
	}

	/**
	 * FILL IN FOR STEP 4
	 * 
	 * Generating code for returns
	 * 
	 * Step 0: Generate new code object
	 * 
	 * Step 1: Add retExpr code to code object (rvalify if necessary)
	 * 
	 * Step 2: Store result of retExpr in appropriate place on stack (fp + 8)
	 * 
	 * Step 3: Jump to out label (use @link{generateFunctionOutLabel()})
	 */
	@Override
	protected CodeObject postprocess(ReturnNode node, CodeObject retExpr) {
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 4 */
		if(node.getRetExpr() != null){
			System.out.println("Calling rvalify from ReturnNode");
			if (retExpr.lval == true) {
				retExpr = rvalify(retExpr);
			}
			co.code.addAll(retExpr.code);


			switch(node.getRetExpr().getType().type) {
			case INT:
			case PTR:
				co.code.add(new Sw(retExpr.temp, "fp", "8"));
				break;
			case FLOAT:
				co.code.add(new Fsw(retExpr.temp, "fp", "8"));
				break;
			default:
				throw new Error("Returning something other than int and float.");
			}

		}

		co.code.add(new J(generateFunctionOutLabel()));
		return co;
	}

	@Override
	protected void preprocess(FunctionNode node) {
		// Generate function label information, used for other labels inside function
		currFunc = node.getFuncName();

		//reset register counts; each function uses new registers!
		intRegCount = 0;
		floatRegCount = 0;
	}

	/**
	 * FILL IN FOR STEP 4
	 * 
	 * Generate code for functions
	 * 
	 * Step 1: add the label for the beginning of the function
	 * 
	 * Step 2: manage frame  pointer
	 * 			a. Save old frame pointer
	 * 			b. Move frame pointer to point to base of activation record (current sp)
	 * 			c. Update stack pointer
	 * 
	 * Step 3: allocate new stack frame (use scope infromation from FunctionNode)
	 * 
	 * Step 4: save registers on stack (Can inspect intRegCount and floatRegCount to know what to save)
	 * 
	 * Step 5: add the code from the function body
	 * 
	 * Step 6: add post-processing code:
	 * 			a. Label for `return` statements inside function body to jump to
	 * 			b. Restore registers
	 * 			c. Deallocate stack frame (set stack pointer to frame pointer)
	 * 			d. Reset fp to old location
	 * 			e. Return from function
	 */
	@Override
	protected CodeObject postprocess(FunctionNode node, CodeObject body) {
		CodeObject co = new CodeObject();

		/* FILL IN */
		// Step 1:
		String funcBegLabel = generateFunctionLabel();
		co.code.add(new Label(funcBegLabel));

		// Step 2:
		//a.
		co.code.add(new Sw("fp", "sp", "0"));
		//b.
		co.code.add(new Mv("sp", "fp"));
		//c.
		co.code.add(new Addi("sp", "-4", "sp")); //label 

		//Step 3:
		co.code.add(new Addi("sp", String.valueOf(node.getScope().getNumLocals()*-4), "sp"));

		//Step 4:
		for (int i=1; i <= getIntRegCount(); i++) {
			co.code.add(new Sw(intTempPrefix+String.valueOf(i), "sp", "0"));
			co.code.add(new Addi("sp", "-4", "sp"));
		}
		for (int i=1; i <= getFloatRegCount(); i++) {
			co.code.add(new Fsw(floatTempPrefix+String.valueOf(i), "sp", "0"));
			co.code.add(new Addi("sp", "-4", "sp"));
		}

		//Step 5:
		co.code.addAll(body.code);

		//Step 6:
		//a.
		String funcRetLabel = generateFunctionOutLabel();
		co.code.add(new Label(funcRetLabel));
		//b.
		for (int i=getFloatRegCount(); i >= 1; i--) {
			co.code.add(new Addi("sp", "4", "sp"));
			co.code.add(new Flw(floatTempPrefix+String.valueOf(i), "sp", "0"));
		}
		for (int i=getIntRegCount(); i >= 1; i--) {
			co.code.add(new Addi("sp", "4", "sp"));
			co.code.add(new Lw(intTempPrefix+String.valueOf(i), "sp", "0"));
		}
		//c.
		co.code.add(new Mv("fp", "sp"));
		//d.
		co.code.add(new Lw("fp", "fp", "0"));
		//e.
		co.code.add(new Ret());

		return co;
	}

	/**
	 * Generate code for the list of functions. This is the "top level" code generation function
	 * 
	 * Step 1: Set fp to point to sp
	 * 
	 * Step 2: Insert a JR to main
	 * 
	 * Step 3: Insert a HALT
	 * 
	 * Step 4: Include all the code of the functions
	 */
	@Override
	protected CodeObject postprocess(FunctionListNode node, List<CodeObject> funcs) {
		CodeObject co = new CodeObject();

		co.code.add(new Mv("sp", "fp"));
		co.code.add(new Jr(generateFunctionLabel("main")));
		co.code.add(new Halt());
		co.code.add(new Blank());

		//add code for each of the functions
		for (CodeObject c : funcs) {
			co.code.addAll(c.code);
			co.code.add(new Blank());
		}

		return co;
	}

	/**
	* 
	* FILL IN FOR STEP 4
	* 
	* Generate code for a call expression
	 * 
	 * Step 1: For each argument:
	 * 
	 * 	Step 1a: insert code of argument (don't forget to rvalify!)
	 * 
	 * 	Step 1b: push result of argument onto stack 
	 * 
	 * Step 2: alloate space for return value
	 * 
	 * Step 3: push current return address onto stack
	 * 
	 * Step 4: jump to function
	 * 
	 * Step 5: pop return address back from stack
	 * 
	 * Step 6: pop return value into fresh temporary (destination of call expression)
	 * 
	 * Step 7: remove arguments from stack (move sp)
	 * 
	 * Add special handling for malloc and free
	 */

	 /**
	  * FOR STEP 6: Make sure to handle VOID functions properly
	  */
	@Override
	protected CodeObject postprocess(CallNode node, List<CodeObject> args) {
		
		//STEP 0
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 4 */
		for (CodeObject ar : args) {
			System.out.println("Calling rvalify from CallNode arg");

			if (ar.lval) {
				ar = rvalify(ar);
			}
			co.code.addAll(ar.code);
			switch(ar.getType().type) {
				case INT:
				case PTR:
					co.code.add(new Sw(ar.temp, "sp", "0"));
					break;
				case FLOAT:
					co.code.add(new Fsw(ar.temp, "sp", "0"));
					break;
				default:
					throw new Error("Argument other than int and float.");

			}
			co.code.add(new Addi("sp", "-4", "sp"));
		}
		//Step 2:
		co.code.add(new Addi("sp", "-4", "sp"));
		//Step 3:
		co.code.add(new Sw("ra", "sp", "0"));
		co.code.add(new Addi("sp", "-4", "sp"));
		//Step 4:
		co.code.add(new Jr(generateFunctionLabel(node.getFuncName())));
		//Step 5:
		co.code.add(new Addi("sp", "4", "sp"));
		co.code.add(new Lw("ra", "sp", "0"));
		co.code.add(new Addi("sp", "4", "sp"));
		//Step 6:
		switch(node.getType().type) {
			case INT:
			case PTR:
				co.code.add(new Lw(generateTemp(Scope.InnerType.INT), "sp", "0"));
				break;
			case FLOAT:
				co.code.add(new Flw(generateTemp(Scope.InnerType.FLOAT), "sp", "0"));
				break;
			case VOID:
				break;
			default:
				throw new Error("What the");	
		}
		String outtemp = co.code.getLast().getDest();
		//Step 7:
		co.code.add(new Addi("sp", String.valueOf(4*args.size()), "sp"));

		co.temp = outtemp;
		return co;
	}	
	
	/**
	 * Generate code for * (expr)
	 * 
	 * Goal: convert the r-val coming from expr (a computed address) into an l-val (an address that can be loaded/stored)
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: Rvalify expr if needed
	 * 
	 * Step 2: Copy code from expr (including any rvalification) into new code object
	 * 
	 * Step 3: New code object has same temporary as old code, but now is marked as an l-val
	 * 
	 * Step 4: New code object has an "unwrapped" type: if type of expr is * T, type of temporary is T. Can get this from node
	 */
	@Override
	protected CodeObject postprocess(PtrDerefNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		// Step 1
		// If the expr is in the symbol table you rvalify
		System.out.println("Calling rvalify from PtrDeref expr");
		if (expr.lval) {
			expr = rvalify(expr);
		}

		// Step 2
		co.code.addAll(expr.code);

		// Step 3
		co.temp = expr.temp;
		co.lval = true;
		
		// Step 4
		System.out.println("Inside ptrderef with addr reg: "+expr.temp+" and type: "+node.getType());
		co.type = node.getType(); // Get the unwrapped type

		co.isderef = true;

		/* FILL IN FOR STEP 6 */

		return co;
	}

	/**
	 * Generate code for a & (expr)
	 * 
	 * Goal: convert the lval coming from expr (an address) to an r-val (a piece of data that can be used)
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: If lval is a variable, generate code to put address into a register (e.g., generateAddressFromVar)
	 *			Otherwise just copy code from other code object
	 * 
	 * Step 2: New code object has same temporary as existing code, but is an r-val
	 * 
	 * Step 3: New code object has a "wrapped" type. If type of expr is T, type of temporary is *T. Can get this from node
	 */
	@Override
	protected CodeObject postprocess(AddrOfNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		/* FILL IN FOR STEP 6 */

		InstructionList iList = new InstructionList();

		co.code.addAll(expr.code);

		co.temp = expr.temp;

		if(expr.isVar() && expr.lval){

			iList = generateAddrFromVariable(expr);
			co.code.addAll(iList);
			co.temp = iList.getLast().getDest();
		}

		
		co.lval = false;

		// Step3
		System.out.println("Inside Addr expr with addr reg: "+expr.temp+" and type: "+node.getType());
		co.type = node.getType();

		return co;
	}

	/**
	 * Generate code for malloc
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: Add code from expression (rvalify if needed)
	 * 
	 * Step 2: Create new MALLOC instruction
	 * 
	 * Step 3: Set code object type to INFER
	 */
	@Override
	protected CodeObject postprocess(MallocNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		/* FILL IN FOR STEP 6 */
		// Step 1
		if (expr.lval) {
			expr = rvalify(expr);
		}
		// Step 1
		co.code.addAll(expr.code);

		// Step 2
		Instruction i;
		// Malloc's first argument is the src(size) and the second arg is the destination, so I am assuming a temporary?
		String tempAddr = generateTemp(Scope.InnerType.INT);
		i = new Malloc( expr.temp, tempAddr); 

		co.code.add(i);

		co.temp = tempAddr;

		System.out.println("Destination is "+co.temp);
		
		co.lval = false; // Check
		// Step 3
		co.type = node.getType(); 

		return co;
	}
	
	/**
	 * Generate code for free
	 * 
	 * Step 0: Create new code object
	 * 
	 * Step 1: Add code from expression (rvalify if needed)
	 * 
	 * Step 2: Create new FREE instruction
	 */
	@Override
	protected CodeObject postprocess(FreeNode node, CodeObject expr) {
		CodeObject co = new CodeObject();

		if (expr.lval) {
			expr = rvalify(expr);
		}
		// Step 1
		co.code.addAll(expr.code);

		Instruction i;
		i = new Free( expr.temp ); 

		co.code.add(i);

		/* FILL IN FOR STEP 6 */

		return co;
	}

	/**
	 * Generate a fresh temporary
	 * 
	 * @return new temporary register name
	 */
	protected String generateTemp(Scope.InnerType t) {
		switch(t) {
			case INT: 
			case PTR: //works the same for pointers
				return intTempPrefix + String.valueOf(++intRegCount);
			case FLOAT: return floatTempPrefix + String.valueOf(++floatRegCount);
			default: throw new Error("Generating temp for bad type");
		}
	}

	protected String generateLoopLabel() {
		return "loop_" + String.valueOf(++loopLabel);
	}

	protected String generateElseLabel() {
		return  "else_" + String.valueOf(++elseLabel);
	}

	protected String generateOutLabel() {
		return "out_" +  String.valueOf(++outLabel);
	}

	protected String generateFunctionLabel() {
		return "func_" + currFunc;
	}

	protected String generateFunctionLabel(String func) {
		return "func_" + func;
	}

	protected String generateFunctionOutLabel() {
		return "func_ret_" + currFunc;
	}
	
	/**
	 * Take a code object that results in an lval, and create a new code
	 * object that adds a load to generate the rval.
	 * 
	 * @param lco The code object resulting in an address
	 * @return A code object with all the code of <code>lco</code> followed by a load
	 *         to generate an rval
	 */
	protected CodeObject rvalify(CodeObject lco) {
		
		assert (lco.lval == true);
		CodeObject co = new CodeObject();

		/* FILL IN FROM STEP 2 */

		/* DON'T FORGET TO ADD CODE TO GENERATE LOADS FOR LOCAL VARIABLES */
		InstructionList ilco = new InstructionList();
		String temp = "";
		// boolean flag = false;
		if (!lco.isVar()) {
			co.code.addAll(lco.code);
			temp = lco.temp;
		} else if (!lco.getSTE().isLocal()) {
			ilco = generateAddrFromVariable(lco);
			co.code.addAll(ilco);
			temp = ilco.getLast().getDest();
		} else {
			temp = lco.getSTE().addressToString();
		}

		Instruction i;
		Scope.InnerType t;
		// if (lco.type.type == Scope.InnerType.PTR){ //pointer
		// 	//get unwrapped type
		// 	t = lco.type.getWrappedType().type;
		// 	if (t == Scope.InnerType.PTR){ //pointer to pointer
		// 		t = lco.type.getWrappedType().getWrappedType().type;
		// 	}
		// } else {
		// 	flag = true;
		// 	t = lco.type.type;
		// }
		boolean ptrVar = false;
		if (lco.getSTE() == null){
			ptrVar = true;
		}

		System.out.println("rvalify type: "+lco.type.type+" wtih ptrVar "+ptrVar);
		switch(lco.type.type) {
			case INT:
			case PTR:
				if (ptrVar){
					i = new Lw(generateTemp(Scope.InnerType.INT), temp, "0"); //left.temp is a deref address
				} else if (!lco.getSTE().isLocal()) {
					i = new Lw(generateTemp(Scope.InnerType.INT), temp, "0"); 
				} else  {
					i = new Lw(generateTemp(Scope.InnerType.INT), "fp" , temp); 
				}
				co.type = new Scope.Type(Scope.InnerType.INT);
				break;
			case FLOAT:
				// if (lco.isderef()){
				// 	if(flag)
				// 		i = new Flw(generateTemp(Scope.InnerType.FLOAT), temp, "0"); //left.temp is a deref address
				// 	else
				// 		i = new Lw(generateTemp(Scope.InnerType.INT), temp, "0"); //left.temp is a deref address
				// } 
				// else if (!lco.getSTE().isLocal()) {
				// 	System.out.println("YOLO IM GLOBAL");
				// 	if (flag)
				// 		i = new Flw(generateTemp(Scope.InnerType.FLOAT), temp, "0");
				// 	else
				// 		i = new Lw(generateTemp(Scope.InnerType.INT), temp, "0"); //left.temp is a deref address
				// } else  {
				// 	System.out.println("YOLO IM LOCAL");
				// 	if (flag)
				// 		i = new Flw(generateTemp(Scope.InnerType.FLOAT), "fp" , temp); 
				// 	else
				// 		i = new Lw(generateTemp(Scope.InnerType.INT), "fp", temp); //left.temp is a deref address
				// }
				// if (flag)
				// 	co.type = new Scope.Type(Scope.InnerType.FLOAT);
				// else
				// 	co.type = new Scope.Type(Scope.InnerType.INT);
				if (ptrVar){
					i = new Flw(generateTemp(Scope.InnerType.FLOAT), temp, "0"); //left.temp is a deref address
				} else if (!lco.getSTE().isLocal()) {
					i = new Flw(generateTemp(Scope.InnerType.FLOAT), temp, "0"); 
				} else  {
					i = new Flw(generateTemp(Scope.InnerType.FLOAT), "fp" , temp); 
				}
				co.type = new Scope.Type(Scope.InnerType.FLOAT);
				break;

			default:
				throw new Error (lco.type.type+" Undefined type");
		}
		
		co.code.add(i);
		co.lval = false;
		co.temp = i.getDest();
		return co;
	}


	/**
	 * Generate an instruction sequence that holds the address of the variable in a code object
	 * 
	 * If it's a global variable, just get the address from the symbol table
	 * 
	 * If it's a local variable, compute the address relative to the frame pointer (fp)
	 * 
	 * @param lco The code object holding a variable
	 * @return a list of instructions that puts the address of the variable in a register
	 */
	private InstructionList generateAddrFromVariable(CodeObject lco) {

		InstructionList il = new InstructionList();

		//Step 1:
		SymbolTableEntry symbol = lco.getSTE();
		String address = symbol.addressToString();

		//Step 2:
		Instruction compAddr = null;
		if (symbol.isLocal()) {
			//If local, address is offset
			//need to load fp + offset
			//addi tmp' fp offset
			compAddr = new Addi("fp", address, generateTemp(Scope.InnerType.INT));
		} else {
			//If global, address in symbol table is the right location
			//la tmp' addr //Register type needs to be an int
			compAddr = new La(generateTemp(Scope.InnerType.INT), address);
		}
		il.add(compAddr); //add instruction to code object

		return il;
	}

}
