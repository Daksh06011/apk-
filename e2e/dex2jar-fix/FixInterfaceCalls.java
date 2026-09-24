import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
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
 * Dex call sites don't record whether the owner of an invoke-static / invoke-super target is an
 * interface, and dex2jar always emits a plain Methodref. The JVM rejects that for interface
 * static/default methods (IncompatibleClassChangeError: must be InterfaceMethodref), so this pass
 * rewrites those call sites with itf=true.
 *
 * Usage: FixInterfaceCalls in.jar out.jar [lookup.jar ...]  (lookup jars: e.g. android-all)
 */
public final class FixInterfaceCalls {
    public static void main(String[] args) throws Exception {
        Set<String> interfaces = new HashSet<>();
        for (int i = 0; i < args.length; i++) {
            if (i == 1) continue;
            collectInterfaces(new File(args[i]), interfaces);
        }
        int fixed[] = {0};
        try (ZipFile in = new ZipFile(args[0]);
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(args[1]))) {
            for (Enumeration<? extends ZipEntry> e = in.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                byte[] data = read(in.getInputStream(entry));
                if (entry.getName().endsWith(".class")) data = rewrite(data, interfaces, fixed);
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(data);
                out.closeEntry();
            }
        }
        System.out.println("FixInterfaceCalls: " + interfaces.size() + " interfaces, " + fixed[0] + " call sites fixed");
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

    private static byte[] rewrite(byte[] data, Set<String> interfaces, int[] fixed) {
        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(0);
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
        }, 0);
        return writer.toByteArray();
    }

    private static void collectInterfaces(File jar, Set<String> out) throws Exception {
        try (ZipFile zip = new ZipFile(jar)) {
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                ClassReader r = new ClassReader(read(zip.getInputStream(entry)));
                if ((r.getAccess() & Opcodes.ACC_INTERFACE) != 0) out.add(r.getClassName());
            }
        }
    }

    private static byte[] read(InputStream in) throws Exception {
        try (in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            in.transferTo(buf);
            return buf.toByteArray();
        }
    }
}
