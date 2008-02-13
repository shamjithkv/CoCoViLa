package ee.ioc.cs.vsle.synthesize;

import ee.ioc.cs.vsle.table.*;
import ee.ioc.cs.vsle.util.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import ee.ioc.cs.vsle.vclass.*;

import ee.ioc.cs.vsle.editor.RuntimeProperties;
import ee.ioc.cs.vsle.equations.EquationSolver;
import ee.ioc.cs.vsle.equations.EquationSolver.*;
import static ee.ioc.cs.vsle.util.TypeUtil.*;

/**
 * This class takes care of parsing the specification and translating it into a
 * graph on which planning can be run.
 * 
 * @author Ando Saabas, Pavel Grigorenko
 */
public class SpecParser {

    private SpecParser() {
    }

    public static void main( String[] args ) {

        try {
            // String s = new String( getStringFromFile( args[ 0 ] ) );
            String ss = "class blah /*@ specification blah super ffff, fffdddd, gjjjh { spec } @*/";

            ArrayList<String> a = getSpec( ss, false );

            while ( !a.isEmpty() ) {
                if ( ! ( a.get( 0 ) ).equals( "" ) ) {
                    try {
                        db.p( getLine( a ) );
                    } catch ( SpecParseException e ) {
                        e.printStackTrace();
                    }
                } else {
                    a.remove( 0 );

                }
            }
        } catch ( Exception e ) {
            db.p( e );
        }
    }

    public static String getClassName( String spec ) {
        Pattern pattern = Pattern.compile( "class[ \t\n]+([a-zA-Z_0-9-]+)[ \t\n]+" );
        Matcher matcher = pattern.matcher( spec );

        if ( matcher.find() ) {
            return matcher.group( 1 );
        }

        return "";
    }

    /**
     * @return ArrayList of lines in specification
     * @param text Secification text as String
     */
    static ArrayList<String> getSpec( String text, boolean isRefinedSpec ) throws IOException {
        if ( !isRefinedSpec ) {
            text = refineSpec( text );
        }
        String[] s = text.trim().split( ";", -1 );
        ArrayList<String> a = new ArrayList<String>();

        for ( int i = 0; i < s.length; i++ ) {
            a.add( s[ i ].trim() );
        }
        return a;

    }

