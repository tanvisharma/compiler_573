package assembly;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ast.visitor.AbstractASTVisitor;

import ast.*;
import assembly.instructions.*;
import assembly.instructions.Instruction.*;
import compiler.Scope;
import compiler.Scope.SymbolTableEntry;


public class RegisterAllocator {
    // Inputs set in the constructor
    int numReg;
    ArrayList<Instruction> funcBody = new ArrayList<Instruction>();
    Scope funcScope;
    // output from parsing the funcBody
    InstructionList assemblyCode;
    int insnIdx;
    ArrayList<String> regsUsed;


    public class RegsEntry {
            String dest; //variable stored in the reg
            int dirty;
            int free; //higher rrpv -> accessed in distant future
            int special;
            int retain;
    
            RegsEntry(){
    
                this.dest = "";
                this.dirty = 0;
                this.free = 1;
                this.special = 0;
                this.retain = 0;
            
            }
    }

    
    //to keep track of registers, help in allocation and spilling
    RegsEntry [] integerRegs;
    RegsEntry [] floatRegs;

    public void printRegsEntry(){
        System.out.println("Integer entries: ");
        for (RegsEntry r : integerRegs){
            System.out.println("  " + r.dest + ", " + r.dirty + ", " + r.free + ", "+ r.special+", "+r.retain);
        }
        System.out.println("Float entries: ");
        for (RegsEntry r : floatRegs){
            System.out.println("  "+ r.dest + ", "+r.dirty+", "+r.free+", "+r.special+", "+r.retain);
        }
    }
    //2D array to keep track of liveness of funcBody
    ArrayList<ArrayList<String>> liveness; //can change the container for more options

    //List of indices in funcBody marking Basic Block start
    ArrayList<Integer> bbLeaders;

    //Constructor
    public RegisterAllocator(int numReg, InstructionList body, Scope scope) {
        System.out.println("numReg: "+numReg);
        this.numReg = numReg;
        this.funcBody.addAll(body.nodes);
        this.funcScope = scope;
        this.bbLeaders = new ArrayList<>();
        this.integerRegs = new RegsEntry[numReg];
        this.floatRegs = new RegsEntry[numReg];
        this.insnIdx = 0;

        for(int i=0; i < numReg; i++ ){
            this.integerRegs[i] = new RegsEntry();
            this.floatRegs[i] = new RegsEntry();
        }

        this.integerRegs[0].special = 1; //zero
        this.integerRegs[1].special = 1; //ra
        this.integerRegs[2].special = 1; //sp
        this.integerRegs[3].special = 1; //address
        if(numReg > 8)
            this.integerRegs[8].special = 1; //fp


        this.liveness = new ArrayList<ArrayList<String>>();
        this.assemblyCode = new InstructionList();
        this.regsUsed = new ArrayList<String>();
    }

    public void setBB() {
        //algorithm to set bbLeaders
        Iterator<Instruction> iter = funcBody.iterator();
        Instruction i; int idx = 0; int prev = 0; int next;
        bbLeaders.add(0); //first statement is always a leader; covered in labels
        while(iter.hasNext()) {
            idx++;
            i = iter.next();
            // System.out.println(i);

            //Label insns
            if (i.getOC() == null) {
                if (i.getLabel() != null){
                    next = idx; //next statement marks a new BB
                    // System.out.println("---");
                    // System.out.println(idx);
                    // System.out.println("---");
                }
                //Exclusive 3AC insns are not recognized as Instruction
                continue;
            }

            //Branch/jump insns
            switch(i.getOC().toString()) {
                case("BEQ"):
                case("BGE"):
                case("BGT"):
                case("BLE"):
                case("BLT"):
                case("BNE"):
                case("J"):
                case("FEQ"):
                case("FLT"):
                case("FLE"):
                    next = idx; //next statement marks a new BB
                    // System.out.println("---");
                    // System.out.println(idx);
                    // System.out.println("---");
                    break;
                // case("JR"): //Confirm
                case("RET"):
                    next = idx; //next statement marks a new BB
                    // System.out.println("---");
                    // System.out.println(idx);
                    // System.out.println("---");
                    break;
                default:
                    continue;
            }

            if(next - prev == 1)
                continue;
            else
                bbLeaders.add(next); //next statement marks a new BB
            prev = next;
        }
    }

