import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        for (String raw : args) {
            String hashed = enc.encode(raw);
            System.out.println(raw + " => " + hashed);
            System.out.println("verify " + raw + " => " + enc.matches(raw, hashed));
        }
    }
}
