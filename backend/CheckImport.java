import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CheckImport {
    public static void main(String[] args) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
        String[] lines = text.split("\\r?\\n");
        System.out.println("Total lines: " + lines.length);
        String[] headers = lines[0].split(",", -1);
        System.out.println("Header count: " + headers.length);
        Map<String,Integer> colMap = new HashMap<>();
        for (int i=0; i<headers.length; i++) {
            String h = headers[i].trim().toLowerCase();
            if (h.startsWith("\uFEFF")) h = h.substring(1);
            colMap.put(h, i);
            System.out.println("h[" + i + "] = '" + h + "' bytes=" + bytesToHex(h));
        }
        System.out.println("colMap.get('họ tên') = " + colMap.get("họ tên"));
        System.out.println("colMap.containsKey('họ tên') = " + colMap.containsKey("họ tên"));
        // Check keys
        for (String k : colMap.keySet()) {
            if (k.startsWith("h")) System.out.println("Key starting with h: '" + k + "' bytes=" + bytesToHex(k));
        }
    }
    static String bytesToHex(String s) {
        StringBuilder sb = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) sb.append(String.format("%02X ", b));
        return sb.toString().trim();
    }
}
