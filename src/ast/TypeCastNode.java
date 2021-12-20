package ast;

import ast.visitor.ASTVisitor;
import compiler.Scope;
/**
 * A node for type cast expressions (float or int)
 * 
 * This has one child: the {@link ExpressionNode} being operated on
 */
public class TypeCastNode extends ExpressionNode {

	private ExpressionNode expr;
	private Scope.Type type;
	
	public TypeCastNode(Scope.Type type, ExpressionNode expr) {
		this.setExpr(expr);
		this.setCastType(type);
	}
		


	@Override
	public <R> R accept(ASTVisitor<R> visitor) {
		return visitor.visit(this);
	}

	public ASTNode getExpr() {
		return expr;
	}

	private void setExpr(ExpressionNode right) {
		this.expr = right;
	}

	public Scope.Type getCastType() {
		return type;
	}

	private void setCastType(Scope.Type type) {
		this.type = type;
	}

}
