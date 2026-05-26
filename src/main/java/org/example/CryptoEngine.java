package org.example;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;

public class CryptoEngine {

    private static final int GCM_TAG_LENGTH = 128; // 128-bit authentication tag
    private static final int GCM_IV_LENGTH = 12;   // 12-byte standard nonce

    // 1. GENERATE ELLIPTIC CURVE KEYS (Fixes Perfect Forward Secrecy)
    public static KeyPair generateECKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(256); // secp256r1 standard
        return keyPairGenerator.generateKeyPair();
    }

    public static PublicKey reconstructECPublicKey(byte[] keyBytes) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    // 2. DERIVE THE SHARED AES KEY (The ECDHE Magic)
    public static SecretKeySpec deriveAESKey(PrivateKey myPrivateKey, PublicKey theirPublicKey) throws Exception {
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(myPrivateKey);
        keyAgreement.doPhase(theirPublicKey, true);
        byte[] sharedSecret = keyAgreement.generateSecret();

        // Hash the raw EC secret into a clean 256-bit AES key
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] aesKeyBytes = sha256.digest(sharedSecret);
        return new SecretKeySpec(aesKeyBytes, "AES");
    }

    // 3. GENERATE A VISUAL FINGERPRINT (Fixes MITM Attacks)
    public static String generateSafetyNumber(byte[] myPubKey, byte[] theirPubKey) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update(myPubKey);
        sha256.update(theirPubKey);
        byte[] hash = sha256.digest();
        // Return the first 6 numbers of the hash as a visual string
        return String.format("%06d", Math.abs(ByteBuffer.wrap(hash).getInt()) % 1000000);
    }

    // 4. AES-GCM ENCRYPTION (Fixes Packet Tampering)
    public static byte[] encryptChunk(byte[] plaintext, SecretKeySpec aesKey, int chunkId) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = generateDeterministicIV(chunkId);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec);
        return cipher.doFinal(plaintext);
    }

    public static byte[] decryptChunk(byte[] ciphertext, SecretKeySpec aesKey, int chunkId) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = generateDeterministicIV(chunkId);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec);
        return cipher.doFinal(ciphertext); // Will throw AEADBadTagException if tampered!
    }

    // GCM requires a unique IV for every chunk. We use the chunkId to ensure it never repeats.
    private static byte[] generateDeterministicIV(int chunkId) {
        ByteBuffer buffer = ByteBuffer.allocate(GCM_IV_LENGTH);
        buffer.putInt(chunkId);
        return buffer.array();
    }
}