    /**
     * Reads a line from an arraylist of specification lines, removes it from
     * the arraylist and returns the line together with its type information
     * 
     * @return a specification line with its type information
     * @param a arraylist of specification lines
     */
    static LineType getLine( ArrayList<String> a ) throws SpecParseException {
        Matcher matcher;
        Pattern pattern;

        while ( ( a.get( 0 ) ).equals( "" ) || a.get( 0 ).trim().startsWith( "//" ) ) {
            a.remove( 0 );
            if ( a.isEmpty() ) {
                return null;
            }
        }
        final String line = a.get( 0 );

        a.remove( 0 );
        if ( line.startsWith( "alias " ) ) {
            pattern = Pattern.compile( "alias *(\\(( *[^\\(\\) ]+ *)\\))* *([^= ]+) *= *\\((.*)\\) *" );
            matcher = pattern.matcher( line );
            if ( matcher.find() ) {
                if ( matcher.group( 3 ).indexOf( "." ) > -1 ) {
                    throw new SpecParseException( "Alias " + matcher.group( 3 ) + " cannot be declared with compound name" );
                }
                String returnLine = matcher.group( 3 ) + ":" + matcher.group( 4 ) + ":"
                        + ( matcher.group( 2 ) == null || matcher.group( 2 ).equals( "null" ) ? "" : matcher.group( 2 ) );
                return new LineType( LineType.TYPE_ALIAS, returnLine, line );
            }
            // allow empty alias declaration e.g. "alias x;"
            pattern = Pattern.compile( "alias *(\\(( *[^\\(\\) ]+ *)\\))* *([^= ]+) *" );
            matcher = pattern.matcher( line );
            if ( matcher.find() ) {
                if ( matcher.group( 3 ).indexOf( "." ) > -1 ) {
                    throw new SpecParseException( "Alias " + matcher.group( 3 ) + " cannot be declared with compound name" );
                }
                String returnLine = matcher.group( 3 ) + "::"
                        + ( matcher.group( 2 ) == null || matcher.group( 2 ).equals( "null" ) ? "" : matcher.group( 2 ) );
                return new LineType( LineType.TYPE_ALIAS, returnLine, line );
            }

            return new LineType( LineType.TYPE_ERROR, line, line );

        } else if ( line.indexOf( "super" ) >= 0 ) {
            pattern = Pattern.compile( "super#([^ .]+)" );
            matcher = pattern.matcher( line );
            if ( matcher.find() ) {
                String returnLine = matcher.group( 1 );

                return new LineType( LineType.TYPE_SUPERCLASSES, returnLine, line );
            }
            return new LineType( LineType.TYPE_ERROR, line, line );

        } else if ( line.trim().startsWith( "const" ) ) {
            pattern = Pattern
                    .compile( " *([a-zA-Z_$][0-9a-zA-Z_$]*[\\[\\]]*) +([a-zA-Z_$][0-9a-zA-Z_$]*) *= *([a-zA-Z0-9.{}\"]+|new [a-zA-Z0-9.{}\\[\\]]+) *" );
            matcher = pattern.matcher( line );
            if ( matcher.find() ) {
                return new LineType( LineType.TYPE_CONST,
                        matcher.group( 1 ) + ":" + matcher.group( 2 ) + ":" + matcher.group( 3 ), line );
            }
            return new LineType( LineType.TYPE_ERROR, line, line );

        } else if ( line.indexOf( "=" ) >= 0 ) { // Extract on solve
                                                    // equations
            // lets check if it's an alias
            pattern = Pattern.compile( " *([^= ]+) *= *\\[(.*)\\] *$" );
            matcher = pattern.matcher( line );
            if ( matcher.find() ) {
                // TODO here is no check against existance of alias we try to
                // use
                // "true" means that this is an assignment, not declaration
                String returnLine = matcher.group( 1 ) + ":" + matcher.group( 2 ) + "::true";

                return new LineType( LineType.TYPE_ALIAS, returnLine, line );
            }

            pattern = Pattern.compile( " *([^= ]+) *= *((\".*\")|(new .*\\(.*\\))|(\\{.*\\})|(true)|(false)) *$" );
            matcher = pattern.matcher( line );
            if ( matcher.find() ) {
                return new LineType( LineType.TYPE_ASSIGNMENT, matcher.group( 1 ) + ":" + matcher.group( 2 ), line );
            }
            pattern = Pattern.compile( " *([^=]+) *= *([-_0-9a-zA-Z.()\\+\\*/^ ]+) *$" );
            matcher = pattern.matcher( line );
            if ( matcher.find() ) {
                return new LineType( LineType.TYPE_EQUATION, line, line );
            }
            return new LineType( LineType.TYPE_ERROR, line, line );

        } else if ( line.indexOf( "->" ) >= 0 ) {
            pattern = Pattern.compile( "(.*) *-> *(.+) *\\{(.+)\\}" );
            matcher = pattern.matcher( line );
            if ( matcher.find() ) {
                return new LineType( LineType.TYPE_AXIOM, line, line );
            }
            pattern = Pattern.compile( "(.*) *-> *([ -_a-zA-Z0-9.,]+) *$" );
            matcher = pattern.matcher( line );
            if ( matcher.find() ) {
                return new LineType( LineType.TYPE_SPECAXIOM, line, line );

            }
            return new LineType( LineType.TYPE_ERROR, line, line );
        } else {
            pattern = Pattern
                    .compile( "^ *(static )?([a-zA-Z_$][0-9a-zA-Z_$]*(\\[\\])*) (([a-zA-Z_$][0-9a-zA-Z_$]* ?, ?)* ?[a-zA-Z_$][0-9a-zA-Z_$]* ?$)" );
            matcher = pattern.matcher( line );
            if ( matcher.find() ) {
                return new LineType( LineType.TYPE_DECLARATION, matcher.group( 2 ) + ":" + matcher.group( 4 ) + ":"
                        + ( matcher.group( 1 ) != null ), // true if static
                        line );
            }
            return new LineType( LineType.TYPE_ERROR, line, line );
        }
    }

    /**
     * Extracts the specification from the java file, also removing unnecessary
     * whitespaces
     * 
     * @return specification text
     * @param fileString a (Java) file containing the specification
     */
    private static String refineSpec( String fileString ) throws IOException {
        Matcher matcher;
        Pattern pattern;

        // remove comments before removing line brake \n
        String[] s = fileString.split( "\n" );
        fileString = "";
        for ( int i = 0; i < s.length; i++ ) {
            if ( !s[ i ].trim().startsWith( "//" ) ) {
                fileString += s[ i ];
            }
        }

        // remove unneeded whitespace
        pattern = Pattern.compile( "[ \r\t\n]+" );
        matcher = pattern.matcher( fileString );
        fileString = matcher.replaceAll( " " );

        // find spec
        pattern = Pattern.compile( // "[ ]+(super [ a-zA-Z_0-9-,]+ )? "
                ".*/\\*@.*specification [a-zA-Z_0-9-.]+ ?(super ([ a-zA-Z_0-9-,]+ ))? ?\\{ ?(.+) ?\\} ?@\\*/ ?" );
        matcher = pattern.matcher( fileString );
        if ( matcher.find() ) {
            String sc = "";
            if ( matcher.group( 2 ) != null ) {
                sc += "super";
                String[] superclasses = matcher.group( 2 ).split( "," );
                for ( int i = 0; i < superclasses.length; i++ ) {
                    String t = superclasses[ i ].trim();
                    if ( t.length() > 0 )
                        sc += "#" + t;
                }
                sc += ";\n";
            }
            fileString = sc + matcher.group( 3 );
        }
        return fileString;
    }

