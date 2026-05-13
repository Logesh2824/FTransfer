package org.example;
import javax.crypto.KeyGenerator;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class CryptoEngine {
    private static final String ALGORITHM="AES";
    private static final String CIPHER_TRANSFORMATION="AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    public static SecretKey generateAESKey() throws Exception{
        KeyGenerator keyGenerator= KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(256);
        return keyGenerator.generateKey();
    }

    public static byte[] encryptChunk(byte[] plaintext, SecretKey key)throws Exception{
        byte[] iv=new byte[IV_LENGTH];
        SecureRandom random=new SecureRandom();
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
        byte[] encryptedData= cipher.doFinal(plaintext);

        ByteBuffer buffer= ByteBuffer.allocate(iv.length+encryptedData.length);
        buffer.put(iv);
        buffer.put(encryptedData);
        return buffer.array();

    }

    public static byte[] decryptChunk(byte[] encryptedDataWithIv, SecretKey key) throws Exception {

        ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedDataWithIv);
        byte[] iv = new byte[IV_LENGTH];
        byteBuffer.get(iv);

        byte[] encryptedData = new byte[byteBuffer.remaining()];
        byteBuffer.get(encryptedData);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        return cipher.doFinal(encryptedData);
    }
    public static void main(String[] args) {
        try {
            SecretKey myKey = generateAESKey();

            String secretMessage = "AegisNode Phase 3: Enterprise Encryption Activated!";
            byte[] rawBytes = secretMessage.getBytes();
            System.out.println("Original: " + new String(rawBytes));


            byte[] encryptedPackage = encryptChunk(rawBytes, myKey);
            System.out.println("Encrypted Package Size: " + encryptedPackage.length + " bytes");


            byte[] decryptedBytes = decryptChunk(encryptedPackage, myKey);
            System.out.println("Decrypted: " + new String(decryptedBytes));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
