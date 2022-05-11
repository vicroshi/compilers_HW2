import syntaxtree.*;
import visitor.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

public class
Main {
    public static void main(String[] args) throws Exception {
        for(int i = 0; i < args.length; i++) {
            FileInputStream fis=null;
            try {
                fis = new FileInputStream(args[i]);
                MiniJavaParser parser = new MiniJavaParser(fis);
                Goal root = parser.Goal();
                System.err.println("Program parsed successfully.");
                DeclVisitor decvis = new DeclVisitor();
                root.accept(decvis, null);
//                System.out.println("Field Symbol Table:");
//                for (Map.Entry<String, String> e : decvis.cfields.entrySet()) {
//                    System.out.println(e.getValue() + " " + e.getKey());
//                }
//                System.out.println("\nMethod Symbol Table:");
//                for (Map.Entry<String, String> e : decvis.cmethods.entrySet()) {
//                    System.out.println(e.getValue() + " " + e.getKey());
//                }
//                System.out.println("\nMethod Parameter Symbol Table:");
//                for (Map.Entry<String, String> e : decvis.mparams.entrySet()) {
//                    System.out.println(e.getKey() + "(" + e.getValue() + ")");
//                }
//                System.out.println("\nMethod LocalVar Symbol Table:");
//                for (Map.Entry<String, String> e : decvis.mvars.entrySet()) {
//                    System.out.println(e.getValue() + " " + e.getKey());
//                }
//                System.out.println("\nClass Inheritance Table:");
//                for (Map.Entry<String, String> e : decvis.cextends.entrySet()) {
//                    System.out.println(e.getKey() + " extends " + e.getValue());
//                }
//                for (Map.Entry<String,Map<String,String>> e_outter : DeclVisitor.vardec.entrySet()){
//                    System.out.println(e_outter.getKey() + " Symbol Table");
//                    for (Map.Entry<String,String>  e_inner : e_outter.getValue().entrySet()){
//                        System.out.println(e_inner.getValue() + " " + e_inner.getKey());
//                    }
//                }
//                for (Map.Entry<String,Map<String,String>> e_outter : DeclVisitor.methdec.entrySet()){
//                    System.out.println(e_outter.getKey() + " Method Symbol Table");
//                    for (Map.Entry<String,String>  e_inner : e_outter.getValue().entrySet()){
//                        System.out.println(e_inner.getValue() + " " + e_inner.getKey());
//                    }
//                }
                TypeVisitor typevis = new TypeVisitor();
                root.accept(typevis,null);
            } catch (ParseException ex) {
                System.out.println(ex.getMessage());
            } catch (FileNotFoundException ex) {
                System.err.println(ex.getMessage());
            } finally {
                try {
                    if (fis != null) fis.close();
                } catch (IOException ex) {
                    System.err.println(ex.getMessage());
                }
            }
        }
    }
}

class SemanticError extends Exception{
    public String getMessage(String error){
        return "Semantic Error: "+error;
    }
}

