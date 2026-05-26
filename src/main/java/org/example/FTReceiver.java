package org.example;

import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.KeyPair;
import java.security.PublicKey;

public class FTReceiver {

    private static final int PORT = 8080;
    private static final int CHUNK_SIZE = 1024 * 1024;

    /**
     * THE API METHOD: Spring Boot calls this to activate the port!
     */
    public static void startListening(String saveDirectory) {

        File dir = new File(saveDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        System.out.println("Receiver active. Waiting for connection on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT);
             Socket clientSocket = serverSocket.accept();
             DataInputStream networkIn = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream networkOut = new DataOutputStream(clientSocket.getOutputStream())) {

            System.out.println("Sender connected! Initiating ECDHE Secure Handshake...");

            // --- STAGE 1: ECDHE KEY EXCHANGE ---
            KeyPair myEcPair = CryptoEngine.generateECKeyPair();
            byte[] myPubKeyBytes = myEcPair.getPublic().getEncoded();

            // Send my Public Key to Sender
            networkOut.writeInt(myPubKeyBytes.length);
            networkOut.write(myPubKeyBytes);
            networkOut.flush();

            // Receive Sender's Public Key
            int theirKeyLength = networkIn.readInt();
            byte[] theirPubKeyBytes = new byte[theirKeyLength];
            networkIn.readFully(theirPubKeyBytes);
            PublicKey senderPublicKey = CryptoEngine.reconstructECPublicKey(theirPubKeyBytes);

            // Derive the AES-256 Session Key (Never transmitted over the network)
            SecretKeySpec sessionKey = CryptoEngine.deriveAESKey(myEcPair.getPrivate(), senderPublicKey);

            // --- STAGE 2: MITM SAFETY NUMBER DEFENSE ---
            // We hash (ReceiverKey + SenderKey) to create the fingerprint
            String safetyNumber = CryptoEngine.generateSafetyNumber(myPubKeyBytes, theirPubKeyBytes);
            System.out.println("\n==================================================");
            System.out.println("🔒 SECURE CONNECTION ESTABLISHED");
            System.out.println("🛡️ Verification Fingerprint: " + safetyNumber);
            System.out.println("==================================================\n");

            // --- STAGE 3: BATCH PROCESSING HEADER ---
            int numberOfFiles = networkIn.readInt();
            System.out.println("Incoming batch of " + numberOfFiles + " files.");

            // --- STAGE 4: THE MULTI-FILE REASSEMBLY LOOP ---
            for (int i = 0; i < numberOfFiles; i++) {
                String originalFilename = networkIn.readUTF();
                long totalFileSize = networkIn.readLong();

                String fullSavePath = saveDirectory + File.separator + originalFilename;
                System.out.println("--- Receiving File " + (i + 1) + " of " + numberOfFiles + ": " + originalFilename + " ---");

                long bytesReceived = 0;

                try (RandomAccessFile fileOut = new RandomAccessFile(fullSavePath, "rw");
                     FileChannel fileChannel = fileOut.getChannel()) {

                    while (true) {
                        try {
                            byte packetType = networkIn.readByte();

                            if (packetType == 0x05) {
                                if (bytesReceived == totalFileSize) {
                                    System.out.println("File Saved Successfully to: " + fullSavePath);
                                } else {
                                    System.err.println("WARNING: File corrupted! Expected " + totalFileSize + " but got " + bytesReceived);
                                }
                                break;
                            }

                            if (packetType != 0x04) continue;

                            int chunkId = networkIn.readInt();
                            int payloadSize = networkIn.readInt();

                            byte[] encryptedPayload = new byte[payloadSize];
                            networkIn.readFully(encryptedPayload);

                            // NEW: We pass the chunkId into the decrypt method for the GCM Nonce!
                            byte[] decryptedData = CryptoEngine.decryptChunk(encryptedPayload, sessionKey, chunkId);

                            long fileOffset = (long) chunkId * CHUNK_SIZE;
                            fileChannel.write(ByteBuffer.wrap(decryptedData), fileOffset);
                            bytesReceived += decryptedData.length;

                        } catch (Exception e) {
                            System.err.println("CRITICAL ERROR: Data tampered or connection lost!");
                            e.printStackTrace();
                            return;
                        }
                    }
                }
            }
            System.out.println("\nEntire batch received successfully. Receiver shutting down.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * THE TERMINAL TESTER
     */
    public static void main(String[] args) {
        String testDirectory = System.getProperty("user.dir") + File.separator + "AegisDownloads";
        startListening(testDirectory);
    }
}