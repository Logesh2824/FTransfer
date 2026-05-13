package org.example;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class FileChunker {
    private static final int CHUNK_SIZE = 1024 * 1024;
    public static void main(String[] args) {

        String sourceFile = "D:\\maven\\FTransfer\\src\\main\\java\\org\\example\\test.mp4";
        String destinationFile = "testop.mp4";

        copyFile(sourceFile, destinationFile);
        System.out.println("File successfully chunked and reassembled!");
    }

    private static void copyFile(String sourcepath, String destpath) {
        try (FileInputStream fis = new FileInputStream(sourcepath);
             FileOutputStream fos = new FileOutputStream(destpath);
             FileChannel inputchannel = fis.getChannel();
             FileChannel outputchannel = fos.getChannel();) {
            ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE);
            while (inputchannel.read(buffer) > 0) {
                buffer.flip();
                outputchannel.write(buffer);
                buffer.clear();
            }
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
}