class DeclVisitor extends GJDepthFirst<String,String>{
    static Map<String, String[]> mparams = new LinkedHashMap<String, String[]>();
    static Map<String,Map<String,String>> vardec;
    static Map<String,Map<String,String>> methdec;
    static Map<String,String> classdec;
    public DeclVisitor(){
        Map<String,String> var = new LinkedHashMap<String,String>();
        vardec = new LinkedHashMap<String,Map<String,String>>();
        methdec = new LinkedHashMap<String, Map<String, String>>();
        classdec = new LinkedHashMap<String, String>();
//        cfields = new LinkedHashMap<String,String>();
//        cmethods = new LinkedHashMap<String,String>();
//        mparams = new LinkedHashMap<String,String>();
//        cextends = new LinkedHashMap<String, String>();
//        mvars = new LinkedHashMap<String, String>();
    }
    /**
     * Grammar production:
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    public void redefinition_error(String type, String var, String scope){
        System.out.println(String.format("error: %s %s is already defined in %s",type,var,scope));
    }

    public String visit(MainClass n, String argu) throws Exception {
        String classname =  n.f1.accept(this,argu);
        classdec.put(classname,null);
        String mainvars = n.f14.accept(this,argu);
        Map<String,String> vars = new LinkedHashMap<String,String>();
        if(!mainvars.isEmpty()) {
            for (String m : mainvars.split(",")) {
                String[] mainvar = m.split(" ");
                if(!vars.containsKey(mainvar[1])) {
                    vars.put(mainvar[1], mainvar[0]);
                }
                else{
                    redefinition_error("variable",mainvar[1],"method " +classname + "::" + "main");
                }
                vardec.put(classname + "::" + "main",vars);
            }
        }
        return null;
    }
    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    @Override
    public String visit(ClassDeclaration n, String argu) throws Exception {
        String classname = n.f1.accept(this, null);
        String fields = n.f3.accept(this,classname);
        classdec.put(classname,null);
        Map<String,String> fields_st = new LinkedHashMap<String, String>();
        for(String f: fields.split(",")){
            String[] field = f.split(" ");
            if(!fields_st.containsKey(field[1])){
                fields_st.put(field[1],field[0]);
            }
            else {
                redefinition_error("variable",field[1],"class " +classname);
            }
        }
        vardec.put(classname,fields_st);
        Map<String,String> methods_st = new LinkedHashMap<String, String>();
        String methods = n.f4.accept(this,classname);
        for(String m : methods.split(",")){
            String[] method = m.split(" ");
            if(!methods_st.containsKey(method[1])){
                    methods_st.put(method[1],method[0]);
            }
            else {
                redefinition_error("method",method[1],"class " + classname);
            }
        }
        methdec.put(classname,methods_st);
        return null;
    }



    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    @Override
    public String visit(ClassExtendsDeclaration n, String argu) throws Exception {
        String classname = n.f1.accept(this, null);
        String extname = n.f3.accept(this,classname);
        if(!classdec.containsKey(extname)){
            throw new Exception("class %s must be defined before class %s");
        }
        classdec.put(classname,extname);
        String fields = n.f5.accept(this,classname);
        Map<String,String> fields_st = new LinkedHashMap<String, String>();
        for(String f: fields.split(",")){
            String[] field = f.split(" ");
            if(!fields_st.containsKey(field[1])){
                fields_st.put(field[1],field[0]);
            }
            else {
                redefinition_error("variable",field[1],"class " +classname);
            }
        }
        vardec.put(classname,fields_st);
        Map<String,String> methods_st = new LinkedHashMap<String, String>();
        String methods = n.f6.accept(this,classname);
        for(String m : methods.split(",")){
            String[] method = m.split(" ");
            if(!methods_st.containsKey(method[1])){
                methods_st.put(method[1],method[0]);
            }
            else {
                redefinition_error("method",method[1],"class "+classname);
            }
        }
        methdec.put(classname,methods_st);
//        System.out.println("Class: " + classname +" extends " + extname);
//        EXTMap.put(classname,extname);
        return null;
    }

    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    @Override
    public String visit(MethodDeclaration n, String argu) throws Exception {
        String argumentList = n.f4.present() ? n.f4.accept(this, null) : "";
        String methodtype = n.f1.accept(this, null);
        String methodname = n.f2.accept(this, null);
        String localvars = n.f4.present() ? n.f7.accept(this,argu+"::"+methodname) : "";
        Map<String,String> locvars = new LinkedHashMap<String, String>();
        String[] paramtypes = new String[argumentList.split(",").length];

        if (!argumentList.isEmpty()){
            int i =0;
            for (String arg : argumentList.split(",")){
                String[] argument = arg.split(" ");
                if(!locvars.containsKey(argument[1])){
                    locvars.put(argument[1],argument[0]);
                }
                else{
                    redefinition_error("variable",argument[1],"method " + argu+"::"+methodname);
                }
                paramtypes[i++] = argument[0];
            }
        }
        if(!localvars.isEmpty()) {
            for (String l : localvars.split(",")) {
                String[] localvar = l.split(" ");
                if(!locvars.containsKey(localvar[1])){
                    locvars.put(localvar[1],localvar[0]);
                }
                else{
                    redefinition_error("variable",localvar[1],"method " + argu+"::"+methodname);
                }

            }
        }
        vardec.put(argu+"::"+methodname,locvars);
        String ext = classdec.get(argu);
        if(ext!=null) {
            if (mparams.containsKey(ext + "::" + methodname)) {
                String supertype = methdec.get(ext).get(methodname);
                if (!mparams.get(ext + "::" + methodname).equals(paramtypes) || !methodtype.equals(supertype)) {
                    System.out.println(("error: to redefine parent class"+
                            " function both return and parameters types must match"));
                }
//                methdec.put()
            }
        }
        mparams.put(argu+"::"+methodname,paramtypes);
        return methodtype+" "+methodname;
    }

    @Override
    public String visit(NodeListOptional n, String argu) throws Exception {
        StringJoiner nodes = new StringJoiner(",");
        for (Node node: n.nodes){
            nodes.add(node.accept(this, argu));
        }
        return nodes.toString();
    }
    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    @Override
    public String visit(FormalParameterList n, String argu) throws Exception {
        String ret = n.f0.accept(this, null);

        if (n.f1 != null) {
            ret += n.f1.accept(this, null);
        }

        return ret;
    }

    /**
     * f0 -> FormalParameter()
     * f1 -> FormalParameterTail()
     */
    public String visit(FormalParameterTerm n, String argu) throws Exception {
        return n.f1.accept(this, argu);
    }

