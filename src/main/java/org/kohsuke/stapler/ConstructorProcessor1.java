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

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Filer.Location;
import com.sun.mirror.declaration.ClassDeclaration;
import com.sun.mirror.declaration.ConstructorDeclaration;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Handles {@link DataBoundConstructor} annotation and captures parameter names.
 * 
 * @author Kohsuke Kawaguchi
 */
public class ConstructorProcessor1 /*implements AnnotationProcessor*/ {
    private final AnnotationProcessorEnvironment env;

    public ConstructorProcessor1(AnnotationProcessorEnvironment env) {
        this.env = env;
    }

    public void process() {
        try {
            for( TypeDeclaration d : env.getTypeDeclarations() ) {
                if(!(d instanceof ClassDeclaration))    continue;

                ClassDeclaration cd = (ClassDeclaration) d;
                for( ConstructorDeclaration c : cd.getConstructors()) {
                    if(c.getAnnotation(DataBoundConstructor.class)!=null) {
                        write(c);
                        continue;
                    }
                    String javadoc = c.getDocComment();
                    if(javadoc!=null && javadoc.contains("@stapler-constructor")) {
                        write(c);
                    }
                }
            }
        } catch (IOException e) {
            env.getMessager().printError(e.getMessage());
        }
    }

    private void write(ConstructorDeclaration c) throws IOException {
        StringBuffer buf = new StringBuffer();
        for( ParameterDeclaration p : c.getParameters() ) {
            if(buf.length()>0)  buf.append(',');
            buf.append(p.getSimpleName());
        }

        File f = new File(c.getDeclaringType().getQualifiedName().replace('.', '/') + ".stapler");
        env.getMessager().printNotice("Generating "+f);
        OutputStream os = env.getFiler().createBinaryFile(Location.CLASS_TREE,"", f);

        Properties p = new Properties();
        p.put("constructor",buf.toString());
        p.store(os,null);
        os.close();
    }
}