    //algorithm to calculate Liveness given a basic block
    public void setLiveness(int startIdx, int nextIdx) { //nextIdx insn is not included
        int size = nextIdx - startIdx;
        ArrayList<String> in = new ArrayList<>(); //Lin for each statement
        ArrayList<ArrayList<String>> out = new ArrayList<ArrayList<String>>(size); //Lout for each statement

        ListIterator<Instruction> iter = funcBody.listIterator(nextIdx);

        //add the global and local variables to in to mark them live
        int idx = 0;
        Instruction i;
        List<String> opers = new ArrayList<String>();

        while(idx < size) { //assuming nextIdx is given s.t. iter.hasPrevious() is true
            i = iter.previous(); //Confirm that it does not skip the last insn
            // System.out.println(i);
            opers.add(i.getOperand(Operand.DEST)); //DEST
            opers.add(i.getOperand(Operand.SRC2)); //DEST
            opers.add(i.getOperand(Operand.SRC1)); //DEST

            for (String oper : opers) {
                if(i.is3AC(oper)) {
                    if(i.isLocalGlobal(oper)) {
                        if (!in.contains(oper)) {
                            in.add(oper);
                            // System.out.println(oper);
                        }
                    }
                }
            }
            idx++;
        }
        // System.out.println("---- Scope ----");
        // // funcScope.printGlobalTable();
        // funcScope.getGlobalVar();
        // System.out.println("---- Scope End----");

        idx = 0;
        iter = funcBody.listIterator(nextIdx);
        while(idx < size) { //assuming nextIdx is given s.t. iter.hasPrevious() is true
            in = new ArrayList<>(in); // in.addAll(out.get(out.size()-1));
            i = iter.previous();

            //get def or operands and remove from out
            String def = i.getOperand(Operand.DEST); //DEST
            if (in.contains(def)) {
                in.remove(new String(def));
            }
            String use = i.getOperand(Operand.SRC1); //SRC1
            if (i.is3AC(use)) { //only add variables
                if (!in.contains(use))
                    in.add(use);
            }
            use = i.getOperand(Operand.SRC2); //SRC1
            if (i.is3AC(use)) { //only add variables
                if (!in.contains(use))
                    in.add(use);
            }
            // System.out.println(in);
            out.add(0,in);
            // System.out.println();
            // System.out.println(out);
            // System.out.println();
            idx++;
        }
        // System.out.println(";");
        // System.out.println(out.size());
        // System.out.println(size);
  
        for (ArrayList<String> l : out) {
            // System.out.println(l);
            liveness.add(l);
        }

        // iter = funcBody.listIterator(nextIdx);
        // System.out.println("Macro expansion check *****");
        // i = iter.previous();
        // System.out.println(i);
        // for (int j=0; j<4;j++)
        //     i = iter.previous();
        // System.out.println(i);
        // String[] op = {"Ra", "Rb", "Rc"};
        // Instruction insn = macroExpan(i, op);
        // System.out.println("Output");
        // System.out.println(insn);
        
    }

    // //given a 3AC instruction, give the assembly instructions
    // public Instruction macroExpan(Instruction insn3AC, String[] operands) {
    //     InstructionList insnlist = new InstructionList();
    //     Instruction i = insn3AC;
    //     if (i.getOC() == null) {
    //         return i;
    //     }
    //     //if it is pop or push, generate new instructions accordingly
    //     //else change the operands
    //     switch(i.getOC().toString()) {

    //         case("PUSH"):
    //             break;
    //         case("POP"):
    //             break;
    //         default:
    //             i.setOperands(operands);
    //             insnlist.add(i);
    //     }

    //     return i;
    // }

