package org.example;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FTSender {

    private static final String IP = "127.0.0.1";
    private static final int PORT = 8080;
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB

    public static void main(String[] args) {
        byte[] staticKeyBytes = "AegisNodeSuperSecretKey123456789".getBytes();
        SecretKey sharedKey = new SecretKeySpec(staticKeyBytes, "AES");


        String fileToSend = "D:\\maven\\FTransfer\\src\\main\\java\\org\\example\\test.mp4";


        ExecutorService threadPool = Executors.newFixedThreadPool(4);

        System.out.println("Sender starting... Connecting to receiver.");

        try (Socket socket = new Socket(IP, PORT);
             DataOutputStream networkOut = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(fileToSend);
             FileChannel fileChannel = fis.getChannel()) {

            System.out.println("Connected! Initiating highly concurrent secure transfer...");

            ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE);
            int chunkId = 0;


            while (fileChannel.read(buffer) > 0) {
                buffer.flip();

                byte[] rawData = new byte[buffer.remaining()];
                buffer.get(rawData);
                buffer.clear();

                final int currentChunkId = chunkId;

                CompletableFuture.supplyAsync(() -> {
                            try {
                                return CryptoEngine.encryptChunk(rawData, sharedKey);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }, threadPool)

                        .thenAccept(encryptedData -> {
                            try {
                                synchronized (networkOut) {

                                    networkOut.writeByte(0x04);                  // Packet Type
                                    networkOut.writeInt(currentChunkId);         // Chunk ID
                                    networkOut.writeInt(encryptedData.length);   // Payload Size


                                    networkOut.write(encryptedData);
                                    networkOut.flush();
                                }
                                System.out.println("Successfully sent encrypted Chunk ID: " + currentChunkId);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });

                chunkId++;
            }

            threadPool.shutdown();
            threadPool.awaitTermination(1, TimeUnit.HOURS);

            System.out.println("All chunks encrypted and sent. Shutting down.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
