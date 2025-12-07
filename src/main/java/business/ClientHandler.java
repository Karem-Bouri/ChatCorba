package business;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

class ClientHandler extends Thread {
    private final Socket clientSocket;
    private final Salon parentSalon;
    private PrintWriter writer;

    public ClientHandler(Socket socket, Salon salon) {
        this.clientSocket = socket;
        this.parentSalon = salon;
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter tempWriter = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            this.writer = tempWriter;
            parentSalon.addWriter(this.writer);

            String clientMessage;
            while ((clientMessage = reader.readLine()) != null) {
                parentSalon.broadcastMessage(clientMessage);
            }

        } catch (IOException e) {
            // Le client s'est déconnecté
        } finally {
            if (writer != null) {
                parentSalon.removeWriter(writer);
            }
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}