    // save a register if dirty and live
    public void save(int regNum, Character type){

        Instruction insn = null;
        String dest;
        if (type == 'x'){
            dest = integerRegs[regNum].dest;
        } else {
            dest = floatRegs[regNum].dest;
        }
        Character id = dest.charAt(1);
        SymbolTableEntry ste = null;


        if(type == 'x'){

            if((integerRegs[regNum].dirty == 1) && liveness.get(insnIdx).contains(dest)){
                // System.out.println("   Save int "+regNum);

                // if(ste == null){ //spill a temporary
                //     // could be a string too!
                //     funcScope.addSymbol( Scope.Type.INT, destName) ;
                //     ste = funcScope.getSymbolTableEntry(destName);
                // }

                String addr;

                if(id == 'l'){ //local variable

                    addr = dest.replace("$l","");
                    insn = new Sw("x"+String.valueOf(regNum), "fp" , addr); 

                }else if (id == 'g') {//global variable
                    ste = funcScope.getSymbolTableEntry(dest.replace("$g",""));
                    addr = ste.addressToString();
                    assemblyCode.add(new La("x3", addr));
                    insn = new Sw("x"+String.valueOf(regNum), "x3", "0");

                } else if (id == 't') {
                    funcScope.addSymbol( Scope.Type.INT, dest.replace("$","")) ;
                    ste = funcScope.getSymbolTableEntry(dest.replace("$",""));
                    if (ste == null){
                        throw new Error("New temporary not added to symbolTable!!!");
                    }
                    addr = ste.addressToString();
                    insn = new Sw("x"+String.valueOf(regNum), "fp", addr);

                } else {
                    throw new Error("Weird operand (saving register x"+regNum+" for " + dest+")");
                }
                assemblyCode.add(insn);
                integerRegs[regNum].dirty = 0;
                
            }

        }
        else{

            String addr;
            if((floatRegs[regNum].dirty == 1) && liveness.get(insnIdx).contains(dest)){
                // System.out.println("   Save float "+regNum);
                // System.out.print("  Save ");

                if(id == 'l'){ //local variable

                    addr = dest.replace("$l","");
                    insn = new Fsw("f"+String.valueOf(regNum), "fp" , addr); 

                }else if (id == 'g') {//global variable
                    ste = funcScope.getSymbolTableEntry(dest.replace("$g",""));
                    addr = ste.addressToString();
                    assemblyCode.add(new La("x3", addr));
                    insn = new Fsw("f"+String.valueOf(regNum), "x3", "0");

                } else if (id == 'f') {
                    funcScope.addSymbol( Scope.Type.FLOAT, dest.replace("$","")) ;
                    ste = funcScope.getSymbolTableEntry(dest.replace("$",""));
                    if (ste == null){
                        throw new Error("New temporary f not added to symbolTable!!!");
                    }
                    addr = ste.addressToString();
                    insn = new Fsw("f"+String.valueOf(regNum), "fp", addr);

                } else {
                    throw new Error("Weird operand (saving register f"+regNum+" for " + dest+")");
                }
                assemblyCode.add(insn);
                floatRegs[regNum].dirty = 0;
                
            }

        }

    }


    // Free a register
    public void free(String reg){
        // System.out.println("  Free " + reg);

        String regNumStr = reg.replace(Character.toString(reg.charAt(0)), "");
        int regNum = Integer.parseInt(regNumStr);

        Character t = reg.charAt(0);

        save(regNum, t);

        if (t == 'x') {

            integerRegs[regNum].dirty = 0;
            integerRegs[regNum].dest = "";
            integerRegs[regNum].retain = 0;
            integerRegs[regNum].free = 1;

        }
        else{

            floatRegs[regNum].dirty = 0;
            floatRegs[regNum].dest = "";
            integerRegs[regNum].retain = 0;
            floatRegs[regNum].free = 1;

        }

    }