    /**
     * f0 -> ","
     * f1 -> FormalParameter()
     */
    @Override
    public String visit(FormalParameterTail n, String argu) throws Exception {
        String ret = "";
        for ( Node node: n.f0.nodes) {
            ret += "," + node.accept(this, null);
        }

        return ret;
    }
    /**
     * f0 -> Type()
     * f1 -> Identifier()
     * f2 -> ";"
     */
    public String visit(VarDeclaration n,String argu) throws Exception{
        String type = n.f0.accept(this,argu);
        String name = n.f1.f0.tokenImage;
//        STMap.put(name,type);
        return type + " " + name;
    }
    /**
     * f0 -> Type()
     * f1 -> Identifier()
     */
    @Override
    public String visit(FormalParameter n,String argu) throws Exception{
        String type = n.f0.accept(this, null);
        String name = n.f1.accept(this, null);
        return type + " " + name;
    }

    @Override
//    public String visit(ArrayType n,String argu) {
//        return "int[]";
//    }

    public String visit(BooleanType n, String argu) {
        return "boolean";
    }

    public String visit(IntegerType n, String argu) {
        return "int";
    }


    @Override
    public String visit(Identifier n, String argu) {
        return n.f0.toString();
    }
}


class TypeVisitor extends GJDepthFirst<String,String>{
    static private Set<String> basictypes = new HashSet<>(Arrays.asList("int","int[]","boolean","boolean[]"));
    static public List<String> errors =  new LinkedList<>();

    static class ErrorInfo{
        static public int line;
        static public String symbol;
        static public String type;
        static public String location;
//        public String message;
    }