    private static ArrayList<String> s_parseErrors = new ArrayList<String>();

    public static ClassList parseSpecification( String fullSpec, String mainClassName, Set<String> schemeObjects, String path )
            throws IOException, SpecParseException, EquationException {
        s_parseErrors.clear();
        long start = System.currentTimeMillis();
        
        ClassList classes = parseSpecificationImpl( refineSpec( fullSpec ), TYPE_THIS, schemeObjects, path,
                new LinkedHashSet<String>() );

        if ( RuntimeProperties.isLogInfoEnabled() )
            db.p( "Specification parsed in: " + ( System.currentTimeMillis() - start ) + "ms." );
        
        /* ****** SPEC_OBJECT_NAME for scheme spec ? ****** */
        // AnnotatedClass _this = classes.getType( TYPE_THIS );
        //    	
        // String meth = AnnotatedClass.SPEC_OBJECT_NAME + " = " + "\"" +
        // mainClassName + "\"";
        //    	
        // ClassRelation classRelation = new ClassRelation(
        // RelType.TYPE_EQUATION, meth );
        //
        // classRelation.getOutputs().add( _this.getFieldByName(
        // AnnotatedClass.SPEC_OBJECT_NAME ) );
        // classRelation.setMethod( meth );
        // _this.addClassRelation( classRelation );
        /* ****** SPEC_OBJECT_NAME ****** */

        return classes;
    }

