import com.fazecast.jSerialComm.*;
import java.io.*;
import java.net.*;

public class Main {

    private static int serverPort = 6666;
    private static ServerSocket server;
    private static Socket client;

    public static void main( String[] args) {

        //  Initialisation du socket pour les clients
        try {
            server = new ServerSocket(serverPort);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        while(client == null) {
            System.out.println("Waiting for a connection");

            //  Écoute de connexion des clients
            try {

                //Si le serveur reçoit une connexion, écouter la conversation
                client = server.accept();
                System.out.println("Connection established");

                BufferedReader inputBuffer = new BufferedReader(new InputStreamReader(client.getInputStream()));
                System.out.println("Read buffer initalized");

                while(client != null) {

                    //Dans l'éventualité que le client ferme le serveur textuellement
                    if(inputBuffer.readLine().equals("close")) {
                        System.out.println("Close signal recieved");
                        client.close();
                        client = null;
                    }

                    System.out.println("Waiting for an input");
                    System.out.println(inputBuffer.readLine());

                }

                //Si la connection a été fermée, marquer l'objet pour le garbage collector
                client = null;

            } catch (IOException e) {
                e.printStackTrace();
                client = null;
            }

        }
    }
}