    //allocate a register given a source/destination
    public String allocate(String oper, boolean src){
        System.out.println("  Allocate " + oper);

        String operName = oper.replace("$", "");
        
        SymbolTableEntry ste = null;
        Scope.Type type = null;
        Character id = operName.charAt(0);
        if (id == 'l') { //local variable

            int laddr = Integer.valueOf(operName.substring(1));
            ste = funcScope.getSymbolTableEntryInt(laddr);
            type = ste.getType();

        } else if (id == 'g'){ //global variable

            ste = funcScope.getSymbolTableEntry(operName.substring(1));
            type = ste.getType();

        } else if (id == 'f'){ //temporary

            type = Scope.Type.FLOAT;
        
        } else { //if (id == 't') or anything else like sp/fp/ra
        
            type = Scope.Type.INT;
        
        }
        // funcScope.printTable();

        int regIndx = anyFreeRegs(type); 

        String regName;

        // Did not find free R, choose R to free
        // Look for any register that is not dirty, even if its alive
        // If you don't find any then find the smallest reg number that is dirty and spill it
        if( regIndx == -1 ){
            regIndx = chooseFreeRegs(type);
        }

        if(type == Scope.Type.FLOAT){

            floatRegs[regIndx].dest = oper;
            floatRegs[regIndx].free = 0;

            if(!src){
                floatRegs[regIndx].dirty = 1;
            }

            regName = "f"+String.valueOf(regIndx);
            System.out.println("   allocated " + regName);

        }
        else{

            integerRegs[regIndx].dest = oper;
            integerRegs[regIndx].free = 0;

            if(!src){
                integerRegs[regIndx].dirty = 1;
            }

            regName = "x"+String.valueOf(regIndx);
            System.out.println("   allocated " + regName);

        }

        regsUsed.add(regName);

        return regName;

    }

    //ensure that the operand has a register allocated
    public String ensure(String oper, boolean src){
        System.out.println("  Ensure " + oper);

        String operName = oper.replace("$", "");

        SymbolTableEntry ste = null;
        Scope.Type type = null;
        Character id = operName.charAt(0);
        if (id == 'l') { //local variable

            int laddr = Integer.valueOf(operName.substring(1));
            ste = funcScope.getSymbolTableEntryInt(laddr);
            type = ste.getType();

        } else if (id == 'g'){ //global variable
            ste = funcScope.getSymbolTableEntry(operName.substring(1));
            type = ste.getType();

        } else if (id == 'f'){ //temporary

            type = Scope.Type.FLOAT;
        
        } else { //if (id == 't') or anything else like sp/fp/ra
        
            type = Scope.Type.INT;
        
        }
        
        int regIndx = oprIsInReg(oper,type);

        String regName;
        if(regIndx == -1){ //oper is not allocated
            System.out.println("   not found " + oper);
            regName = allocate(oper, src);

            //generate a load
            if (src) {
                Instruction insn = null;
                //temporaries
                if (id == 'f' || id == 't'){ //temp
                    ste = funcScope.getSymbolTableEntry(regName.replace("$",""));
                    if (ste == null){ //not spilled
                        throw new Error("Temporary getting allocated without being on stack!");
                    } else { //load
                        if (id == 'f'){
                            insn = new Flw(regName, "fp", ste.addressToString());
                            floatRegs[Integer.valueOf(regName.replace("f",""))].retain = 1;
                        } else{ //id ==t
                            insn = new Lw(regName, "fp", ste.addressToString());
                            integerRegs[Integer.valueOf(regName.replace("x",""))].retain = 1;
                        }
                        assemblyCode.add(insn);
                    }
                
                } else if (id == 'l') { //local
                    if(type == Scope.Type.FLOAT) {
                        insn = new Flw(regName, "fp", ste.addressToString());
                        floatRegs[Integer.valueOf(regName.replace("f",""))].retain = 1;
                    } else if (type == Scope.Type.INT) {
                        insn = new Lw(regName, "fp", ste.addressToString());
                        integerRegs[Integer.valueOf(regName.replace("x",""))].retain = 1;
                    } else { //string
                        insn = new La(regName, ste.addressToString());
                        integerRegs[Integer.valueOf(regName.replace("x",""))].retain = 1;

                    }
                    assemblyCode.add(insn);
                
                } else { //global
                    if(type == Scope.Type.FLOAT) {
                        insn = new Flw(regName, ste.addressToString(), "0");
                        floatRegs[Integer.valueOf(regName.replace("f",""))].retain = 1;
                    } else if (type == Scope.Type.INT){
                        insn = new Lw(regName, ste.addressToString(), "0");
                        integerRegs[Integer.valueOf(regName.replace("x",""))].retain = 1;
                    } else { //string
                        insn = new La(regName, ste.addressToString());
                        integerRegs[Integer.valueOf(regName.replace("x",""))].retain = 1;
                    }
                    assemblyCode.add(insn);
                    
                }
            }

       } else {

            if(type == Scope.Type.FLOAT){
                regName = "f"+String.valueOf(regIndx);
                floatRegs[Integer.valueOf(regName.replace("f",""))].retain = 1;
            }
            else{
                regName = "x"+String.valueOf(regIndx);
                integerRegs[Integer.valueOf(regName.replace("x",""))].retain = 1;
            }
       }

        return regName;

    }

