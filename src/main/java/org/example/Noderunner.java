package org.example;
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Noderunner {
    private static final int PORT = 8080;
    private static final String localhost = "127.0.0.1";


    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
        System.out.println("Sender(1) or Receiver(2):");
        int choice = scan.nextInt();

        if (choice == 1) {
            startSender();
        } else {
            startReceiver();
        }
    }

    private static void startReceiver(){
        System.out.println("Receiver starting...waiting for connection on port 8080");
        try (ServerSocket serverSocket = new ServerSocket(PORT);
             Socket clientSocket = serverSocket.accept(); // This line blocks until Node A connects
             BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

            System.out.println("Connection established with: " + clientSocket.getInetAddress());

            // Read the message sent by Node A
            String message = in.readLine();
            System.out.println("Received message: " + message);

        } catch (IOException e) {
            System.err.println("Receiver Error: " + e.getMessage());
        }
    }
    private static void startSender() {
        System.out.println("Sender starting...!");
        try (Socket socket = new Socket(localhost, PORT);
        PrintWriter out= new PrintWriter(socket.getOutputStream(),true)){
            System.out.println("Connected to Receiver");
            String testMessage="FTransfer success";
            out.println(testMessage);
            System.out.println("Message sent");

        }
        catch(IOException e){
            System.err.println("Sender error...make sure the receiver is active");
        }
    }
}