    /**
     * A recrusve method that does the actual parsing. It creates a list of
     * annotated classes that carry infomation about the fields and relations in
     * a class specification.
     * 
     * @param spec a specfication to be parsed. If it includes a declaration of
     *                an annotated class, it will be recursively parsed.
     * @param className the name of the class being parsed
     * @param parent
     * @param checkedClasses the list of classes that parser has started to
     *                check. Needed to prevent infinite loop in case of mutual
     *                declarations.
     */
    private static ClassList parseSpecificationImpl( String spec, String className, Set<String> schemeObjects, String path,
            Set<String> checkedClasses ) throws IOException, SpecParseException, EquationException {
        Matcher matcher;
        Pattern pattern;
        String[] split;
        AnnotatedClass annClass = new AnnotatedClass( className );

        /* ****** SPEC_OBJECT_NAME ****** */
        ClassField specObjectName = new ClassField( AnnotatedClass.SPEC_OBJECT_NAME, "String" );
        annClass.addField( specObjectName );
        /* ****** SPEC_OBJECT_NAME ****** */

        ClassList classList = new ClassList();

        ArrayList<String> specLines = getSpec( spec, true );

        LineType lt = null;
        
        try {

            while ( !specLines.isEmpty() ) {

                if ( ( lt = getLine( specLines ) ) != null ) {

                    if ( RuntimeProperties.isLogDebugEnabled() )
                        db.p( "Parsing: Class " + className + " " + lt );

                    if ( lt.getType() == LineType.TYPE_SUPERCLASSES ) {
                        split = lt.getSpecLine().split( "#", -1 );

                        for ( int i = 0; i < split.length; i++ ) {
                            String name = split[ i ];

                            if ( checkSpecClass( className, path, checkedClasses, classList, name ) ) {

                                AnnotatedClass superClass = classList.getType( name );

                                annClass.addSuperClass( superClass );
                            } else {
                                throw new SpecParseException( "Unable to parse superclass " + name + " of " + className );
                            }

                        }
                    } else if ( lt.getType() == LineType.TYPE_ASSIGNMENT ) {
                        split = lt.getSpecLine().split( ":", -1 );
                        ClassRelation classRelation = new ClassRelation( RelType.TYPE_EQUATION, lt.getOrigSpecLine() );

                        classRelation.addOutput( split[ 0 ], annClass.getFields() );
                        classRelation.setMethod( split[ 0 ] + " = " + split[ 1 ] );
                        checkAnyType( split[ 0 ], split[ 1 ], annClass.getFields() );
                        annClass.addClassRelation( classRelation );
                        if ( RuntimeProperties.isLogDebugEnabled() )
                            db.p( classRelation );

                    } else if ( lt.getType() == LineType.TYPE_CONST ) {
                        split = lt.getSpecLine().split( ":", -1 );
                        String type = split[ 0 ].trim();
                        String name = split[ 1 ].trim();
                        String value = split[ 2 ].trim();

                        if ( containsVar( annClass.getFields(), name ) ) {
                            s_parseErrors.add( "Variable " + name + " declared more than once in class " + className );

                            throw new SpecParseException( "Variable " + name + " declared more than once in class " + className );
                        }

                        File file = new File( path + type + ".java" );
                        if ( file.exists() && isSpecClass( path, type ) ) {
                            s_parseErrors.add( "Constant " + name + " cannot be of type " + type );
                            throw new SpecParseException( "Constant " + name + " cannot be of type " + type );
                        }
                        if ( RuntimeProperties.isLogDebugEnabled() )
                            db.p( "---===!!! " + type + " " + name + " = " + value );

                        ClassField var = new ClassField( name, type, value, true );

                        annClass.addField( var );

                    } else if ( lt.getType() == LineType.TYPE_DECLARATION ) {
                        split = lt.getSpecLine().split( ":", -1 );
                        String[] vs = split[ 1 ].trim().split( " *, *", -1 );
                        String type = split[ 0 ].trim();
                        boolean isStatic = Boolean.parseBoolean( split[ 2 ] );

                        boolean specClass = checkSpecClass( className, path, checkedClasses, classList, type );

                        for ( int i = 0; i < vs.length; i++ ) {
                            if ( containsVar( annClass.getFields(), vs[ i ] ) ) {
                                s_parseErrors.add( "Variable " + vs[ i ] + " declared more than once in class " + className );
                                throw new SpecParseException( "Variable " + vs[ i ] + " declared more than once in class "
                                        + className );
                            }
                            ClassField var = new ClassField( vs[ i ], type, specClass );
                            var.setStatic( isStatic );

                            /* ****** SPEC_OBJECT_NAME ****** */
                            // add the following relation only if the object
                            // exists on a given scheme
                            if ( schemeObjects != null && specClass && ( className == TYPE_THIS )
                                    && schemeObjects.contains( vs[ i ] ) ) {
                                String s = vs[ i ] + "." + AnnotatedClass.SPEC_OBJECT_NAME;
                                String meth = s + " = " + "\"" + vs[ i ] + "\"";

                                ClassRelation classRelation = new ClassRelation( RelType.TYPE_EQUATION, meth );

                                classRelation.addOutput( s, annClass.getFields() );
                                classRelation.setMethod( meth );
                                annClass.addClassRelation( classRelation );
                            }
                            /* ****** SPEC_OBJECT_NAME ****** */

                            annClass.addField( var );
                        }

                    } else if ( lt.getType() == LineType.TYPE_ALIAS ) {
                        split = lt.getSpecLine().split( ":", -1 );
                        String name = split[ 0 ];

                        Alias alias = null;
                        // required for two-line alias declaration, mostly for
                        // multiport feature
                        Alias aliasDeclaration = null;

                        if ( split[ 1 ].trim().equals( "" ) ) {
                            // if there are no variables on the rhs, mark alias
                            // as empty
                            if ( !containsVar( annClass.getFields(), name ) ) {
                                alias = new Alias( name, split[ 2 ].trim() );
                                alias.setDeclaration( true );
                                annClass.addField( alias );
                                continue;
                            }
                        }

                        String[] list = split[ 1 ].trim().split( " *, *", -1 );

                        ClassField var = getVar( name, annClass.getFields() );

                        if ( var != null && !var.isAlias() ) {
                            s_parseErrors.add( "Variable " + name + " declared more than once in class " + className );
                            throw new SpecParseException( "Variable " + name + " declared more than once in class " + className );
                        } else if ( var != null && var.isAlias() ) {
                            alias = (Alias) var;
                            if ( !alias.isDeclaration() ) {
                                throw new SpecParseException( "Alias " + name + " cannot be overriden, class " + className );
                            }
                        } else if ( split.length > 3 && Boolean.parseBoolean( split[ 3 ] ) ) {
                            // if its an assignment, check if alias has already
                            // been declared
                            try {
                                if ( ( name.indexOf( "." ) == -1 ) && !containsVar( annClass.getFields(), name ) ) {
                                    throw new UnknownVariableException( "Alias " + name + " not declared", lt.getOrigSpecLine() );

                                } else if ( name.indexOf( "." ) > -1 ) {
                                    // here we have to dig deeply
                                    int ind = name.indexOf( "." );

                                    String parent = name.substring( 0, ind );
                                    String leftFromName = name.substring( ind + 1, name.length() );

                                    ClassField parentVar = getVar( parent, annClass.getFields() );
                                    String parentType = parentVar.getType();

                                    AnnotatedClass parentClass = classList.getType( parentType );

                                    while ( leftFromName.indexOf( "." ) > -1 ) {

                                        ind = leftFromName.indexOf( "." );
                                        parent = leftFromName.substring( 0, ind );
                                        leftFromName = leftFromName.substring( ind + 1, leftFromName.length() );

                                        parentVar = parentClass.getFieldByName( parent );

                                        parentType = parentVar.getType();
                                        parentClass = classList.getType( parentType );
                                    }

                                    if ( !parentClass.hasField( leftFromName ) ) {
                                        throw new UnknownVariableException( "Variable " + leftFromName
                                                + " is not declared in class " + parentClass, lt.getOrigSpecLine() );
                                    }

                                    aliasDeclaration = (Alias) parentClass.getFieldByName( leftFromName );

                                    // if everything is ok, create alias
                                    alias = new Alias( name, aliasDeclaration.getVarType() );

                                }
                            } catch ( Exception e ) {
                                throw new SpecParseException( "Alias " + name + " is not declared, class " + className
                                        + ( e.getMessage() != null ? "\n" + e.getMessage() : "" ) );
                            }
                        } else {
                            alias = new Alias( name, split[ 2 ].trim() );
                        }

                        alias.addAll( list, annClass.getFields(), classList );
                        annClass.addField( alias );
                        ClassRelation classRelation = new ClassRelation( RelType.TYPE_ALIAS, lt.getOrigSpecLine() );

                        classRelation.addInputs( list, annClass.getFields() );
                        classRelation.setMethod( TypeUtil.TYPE_ALIAS );
                        classRelation.addOutput( name, annClass.getFields() );
                        annClass.addClassRelation( classRelation );

                        if ( RuntimeProperties.isLogDebugEnabled() )
                            db.p( classRelation );

                        if ( !alias.isWildcard() ) {
                            classRelation = new ClassRelation( RelType.TYPE_ALIAS, lt.getOrigSpecLine() );
                            classRelation.addOutputs( list, annClass.getFields() );
                            classRelation.setMethod( TypeUtil.TYPE_ALIAS );
                            classRelation.addInput( name, annClass.getFields() );
                            annClass.addClassRelation( classRelation );
                            if ( RuntimeProperties.isLogDebugEnabled() )
                                db.p( classRelation );
                        }

                        if ( aliasDeclaration != null ) {
                            aliasDeclaration.setDeclaredValues( alias );
                        }

                    } else if ( lt.getType() == LineType.TYPE_EQUATION ) {
                        EquationSolver.solve( lt.getSpecLine() );
                        next: for ( Relation rel : EquationSolver.getRelations() ) {
                            if ( RuntimeProperties.isLogDebugEnabled() )
                                db.p( "equation: " + rel );
                            String[] pieces = rel.getRel().split( ":" );
                            String method = rel.getExp();
                            String out = pieces[ 2 ].trim();

                            // cannot assign new values for constants
                            ClassField tmp = getVar( checkAliasLength( out, annClass, className ), annClass.getFields() );
                            if ( tmp != null && tmp.isConstant() ) {
                                db.p( "Ignoring constant and equation output: " + tmp );
                                continue;
                            }
                            // if one variable is used on both sides of "=", we
                            // cannot use such relation.
                            String[] inputs = pieces[ 1 ].trim().split( " " );
                            for ( int j = 0; j < inputs.length; j++ ) {
                                if ( inputs[ j ].equals( out ) ) {
                                    if ( RuntimeProperties.isLogDebugEnabled() )
                                        db.p( " - unable use this equation because variable " + out
                                                + " appears on both sides of =" );
                                    continue next;
                                }
                            }

                            ClassRelation classRelation = new ClassRelation( RelType.TYPE_EQUATION, lt.getOrigSpecLine() );

                            classRelation.addOutput( out, annClass.getFields() );

                            // checkAliasLength( inputs, annClass.getFields(), className );
                            for ( int i = 0; i < inputs.length; i++ ) {
                                String initial = inputs[ i ];
                                inputs[ i ] = checkAliasLength( inputs[ i ], annClass, className );
                                String name = inputs[ i ];
                                if ( name.startsWith( "*" ) ) {
                                    name = inputs[ i ].substring( 1 );
                                }
                                method = method.replaceAll( "\\$" + initial + "\\$", name );
                            }
                            method = method.replaceAll( "\\$" + out + "\\$", out );

                            checkAnyType( out, inputs, annClass.getFields() );

                            if ( !inputs[ 0 ].equals( "" ) ) {
                                classRelation.addInputs( inputs, annClass.getFields() );
                            }
                            classRelation.setMethod( method );
                            annClass.addClassRelation( classRelation );
                            if ( RuntimeProperties.isLogDebugEnabled() )
                                db.p( "Equation: " + classRelation );

                        }
                    } else if ( lt.getType() == LineType.TYPE_AXIOM ) {
                        List<String> subtasks = new ArrayList<String>();

                        pattern = Pattern.compile( "\\[([^\\]\\[]*) *-> *([^\\]\\[]*)\\]" );
                        matcher = pattern.matcher( lt.getSpecLine() );

                        while ( matcher.find() ) {
                            if ( RuntimeProperties.isLogDebugEnabled() )
                                db.p( "matching " + matcher.group( 0 ) );
                            subtasks.add( matcher.group( 0 ) );
                        }
                        lt = new LineType( lt.getType(), lt.getSpecLine()
                                .replaceAll( "\\[([^\\]\\[]*) *-> *([^\\]\\[]*)\\]", "#" ), lt.getOrigSpecLine() );

                        pattern = Pattern.compile( "(.*) *-> ?(.*)\\{(.*)\\}" );
                        matcher = pattern.matcher( lt.getSpecLine() );
                        if ( matcher.find() ) {

                            String[] outputs = matcher.group( 2 ).trim().split( " *, *", -1 );

                            if ( !outputs[ 0 ].equals( "" ) ) {
                                if ( outputs[ 0 ].indexOf( "*" ) >= 0 ) {
                                    getWildCards( classList, outputs[ 0 ] );
                                }

                            }
                            ClassRelation classRelation = new ClassRelation( RelType.TYPE_JAVAMETHOD, lt.getOrigSpecLine() );

                            if ( matcher.group( 2 ).trim().equals( "" ) ) {
                                s_parseErrors.add( "Error in line \n" + lt.getOrigSpecLine() + "\nin class " + className
                                        + ".\nAn axiom can not have an empty output." );
                                throw new SpecParseException( "Error in line \n" + lt.getOrigSpecLine() + "\nin class "
                                        + className + ".\nAn axiom can not have an empty output." );
                            }

                            if ( !outputs[ 0 ].equals( "" ) ) {
                                classRelation.addOutputs( outputs, annClass.getFields() );
                            }

                            classRelation.setMethod( matcher.group( 3 ).trim() );

                            if( Table.TABLE_KEYWORD.equals( classRelation.getMethod() ) ) {
                                classRelation.getExceptions().clear();
                                classRelation.getExceptions().add( new ClassField( "java.lang.Exception", "exception" ) );
                            }
                                
                            String[] inputs = matcher.group( 1 ).trim().split( " *, *", -1 );

                            checkAliasLength( inputs, annClass, className );

                            if ( !inputs[ 0 ].equals( "" ) ) {
                                classRelation.addInputs( inputs, annClass.getFields() );
                            }
                            if ( subtasks.size() != 0 ) {

                                for ( String subtaskString : subtasks ) {
                                    Collection<ClassField> varsForSubtask = annClass.getFields();
                                    pattern = Pattern.compile( "\\[ *(([a-zA-Z_$][0-9a-zA-Z_$]*) *\\|-)? *(.*) *-> ?(.*)\\]" );
                                    matcher = pattern.matcher( subtaskString );
                                    if ( matcher.find() ) {
                                        SubtaskClassRelation subtask;

                                        String context = matcher.group( 2 );
                                        // this denotes independant subtask,
                                        // have to make sure that this class has
                                        // already been parsed
                                        if ( context != null ) {
                                            if ( !checkSpecClass( className, path, checkedClasses, classList, context ) ) {
                                                throw new SpecParseException(
                                                        "Unable to parse independent subtask's context specification "
                                                                + subtaskString );
                                            }
                                            varsForSubtask = classList.getType( context ).getFields();

                                            ClassField contextCF = new ClassField( "_" + context.toLowerCase(), context );

                                            subtask = SubtaskClassRelation.createIndependentSubtask( subtaskString, contextCF );
                                        } else {
                                            subtask = SubtaskClassRelation.createDependentSubtask( subtaskString );
                                        }

                                        inputs = matcher.group( 3 ).trim().split( " *, *", -1 );
                                        outputs = matcher.group( 4 ).trim().split( " *, *", -1 );

                                        for ( int j = 0; j < outputs.length; j++ ) {
                                            subtask.addOutput( outputs[ j ], varsForSubtask );
                                        }

                                        if ( !inputs[ 0 ].equals( "" ) ) {
                                            subtask.addInputs( inputs, varsForSubtask );
                                        }
                                        classRelation.addSubtask( subtask );
                                    }
                                }
                                classRelation.setType( RelType.TYPE_METHOD_WITH_SUBTASK );
                            }

                            if ( RuntimeProperties.isLogDebugEnabled() )
                                db.p( classRelation );

                            annClass.addClassRelation( classRelation );
                        }

                    } else if ( lt.getType() == LineType.TYPE_SPECAXIOM ) {
                        pattern = Pattern.compile( "(.*) *-> *([ -_a-zA-Z0-9.,]+) *$" );
                        matcher = pattern.matcher( lt.getSpecLine() );
                        if ( matcher.find() ) {
                            ClassRelation classRelation = new ClassRelation( RelType.TYPE_UNIMPLEMENTED, lt.getOrigSpecLine() );
                            String[] outputs = matcher.group( 2 ).trim().split( " *, *", -1 );

                            if ( !outputs[ 0 ].equals( "" ) ) {
                                classRelation.addOutputs( outputs, annClass.getFields() );
                            }

                            String[] inputs = matcher.group( 1 ).trim().split( " *, *", -1 );

                            if ( !inputs[ 0 ].equals( "" ) ) {
                                classRelation.addInputs( inputs, annClass.getFields() );
                            }
                            if ( RuntimeProperties.isLogDebugEnabled() )
                                db.p( classRelation );
                            annClass.addClassRelation( classRelation );
                        }
                    } else if ( lt.getType() == LineType.TYPE_ERROR ) {
                        throw new LineErrorException( lt.getOrigSpecLine() );
                    }
                }
            }
        } catch ( UnknownVariableException uve ) {

            String line = uve.getLine() != null ? uve.getLine() : lt != null ? lt.getOrigSpecLine() : null;
            throw new UnknownVariableException( className + "." + uve.excDesc, line );

        }
        classList.add( annClass );
        return classList;
    }

