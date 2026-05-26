package org.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController // Tells Spring this class handles HTTP API requests
@RequestMapping("/api/p2p") // The base URL for all endpoints in this class
@CrossOrigin(origins = "*") // Allows your future React frontend to talk to this API without security blocks
public class FTController {

    /**
     * Endpoint: POST http://localhost:8080/api/p2p/send
     * Purpose: Accepts files and an IP address from React, and triggers the Java sender engine.
     */
    @PostMapping("/send")
    public ResponseEntity<String> sendFiles(
            @RequestParam("ip") String targetIp,
            @RequestParam("files") MultipartFile[] webFiles) {

        if (webFiles == null || webFiles.length == 0) {
            return ResponseEntity.badRequest().body("Error: No files provided.");
        }

        try {
            // 1. Web browsers send 'MultipartFile' objects.
            // Our engine needs standard 'java.io.File' objects. We must convert them.
            List<File> javaFiles = new ArrayList<>();
            String tempDir = System.getProperty("java.io.tmpdir"); // OS Temporary folder

            for (MultipartFile webFile : webFiles) {
                // Create a temporary file on the hard drive
                File tempFile = new File(tempDir + File.separator + webFile.getOriginalFilename());
                webFile.transferTo(tempFile); // Save the web data to the hard drive
                javaFiles.add(tempFile);
            }

            System.out.println("API Received Request: Sending " + javaFiles.size() + " files to " + targetIp);

            // 2. Trigger your existing Core Java Engine!
            // We run this in a new Thread so the HTTP request doesn't freeze
            // if the transfer takes 10 minutes.
            new Thread(() -> {
                try {
                    FTSender.startTransfer(targetIp, javaFiles);
                    System.out.println("Background transfer to " + targetIp + " finished.");

                    // Optional: Delete the temp files after successful sending to save space
                    for (File f : javaFiles) f.delete();
                } catch (Exception e) {
                    System.err.println("Engine failed during transfer: " + e.getMessage());
                }
            }).start();

            // 3. Immediately tell React that the process has successfully started
            return ResponseEntity.ok("Transfer initialized successfully to " + targetIp);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error processing files on the server.");
        }
    }
}
