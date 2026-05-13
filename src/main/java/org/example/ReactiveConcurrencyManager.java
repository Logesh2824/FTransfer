package org.example;

import javax.crypto.SecretKey;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ReactiveConcurrencyManager {

    public static void main(String[] args) {
        try {

            SecretKey myKey = CryptoEngine.generateAESKey();

            ExecutorService encryptionPool = Executors.newFixedThreadPool(4);

            System.out.println("--- Starting Reactive Encryption Pipeline ---");

            for (int chunkId = 0; chunkId < 5; chunkId++) {
                final int currentId = chunkId;
                byte[] rawData = ("Binary data for video chunk #" + currentId).getBytes();

                CompletableFuture.supplyAsync(() -> {

                            try {
                                System.out.println("[CHUNK " + currentId + "] Encrypting on: " + Thread.currentThread().getName());
                                return CryptoEngine.encryptChunk(rawData, myKey);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }, encryptionPool)

                        .thenAccept(encryptedData -> {

                            System.out.println("[CHUNK " + currentId + "] SUCCESS. Ready for network transmission (" + encryptedData.length + " bytes)");
                        })

                        .exceptionally(ex -> {

                            System.err.println("[CHUNK " + currentId + "] FAILED: " + ex.getMessage());
                            return null;
                        });
            }

            encryptionPool.shutdown();
            encryptionPool.awaitTermination(5, TimeUnit.SECONDS);
            System.out.println("--- Pipeline processing complete ---");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}