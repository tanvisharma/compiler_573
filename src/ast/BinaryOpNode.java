package ast;

import ast.visitor.ASTVisitor;

import compiler.Scope;
/**
 * Node for binary expressions
 * 
 * It has two children:
 * 
 * 1. <code>left</code>
 * 2. <code>right</code>
 * 
 * for the two sides of the binary op
 * 
 * It also keeps track of the operation type
 */
public class BinaryOpNode extends ExpressionNode {

	public enum OpType {
		ADD,
		SUB,
		MUL,
		DIV
	}
	
	private ExpressionNode left;
	private ExpressionNode right;
	protected Scope.Type lType;
	protected Scope.Type rType;
	private OpType op;
	
	public BinaryOpNode(ExpressionNode left, ExpressionNode right, String op) {
		this.setLeft(left);
		this.setRight(right);
		this.setOp(getOpFromString(op));
		this.setType(left.getType()); //This node inherits its type from the left child
		this.setlType(left.getType());
		this.setrType(right.getType());
	}
		
	private OpType getOpFromString(String s) {
		switch (s) {
		case "+" : return OpType.ADD;
		case "-" : return OpType.SUB;
		case "/" : return OpType.DIV;
		case "*" : return OpType.MUL;
		default : throw new Error ("Unrecognized op type");
		}
	}

	@Override
	public <R> R accept(ASTVisitor<R> visitor) {
		return visitor.visit(this);
	}

	public ASTNode getLeft() {
		return left;
	}

	private void setLeft(ExpressionNode left) {
		this.left = left;
	}

	public ASTNode getRight() {
		return right;
	}

	private void setRight(ExpressionNode right) {
		this.right = right;
	}

	public OpType getOp() {
		return op;
	}

	private void setOp(OpType op) {
		this.op = op;
	}

	protected void setlType(Scope.Type type) {
		this.lType = type;
	}

	protected void setrType(Scope.Type type) {
		this.rType = type;
	}

	public Scope.Type getrType() {
		return rType;
	}

	public Scope.Type getlType() {
		return lType;
	}

}
