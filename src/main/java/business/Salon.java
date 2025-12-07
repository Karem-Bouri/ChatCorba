package business;

import data.DBManager;
import server.ChatControlImpl;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class Salon {
    private final String nom;
    private final int port;
    private ServerSocket serverSocket;

    private final Set<PrintWriter> clientWriters = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> currentUsers = Collections.synchronizedSet(new HashSet<>());
    private final ChatControlImpl controller;

    public Salon(String nom, int port, ChatControlImpl controller) {
        this.nom = nom;
        this.port = port;
        this.controller = controller;
    }

    public int getPort() {
        return port;
    }

    public void addWriter(PrintWriter writer) {
        clientWriters.add(writer);
    }

    public void removeWriter(PrintWriter writer) {
        clientWriters.remove(writer);
    }

    public void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    new ClientHandler(clientSocket, this).start();
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("Erreur du serveur de salon " + nom + ": " + e.getMessage());
                }
            }
        }).start();
    }

    public void addUser(String user) {
        boolean newUser = currentUsers.add(user);
        if (newUser) {
            broadcastMessage("[System]: " + user + " a rejoint le salon.", true);
        }
    }

    public void removeUser(String user) {
        currentUsers.remove(user);

        broadcastMessage("[System]: " + user + " a quitté le salon.", true);

        // Logique d'arrêt si le salon est vide
        if (currentUsers.isEmpty()) {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close(); // Ferme le socket TCP
                    controller.notifySalonClosed(nom); // Informe le contrôleur CORBA
                }
            } catch (IOException e) {
                // Gestion des exceptions
            }
        }
    }

    // Archive et diffuse le message à tous les clients connectés via Socket
    public void broadcastMessage(String message, boolean isSystemMessage) {

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM HH:mm:ss");
        String timestamp = dateFormat.format(new Date());

        String pseudo = "System";
        String contenu = message;

        if (!isSystemMessage) {
            if (message.contains(": ")) {
                String[] parts = message.split(": ", 2);
                if (parts.length == 2) {
                    pseudo = parts[0];
                    contenu = parts[1];
                }
            }
        }

        String fullFormattedMessage = "[" + timestamp + "] " + pseudo + ": " + contenu;

        // 1. LOGIQUE D'ARCHIVAGE
        if (!isSystemMessage) {
            try {
                DBManager.saveMessage(this.nom, pseudo, contenu);
            } catch (SQLException e) {
                System.err.println("ERREUR DB - Échec de l'archivage du message : " + e.getMessage());
            }
        }

        // 2. LOGIQUE DE DIFFUSION (VERS SOCKETS TCP)
        synchronized (clientWriters) {
            for (PrintWriter writer : clientWriters) {
                writer.println(fullFormattedMessage);
            }
        }
    }

    public void broadcastMessage(String rawMessage) {
        broadcastMessage(rawMessage, false);
    }
}