    // // pass the type
    // // if this function returns -1 then I did not find a free register!
    public int anyFreeRegs(Scope.Type type){

        // if float
        if(type == Scope.Type.FLOAT){
            for(int i=0; i < numReg; i++ ){
                if(floatRegs[i].free == 1 && floatRegs[i].retain == 0){
                    return i;
                }
            }
        }
        // if int/string
        else{
            for(int i=0; i < numReg; i++ ){
                if(integerRegs[i].free == 1 && integerRegs[i].retain == 0 && integerRegs[i].special == 0){
                    return i;
                }
            }
        }

        return -1;

     }


    public int chooseFreeRegs(Scope.Type type){

        // if float
        if(type == Scope.Type.FLOAT){

            for(int i=0; i < numReg; i++ ){
                if(floatRegs[i].dirty == 0 && floatRegs[i].retain == 0){
                    return i;
                }
            }
            if (floatRegs[0].retain == 0){
                free("f0");
                return 0;
            } else {
                free("f1");
                return 1;
            }
        
        }
        // if int/string
        else{
            for(int i=0; i < numReg; i++ ){
                if(integerRegs[i].dirty == 0 && integerRegs[i].retain == 0 && integerRegs[i].special == 0){
                    return i;
                }
            }
            if (integerRegs[3].retain == 0){
                free("x4");
                return 3;
            } else {
                free("x5");
                return 4;
            }

        }

     } 

    // // Check if opr is in any of the registers!
    // // if it is in register it will return the index, else returns -1;
    public int oprIsInReg(String oper, Scope.Type type){
        System.out.println("  Trying to find "+oper+" with type ");

     
        if(type == Scope.Type.FLOAT){
            // System.out.println("FLOAT");

            for(int i=0; i < numReg; i++ ){
                System.out.print("  Reg"+i+": "+floatRegs[i].dest+", ");
                if(floatRegs[i].dest.equals(oper)){
                    return i;
                }
            }
        
        }
        // if int/string
        else{
            // System.out.println("INT");
            for(int i=0; i < numReg; i++ ){
                System.out.print("  Reg"+i+": "+integerRegs[i].dest+", ");
                if(integerRegs[i].dest.equals(oper) && integerRegs[i].special == 0){
                // if(integerRegs[i].dest == oper){
                    return i;
                }
            }

        }

        return -1;
    }




