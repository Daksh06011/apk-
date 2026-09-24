import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Post-processes dex2jar output so HotSpot can load it with verification on:
 *  1. Dex call sites don't record whether the owner of an invoke-static / invoke-super target is an
 *     interface, and dex2jar always emits a plain Methodref. The JVM rejects that for interface
 *     static/default methods (IncompatibleClassChangeError: must be InterfaceMethodref), so those
 *     call sites are rewritten with itf=true.
 *  2. dex2jar emits Java 8 class files without StackMapTable frames. Frames (and max stack/locals)
 *     are recomputed here from a class-hierarchy index of all jars given, so nothing needs
 *     -XX:-BytecodeVerification (running unverified dex2jar code crashes HotSpot's oop-map builder).
 *     A class whose frames can't be computed is written unchanged and reported.
 *
 * Usage: FixInterfaceCalls in.jar out.jar [lookup.jar ...]  (lookup jars: android-all, stdlib...)
 */
public final class FixInterfaceCalls {
    public static void main(String[] args) throws Exception {
        Set<String> interfaces = new HashSet<>();
        for (int i = 0; i < args.length; i++) {
            if (i == 1) continue;
            collectInterfaces(new File(args[i]), interfaces);
        }
        List<String> unframed = new ArrayList<>();
        int fixed[] = {0};
        try (ZipFile in = new ZipFile(args[0]);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[1]))) {
            for (Enumeration<? extends ZipEntry> e = in.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                byte[] data = read(in.getInputStream(entry));
                if (entry.getName().endsWith(".class")) data = rewrite(data, interfaces, fixed, unframed);
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(data);
                out.closeEntry();
            }
        }
        System.out.println("FixInterfaceCalls: " + interfaces.size() + " interfaces, " + fixed[0]
                + " call sites fixed, frames failed for " + unframed.size() + " classes " + unframed);
    }

    private static boolean isInterface(String owner, Set<String> interfaces) {
        if (interfaces.contains(owner)) return true;
        if (owner.startsWith("java/") || owner.startsWith("javax/")) {
            try {
                return Class.forName(owner.replace('/', '.'), false, ClassLoader.getPlatformClassLoader()).isInterface();
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static byte[] rewrite(byte[] data, Set<String> interfaces, int[] fixed, List<String> unframed) {
        try {
            return rewrite(data, interfaces, fixed, ClassWriter.COMPUTE_FRAMES);
        } catch (RuntimeException e) {
            unframed.add(new ClassReader(data).getClassName() + " (" + e + ")");
            return rewrite(data, interfaces, fixed, 0);
        }
    }

    private static byte[] rewrite(byte[] data, Set<String> interfaces, int[] fixed, int flags) {
        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(flags) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                return commonSuperClass(a, b, interfaces);
            }
        };
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exc) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, sig, exc)) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        if (!itf && (op == Opcodes.INVOKESTATIC || op == Opcodes.INVOKESPECIAL)
                                && isInterface(owner, interfaces)) {
                            itf = true;
                            fixed[0]++;
                        }
                        super.visitMethodInsn(op, owner, n, d, itf);
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);
        return writer.toByteArray();
    }

    /** class -> superclass, for every class in every jar (first definition wins). */
    private static final Map<String, String> SUPERS = new HashMap<>();

    private static void collectInterfaces(File jar, Set<String> out) throws Exception {
        try (ZipFile zip = new ZipFile(jar)) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassReader r = new ClassReader(read(zip.getInputStream(entry)));
                if ((r.getAccess() & Opcodes.ACC_INTERFACE) != 0) out.add(r.getClassName());
                SUPERS.putIfAbsent(r.getClassName(), r.getSuperName());
            }
        }
    }

    private static String superOf(String c) {
        if (SUPERS.containsKey(c)) return SUPERS.get(c);
        try {   // JDK classes: java.lang.Object, Throwable subclasses, ...
            Class<?> k = Class.forName(c.replace('/', '.'), false, ClassLoader.getPlatformClassLoader());
            String s = k.getSuperclass() == null ? null : k.getSuperclass().getName().replace('.', '/');
            SUPERS.put(c, s);
            return s;
        } catch (Throwable t) {
            throw new IllegalStateException("unknown class " + c);
        }
    }

    private static String commonSuperClass(String a, String b, Set<String> interfaces) {
        if (a.equals(b)) return a;
        if (isInterface(a, interfaces) || isInterface(b, interfaces)) return "java/lang/Object";
        Set<String> chain = new HashSet<>();
        for (String c = a; c != null; c = superOf(c)) chain.add(c);
        for (String c = b; c != null; c = superOf(c)) if (chain.contains(c)) return c;
        return "java/lang/Object";
    }

    private static byte[] read(InputStream in) throws Exception {
        try (in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            in.transferTo(buf);
            return buf.toByteArray();
        }
    }
}
