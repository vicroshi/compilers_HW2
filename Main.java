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
                TypeVisitor.errors = 0; //reset error counter
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


class DeclVisitor extends GJDepthFirst<String,String>{
    static Map<String, String> mparams; //contains keys Class::Methodname and values Type,Type,Type... where Type is the type of the parameter
    static Map<String,Map<String,String>> vardec; //fields and local variables
    static Map<String,Map<String,String>> methdec; //methods
    static Map<String,String> classdec; //classes and parent classes
    static String mainclass; //main class for printing purposes
    static Map<String,Map<String, Integer>> methoffsets; //offsets for methods
    static Map<String,Map<String,Integer>> fieldoffsets; //offsets for fields
    public DeclVisitor(){
        //map initialization, we use LinkedHashMaps to preserve insertion order
        vardec = new LinkedHashMap<String,Map<String,String>>();
        methdec = new LinkedHashMap<String, Map<String, String>>();
        classdec = new LinkedHashMap<String, String>();
        methoffsets = new LinkedHashMap<String,Map<String,Integer>>();
        fieldoffsets = new LinkedHashMap<String,Map<String,Integer>>();
        mparams = new LinkedHashMap<String, String>();

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
    public int sizeof(String type){ //returns the size of type for offsets
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
    public void redefinition_error(String type, String var, String scope){ //print function for redefinition
        System.out.printf("error: %s %s is already defined in %s%n",type,var,scope);
        TypeVisitor.errors++;
    }
    public boolean overriden(String classname, String methodname){ //loops over the inheritance chain to find if the method is to be overriden
        String ext = classname;
        while (ext!=null ){
            if(methdec.containsKey(ext) && methdec.get(ext).containsKey(methodname)){ //if you find declaration in a parent class than you must override it
                return true;
            }
            ext = classdec.get(ext);
        }
        return false; //no previous declaration
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
    public String visit(MainClass n, String argu) throws Exception {
        String classname =  n.f1.accept(this,argu);
        classdec.put(classname,null);
        mainclass = classname;
        String mainvars = n.f14.accept(this,argu); //get all var declartions in main method, return them in a string like "type name,type name,type name..."
        Map<String,String> vars = new LinkedHashMap<String,String>(); //symbol table for current scope
        vars.put(n.f11.accept(this,argu),"String[]");
        if(!mainvars.isEmpty()) {
            for (String m : mainvars.split(",")) { //string manipulation
                String[] mainvar = m.split(" ");
                if(!vars.containsKey(mainvar[1])) {
                    vars.put(mainvar[1], mainvar[0]);
                }
                else{
                    redefinition_error("variable",mainvar[1],"method " +classname + "::" + "main");
                }
            }
            if(!vars.isEmpty()){
                vardec.put(classname + "::" + "main",vars); //insert symbol table, if it has anything in
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
        int off = 0;
        if(classdec.containsKey(classname)){ //handles class redeclaration
            System.out.printf("error: class %s is already defined\n",classname);
            TypeVisitor.errors++;
            return null;
        }
        else{
            classdec.put(classname,null);
        }
        String fields = n.f3.present()?n.f3.accept(this,classname):",";
        Map<String,String> fields_st = new LinkedHashMap<String, String>();
        Map<String ,Integer> fields_off = new LinkedHashMap<String, Integer>();
        int offset = 0;
        for(String f: fields.split(",")){ //populate symbol table for current scope, similar to previous one
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
        for(String m : methods.split(",")){ //populate method symbol table for current scope
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
        if(!classdec.containsKey(extname)) { //handle case of no previous definition of parent class
            System.out.printf("class %s must be defined before class %s\n", extname, classname);
            TypeVisitor.errors++;
            classdec.put(classname,null);
            f_offset = 0;
            m_offset = 0;
        }
        else{
            if(fieldoffsets.containsKey(extname)){
                String[] f_orderedKeys = fieldoffsets.get(extname).keySet().toArray(new String[fieldoffsets.get(extname).size()]); //get the offset of previous parent class, continue from there on
                int f_last = f_orderedKeys.length - 1;
                f_offset = fieldoffsets.get(extname).get(f_orderedKeys[f_last]) + sizeof(vardec.get(extname).get(f_orderedKeys[f_last]));
            }
            else{
                f_offset = 0;
            }
            if(methoffsets.containsKey(extname)) {
                String[] m_orderedKeys = methoffsets.get(extname).keySet().toArray(new String[methoffsets.get(extname).size()]); //get the offset of previous parent class, continue from there on
                int m_last = m_orderedKeys.length - 1;
                m_offset = methoffsets.get(extname).get(m_orderedKeys[m_last]) + 8;
            }
            else{
                m_offset = 0;
            }
            classdec.put(classname, extname);
        }
        String fields = n.f5.present()?n.f5.accept(this,classname):","; //same as classdeclaration
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
        String argumentList = n.f4.present() ? n.f4.accept(this, null) : ","; //get methods arguments
        String methodtype = n.f1.accept(this, null);
        String methodname = n.f2.accept(this, null);
        String localvars = n.f7.present() ? n.f7.accept(this,argu+"::"+methodname) : ","; //get methods local vars
        Map<String,String> locvars = new LinkedHashMap<String, String>();
        String[] args = argumentList.split(",");
        StringJoiner jparamtypes = new StringJoiner(",");
        for(String a : args){ //extract only the types of the parameters for mparams symbol table
            String type = a.split(" ")[0];
            jparamtypes.add(type);
        }
        String paramtypes = jparamtypes.toString();
        int i =0;
        for (String arg : argumentList.split(",")){ //populate local vars symbol table, starting with the arguments first
            String[] argument = arg.split(" ");
            if(!locvars.containsKey(argument[1])){
                locvars.put(argument[1],argument[0]);
            }
            else{
                redefinition_error("variable",argument[1],"method " + argu+"::"+methodname);
            }
        }
        for (String l : localvars.split(",")) { //now with the local vars
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
        while(ext!=null) { //check for overriding functions
            if (mparams.containsKey(ext + "::" + methodname)) {
                System.out.println(mparams.get(ext+"::"+methodname));
                String supertype = methdec.get(ext).get(methodname);
                if (!mparams.get(ext + "::" + methodname).equals(paramtypes) || (!methodtype.equals(supertype) && !TypeVisitor.checkInheritance(methodtype,supertype))) {
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

    public String visit(NodeListOptional n, String argu) throws Exception { //this is for returning NodeLists with "," delimiter
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
    static private Set<String> basictypes = new HashSet<>(Arrays.asList("int","int[]","boolean","boolean[]")); //basic types
    static public Set<String> unknown;  //for unknown class symbols
    static int errors = 0; //errors counter
    public TypeVisitor(){
        unknown = new LinkedHashSet<>();
    }
    public void checkUnknownType() throws Exception { //check if there has been a declaration where the type is of an unknown Class
        for(Map.Entry<String,Map<String,String>> e_outer : DeclVisitor.vardec.entrySet()) {
            for (Map.Entry<String, String> e_inner : e_outer.getValue().entrySet()) {
                String type = e_inner.getValue();
                if (!basictypes.contains(type) && !"String[]".equals(type) && !DeclVisitor.classdec.containsKey(type)) {
                    System.out.printf(
                            "\nerror: cannot find symbol\n\t%s %s;\n" +
                                    "\t^\n" +
                                    "symbol:  class %s\n" +
                                    "location:  class %s%n",
                            type, e_inner.getKey(),
                            type, e_outer.getKey().split("::")[0]);
                    unknown.add(type); //add it to the list
                    errors++;
                }
            }
        }
    }
    public void checkParams(String[] formal ,String[] real){ //for checking if call parameters of method are ok
        if(formal.length != real.length){
            System.out.printf("formal and real parameters differ in legnth \nexpected: %s\nfound: %s\n",String.join(",", formal), String.join(",", real));
            errors++;
        }
        else{
            for(int i = 0; i < formal.length; i++){
                if(!formal[i].equals(real[i])){
                    if(!checkInheritance(real[i],formal[i])) { //checks if real[i] is a subclass of formal[i]
                        System.out.printf("parameters type don't match\nexpected: %s\nfound: %s\n", String.join(",", formal), String.join(",", real));
                        errors++;
                    }
                }
            }
        }

    }
    public static boolean checkInheritance(String subclass, String superclass){ //checks for subclass-superclass relationship between two classes, works for multilevel inheritance
        String ext = DeclVisitor.classdec.get(subclass);
        while(ext != null){
            if(ext.equals(superclass)){
                return true;
            }
            ext = DeclVisitor.classdec.get(ext);
        }
        return false;
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
        checkUnknownType(); //check for unknown classes after having filled class declaration symbol table
        n.f15.accept(this,classname+"::main"); //just check the statements
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
        n.f4.accept(this,classname); //visit the method declarations to check on statements
        return classname; //might as well make it return null :P
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
        n.f8.accept(this,scope+"::"+methodname); //check on the statements
        String exptype = n.f10.accept(this,scope+"::"+methodname);
        String returntype = DeclVisitor.methdec.get(scope).get(methodname);
        if (!returntype.equals(exptype) && !checkInheritance(exptype,returntype) && !(unknown.contains(returntype) || unknown.contains(exptype))){ //check for return type of method
            System.out.println(String.format("error: trying to return" +
                    " value of type %s from method of type %s",
                    exptype,returntype));
            errors++;
        }
        return null;
    }

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
        if(!"int".equals(exp)){ //only ints allowed
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
       if(!"boolean".equals(exp) && !unknown.contains(exp)){ //only bools inside while condition
           System.out.printf("error: while condition must be of type boolean not %s\n",exp);
           errors++;
       }
       n.f4.accept(this,scope); //chech on the statement inside while
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
        if(!"boolean".equals(exp) && !unknown.contains(exp)){ //only bools inside if condition
            n.f2.accept(this,scope);
            System.out.println(scope);
            System.out.printf("error: if condition must be of type boolean not %s\n",exp);
            errors++;
        }
        n.f4.accept(this,scope); //check on the if statements
        n.f6.accept(this,scope); //check on the else statements
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
    public String visit(ArrayAssignmentStatement n, String scope) throws Exception { //checks for assignment statement legality
        String arr = n.f0.accept(this, scope); //get array name
        String arrtype = DeclVisitor.vardec.containsKey(scope)?DeclVisitor.vardec.get(scope).get(arr):null; //get its type
        if(arrtype==null){ //handle undeclared
            String classname = scope.split("::")[0];
            arrtype = DeclVisitor.vardec.containsKey(classname)?DeclVisitor.vardec.get(classname).get(arr):null;
            while (classname != null && arrtype == null){ //look up the inheritance chain for inherited fields
                arrtype = DeclVisitor.vardec.containsKey(classname)?DeclVisitor.vardec.get(classname).get(arr):null;
                classname = DeclVisitor.classdec.get(classname);
            }
            if(arrtype == null){ //if nothing found then call undeclared error
                System.out.printf("cannot find symbol %s\n",arr);
                errors++;
                return null;
            }
        }
        String idx = n.f2.accept(this, scope);
        if (!"int".equals(idx) && idx!=null) { //check for index of array to be int
            System.out.printf("error: array index must be of type int" +
            "not %s%n", idx);
            errors++;
        }
        if (!arrtype.endsWith("[]")) { //check if identifier is really of array type
            System.out.printf("error: array required got %s instead\n", arrtype);
            errors++;
        }
        else { //check if the assigned expression is of the dereferenced type, meaning if array is of type int[] then expression should be int
            String expr = n.f5.accept(this, scope);
            if (!(expr).equals(arrtype.split("\\[")[0])) { //string manipulation to "remove" the []
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
        n.f1.accept(this,scope); //just visit statements inside block
        return null;
    }
    /**
     * Grammar production:
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public String visit(AssignmentStatement n, String scope) throws Exception { //same thing as array assignment but without the array error checks
        String lval = n.f0.accept(this,scope);
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
            System.out.printf("undefined variable (lvalue %s)\n",lval); //undefined left variable
            errors++;
        }
        else if(!lvaltype.equals(exptype) && exptype!=null && !checkInheritance(exptype,lvaltype) && !(unknown.contains(lvaltype) || unknown.contains(exptype))){
            System.out.printf("error: trying to assign %s to %s%n",exptype,lvaltype); //type missmatch of assignment
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
        String classname = n.f0.accept(this, scope); //get identifier's type
        String methodname = n.f2.accept(this,scope); //get methods name
        String args = n.f4.present() ? n.f4.accept(this,scope) : ""; //get the real parameters types separated with ","
        if(basictypes.contains(classname)){
            System.out.printf("error: %s cannot be dereferenced\n",classname);
            errors++;
            return null;
        }
        else if (DeclVisitor.classdec.containsKey(classname)) { //identifier must be a class
            String tmpclassname = classname;
            while(!DeclVisitor.methdec.containsKey(tmpclassname) || !DeclVisitor.methdec.get(tmpclassname).containsKey(methodname)){ //look up the inheritance chain for function
                if(tmpclassname==null){
                    System.out.printf("error: cannot find method %s in class %s\n",methodname,classname);
                    errors++;
                    return null;
                }
                tmpclassname=DeclVisitor.classdec.get(tmpclassname);
            }
            checkParams(DeclVisitor.mparams.get(tmpclassname + "::" + methodname).split(","),args.split(",")); //check if real and formal parameter types match
            return DeclVisitor.methdec.get(tmpclassname).get(methodname); // expression type is the type of the method, even if parameters don't match the correct type should be returned
        }
        else {
            return classname; //this is incase of unknown class type, so it can be ignored
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
    public String visit(ArrayLength n, String scope) throws Exception{ //check for array length
        String arr = n.f0.accept(this,scope);
        if(unknown.contains(arr) || arr == null) {
            return arr; //if its unknown return its name so it can be ignored
        }
        else if(!arr.endsWith("[]")){ //must be an array
            System.out.printf("error: %s cannot be dereferenced\n",arr);
            errors++;
            return null;
        }
        return "int"; //its length so its always int, even if errors are found int must be returned
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
        if (!arr.endsWith("[]")){ //must be an array
            System.out.printf("error: array required got %s instead\n", arr);
            errors++;
        }
        if(!"int".equals(idx)){ //index must be int
            System.out.printf("error: array index must be of type int" +
                    "not %s%n",idx);
            errors++;
        }
        return  arr.split("\\[")[0]; //return the "dereferenced" type, i.e. if a is of type int[], then a[0] is of type int
    }

    /**
     * Grammar production:
     * f0 -> PrimaryExpression()
     * f1 -> "+"
     * f2 -> PrimaryExpression()
     */
    public String visit(PlusExpression n, String scope) throws Exception{
        String lexp = n.f0.accept(this,scope);
        String rexp = n.f2.accept(this,scope);

        if((!"int".equals(lexp) || !"int".equals(rexp)) && !(unknown.contains(lexp) || unknown.contains(rexp))){ // operator works only on ints
            System.out.println("error: bad operand type for operator '+'");
            errors++;
        }
        return "int"; // the result is obviously int
    }
    /**
     * Grammar production:
     * f0 -> PrimaryExpression()
     * f1 -> "-"
     * f2 -> PrimaryExpression()
     */
    public String visit(MinusExpression n, String scope) throws Exception{ //same as plus
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
    public String visit(TimesExpression n, String scope) throws Exception{ //same as plus
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
        if(!"int".equals(lexp) || !"int".equals(rexp) && !(unknown.contains(lexp) || unknown.contains(rexp))){ //works only with ints
            System.out.println("error: bad operand type for operator '<'");
            errors++;
        }
        return "boolean"; //result is always boolean
    }
    /**
     * f0 -> Clause()
     * f1 -> "&&"
     * f2 -> Clause()
     */
    public String visit(AndExpression n, String scope) throws Exception{
        String lclause = n.f0.accept(this,scope);
        if(lclause == null){
            System.out.println("undefined right expression in && operator");
        }
        String rclause = n.f2.accept(this,scope);
        if(rclause == null){
            System.out.println("undefined left expression in && operator");
        }
        if(!"boolean".equals(lclause) || !"boolean".equals(rclause) && !(unknown.contains(lclause) || unknown.contains(rclause))){ //works only with bools
            System.out.println("error: bad operand type for logical operator '&&'");
            errors++;
        }
        return "boolean"; //results is also boolean
    }
    /**
     * Grammar production:
     * f0 -> "!"
     * f1 -> Clause()
     */
    public String visit(NotExpression n, String scope)throws Exception{ //same as &&
        String clause = n.f1.accept(this,scope);
        if(clause==null) {
            System.out.println("undefined expression in ! operator");
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
        String pexp = n.f0.accept(this,scope); //get the type of expression, identifiers return its name
        String type = DeclVisitor.vardec.containsKey(scope) ? DeclVisitor.vardec.get(scope).get(pexp):null; //if it is an identifier get its type
        if(basictypes.contains(pexp) || DeclVisitor.classdec.containsKey(pexp)){ //if it is a basic type or a class return it
            return pexp;
        }
        String classscope  = scope.split("::")[0];
        while (classscope != null && type==null){ //loop over the inheritance chain incase of inherited identifier
            type = DeclVisitor.vardec.containsKey(classscope) ? DeclVisitor.vardec.get(classscope).get(pexp):null;
            classscope = DeclVisitor.classdec.get(classscope);
        }
        if (type==null){
            System.out.printf("cannot find symbol %s in method %s\n",pexp,scope);
            errors++;
        }
        return type; //return the type of the identifier found
    }
    //the rest are pretty straightforward
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
