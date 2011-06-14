package org.kohsuke.stapler.processor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.export.Exported;

/**
 * Handles {@link org.kohsuke.stapler.export.ExportedBean} and {@link org.kohsuke.stapler.export.Exported} annotations.
 * <p/>
 * Date: 6/14/11
 *
 * @author Anton Kozak
 */
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes("*")
public class ExportedBeanAnnotationProcessor extends AbstractProcessor {

    private String destinationPath = "";

    /**
     * Sets destination path.
     *
     * @param destinationPath destination path.
     */
    public void setDestinationPath(String destinationPath) {
        this.destinationPath = destinationPath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            //TODO find way how to get java options and receive destinationPath from it
            //String destinationPath = processingEnv.getOptions().get("-d");
            if (StringUtils.isEmpty(destinationPath)) {
                return false;
            }
            File beans = new File(new File(destinationPath), "META-INF/exposed.stapler-beans");
            Set<String> exposedBeanNames = new TreeSet<String>();
            if (beans.exists()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(beans)));
                String line;
                while ((line = in.readLine()) != null) {
                    exposedBeanNames.add(line.trim());
                }
                in.close();
            }

            Map<String, List<Element>> subclassesElements = new HashMap<String, List<Element>>();

            for (Element rootElement : roundEnv.getRootElements()) {
                if (ElementKind.CLASS == rootElement.getKind()) {
                    String rootElementName = ((TypeElement) rootElement).getQualifiedName().toString();
                    for (Element element : rootElement.getEnclosedElements()) {
                        //collect subclasses
                        if (ElementKind.CLASS == element.getKind()) {
                            List<Element> subclasses = subclassesElements.get(rootElementName);
                            if (subclasses == null) {
                                subclassesElements.put(rootElementName, subclasses = new ArrayList<Element>());
                            }
                            subclasses.add(element);
                        }

                        if (element.getAnnotation(Exported.class) != null) {
                            exposedBeanNames.add(rootElementName);
                        }
                    }
                }
            }
            Set<String> exposedBeans = new TreeSet<String>();
            exposedBeans.addAll(exposedBeanNames);
            for (String exposedBeanName : exposedBeanNames) {
                List<Element> subclasses = subclassesElements.get(exposedBeanName);
                if (CollectionUtils.isNotEmpty(subclasses)) {
                    for (Element element : subclasses) {
                        exposedBeans.add(((TypeElement) element).getQualifiedName().toString());
                    }
                }
            }

            //add subclasses into exposedBeanNames if root element contains Exported methods/properties


//            for (Element element : roundEnv.getElementsAnnotatedWith(Exported.class)) {
//                if (element instanceof TypeElement) {
//
//                }
//            }

/*
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

            for (Map.Entry<TypeDeclaration, List<MemberDeclaration>> e : props.entrySet()) {
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
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Generating "+javadocFile);
                OutputStream os = processingEnv.getFiler().createResource(Filer.Location.CLASS_TREE,"", "", javadocFile);
                try {
                    javadocs.store(os,null);
                } finally {
                    os.close();
                }
            }
*/
            beans.getParentFile().mkdirs();
            PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(beans), "UTF-8"));
            for (String beanName : exposedBeans) {
                w.println(beanName);
            }
            w.close();

        } catch (IOException x) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, x.toString());
        }
        return true;
    }
}
