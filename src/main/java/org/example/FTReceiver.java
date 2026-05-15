package org.example;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.KeyPair;

public class FTReceiver {

    private static final int PORT = 8080;
    private static final int CHUNK_SIZE = 1024 * 1024;

    public static void main(String[] args) {
        System.out.println("Receiver active. Waiting for connection on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT);
             Socket clientSocket = serverSocket.accept();
             DataInputStream networkIn = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream networkOut = new DataOutputStream(clientSocket.getOutputStream())) {

            System.out.println("Sender connected! Initiating Secure RSA Handshake...");

            // --- STAGE 1: RSA PUBLIC KEY EXCHANGE ---
            KeyPair rsaPair = RSAEngine.generateKeyPair();
            byte[] publicKeyBytes = rsaPair.getPublic().getEncoded();

            // Send the Public Key to the Sender
            networkOut.writeInt(publicKeyBytes.length);
            networkOut.write(publicKeyBytes);
            networkOut.flush();

            // --- STAGE 2: RECEIVE ENCRYPTED AES KEY ---
            int aesKeyLength = networkIn.readInt();
            byte[] encryptedAesKey = new byte[aesKeyLength];
            networkIn.readFully(encryptedAesKey);

            // Unlock the AES key using our Private Key
            byte[] rawAesKey = RSAEngine.decryptAESKey(encryptedAesKey, rsaPair.getPrivate());
            SecretKey sessionKey = new SecretKeySpec(rawAesKey, "AES");
            System.out.println("Handshake Successful! Session secured with AES-256.");

            // --- STAGE 3: BATCH PROCESSING HEADER ---
            int numberOfFiles = networkIn.readInt();
            System.out.println("Incoming batch of " + numberOfFiles + " files.");

            // --- STAGE 4: THE MULTI-FILE REASSEMBLY LOOP ---
            for (int i = 0; i < numberOfFiles; i++) {
                String originalFilename = networkIn.readUTF();
                long totalFileSize = networkIn.readLong();
                System.out.println("\n--- Receiving File " + (i + 1) + " of " + numberOfFiles + ": " + originalFilename + " ---");

                long bytesReceived = 0;

                try (RandomAccessFile fileOut = new RandomAccessFile(originalFilename, "rw");
                     FileChannel fileChannel = fileOut.getChannel()) {

                    while (true) {
                        try {
                            byte packetType = networkIn.readByte();

                            // Check for End of THIS File
                            if (packetType == 0x05) {
                                if (bytesReceived == totalFileSize) {
                                    System.out.println("File Saved Successfully: " + originalFilename);
                                } else {
                                    System.err.println("WARNING: File corrupted! Expected " + totalFileSize + " but got " + bytesReceived);
                                }
                                break; // Break inner loop, move to the next file
                            }

                            if (packetType != 0x04) continue;

                            int chunkId = networkIn.readInt();
                            int payloadSize = networkIn.readInt();

                            byte[] encryptedPayload = new byte[payloadSize];
                            networkIn.readFully(encryptedPayload);

                            byte[] decryptedData = CryptoEngine.decryptChunk(encryptedPayload, sessionKey);

                            long fileOffset = (long) chunkId * CHUNK_SIZE;
                            fileChannel.write(ByteBuffer.wrap(decryptedData), fileOffset);
                            bytesReceived += decryptedData.length;

                        } catch (java.io.EOFException e) {
                            System.err.println("CRITICAL ERROR: Connection lost during file transfer!");
                            return; // Kill the receiver
                        }
                    }
                }
            }
            System.out.println("\nEntire batch received successfully. Receiver shutting down.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
