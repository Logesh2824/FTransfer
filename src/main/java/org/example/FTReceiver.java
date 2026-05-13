package org.example;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FTReceiver {

    private static final int PORT = 8080;
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB

    public static void main(String[] args) {

        byte[] staticKeyBytes = "AegisNodeSuperSecretKey123456789".getBytes();
        SecretKey sharedKey = new SecretKeySpec(staticKeyBytes, "AES");

        System.out.println("Receiver active. Waiting for connection on port " + PORT + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORT);
             Socket clientSocket = serverSocket.accept();
             DataInputStream networkIn = new DataInputStream(clientSocket.getInputStream());
             RandomAccessFile fileOut = new RandomAccessFile("received_secure_file.mp4", "rw");
             FileChannel fileChannel = fileOut.getChannel()) {

            System.out.println("Sender connected! Incoming secure transmission...");

            while (true) {
                try {
                    byte packetType = networkIn.readByte();
                    if (packetType != 0x04) continue;

                    int chunkId = networkIn.readInt();
                    int payloadSize = networkIn.readInt();

                    byte[] encryptedPayload = new byte[payloadSize];
                    networkIn.readFully(encryptedPayload);

                    System.out.println("Received Chunk ID: " + chunkId + " | Size: " + payloadSize);

                    byte[] decryptedData = CryptoEngine.decryptChunk(encryptedPayload, sharedKey);

                    long fileOffset = (long) chunkId * CHUNK_SIZE;
                    ByteBuffer buffer = ByteBuffer.wrap(decryptedData);

                    fileChannel.write(buffer, fileOffset);

                } catch (java.io.EOFException e) {
                    break;
                }
            }

            System.out.println("Transmission complete. File safely reconstructed!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
