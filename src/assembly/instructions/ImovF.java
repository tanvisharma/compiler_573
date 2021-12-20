package assembly.instructions;

/**
 * Class corresponding to RISC-V IMOVF.S instruction
 * 
 * Models: imovf.s dest src #dest = (float) src
 */
public class ImovF extends Instruction {

    /**
     * Initializes a IMOVF.S instruction that will print: IMOVF.S dest src
     * 
     * @param src source operand 1
     * @param dest destination operand
     */

    public ImovF(String src, String dest) {
        super();
        this.src1 = src;
        this.dest = dest;
        this.oc = OpCode.IMOVF;
    }

    /**
     * @return "ImovF dest src"
     */
    public String toString() {
        return this.oc + " " + this.dest + ", " + this.src1;
    }
}