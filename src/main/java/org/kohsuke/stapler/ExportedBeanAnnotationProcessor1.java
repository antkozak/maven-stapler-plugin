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
import com.sun.mirror.declaration.AnnotationTypeDeclaration;
import com.sun.mirror.declaration.Declaration;
import com.sun.mirror.declaration.FieldDeclaration;
import com.sun.mirror.declaration.MemberDeclaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.util.SimpleDeclarationVisitor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Handles {@link ExportedBean} and {@link Exported} annotations.
 * 
 * @author Kohsuke Kawaguchi
 */
public class ExportedBeanAnnotationProcessor1 /*implements AnnotationProcessor*/ {
    private final AnnotationProcessorEnvironment env;

    public ExportedBeanAnnotationProcessor1(AnnotationProcessorEnvironment env) {
        this.env = env;
    }

    public void process() {
        try {
            File out = new File(env.getOptions().get("-d"));

            AnnotationTypeDeclaration $exposed =
                (AnnotationTypeDeclaration) env.getTypeDeclaration(Exported.class.getName());

            // collect all exposed properties
            Map<TypeDeclaration, List<MemberDeclaration>> props =
                new HashMap<TypeDeclaration, List<MemberDeclaration>>();

            for( Declaration d : env.getDeclarationsAnnotatedWith($exposed)) {
                MemberDeclaration md = (MemberDeclaration) d;
                TypeDeclaration owner = md.getDeclaringType();
                List<MemberDeclaration> list = props.get(owner);
                if(list==null)
                    props.put(owner,list=new ArrayList<MemberDeclaration>());
                list.add(md);
            }

            File beans = new File(out,"META-INF/exposed.stapler-beans");
            Set<String> exposedBeanNames = new TreeSet<String>();
            if(beans.exists()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(beans)));
                String line;
                while((line=in.readLine())!=null)
                    exposedBeanNames.add(line.trim());
                in.close();
            }

            for (Entry<TypeDeclaration, List<MemberDeclaration>> e : props.entrySet()) {
                exposedBeanNames.add(e.getKey().getQualifiedName());

                final Properties javadocs = new Properties();
                for (MemberDeclaration md : e.getValue()) {
                    md.accept(new SimpleDeclarationVisitor() {
                        public void visitFieldDeclaration(FieldDeclaration f) {
                            String javadoc = f.getDocComment();
                            if(javadoc!=null)
                                javadocs.put(f.getSimpleName(), javadoc);
                        }
                        public void visitMethodDeclaration(MethodDeclaration m) {
                            String javadoc = m.getDocComment();
                            if(javadoc!=null)
                                javadocs.put(m.getSimpleName()+"()", javadoc);
                        }

                        // way too tedious.
                        //private String getSignature(MethodDeclaration m) {
                        //    final StringBuilder buf = new StringBuilder(m.getSimpleName());
                        //    buf.append('(');
                        //    boolean first=true;
                        //    for (ParameterDeclaration p : m.getParameters()) {
                        //        if(first)   first = false;
                        //        else        buf.append(',');
                        //        p.getType().accept(new SimpleTypeVisitor() {
                        //            public void visitPrimitiveType(PrimitiveType pt) {
                        //                buf.append(pt.getKind().toString().toLowerCase());
                        //            }
                        //            public void visitDeclaredType(DeclaredType dt) {
                        //                buf.append(dt.getDeclaration().getQualifiedName());
                        //            }
                        //
                        //            public void visitArrayType(ArrayType at) {
                        //                at.getComponentType().accept(this);
                        //                buf.append("[]");
                        //            }
                        //
                        //            public void visitTypeVariable(TypeVariable tv) {
                        //
                        //                // TODO
                        //                super.visitTypeVariable(typeVariable);
                        //            }
                        //
                        //            public void visitVoidType(VoidType voidType) {
                        //                // TODO
                        //                super.visitVoidType(voidType);
                        //            }
                        //        });
                        //    }
                        //    buf.append(')');
                        //    // TODO
                        //    return null;
                        //}
                    });
                }

                File javadocFile = new File(e.getKey().getQualifiedName().replace('.', '/') + ".javadoc");
                env.getMessager().printNotice("Generating "+javadocFile);
                OutputStream os = env.getFiler().createBinaryFile(Location.CLASS_TREE,"", javadocFile);
                try {
                    javadocs.store(os,null);
                } finally {
                    os.close();
                }
            }

            beans.getParentFile().mkdirs();
            PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(beans),"UTF-8"));
            for (String beanName : exposedBeanNames)
                w.println(beanName);
            w.close();

        } catch (IOException x) {
            env.getMessager().printError(x.toString());
        }
    }
}