    public void checkUnknownType() throws Exception {
        for(Map.Entry<String,Map<String,String>> e_outer : DeclVisitor.vardec.entrySet()) {
            for (Map.Entry<String, String> e_inner : e_outer.getValue().entrySet()) {
                String type = e_inner.getValue();
                if (!basictypes.contains(type) && !DeclVisitor.classdec.keySet().contains(type)) {
                    errors.add(String.format(
                            "\nerror: cannot find symbol\n\t%s %s;\n" +
                                    "\t^\n" +
                                    "symbol:  class %s\n" +
                                    "location:  class %s",
                            type, e_inner.getKey(),
                            type, e_outer.getKey().split("::")[0]));
                }
            }
        }
    }
    public void checkUndeclaredID(String scope, String id) throws Exception{
//        DeclVisitor.vardec.get(scope)
    }
    /**
     * Grammar production:
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> "public"
     * f4 -> "static"
     * f5 -> "void"
     * f6 -> "main"
     * f7 -> "("
     * f8 -> "String"
     * f9 -> "["
     * f10 -> "]"
     * f11 -> Identifier()
     * f12 -> ")"
     * f13 -> "{"
     * f14 -> ( VarDeclaration() )*
     * f15 -> ( Statement() )*
     * f16 -> "}"
     * f17 -> "}"
     */
    public String visit(MainClass n, String scope) throws Exception{
        String classname = n.f1.accept(this,scope);
//        try{
            checkUnknownType();
//        }catch (Exception e){
//            throw e;
//        }
        n.f15.accept(this,classname+"::main");
        return null;
    }

    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "{"
     * f3 -> ( VarDeclaration() )*
     * f4 -> ( MethodDeclaration() )*
     * f5 -> "}"
     */
    public String visit(ClassDeclaration n, String scope) throws Exception{
        String classname = n.f1.accept(this,scope);
        n.f4.accept(this,classname);
        return classname;
    }
    /**
     * f0 -> "class"
     * f1 -> Identifier()
     * f2 -> "extends"
     * f3 -> Identifier()
     * f4 -> "{"
     * f5 -> ( VarDeclaration() )*
     * f6 -> ( MethodDeclaration() )*
     * f7 -> "}"
     */
    public String visit(ClassExtendsDeclaration n, String scope) throws Exception{
        String classname = n.f1.accept(this,scope);
        n.f6.accept(this,classname);
        return classname;
    }
    /**
     * f0 -> "public"
     * f1 -> Type()
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( FormalParameterList() )?
     * f5 -> ")"
     * f6 -> "{"
     * f7 -> ( VarDeclaration() )*
     * f8 -> ( Statement() )*
     * f9 -> "return"
     * f10 -> Expression()
     * f11 -> ";"
     * f12 -> "}"
     */
    public String visit(MethodDeclaration n, String scope) throws Exception{
        String methodname = n.f2.accept(this,scope);
        n.f8.accept(this,scope+"::"+methodname);
        String exptype = n.f10.accept(this,scope+"::"+methodname);
        System.out.println(scope+"::"+methodname);
        String returntype = DeclVisitor.methdec.get(scope).get(methodname);
        if (!(returntype.equals(exptype))){
            System.out.println(String.format("error: trying to return" +
                    " value of type %s from method of type %s",
                    exptype,returntype));
        }
        return null;
    }

