/*
 * Copyright (c) 2004-2010, Kohsuke Kawaguchi
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this list of
 *       conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.kohsuke.stapler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.AbstractProcessor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.CompilerException;
import org.codehaus.plexus.compiler.javac.JavacCompiler;
import org.kohsuke.stapler.processor.ExportedBeanAnnotationProcessor;

/**
 * {@link Compiler} for APT.
 *
 * <p>
 * In Maven, {@link Compiler} handles the actual compiler invocation.
 *
 * @author Kohsuke Kawaguchi
 */
public class AptCompiler extends JavacCompiler {

    public List compile( CompilerConfiguration config ) throws CompilerException {
        // force 1.5
        config.setTargetVersion("1.5");
        config.setSourceVersion("1.5");


        File destinationDir = new File( config.getOutputLocation() );

        if ( !destinationDir.exists() )
        {
            destinationDir.mkdirs();
        }

        String[] sourceFiles = getSourceFiles( config );

        if ( sourceFiles.length == 0 )
        {
            return Collections.EMPTY_LIST;
        }

        getLogger().info( "Compiling " + sourceFiles.length + " " +
                          "source file" + ( sourceFiles.length == 1 ? "" : "s" ) +
                          " to " + destinationDir.getAbsolutePath() );

//        if (config.isFork()) {
//            // forking a compiler requires classpath set up and passing AnnotationProcessorFactory.
//            config.addClasspathEntry(whichJar(AnnotationProcessorFactoryImpl.class));
//            config.addCompilerCustomArgument("-factory",AnnotationProcessorFactoryImpl.class.getName());
//        }

        // this is where the META-INF/services get generated.
        config.addCompilerCustomArgument("-s",new File(config.getOutputLocation()).getAbsolutePath());
//        String[] args = buildCompilerArguments( config, sourceFiles );
        List<String> options = buildCompilerOptions(config, sourceFiles);

//        if (config.isFork()) {
//            String executable = config.getExecutable();
//            if (StringUtils.isEmpty(executable)) {
//                File apt = new File(new File(System.getProperty("java.home")),"bin/apt"); // Mac puts $JAVA_HOME to JDK
//                if (!apt.exists())
//                    // on other platforms $JAVA_HOME is JRE in JDK.
//                    apt = new File(new File(System.getProperty("java.home")),"../bin/apt");
//                executable = apt.getAbsolutePath();
//            }
//            return compileOutOfProcess(config, executable, args);
//        } else {

            return compileInProcess(options, Arrays.asList(sourceFiles), destinationDir.getAbsolutePath());
//        }
    }

    private static List<String> buildCompilerOptions(CompilerConfiguration config, String[] sourceFiles) {
        List<String> args = new ArrayList<String>();
        File destinationDir = new File(config.getOutputLocation());
        args.add("-d");
        args.add(destinationDir.getAbsolutePath());

        List classpathEntries = config.getClasspathEntries();
        if (classpathEntries != null && !classpathEntries.isEmpty()) {
            args.add("-classpath");
            args.add(getPathString(classpathEntries));
        }

        List sourceLocations = config.getSourceLocations();
        if (sourceLocations != null && !sourceLocations.isEmpty() && (sourceFiles.length == 0)) {
            args.add("-sourcepath");
            args.add(getPathString(sourceLocations));
        }

        if (config.isOptimize()) {
            args.add("-O");
        }

        if (config.isDebug()) {
            args.add("-g");
        }

        if (config.isVerbose()) {
            args.add("-verbose");
        }

        if (config.isShowDeprecation()) {
            args.add("-deprecation");
            // This is required to actually display the deprecation messages
            config.setShowWarnings(true);
        }

        if (!config.isShowWarnings()) {
            args.add("-nowarn");
        }

        args.add("-target");
        args.add(org.codehaus.plexus.util.StringUtils.isEmpty(config.getTargetVersion()) ? "1.5"
            : config.getTargetVersion());

        args.add("-source");
        args.add(org.codehaus.plexus.util.StringUtils.isEmpty(config.getSourceVersion()) ? "1.5"
            : config.getSourceVersion());

        for (Iterator it = config.getCustomCompilerArguments().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry) it.next();
            String key = (String) entry.getKey();
            if (org.codehaus.plexus.util.StringUtils.isEmpty(key)) {
                continue;
            }
            args.add(key);
            String value = (String) entry.getValue();
            if (org.codehaus.plexus.util.StringUtils.isEmpty(value)) {
                continue;
            }
            args.add(value);
        }
        return args;
    }


