package ast.visitor;

import java.util.List;

import ast.ASTNode;
import ast.AssignNode;
import ast.BinaryOpNode;
import ast.CallNode;
import ast.WhileNode;
import ast.IfStatementNode;
import ast.IntLitNode;
import ast.ReadNode;
import ast.StatementListNode;
import ast.VarNode;
import ast.WriteNode;
import ast.ReturnNode;
import ast.CondNode;
import ast.FunctionNode;
import ast.FloatLitNode;

public class TypeVisitor extends AbstractASTVisitor<Void> {

	int depth;
	
	public TypeVisitor() {depth = 0;}
	
	@Override
	public Void run(ASTNode node) {
		depth = 0;
		return node.accept(this);
	}
	
	@Override
	protected void preprocess(VarNode node) {
	}

	@Override
	protected void preprocess(IntLitNode node) {

	}

	@Override
	protected void preprocess(FloatLitNode node) {

	}

	@Override
	protected void preprocess(BinaryOpNode node) {
		depth++;
	}
	
	@Override
	protected Void postprocess(BinaryOpNode node, Void left, Void right) {

		if(node.getlType() != node.getrType()){

			System.err.println("TYPE ERROR\n");
			System.exit(7);
		}



		--depth;
		return null;
	}
	
	@Override
	protected void preprocess(AssignNode node) {
		depth++;
	}
	
	@Override
	protected Void postprocess(AssignNode node, Void left, Void right) {
		
		if(node.getLeft().getType() != node.getRight().getType()){
			System.err.println("TYPE ERROR\n");
			System.exit(7);
		}

		--depth;
		return null;
	}
	
	private void printTabs() {
		for (int i = 0; i < depth; i++) {
			System.out.print("\t");
		}
	}

	@Override
	protected void preprocess(StatementListNode node) {
		depth++;
	}

	@Override
	protected Void postprocess(StatementListNode node, List<Void> statements) {
		--depth;
		return null;
	}
	
	@Override
	protected void preprocess(ReadNode node) {
		depth++;
	}

	@Override
	protected void preprocess(WriteNode node) {
		depth++;
	}

	@Override
	protected Void postprocess(WriteNode node, Void writeExpr) {
		--depth;
		return null;
	}

	@Override
	protected Void postprocess(ReadNode node, Void var) {
		--depth;
		return null;
	}

	@Override
	protected void preprocess(ReturnNode node) {
		depth++;
	}

	@Override
	protected Void postprocess(ReturnNode node, Void retExpr) {

		if(node.getRetExpr().getType() != node.getFuncSymbol().getReturnType()){

			System.err.println("TYPE ERROR\n");
			System.exit(7);

		}
		--depth;
		return null;
	}

	@Override
	protected void preprocess(CondNode node) {
		depth++;
	}
	
	@Override
	protected Void postprocess(CondNode node, Void left, Void right) {

		if(node.getLeft().getType() != node.getRight().getType()){
			System.err.println("TYPE ERROR\n");
			System.exit(7);
		}
		--depth;
		return null;
	}

	@Override
	protected void preprocess(IfStatementNode node) {
		depth++;
	}
	
	@Override
	protected Void postprocess(IfStatementNode node, Void cond, Void slist, Void epart) {
		--depth;
		return null;
	}
	
	@Override
	protected void preprocess(WhileNode node) {
		depth++;
	}
	
	@Override
	protected Void postprocess(WhileNode node, Void cond, Void slist) {
		--depth;
		return null;
	}

	@Override
	protected void preprocess(FunctionNode node) {
		depth++;
	}

	@Override
	protected Void postprocess(FunctionNode node, Void body) {
		--depth;
		return null;
	}

	@Override
	protected void preprocess(CallNode node) {

		depth++;
	}

	@Override
	protected Void postprocess(CallNode node, List<Void> args) {

		// If the number of args mismatch then exit!
		if(node.getArgs().size() != node.getArgTypeFromSte().size() ){
			System.err.println("TYPE ERROR\n");
			System.exit(7);
		}

		// Else check and make sure that args and params match!
		for(int i=0;i<node.getArgs().size();i++){
			//tVarNode = node.getArgs().get(i);
			if(node.getArgs().get(i).getType() != node.getArgTypeFromSte().get(i)){
				System.err.println("TYPE ERROR\n");
				System.exit(7);
			}
		}

		--depth;
		return null;
	}

}