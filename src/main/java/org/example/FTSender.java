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

    private static final int PORT = 8080;
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks


    public static void startTransfer(String targetIp, List<File> filesToSend) {
        if (filesToSend == null || filesToSend.isEmpty()) {
            System.err.println("No files selected to send.");
            return;
        }

        System.out.println("Sender starting... attempting to connect to " + targetIp + ":" + PORT);
        ExecutorService threadPool = Executors.newFixedThreadPool(4);

        try (Socket socket = new Socket(targetIp, PORT);
             DataOutputStream networkOut = new DataOutputStream(socket.getOutputStream());
             DataInputStream networkIn = new DataInputStream(socket.getInputStream())) {

            System.out.println("Connected! Initiating Secure RSA Handshake...");


            int pubKeyLength = networkIn.readInt();
            byte[] pubKeyBytes = new byte[pubKeyLength];
            networkIn.readFully(pubKeyBytes);
            PublicKey receiverPublicKey = RSAEngine.reconstructPublicKey(pubKeyBytes);

            SecretKey sessionKey = CryptoEngine.generateAESKey();
            byte[] encryptedAesKey = RSAEngine.encryptAESKey(sessionKey.getEncoded(), receiverPublicKey);

            networkOut.writeInt(encryptedAesKey.length);
            networkOut.write(encryptedAesKey);
            networkOut.flush();
            System.out.println("Handshake Successful! AES Session Key securely transmitted.");


            networkOut.writeInt(filesToSend.size());
            networkOut.flush();


            for (int i = 0; i < filesToSend.size(); i++) {
                File currentFile = filesToSend.get(i);
                System.out.println("\n--- Sending File " + (i + 1) + " of " + filesToSend.size() + ": " + currentFile.getName() + " ---");

                networkOut.writeUTF(currentFile.getName());
                networkOut.writeLong(currentFile.length());
                networkOut.flush();

                try (FileInputStream fis = new FileInputStream(currentFile);
                     FileChannel fileChannel = fis.getChannel()) {

                    ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE);
                    int chunkId = 0;
                    List<CompletableFuture<Void>> activeJobs = new ArrayList<>();

                    while (fileChannel.read(buffer) > 0) {
                        buffer.flip();
                        byte[] rawData = new byte[buffer.remaining()];
                        buffer.get(rawData);
                        buffer.clear();

                        final int currentChunkId = chunkId;

                        CompletableFuture<Void> job = CompletableFuture.supplyAsync(() -> {
                            try {
                                return CryptoEngine.encryptChunk(rawData, sessionKey);
                            } catch (Exception e) {
                                throw new RuntimeException("Encryption failed on chunk " + currentChunkId, e);
                            }
                        }, threadPool).thenAccept(encryptedData -> {
                            try {
                                synchronized (networkOut) {
                                    networkOut.writeByte(0x04);
                                    networkOut.writeInt(currentChunkId);
                                    networkOut.writeInt(encryptedData.length);
                                    networkOut.write(encryptedData);
                                    networkOut.flush();
                                }
                            } catch (Exception e) {
                                System.err.println("Network write failed for chunk " + currentChunkId);
                            }
                        });

                        activeJobs.add(job);
                        chunkId++;
                    }

                    CompletableFuture.allOf(activeJobs.toArray(new CompletableFuture[0])).join();

                    synchronized (networkOut) {
                        networkOut.writeByte(0x05);
                        networkOut.flush();
                    }
                    System.out.println("File '" + currentFile.getName() + "' fully encrypted and transmitted.");
                }
            }

            threadPool.shutdown();
            System.out.println("\nEntire batch transferred safely. Secure connection closing.");

        } catch (Exception e) {
            System.err.println("Transfer aborted due to fatal error:");
            e.printStackTrace();
            if (!threadPool.isShutdown()) {
                threadPool.shutdownNow();
            }
        }
    }


    public static void main(String[] args) {
        try {
            File f1 = new File("test1.txt");
            File f2 = new File("test2.txt");
            if (!f1.exists()) Files.writeString(f1.toPath(), "Hello from File 1! This is secure batch transfer testing.");
            if (!f2.exists()) Files.writeString(f2.toPath(), "Hello from File 2! AegisNode engine is working perfectly.");
        } catch (Exception e) {
            System.err.println("Could not create test files.");
        }

        List<File> dummyFiles = Arrays.asList(new File("test1.txt"), new File("test2.txt"));


        startTransfer("127.0.0.1", dummyFiles);
    }
}