//    private String whichJar(Class c) throws CompilerException {
//        try {
//            String url = c.getClassLoader().getResource(c.getName().replace('.', '/') + ".class").toExternalForm();
//            if (url.startsWith("jar:")) {
//                url = url.substring(4);
//                url = url.substring(0,url.indexOf('!'));
//                return new URL(url).getPath();
//            }
//            throw new CompilerException("Failed to infer classpath for "+c);
//        } catch (MalformedURLException e) {
//            throw new CompilerException("Failed to infer classpath for "+c,e);
//        }
//    }

    /**
     * Compile the java sources in the current JVM, without calling an external executable,
     * using <code>com.sun.tools.javac.Main</code> class
     *
     * @param options arguments for the compiler as they would be used in the command line javac
     * @return List of CompilerError objects with the errors encountered.
     * @throws CompilerException
     */
    protected List compileInProcess(List<String> options, List<String> sourceFiles, String destinationAbsolutePath)
        throws CompilerException {
//        com.sun.tools.apt.Main aptTool = new com.sun.tools.apt.Main();
//        int r = aptTool.process(new AnnotationProcessorFactoryImpl(),
//            new PrintWriter(System.out,true),args);
        List<String> compilationErrors = new ArrayList<String>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.
            getJavaFileObjectsFromStrings(sourceFiles);
        JavaCompiler.CompilationTask task = compiler.getTask(new PrintWriter(System.out, true), fileManager, null,
            options, null, compilationUnits);
        LinkedList<AbstractProcessor> processors = new LinkedList<AbstractProcessor>();
        ExportedBeanAnnotationProcessor processor = new ExportedBeanAnnotationProcessor();
        processor.setDestinationPath(destinationAbsolutePath);
        processors.add(processor);
        task.setProcessors(processors);
        Boolean success = task.call();
        for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
            String error = String.format("Error in source %s on line %d position %d kind %s message %s",
                diagnostic.getSource(), diagnostic.getLineNumber(), diagnostic.getPosition(), diagnostic.getKind(),
                diagnostic.getMessage(null));
            System.out.println(error);
            compilationErrors.add(error);
        }
        try {
            fileManager.close();
        } catch (IOException ignore) {
        }
        if (!success) {
            throw new CompilerException("APT failed");
        }
        return compilationErrors;
    }

/*
    protected List compileOutOfProcess(CompilerConfiguration config, String executable, String[] args)
            throws CompilerException {
        Commandline cli = new Commandline();

        cli.setWorkingDirectory(config.getWorkingDirectory().getAbsolutePath());

        cli.setExecutable(executable);

        try {
            File argumentsFile = createFileWithArguments(args);
            cli.addArguments(new String[]{"@" + argumentsFile.getCanonicalPath().replace(File.separatorChar, '/')});

            if (!StringUtils.isEmpty(config.getMaxmem())) {
                cli.addArguments(new String[]{"-J-Xmx" + config.getMaxmem()});
            }

            if (!StringUtils.isEmpty(config.getMeminitial())) {
                cli.addArguments(new String[]{"-J-Xms" + config.getMeminitial()});
            }
        }
        catch (IOException e) {
            throw new CompilerException("Error creating file with javac arguments", e);
        }

        CommandLineUtils.StringStreamConsumer out = new CommandLineUtils.StringStreamConsumer();

        CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();

        int returnCode;

        List<CompilerError> messages;

        try {
            returnCode = CommandLineUtils.executeCommandLine(cli, out, err);

            messages = parseModernStream(new BufferedReader(new StringReader(err.getOutput())));
        }
        catch (CommandLineException e) {
            throw new CompilerException("Error while executing the external compiler.", e);
        }
        catch (IOException e) {
            throw new CompilerException("Error while executing the external compiler.", e);
        }

        if (returnCode != 0 && messages.isEmpty()) {
            if (err.getOutput().length() == 0) {
                throw new CompilerException("Unknown error trying to execute the external compiler: " + EOL
                        + cli.toString());
            } else {
                messages.add(new CompilerError("Failure executing javac,  but could not parse the error:" + EOL
                        + err.getOutput(), true));
            }
        }

        return messages;
    }
*/

//    private File createFileWithArguments(String[] args) throws IOException {
//        PrintWriter writer = null;
//        try {
//            File tempFile = File.createTempFile(JavacCompiler.class.getName(), "arguments");
//            tempFile.deleteOnExit();
//
//            writer = new PrintWriter(new FileWriter(tempFile));
//
//            for (int i = 0; i < args.length; i++) {
//                String argValue = args[i].replace(File.separatorChar, '/');
//
//                writer.write("\"" + argValue + "\"");
//
//                writer.write(EOL);
//            }
//
//            writer.flush();
//
//            return tempFile;
//
//        }
//        finally {
//            if (writer != null) {
//                writer.close();
//            }
//        }
//    }
}
