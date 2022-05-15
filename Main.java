import com.sun.source.tree.Scope;
import syntaxtree.*;
import visitor.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;

public class
Main {
    public static void main(String[] args) throws Exception {
        for(int i = 0; i < args.length; i++) {
            FileInputStream fis=null;
            try {
                fis = new FileInputStream(args[i]);
                Error.fis = fis;
                System.out.printf("\nParsing file %s\n",args[i]);
                MiniJavaParser parser = new MiniJavaParser(fis);
                Goal root = parser.Goal();
                System.err.println("Program parsed successfully.");
                DeclVisitor decvis = new DeclVisitor();
                root.accept(decvis, null);
                TypeVisitor typevis = new TypeVisitor();
                root.accept(typevis,null);
                if(TypeVisitor.errors==0){
                    for(String scope : DeclVisitor.classdec.keySet()){
                        if(!scope.equals(DeclVisitor.mainclass)) {
                            System.out.printf("-----------Class %s-----------\n", scope);
                            System.out.println("--Variables--");
                            if (DeclVisitor.fieldoffsets.containsKey(scope)) {
                                for (Map.Entry<String, Integer> f_e : DeclVisitor.fieldoffsets.get(scope).entrySet()) {
                                    System.out.printf("%s.%s: %d\n", scope, f_e.getKey(), f_e.getValue());
                                }
                            }
                            System.out.println("---Methods---");
                            if (DeclVisitor.methoffsets.containsKey(scope)) {
                                for (Map.Entry<String, Integer> m_e : DeclVisitor.methoffsets.get(scope).entrySet()) {
                                    System.out.printf("%s.%s : %d\n", scope, m_e.getKey(), m_e.getValue());
                                }
                            }
                            System.out.println();
                        }
                    }
                }
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

class Error{
    public static FileInputStream fis;
    public static int errLine;
}

class DeclVisitor extends GJDepthFirst<String,String>{
    static Map<String, String> mparams = new LinkedHashMap<String, String>();
    static Map<String,Map<String,String>> vardec;
    static Map<String,Map<String,String>> methdec;
    static Map<String,String> classdec;
    static String mainclass;
    static Map<String,Map<String, Integer>> methoffsets;
    static Map<String,Map<String,Integer>> fieldoffsets;
    public DeclVisitor(){
        Map<String,String> var = new LinkedHashMap<String,String>();
        vardec = new LinkedHashMap<String,Map<String,String>>();
        methdec = new LinkedHashMap<String, Map<String, String>>();
        classdec = new LinkedHashMap<String, String>();
        methoffsets = new LinkedHashMap<String,Map<String,Integer>>();
        fieldoffsets = new LinkedHashMap<String,Map<String,Integer>>();
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
     * f4 -> "static"`
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
    public int sizeof(String type){
        if(type.equals("int")){
            return 4;
        }
        else if(type.equals("boolean")){
            return 1;
        }
        else {
            return 8;
        }
    }
    public void redefinition_error(String type, String var, String scope){
        System.out.printf("error: %s %s is already defined in %s%n",type,var,scope);
        TypeVisitor.errors++;
    }
    public boolean overriden(String classname, String methodname){
        String ext = classname;
        while (ext!=null ){
            if(methdec.containsKey(ext) && methdec.get(ext).containsKey(methodname)){
                return true;
            }
            ext = classdec.get(ext);
        }
        return false;
    }

    public String visit(MainClass n, String argu) throws Exception {
        String classname =  n.f1.accept(this,argu);
        classdec.put(classname,null);
        mainclass = classname;
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
        String fields = n.f3.present()?n.f3.accept(this,classname):",";
        int off = 0;
        if(classdec.containsKey(classname)){
            System.out.printf("error: class %s is already defined\n",classname);
            TypeVisitor.errors++;
        }
        else{
            classdec.put(classname,null);
        }
        Map<String,String> fields_st = new LinkedHashMap<String, String>();
        Map<String ,Integer> fields_off = new LinkedHashMap<String, Integer>();
        int offset = 0;
        for(String f: fields.split(",")){
            String[] field = f.split(" ");
            if(!fields_st.containsKey(field[1])){
                fields_st.put(field[1],field[0]);
                fields_off.put(field[1],offset);
                offset+=sizeof(field[0]);
            }
            else {
                redefinition_error("variable",field[1],"class " +classname);
            }
        }
        if(!fields_st.isEmpty()) {
            vardec.put(classname, fields_st);
        }
        if(!fields_off.isEmpty()) {
            fieldoffsets.put(classname, fields_off);
        }
        Map<String,String> methods_st = new LinkedHashMap<String, String>();
        String methods = n.f4.present()?n.f4.accept(this,classname):",";
        Map<String ,Integer>  methods_off = new LinkedHashMap<String, Integer>();
        offset = 0;
        for(String m : methods.split(",")){
            String[] method = m.split(" ");
            if(!methods_st.containsKey(method[1])){
                    methods_st.put(method[1],method[0]);
                    methods_off.put(method[1],offset);
                    offset+=8;
            }
            else {
                redefinition_error("method",method[1],"class " + classname);
            }
        }
        if(!methods_st.isEmpty()) {
            methdec.put(classname, methods_st);
        }
        if(!methods_off.isEmpty()){
            methoffsets.put(classname, methods_off);
        }
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
        int f_offset;
        int m_offset;
        if(!classdec.containsKey(extname)) {
            System.out.printf("class %s must be defined before class %s\n", extname, classname);
            TypeVisitor.errors++;
            classdec.put(classname,null);
            f_offset = 0;
            m_offset = 0;
        }
        else{
//            System.out.println(extname);
            if(fieldoffsets.containsKey(extname)){
                String[] f_orderedKeys = fieldoffsets.get(extname).keySet().toArray(new String[fieldoffsets.get(extname).size()]);
                int f_last = f_orderedKeys.length - 1;
                f_offset = fieldoffsets.get(extname).get(f_orderedKeys[f_last]) + sizeof(vardec.get(extname).get(f_orderedKeys[f_last]));
            }
            else{
                f_offset = 0;
            }
            if(methoffsets.containsKey(extname)) {
//                System.out.println(extname);
                String[] m_orderedKeys = methoffsets.get(extname).keySet().toArray(new String[methoffsets.get(extname).size()]);
                int m_last = m_orderedKeys.length - 1;
                m_offset = methoffsets.get(extname).get(m_orderedKeys[m_last]) + 8;
            }
            else{
                m_offset = 0;
            }
            classdec.put(classname, extname);
        }
        String fields = n.f5.present()?n.f5.accept(this,classname):",";
        Map<String,String> fields_st = new LinkedHashMap<String, String>();
        Map<String,Integer> fields_off = new LinkedHashMap<String, Integer>();
        for(String f: fields.split(",")){
            String[] field = f.split(" ");
            if(!fields_st.containsKey(field[1])){
                fields_st.put(field[1],field[0]);
                fields_off.put(field[1],f_offset);
                f_offset+=sizeof(field[0]);
            }
            else {
                redefinition_error("variable",field[1],"class " +classname);
            }
        }
        if(!fields_st.isEmpty()) {
            vardec.put(classname, fields_st);
        }
        if(!fields_off.isEmpty()) {
            fieldoffsets.put(classname, fields_off);
        }
        Map<String,String> methods_st = new LinkedHashMap<String, String>();
        Map<String,Integer> methods_off = new LinkedHashMap<String, Integer>();
        String methods = n.f6.present()?n.f6.accept(this,classname):",";

        for(String m : methods.split(",")){
            String[] method = m.split(" ");
            if(!methods_st.containsKey(method[1])){
                methods_st.put(method[1],method[0]);
                if(!overriden(extname,method[1])){
                    methods_off.put(method[1],m_offset);
                }
                m_offset+=8;
            }
            else {
                redefinition_error("method",method[1],"class "+classname);
            }
        }
        if(!methods_st.isEmpty()) {
            methdec.put(classname, methods_st);
        }
        if(!methods_off.isEmpty()){
            methoffsets.put(classname, methods_off);
        }
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
        String argumentList = n.f4.present() ? n.f4.accept(this, null) : ",";
        String methodtype = n.f1.accept(this, null);
        String methodname = n.f2.accept(this, null);
        String localvars = n.f7.present() ? n.f7.accept(this,argu+"::"+methodname) : ",";
        Map<String,String> locvars = new LinkedHashMap<String, String>();
        String[] args = argumentList.split(",");
        StringJoiner jparamtypes = new StringJoiner(",");
        for(String a : args){
            String type = a.split(" ")[0];
            jparamtypes.add(type);
        }
        String paramtypes = jparamtypes.toString();
        int i =0;
        for (String arg : argumentList.split(",")){
            String[] argument = arg.split(" ");
            if(!locvars.containsKey(argument[1])){
                locvars.put(argument[1],argument[0]);
            }
            else{
                redefinition_error("variable",argument[1],"method " + argu+"::"+methodname);
            }
        }
        for (String l : localvars.split(",")) {
            String[] localvar = l.split(" ");
            if(!locvars.containsKey(localvar[1])){
                locvars.put(localvar[1],localvar[0]);
            }
            else{
                redefinition_error("variable",localvar[1],"method " + argu+"::"+methodname);
            }
        }
        if(!locvars.isEmpty()){
            vardec.put(argu+"::"+methodname,locvars);
        }
        String ext = classdec.get(argu);
        while(ext!=null) {
            if (mparams.containsKey(ext + "::" + methodname)) {
                String supertype = methdec.get(ext).get(methodname);
                if (!mparams.get(ext + "::" + methodname).equals(paramtypes) || !methodtype.equals(supertype)) {
//                    System.out.println(mparams.get(ext + "::" + methodname) + "<>" +paramtypes);
                    System.out.println(("error: to redefine parent class"+
                            " method both return and parameters types must match"));
                    TypeVisitor.errors++;

                }
                break;
            }
            ext = classdec.get(ext);
        }
        mparams.put(argu+"::"+methodname,paramtypes);
        return methodtype+" "+methodname;
    }

//    @Override
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
        if (n.f1.f0.present()) {
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

    public String visit(BooleanArrayType n,String argu) {
        return "boolean[]";
    }
    @Override
    public String visit(IntegerArrayType n,String argu) {
        return "int[]";
    }

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
//    static public List<String> errors =  new LinkedList<>();
    static public Set<String> unknown = new LinkedHashSet<>();
    static int errors = 0;
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
                    System.out.printf(
                            "\nerror: cannot find symbol\n\t%s %s;\n" +
                                    "\t^\n" +
                                    "symbol:  class %s\n" +
                                    "location:  class %s%n",
                            type, e_inner.getKey(),
                            type, e_outer.getKey().split("::")[0]);
                    unknown.add(type);
                    errors++;
                }
            }
        }
    }
    public void checkParams(String[] formal ,String[] real){
        if(formal.length != real.length){
            System.out.println("formal and real parameters differ in legnth");
            errors++;
        }
        else{
            for(int i = 0; i < formal.length; i++){
                if(!formal[i].equals(real[i])){
                    if(!checkInheritance(real[i],formal[i])) {
                        System.out.printf("parameters type don't match\nexpected: %s\nfound: %s\n", String.join(",", formal), String.join(",", real));
                        errors++;
                    }
                }
            }
        }

    }
    public boolean checkInheritance(String subclass, String superclass){
        String ext = DeclVisitor.classdec.get(subclass);
        while(ext != null){
            if(ext.equals(superclass)){
                return true;
            }
            ext = DeclVisitor.classdec.get(ext);
        }
        return false;
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
//        System.out.println(scope+"::"+methodname);
        String returntype = DeclVisitor.methdec.get(scope).get(methodname);
        if (!returntype.equals(exptype) && !(unknown.contains(returntype) || unknown.contains(exptype))){
//            System.out.println(scope+"::"+methodname);
            System.out.println(String.format("error: trying to return" +
                    " value of type %s from method of type %s",
                    exptype,returntype));
            errors++;
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
     * f0 -> "System.out.println"
     * f1 -> "("
     * f2 -> Expression()
     * f3 -> ")"
     * f4 -> ";"
     */
    public String visit(PrintStatement n, String scope) throws Exception{
        String exp = n.f2.accept(this,scope);
        if(!"int".equals(exp)){
            System.out.printf("error: print statement expected int, but found %s\n",exp);
            errors++;
        }
        return null;
    }
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
       if(!"boolean".equals(exp) && !unknown.contains(exp)){
           System.out.printf("error: while condition must be of type boolean not %s\n",exp);
           errors++;
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
        if(!"boolean".equals(exp) && !unknown.contains(exp)){
            n.f2.accept(this,scope);
            System.out.println(scope);
            System.out.printf("error: if condition must be of type boolean not %s\n",exp);
            errors++;
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
        String arrtype = DeclVisitor.vardec.get(scope).get(arr);
        if(arrtype==null){
            String classname = scope.split("::")[0];
            arrtype = DeclVisitor.vardec.containsKey(classname)?DeclVisitor.vardec.get(classname).get(arr):null;
            while (classname != null && arrtype == null){
                arrtype = DeclVisitor.vardec.containsKey(classname)?DeclVisitor.vardec.get(classname).get(arr):null;
                classname = DeclVisitor.classdec.get(classname);
            }
            if(arrtype == null){
                System.out.printf("cannot find symbol %s",arr);
                errors++;
            }
        }
        String idx = n.f2.accept(this, scope);
        if (!"int".equals(idx) && idx!=null) {
            System.out.printf("error: array index must be of type int" +
            "not %s%n", idx);
            errors++;
        }
        if (!arrtype.endsWith("[]")) {
            System.out.printf("error: array required got %s instead\n", arrtype);
            errors++;
        }
        else {
            String expr = n.f5.accept(this, scope);
            if (!(expr).equals(arrtype.split("\\[")[0])) {
                System.out.printf("error: trying to assign %s to %s\n", expr, arrtype);
                errors++;
            }
        }
        return null;
    }
    /**
     * Grammar production:
     * f0 -> "{"
     * f1 -> ( Statement() )*
     * f2 -> "}"
     */
    public String visit(Block n, String scope) throws Exception{
        n.f1.accept(this,scope);
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
//        System.out.println(scope);
        String lvaltype = DeclVisitor.vardec.containsKey(scope)?DeclVisitor.vardec.get(scope).get(lval):null;
        if(lvaltype == null){
            String classname = scope.split("::")[0];
            lvaltype = DeclVisitor.vardec.containsKey(classname)?DeclVisitor.vardec.get(classname).get(lval):null;
            while (classname != null && lvaltype == null){
                lvaltype = DeclVisitor.vardec.containsKey(classname)?DeclVisitor.vardec.get(classname).get(lval):null;
                classname = DeclVisitor.classdec.get(classname);
            }
        }
        String exptype = n.f2.accept(this,scope);
        if(lvaltype == null){
            System.out.println(lval);
            System.out.println("undefined variable (LEFT)");
            errors++;
        }
//        else if(exptype == null){
////            System.out.println(exptype);
//            System.out.println("undefined expression (RIGHT)");
//        }
        else if(!lvaltype.equals(exptype) && exptype!=null && !checkInheritance(exptype,lvaltype) && !(unknown.contains(lvaltype) || unknown.contains(exptype))){
//            System.out.println(lval);
            System.out.printf("error: trying to assign %s to %s%n",exptype,lvaltype);
            errors++;
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
        String args = n.f4.present() ? n.f4.accept(this,scope) : "";
        if (DeclVisitor.classdec.containsKey(classname)) {
//            System.out.printf("error: cannot find class %s%n", classname);
//        }
            String tmpclassname = classname;
            while(!DeclVisitor.methdec.containsKey(tmpclassname) || !DeclVisitor.methdec.get(tmpclassname).containsKey(methodname)){
                if(tmpclassname==null){
                    System.out.printf("error: cannot find method %s in class %s\n",methodname,classname);
                    errors++;
                    return null;
                }
                tmpclassname=DeclVisitor.classdec.get(tmpclassname);
            }
            checkParams(DeclVisitor.mparams.get(tmpclassname + "::" + methodname).split(","),args.split(","));
            return DeclVisitor.methdec.get(tmpclassname).get(methodname);
        }
        else {
            return classname;
        }
    }
    /**
     * Grammar production:
     * f0 -> Expression()
     * f1 -> ExpressionTail()
     */
    public String visit(ExpressionList n, String scope) throws Exception {
        String ret = n.f0.accept(this, scope);
        if (n.f1.f0.present()) {
            ret += n.f1.accept(this, scope);

        }
        return ret;
    }
//    public String visit(NodeListOptional n, String argu) throws Exception {
//        StringJoiner nodes = new StringJoiner(",");
//        for (Node node: n.nodes){
//            nodes.add(node.accept(this, argu));
//        }
//        return nodes.toString();
//    }
    /**
     * f0 -> ( ExpressionTerm() )*
     *
     */
    public String visit(ExpressionTail n, String scope) throws Exception{
        String ret = "";
        for ( Node node: n.f0.nodes) {
            ret += "," + node.accept(this, scope);
        }

        return ret;    }
    /**
     * f0 -> ","
     * f1 -> Expression()
     */
    public String visit(ExpressionTerm n, String scope) throws Exception{
        return n.f1.accept(this,scope);
    }
    /**
     * Grammar production:
     * f0 -> PrimaryExpression()
     * f1 -> "."
     * f2 -> "length"
     */
    public String visit(ArrayLength n, String scope) throws Exception{
        String arr = n.f0.accept(this,scope);
        if(unknown.contains(arr)) {
            return arr;
        }
        else if(!arr.endsWith("[]")){
            System.out.printf("error: %s cannot" +
                    " has not attribute length%n",arr);
            errors++;
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
        if(unknown.contains(arr)){
            return arr;
        }
        if (!arr.endsWith("[]")){
            System.out.printf("error: array required got %s instead\n", arr);
            errors++;
        }
        if(!"int".equals(idx)){
            System.out.printf("error: array index must be of type int" +
                    "not %s%n",idx);
            errors++;
        }
        return  arr.split("\\[")[0];
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

        if((!"int".equals(lexp) || !"int".equals(rexp)) && !(unknown.contains(lexp) || unknown.contains(rexp))){
            System.out.println("error: bad operand type for operator '+'");
            errors++;
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
        if(!"int".equals(lexp) || !"int".equals(rexp) && !(unknown.contains(lexp) || unknown.contains(rexp))){
            System.out.println("error: bad operand type for operator '-'");
            errors++;
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
        if(!"int".equals(lexp) || !"int".equals(rexp) && !(unknown.contains(lexp) || unknown.contains(rexp))){
            System.out.println("error: bad operand type for operator '*'");
            errors++;
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
        if(!"int".equals(lexp) || !"int".equals(rexp) && !(unknown.contains(lexp) || unknown.contains(rexp))){
            System.out.println("error: bad operand type for operator '<'");
            errors++;
        }
        return "boolean";
    }
    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    public String visit(AndExpression n, String scope) throws Exception{
        String lclause = n.f0.accept(this,scope);
        String rclause = n.f2.accept(this,scope);
        if(!"boolean".equals(lclause) || !"boolean".equals(rclause) && !(unknown.contains(lclause) || unknown.contains(rclause))){
            System.out.println("error: bad operand type for logical operator '&&'");
            errors++;
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
            errors++;
        }
        else if(!clause.equals("boolean")  && !unknown.contains(clause)){
            System.out.println("type error, can't apply logical operator to " + clause);
            errors++;
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
        String type = DeclVisitor.vardec.containsKey(scope) ? DeclVisitor.vardec.get(scope).get(pexp):null;
        if(basictypes.contains(pexp) || DeclVisitor.classdec.containsKey(pexp)){
            return pexp;
        }
        String classscope  = scope.split("::")[0];
        while (classscope != null && type==null){
            type = DeclVisitor.vardec.containsKey(classscope) ? DeclVisitor.vardec.get(classscope).get(pexp):null;
            classscope = DeclVisitor.classdec.get(classscope);
        }
        if (type==null){
            System.out.printf("cannot find symbol %s in method %s\n",pexp,scope);
            errors++;
        }
        return type;
    }

    public String visit(BooleanArrayType n,String scope) {
        return "boolean[]";
    }
    @Override
    public String visit(IntegerArrayType n,String scope) throws Exception{
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
        String id =n.f1.accept(this,scope);
        return id;
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
        return n.f0.toString();
    }
}