    /**
     * Grammar production:
     * f0 -> AndExpression()
     *       | CompareExpression()
     *       | PlusExpression()
     *       | MinusExpression()
     *       | TimesExpression()
     *       | ArrayLookup()
     *       | ArrayLength()
     *       | MessageSend()
     *       | Clause()
     */
//    public String visit(Expression n, String scope) throws Exception {
//        String exp = n.f0.accept(this,scope);
//        System.out.println(exp);
//        return null;
//    }
//    public String visit(NodeChoice n, String scope) throws Exception {
//        return n.choice.accept(this,scope);
//    }
    /**
     * Grammar production:
     * f0 -> Block()
     *       | AssignmentStatement()
     *       | ArrayAssignmentStatement()
     *       | IfStatement()
     *       | WhileStatement()
     *       | PrintStatement()
     */
//    public String visit(Statement n, String scope) throws  Exception{
//        String ok = n.f0.accept(this,scope);
//        return null;
//    }
    /**
     * Grammar production:
     * f0 -> "while"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     */
    public String visit(WhileStatement n, String scope) throws Exception{
       String exp = n.f2.accept(this,scope);
       if(!"boolean".equals(exp)){
           System.out.printf("error: while condition must be of type boolean not %s",exp);
       }
       n.f4.accept(this,scope);
       return null;
    }
    /**
     * Grammar production:
     * f0 -> "if"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> Statement()
     * f5 -> "else"
     * f6 -> Statement()
     */
    public String visit(IfStatement n, String scope) throws Exception{
        String exp = n.f2.accept(this,scope);
        if(!"boolean".equals(exp)){
            System.out.printf("error: if condition must be of type boolean not %s",exp);
        }
        n.f4.accept(this,scope);
        n.f6.accept(this,scope);
        return null;
    }
    /**
     * Grammar production:
     * f0 -> Identifier()
     * f1 -> "["
     * f2 -> Expression()
     * f3 -> "]"
     * f4 -> "="
     * f5 -> Expression()
     * f6 -> ";"
     */
    public String visit(ArrayAssignmentStatement n, String scope) throws Exception {
        String arr = n.f0.accept(this, scope);
        if (!arr.endsWith("[]")) {
            System.out.printf("error: array expected but found %s instead", arr);
        }
        String idx = n.f2.accept(this, scope);
        if (!"int".equals(idx)) {
            System.out.printf("error: array index must be of type int" +
                    "not %s%n", idx);
        }
        String expr = n.f5.accept(this, scope);
        if (!(expr + "[]").equals(arr)) {
            System.out.printf("error: trying to assign %s to %s", expr, arr);
        }
        return null;
    }
    /**
     * Grammar production:
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public String visit(AssignmentStatement n, String scope) throws Exception {
        String lval = n.f0.accept(this,scope);
        String lvaltype = DeclVisitor.vardec.get(scope).get(lval);
        String exptype = n.f2.accept(this,scope);
        if(lvaltype == null){
            System.out.println("undefined variable");
        }
        else if(exptype == null){
            System.out.println("undefined expression");
        }
        else if(!lvaltype.equals(exptype)){
            System.out.println(String.format("type error, trying to assign %s to %s",exptype,lvaltype));
        }
        return null;
    }
    /**
     * Grammar production:
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> Identifier()
     * f3 -> "("
     * f4 -> ( ExpressionList() )?
     * f5 -> ")"
     */
    public String visit(MessageSend n, String scope) throws Exception{
        String classname = n.f0.accept(this, scope);
        String methodname = n.f2.accept(this,scope);
        if(DeclVisitor.classdec.containsKey(classname)){
            if(DeclVisitor.methdec.get(classname).containsKey(methodname)){
                return DeclVisitor.methdec.get(classname).get(methodname);
            }
            else{
                System.out.printf("error: cannot find method %s in class %s%n",methodname,classname);
            }
        }
        else{
            System.out.printf("error: cannot find class %s%n",classname);
        }
        return null;
    }
    /**
     * Grammar production:
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public String visit(ArrayLength n, String scope) throws Exception{
        String arr = n.f0.accept(this,scope);
        if(!arr.endsWith("[]")){
            System.out.println(String.format("error: %s cannot" +
                    " has not attribute length",arr));
        }
        return "int";
    }
    /**
     * Grammar production:
     * f0 -> PrimaryExpression()
     * f1 -> "["
     * f2 -> PrimaryExpression()
     * f3 -> "]"
     */
    public String visit(ArrayLookup n, String scope) throws Exception{
        String arr = n.f0.accept(this,scope);
        String idx = n.f2.accept(this,scope);
        if(!"int".equals(idx)){
            System.out.println(String.format("error: array index must be of type int" +
                    "not %s",idx));
        }
        return arr;
    }
//    public String visit(NotExpression)

