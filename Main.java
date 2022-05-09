import org.javatuples.Tuple;
import syntaxtree.*;
import visitor.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

public class Main {
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
                System.out.println("Field Symbol Table:");
                for (Map.Entry<String, String> e : decvis.cfields.entrySet()) {
                    System.out.println(e.getValue() + " " + e.getKey());
                }
                System.out.println("\nMethod Symbol Table:");
                for (Map.Entry<String, String> e : decvis.cmethods.entrySet()) {
                    System.out.println(e.getValue() + " " + e.getKey());
                }
                System.out.println("\nMethod Parameter Symbol Table:");
                for (Map.Entry<String, String> e : decvis.mparams.entrySet()) {
                    System.out.println(e.getKey() + "(" + e.getValue() + ")");
                }
                System.out.println("\nMethod LocalVar Symbol Table:");
                for (Map.Entry<String, String> e : decvis.mvars.entrySet()) {
                    System.out.println(e.getValue() + " " + e.getKey());
                }
                System.out.println("\nClass Inheritance Table:");
                for (Map.Entry<String, String> e : decvis.cextends.entrySet()) {
                    System.out.println(e.getKey() + " extends " + e.getValue());
                }
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

class DeclVisitor extends GJDepthFirst<String,String>{
    static Map<String,String> cfields = new LinkedHashMap<String,String>();
    static Map<String,String> cmethods = new LinkedHashMap<String,String>();
    static Map<String,String> mparams = new LinkedHashMap<String,String>();
    static Map<String,String> cextends = new LinkedHashMap<String,String>();
    static Map<String,String> mvars = new LinkedHashMap<String,String>();
//    public DeclVisitor(){
//        cfields = new LinkedHashMap<String,String>();
//        cmethods = new LinkedHashMap<String,String>();
//        mparams = new LinkedHashMap<String,String>();
//        cextends = new LinkedHashMap<String, String>();
//        mvars = new LinkedHashMap<String, String>();
//    }
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
        String mainvars = n.f14.accept(this,argu);
        if(!mainvars.isEmpty()) {
            for (String m : mainvars.split(",")) {
                String[] mainvar = m.split(" ");
                mvars.put(classname + "::" + "main" + "::" + mainvar[1], mainvar[0]);
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
        for(String f: fields.split(",")){
            String[] field = f.split(" ");
            cfields.put(classname+"::"+field[1],field[0]);
        }
        String methods = n.f4.accept(this,classname);
        for(String m : methods.split(",")){
            String[] method = m.split(" ");
            cmethods.put(classname+"::"+method[1],method[0]);
        }
        super.visit(n, classname);
        System.out.println();
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
        cextends.put(classname,extname);
        String fields = n.f5.accept(this,classname);
        for(String f: fields.split(",")){
            String[] field = f.split(" ");
            cfields.put(classname+"::"+field[1],field[0]);
        }
        String methods = n.f6.accept(this,classname);
        for(String m : methods.split(",")){
            String[] method = m.split(" ");
            cmethods.put(classname+"::"+method[1],method[0]);
        }
//        System.out.println("Class: " + classname +" extends " + extname);
//        EXTMap.put(classname,extname);
        super.visit(n, classname);
        System.out.println();
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
        String myType = n.f1.accept(this, null);
        String myName = n.f2.accept(this, null);
        String localvars = n.f7.accept(this,argu+"::"+myName);
        if(!localvars.isEmpty()) {
            for (String l : localvars.split(",", -1)) {
                String[] localvar = l.split(" ");
                mvars.put(argu + "::" + myName + "::" + localvar[1], localvar[0]);
            }
        }
        mparams.put(argu+"::"+myName,argumentList);
        return myType+" "+myName;
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
            ret += ", " + node.accept(this, null);
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
    public String visit(ArrayType n,String argu) {
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
    public String visit(MainClass n, String argu) throws Exception{
        n.f15.accept(this,argu);
        return null;
    }


    /**
     * Grammar production:
     * f0 -> Identifier()
     * f1 -> "="
     * f2 -> Expression()
     * f3 -> ";"
     */
    public String visit(AssignmentStatement n, String argu) throws Exception {
        String lval = n.f0.accept(this,argu);
        String rval = n.f2.accept(this,argu);
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
    public String visit(Expression n, String argu) throws Exception {
        String exp = n.f0.accept(this,argu);
        System.out.println(exp);
        return null;
    }
    public String visit(NodeChoice n, String argu) throws Exception {
        return n.choice.accept(this,argu);
    }
    /**
     * Grammar production:
     * f0 -> Block()
     *       | AssignmentStatement()
     *       | ArrayAssignmentStatement()
     *       | IfStatement()
     *       | WhileStatement()
     *       | PrintStatement()
     */
    public String visit(Statement n, String argu) throws  Exception{
        String ok = n.f0.accept(this,argu);
        return null;
    }
}