    /**
     * @param className
     * @param path
     * @param checkedClasses
     * @param classList
     * @param type
     * @return
     * @throws MutualDeclarationException
     * @throws IOException
     * @throws SpecParseException
     * @throws EquationException
     */
    private static boolean checkSpecClass( String className, String path, Set<String> checkedClasses, ClassList classList,
            String type ) throws MutualDeclarationException, IOException, SpecParseException, EquationException {
        if ( RuntimeProperties.isLogDebugEnabled() )
            db.p( "Checking existence of " + path + type + ".java" );
        if ( checkedClasses.contains( type ) ) {
        	if( RuntimeProperties.isRecursiveSpecsAllowed() ) {
        		return true;
        	}
            throw new MutualDeclarationException( className + " <-> " + type );
        } else if ( classList.getType( type ) != null ) {
            // do not need to parse already parsed class again
            return true;
        }
        File file = new File( path + type + ".java" );
        boolean specClass = false;

        // if a file by this name exists in the package directory and it
        // includes a specification, we're gonna check it
        if ( file.exists() && isSpecClass( path, type ) ) {
            specClass = true;
            if ( !classList.containsType( type ) ) {
                checkedClasses.add( type );
                String s = FileFuncs.getFileContents(file);

                classList.addAll( parseSpecificationImpl( refineSpec( s ), type, null, path, checkedClasses ) );
                checkedClasses.remove( type );
            }
        }
        return specClass;
    }

