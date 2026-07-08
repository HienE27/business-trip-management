import java.io.*;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.jar.*;

public class PatchJar {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: PatchJar <jar> <class1:innerPath> [class2:innerPath] ...");
            System.exit(1);
        }
        String jarPath = args[0];
        File tmp = new File(jarPath + ".tmp");
        try (JarFile jar = new JarFile(jarPath);
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(tmp))) {
            byte[] buf = new byte[8192];
            Enumeration<JarEntry> e = jar.entries();
            java.util.Set<String> toReplace = new java.util.HashSet<>();
            for (int i = 1; i < args.length; i++) {
                String[] parts = args[i].split(":", 2);
                String innerPath = parts[0];
                String filePath = parts[1];
                toReplace.add(innerPath);
                // We will write this entry from file below
                File src = new File(filePath);
                JarEntry je = new JarEntry(innerPath);
                jos.putNextEntry(je);
                try (FileInputStream fis = new FileInputStream(src)) {
                    int n;
                    while ((n = fis.read(buf)) > 0) jos.write(buf, 0, n);
                }
                jos.closeEntry();
                System.out.println("Replaced " + innerPath);
            }
            // Copy other entries
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (toReplace.contains(je.getName())) continue;
                jos.putNextEntry(je);
                try (InputStream is = jar.getInputStream(je)) {
                    int n;
                    while ((n = is.read(buf)) > 0) jos.write(buf, 0, n);
                }
                jos.closeEntry();
            }
        }
        Files.move(tmp.toPath(), Paths.get(jarPath), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("DONE " + jarPath);
    }
}