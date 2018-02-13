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

                //Si le serveur reçoit une connexion, il la ferme et écrit le message de succès
                client = server.accept();
                System.out.println("Someone connected!");
                client.close();
                client = null;

            } catch (IOException e) {
                e.printStackTrace();
                client = null;
            }

        }
    }
}