    private static void checkAnyType( String output, String input, Collection<ClassField> vars ) throws UnknownVariableException {
        checkAnyType( output, new String[] { input }, vars );
    }

    // TODO - implement _any_!!!
    private static void checkAnyType( String output, String[] inputs, Collection<ClassField> vars )
            throws UnknownVariableException {
        ClassField out = getVar( output, vars );

        if ( out == null || !out.getType().equals( TYPE_ANY ) ) {
            return;
        }

        String newType = TYPE_ANY;

        for ( int i = 0; i < inputs.length; i++ ) {
            ClassField in = getVar( inputs[ i ], vars );

            if ( in == null ) {
                try {
                    Integer.parseInt( inputs[ i ] );
                    newType = TYPE_INT;
                    continue;
                } catch ( NumberFormatException ex ) {
                }

                try {
                    Double.parseDouble( inputs[ i ] );
                    newType = TYPE_DOUBLE;
                    continue;
                } catch ( NumberFormatException ex ) {
                }

                if ( inputs[ i ] != null && inputs[ i ].trim().equals( "" ) ) {
                    newType = TYPE_DOUBLE;// TODO - tmp
                    continue;
                }

                throw new UnknownVariableException( inputs[ i ] );
            }
            if ( i == 0 ) {
                newType = in.getType();
                continue;
            }
            TypeToken token = TypeToken.getTypeToken( newType );

            TypeToken tokenIn = TypeToken.getTypeToken( in.getType() );

            if ( token != null && tokenIn != null && token.compareTo( tokenIn ) < 0 ) {
                newType = in.getType();
            }
        }

        out.setType( newType );
    }

