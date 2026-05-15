package org.example;

import javax.crypto.SecretKey;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FTSender {

    private static final String IP = "127.0.0.1";
    private static final int PORT = 8080;
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks

    public static void main(String[] args) {
        // --- TEST SETUP: Auto-generate dummy files if they don't exist ---
        try {
            File f1 = new File("test1.txt");
            File f2 = new File("test2.txt");
            if (!f1.exists()) Files.writeString(f1.toPath(), "Hello from File 1! This is secure batch transfer testing.");
            if (!f2.exists()) Files.writeString(f2.toPath(), "Hello from File 2! AegisNode engine is working perfectly.");
        } catch (Exception e) {
            System.err.println("Could not create test files.");
        }

        // Define the list of files we want to send
        List<File> filesToSend = Arrays.asList(new File("test1.txt"), new File("test2.txt"));

        System.out.println("Sender starting... attempting to connect to " + IP + ":" + PORT);

        // 1. Initialize the Thread Pool for heavy AES encryption
        ExecutorService threadPool = Executors.newFixedThreadPool(4);

        try (Socket socket = new Socket(IP, PORT);
             DataOutputStream networkOut = new DataOutputStream(socket.getOutputStream());
             DataInputStream networkIn = new DataInputStream(socket.getInputStream())) {

            System.out.println("Connected! Initiating Secure RSA Handshake...");

            // --- STAGE 1: RSA HANDSHAKE & KEY EXCHANGE ---

            // Read exactly how long the incoming Public Key is, then read the bytes
            int pubKeyLength = networkIn.readInt();
            byte[] pubKeyBytes = new byte[pubKeyLength];
            networkIn.readFully(pubKeyBytes);
            PublicKey receiverPublicKey = RSAEngine.reconstructPublicKey(pubKeyBytes);

            // Generate a fresh AES Session Key and lock it with the Receiver's Public Key
            SecretKey sessionKey = CryptoEngine.generateAESKey();
            byte[] encryptedAesKey = RSAEngine.encryptAESKey(sessionKey.getEncoded(), receiverPublicKey);

            // Send the locked AES Key to the Receiver
            networkOut.writeInt(encryptedAesKey.length);
            networkOut.write(encryptedAesKey);
            networkOut.flush();
            System.out.println("Handshake Successful! AES Session Key securely transmitted.");

            // --- STAGE 2: BATCH METADATA HEADER ---

            // Tell the Receiver exactly how many files are coming
            networkOut.writeInt(filesToSend.size());
            networkOut.flush();

            // --- STAGE 3: THE MULTI-FILE PIPELINE ---

            for (int i = 0; i < filesToSend.size(); i++) {
                File currentFile = filesToSend.get(i);
                System.out.println("\n--- Sending File " + (i + 1) + " of " + filesToSend.size() + ": " + currentFile.getName() + " ---");

                // Send dynamic metadata for this specific file
                networkOut.writeUTF(currentFile.getName());
                networkOut.writeLong(currentFile.length());
                networkOut.flush();

                try (FileInputStream fis = new FileInputStream(currentFile);
                     FileChannel fileChannel = fis.getChannel()) {

                    ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE);
                    int chunkId = 0;

                    // Track all background threads for THIS specific file
                    List<CompletableFuture<Void>> activeJobs = new ArrayList<>();

                    while (fileChannel.read(buffer) > 0) {
                        buffer.flip();

                        // Extract exact bytes (handles files smaller than 1MB)
                        byte[] rawData = new byte[buffer.remaining()];
                        buffer.get(rawData);
                        buffer.clear();

                        final int currentChunkId = chunkId;

                        // Start the background encryption job
                        CompletableFuture<Void> job = CompletableFuture.supplyAsync(() -> {
                                    try {
                                        return CryptoEngine.encryptChunk(rawData, sessionKey);
                                    } catch (Exception e) {
                                        throw new RuntimeException("Encryption failed on chunk " + currentChunkId, e);
                                    }
                                }, threadPool)

                                // Callback: Push to network socket safely
                                .thenAccept(encryptedData -> {
                                    try {
                                        // Synchronize to prevent threads from mixing bytes in the network pipe
                                        synchronized (networkOut) {
                                            networkOut.writeByte(0x04);                  // Data Packet ID
                                            networkOut.writeInt(currentChunkId);         // Chunk sequence
                                            networkOut.writeInt(encryptedData.length);   // Payload size
                                            networkOut.write(encryptedData);             // Actual encrypted payload
                                            networkOut.flush();
                                        }
                                    } catch (Exception e) {
                                        System.err.println("Network write failed for chunk " + currentChunkId);
                                    }
                                });

                        activeJobs.add(job);
                        chunkId++;
                    }

                    // CRITICAL: Pause the loop and wait for all chunks of THIS file to finish
                    // before sending the End-of-File packet
                    CompletableFuture.allOf(activeJobs.toArray(new CompletableFuture[0])).join();

                    // Send the Graceful Termination Packet (0x05) for this file
                    synchronized (networkOut) {
                        networkOut.writeByte(0x05);
                        networkOut.flush();
                    }
                    System.out.println("File '" + currentFile.getName() + "' fully encrypted and transmitted.");
                }
            }

            // --- STAGE 4: CLEANUP ---
            threadPool.shutdown();
            System.out.println("\nEntire batch transferred safely. Secure connection closing.");

        } catch (Exception e) {
            System.err.println("Transfer aborted due to fatal error:");
            e.printStackTrace();

            // Ensure thread pool dies even if the network crashes
            if (!threadPool.isShutdown()) {
                threadPool.shutdownNow();
            }
        }
    }
}