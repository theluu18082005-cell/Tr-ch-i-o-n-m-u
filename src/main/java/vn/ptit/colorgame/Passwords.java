package vn.ptit.colorgame;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {
    public static final int ITERATIONS = 210_000;
    private Passwords() {}

    public static String salt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    public static String hash(String password, String salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), iterations, 256);
        try {
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
        finally { spec.clearPassword(); }
    }

    public static boolean verify(String password, Store.Account account) {
        return MessageDigest.isEqual(Base64.getDecoder().decode(account.hash),
                Base64.getDecoder().decode(hash(password, account.salt, account.iterations)));
    }
}
