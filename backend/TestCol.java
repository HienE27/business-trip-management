import java.util.*;

public class TestCol {
    public static void main(String[] args) {
        Map<String, Integer> colMap = new HashMap<>();
        colMap.put("họ tên", 2);
        colMap.put("email", 3);

        System.out.println("colMap.get('họ tên') = " + colMap.get("họ tên"));
        System.out.println("colMap.containsKey('họ tên') = " + colMap.containsKey("họ tên"));
        // Check what's actually in the map
        for (String k : colMap.keySet()) {
            System.out.println("Key: '" + k + "' bytes=" + java.util.Arrays.toString(k.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        // Check the lookup key
        String lookupKey = "họ tên";
        System.out.println("Lookup: '" + lookupKey + "' bytes=" + java.util.Arrays.toString(lookupKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}