    private static void checkAliasLength( String inputs[], AnnotatedClass thisClass, String className )
            throws UnknownVariableException {
        for ( int i = 0; i < inputs.length; i++ ) {
            inputs[ i ] = checkAliasLength( inputs[ i ], thisClass, className );
        }
    }

    private static String checkAliasLength( String input, AnnotatedClass thisClass, String className )
            throws UnknownVariableException {
        // check if inputs contain <alias>.lenth variable
        if ( input.endsWith( ".length" ) ) {
            int index = input.lastIndexOf( ".length" );
            String aliasName = input.substring( 0, index );
            ClassField field = getVar( aliasName, thisClass.getFields() );
            if ( field != null && field.isAlias() ) {
                Alias alias = (Alias) field;
                String aliasLengthName = ( ( alias.isWildcard() ? "*" : "" ) + aliasName + "_LENGTH" );
                if ( containsVar( thisClass.getFields(), aliasLengthName ) ) {
                    return aliasLengthName;
                }

                int length = alias.getVars().size();

                ClassField var = new ClassField( aliasLengthName, TYPE_INT, "" + length, true );

                thisClass.addField( var );

                return aliasLengthName;

            }
            throw new UnknownVariableException( "Alias " + aliasName + " not found in " + className );
        }
        return input;
    }