    /**
     * Grammar production:
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, String scope) throws Exception{
        String lexp = n.f0.accept(this,scope);
        String rexp = n.f2.accept(this,scope);
        if(!"int".equals(lexp) || !"int".equals(rexp)){
            System.out.println("error: bad operand type for operator '+'");
        }
        return "int";
    }
    /**
     * Grammar production:
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, String scope) throws Exception{
        String lexp = n.f0.accept(this,scope);
        String rexp = n.f2.accept(this,scope);
        if(!"int".equals(lexp) || !"int".equals(rexp)){
            System.out.println("error: bad operand type for operator '-'");
        }
        return "int";
    }
    /**
     * Grammar production:
     * f0 -> PrimaryExpression()
     * f1 -> "*"
     * f2 -> PrimaryExpression()
     */
    public String visit(TimesExpression n, String scope) throws Exception{
        String lexp = n.f0.accept(this,scope);
        String rexp = n.f2.accept(this,scope);
        if(!"int".equals(lexp) || !"int".equals(rexp)){
            System.out.println("error: bad operand type for operator '*'");
        }
        return "int";
    }
    /**
     * Grammar production:
     * f0 -> PrimaryExpression()
     * f1 -> "<"
     * f2 -> PrimaryExpression()
     */
    public String visit(CompareExpression n, String scope) throws Exception{
        String lexp = n.f0.accept(this,scope);
        String rexp = n.f2.accept(this,scope);
        if(!"int".equals(lexp) || !"int".equals(rexp)){
            System.out.println("error: bad operand type for operator '<'");
        }
        return "boolean";
    }
    /**
     * Grammar production:
     * f0 -> "!"
     * f1 -> Clause()
     */
    public String visit(NotExpression n, String scope)throws Exception{
        String clause = n.f1.accept(this,scope);
        if(clause==null) {
            System.out.println("undefined expression");
        }
        else if(!clause.equals("boolean")){
            System.out.println("type error, can't apply logical operator to " + clause);
        }
        return "boolean";
    }
    /**
     * Grammar production:
     * f0 -> IntegerLiteral()
     *       | TrueLiteral()
     *       | FalseLiteral()
     *       | Identifier()
     *       | ThisExpression()
     *       | ArrayAllocationExpression()
     *       | AllocationExpression()
     *       | BracketExpression()
     */

    public String visit(PrimaryExpression n, String scope) throws Exception {
        String pexp = n.f0.accept(this,scope);
        Map<String,String> ST = DeclVisitor.vardec.get(scope);
        if(basictypes.contains(pexp)){
            return pexp;
        }
        else if(ST.get(pexp)!=null){
            return ST.get(pexp);
        }
        else {
            return null;
        }




    }

    public String visit(BooleanArrayType n,String scope) {
        return "boolean[]";
    }
    @Override
    public String visit(IntegerArrayType n,String scope) {
        return "int[]";
    }

    public String visit(BooleanType n, String scope) {
        return "boolean";
    }

    public String visit(IntegerType n, String scope) {
        return "int";
    }

    public String visit(IntegerLiteral n, String scope){
        return  "int";
    }

    public String visit(TrueLiteral n, String scope){
        return "boolean";
    }

    public String visit(FalseLiteral n, String scope){
        return "boolean";
    }
    public String visit(ThisExpression n, String scope){
        String classname = scope.split("::")[0];
        return classname;
    }

    public String visit(BooleanArrayAllocationExpression n, String scope){
        return "boolean[]";
    }

    public String visit(IntegerArrayAllocationExpression n,String scope){
        return "int[]";
    }

    /**
     * Grammar production:
     * f0 -> "new"
     * f1 -> Identifier()
     * f2 -> "("
     * f3 -> ")"
     */
    public String visit(AllocationExpression n, String scope) throws Exception {
        return n.f1.accept(this,scope);
    }
    /**
     * Grammar production:
     * f0 -> "("
     * f1 -> Expression()
     * f2 -> ")"
     */
    public String visit(BracketExpression n, String scope) throws Exception{
        return n.f1.accept(this,scope);
    }
    @Override
    public String visit(Identifier n, String scope) {
//        ErrorInfo.line = n.f0.beginLine;
//        ErrorInfo.type = n.f0.
        return n.f0.toString();
    }
}
