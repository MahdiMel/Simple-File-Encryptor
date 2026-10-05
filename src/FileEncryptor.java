import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class FileEncryptor {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    private static Properties loadEnv() {
        Properties env = new Properties();
        try (FileInputStream fis = new FileInputStream(".env")) {
            env.load(fis);
        } catch (IOException e) {
            System.err.println("Warning: Could not find or load .env file. " + e.getMessage());
        }
        return env;
    }

    //Generating the Key
    public static SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }
    //Encrypting the File
    public static void encryptFile(String inputPath, String outputPath, SecretKey key) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

        try (FileInputStream in = new FileInputStream(inputPath);
             FileOutputStream out = new FileOutputStream(outputPath);
             CipherOutputStream cipherOut = new CipherOutputStream(out, cipher)) {

            out.write(iv);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                cipherOut.write(buffer, 0, bytesRead);
            }
        }
    }

    //Decrypting the File
    public static void decryptFile(String inputPath, String outputPath, SecretKey key) throws Exception {
        try (FileInputStream in = new FileInputStream(inputPath)) {
            byte[] iv = new byte[16];
            in.read(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);

            try (CipherInputStream cipherIn = new CipherInputStream(in, cipher);
                 FileOutputStream out = new FileOutputStream(outputPath)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = cipherIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    //Saving to Database
    public static void saveKeyToDatabase(String filename, String base64Key) {
        Properties env = loadEnv();
        String url = env.getProperty("DB_URL");
        String user = env.getProperty("DB_USER");
        String password = env.getProperty("DB_PASS");
        String sql = "INSERT INTO file_security.secure_files (filename, encryption_key) VALUES (?, ?)";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, filename);
            pstmt.setString(2, base64Key);
            pstmt.executeUpdate();
            System.out.println("Key successfully secured in the database.");

        } catch (Exception e) {
            System.out.println("Database connection skipped or failed: " + e.getMessage());
        }
    }

    //Retrieving from Database
    public static SecretKey retrieveKeyFromDatabase(String filename) {
        Properties env = loadEnv();
        String url = env.getProperty("DB_URL");
        String user = env.getProperty("DB_USER");
        String password = env.getProperty("DB_PASS");

        String sql = "SELECT encryption_key FROM file_security.secure_files WHERE filename = ? ORDER BY created_at DESC LIMIT 1";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, filename);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String base64Key = rs.getString("encryption_key");
                byte[] decodedKeyBytes = Base64.getDecoder().decode(base64Key);
                System.out.println("Key successfully retrieved from database.");
                return new SecretKeySpec(decodedKeyBytes, 0, decodedKeyBytes.length, "AES");
            } else {
                System.out.println("No key found for file: " + filename);
                return null;
            }

        } catch (Exception e) {
            System.out.println("Database retrieval failed: " + e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("1. Generating Key...");
            SecretKey originalKey = generateKey();
            String stringKey = Base64.getEncoder().encodeToString(originalKey.getEncoded());

            System.out.println("2. Encrypting file");
            encryptFile("test.txt", "test.encrypted", originalKey);

            System.out.println("3. Saving key to Supabase...");
            saveKeyToDatabase("test.encrypted", stringKey);

            System.out.println("4. Retrieving key from Supabase...");
            SecretKey retrievedKey = retrieveKeyFromDatabase("test.encrypted");

            if (retrievedKey != null) {
                System.out.println("5. Decrypting file using database key...");
                decryptFile("test.encrypted", "test_decrypted.txt", retrievedKey);
                System.out.println("Process Complete!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}