    public void convert() {
        //set the basic block leaders
        System.out.print(" *** Set Basic Blocks *** ");
		    setBB();
		    for(int i = 0; i < bbLeaders.size(); i++)
		        System.out.println(bbLeaders.get(i));
        System.out.println(" *** Basic Blocks End *** ");

        //get liveness for whole function body
        System.out.print(" *** LIveness *** ");
        ListIterator<Integer> iter = bbLeaders.listIterator(0);
        int present = iter.next();
        int next = 0;
        while(iter.hasNext()) {
            next = iter.next();
		        // System.out.println(next);
            setLiveness(present,next);
            present = next; 
        }

        for (ArrayList<String> l1 : liveness) {
            for (String l2 : l1) {
                System.out.print(l2 + ",");
            }
            System.out.println();
        }
        System.out.println(" *** Liveness End*** ");

        int start, end;
        for(int i = 0; i < bbLeaders.size()-1; i++) { 
	        // System.out.println(bbLeaders.get(i));
            start = bbLeaders.get(i);
            end = bbLeaders.get(i+1);

            Instruction insn = null;
            String oper1, oper2, oper3;
            String reg1, reg2, reg3;

            //for each basic block
            for (int j=start; j < end; j++) {
                insnIdx = j;
                insn = funcBody.get(j);
                System.out.println("Main: " + insn);

                if (insn.is3AC()) {
                    if (insn.getOC()!= null){
                        if(insn.getOC().toString().equals("SW") && 
                              (insn.getOperand(Operand.SRC1).equals("sp")||insn.getOperand(Operand.SRC1).equals("fp"))){
                            // Ensure the register
                            oper3 = insn.getOperand(Operand.DEST);
                            if (insn.is3AC(oper3))
                                reg3 = ensure(oper3, true);
                            else
                                reg3 = oper3;
                        
                            //generate code for insn
                            Instruction icode = insn;
                            icode.setOperands(insn.getOperand(Operand.SRC1), insn.getOperand(Operand.SRC2), reg3);
                            assemblyCode.add(icode);                        
                            continue;
                        }
                    }
                    //     Ra = ensure(opA)
                    oper1 = insn.getOperand(Operand.SRC1);
                    if (insn.is3AC(oper1))
                        reg1 = ensure(oper1, true);
                    else 
                        reg1 = oper1;
                
                    //     Rb = ensure(opB)
                    oper2 = insn.getOperand(Operand.SRC2);
                    if (insn.is3AC(oper2))
                        reg2 = ensure(oper2, true);
                    else
                        reg2 = oper2;

                    //     free -- Ra, Rb if dead
                    // Also make retain 0 here so that the ensured registers can be used when everything is full
                    if (insn.is3AC(oper1)){
                        Character id = reg1.charAt(0);
                        int regIndx = Integer.valueOf(reg1.replace(Character.toString(id), ""));
                        if (id == 'x') {
                            integerRegs[regIndx].retain = 0;
                        } else {
                            floatRegs[regIndx].retain = 0;
                        }
                        if (!liveness.get(insnIdx).contains(oper1))
                            free(reg1);
                    }

                    if (insn.is3AC(oper2)){
                        Character id = reg2.charAt(0);
                        int regIndx = Integer.valueOf(reg2.replace(Character.toString(id), ""));
                        if (id == 'x') {
                            integerRegs[regIndx].retain = 0;
                        } else {
                            floatRegs[regIndx].retain = 0;
                        }
                        if (!liveness.get(insnIdx).contains(oper2))
                            free(reg2);
                    }

                    //     Rc = allocate(opC)
                    oper3 = insn.getOperand(Operand.DEST);
                    if (insn.is3AC(oper3))
                        reg3 = ensure(oper3, false);
                    else
                        reg3 = oper3;

                    //generate code for insn
                    Instruction icode = insn;
                    icode.setOperands(reg1, reg2, reg3);

                    assemblyCode.add(icode);
                } else {
                    //Jump, Label, etc.
                    assemblyCode.add(insn);
                }
                // printRegsEntry();
            }

            //save all dirty/live registers
            String regName;
            for(int n=0; n < numReg; n++ ){
                if (floatRegs[n].free == 0){
                    if(floatRegs[n].dest.charAt(1) == 'l' || floatRegs[n].dest.charAt(1) == 'g'){
                        save(n,'f');
                    }

                }
                if (integerRegs[n].free == 0){
                    if(integerRegs[n].dest.charAt(1) == 'l' || integerRegs[n].dest.charAt(1) == 'g'){
                        System.out.println("  " + integerRegs[n].dest);
                        save(n,'x'); //automatically excludes the speacial registers
                    }
                }
            }
        }
    }
}