    private static void getWildCards( ClassList classList, String output ) {
        String list[] = output.split( "\\." );
        for ( int i = 0; i < list.length; i++ ) {
            if ( RuntimeProperties.isLogDebugEnabled() )
                db.p( list[ i ] );
        }
    }

    /**
     * @return list of fields declared in a specification.
     */
    public static Collection<ClassField> getFields( String path, String fileName, String ext ) throws IOException {
        Map<String, ClassField> fields = new LinkedHashMap<String, ClassField>();
        String s = FileFuncs.getFileContents(new File(path, fileName + ext));
        ArrayList<String> specLines = getSpec( s, false );
        String[] split;

        while ( !specLines.isEmpty() ) {
            LineType lt = null;
            try {
                lt = getLine( specLines );
            } catch ( SpecParseException e ) {
                e.printStackTrace();
            }

            if ( lt != null ) {
                if ( lt.getType() == LineType.TYPE_ASSIGNMENT ) {
                    split = lt.getSpecLine().split( ":", -1 );
                    ClassField field;
                    if( ( field = fields.get( split[ 0 ] ) ) != null ) {
                        field.setValue( split[ 1 ] );
                    }
                } else if ( lt.getType() == LineType.TYPE_DECLARATION ) {
                    split = lt.getSpecLine().split( ":", -1 );
                    String[] vs = split[ 1 ].trim().split( " *, *", -1 );
                    String type = split[ 0 ].trim();

                    for ( int i = 0; i < vs.length; i++ ) {
                        ClassField var = new ClassField( vs[ i ], type );

                        fields.put( var.getName(), var );
                    }
                } else if ( lt.getType() == LineType.TYPE_ALIAS ) {
                    split = lt.getSpecLine().split( ":", -1 );
                    String name = split[ 0 ];
                    Alias alias = new Alias( name, split[ 2 ].trim() );
                    String[] list = split[ 1 ].trim().split( " *, *", -1 );
                    if( list.length > 0 ) {
                        try {
                            //TODO - probably some time it will be needed to fill the class list
                            //and this does not work for aliases with wildcards
                            alias.addAll( list, fields.values(), new ClassList() );
                            //alternative approach is to do next - 
                            //for( String var : list ) {
                            //    ClassField aliasCF = fields.get( var );
                            //    if( aliasCF != null )
                            //        alias.addVar( aliasCF );
                            //}
                        } catch ( UnknownVariableException e ) {
                        } catch ( AliasException e ) {
                        }
                    }
                    fields.put( name, alias );
                } else if ( lt.getType() == LineType.TYPE_SUPERCLASSES ) {
                    String[] superClasses = lt.getSpecLine().split( "#", -1 );

                    for ( String name : superClasses ) {
                        for( ClassField var : getFields( path, name, ext ) ) {
                            fields.put( var.getName(), var );
                        }
                    }
                }
            }
        }
        return fields.values();
    }

    private static boolean isSpecClass( String path, String file ) {
        try {
            BufferedReader in = new BufferedReader( new FileReader( path + file + ".java" ) );
            String lineString, fileString = new String();

            while ( ( lineString = in.readLine() ) != null ) {
                fileString += lineString;
            }
            in.close();
            if ( fileString.matches( ".*specification +" + file + ".*" ) ) {

                return true;
            }
        } catch ( IOException ioe ) {
            db.p( ioe );
        }
        return false;
    }

    private static boolean containsVar( Collection<ClassField> vars, String varName ) {

        return getVar( varName, vars ) != null;
    }

    /**
     * @param varName String
     * @param varList ArrayList
     * @return ClassField
     */
    static ClassField getVar( String varName, Collection<ClassField> varList ) {

        for ( ClassField var : varList ) {
            if ( var.getName().equals( varName ) ) {
                return var;
            }
        }
        return null;
    } // getVar
}
