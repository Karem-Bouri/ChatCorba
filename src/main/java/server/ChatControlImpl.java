package server;

import ChatSystem.ChatControlPOA;
import data.DBManager;
import business.Salon;

import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatControlImpl extends ChatControlPOA {

    // Stocke les instances de Salon actives (ceux qui ont au moins un utilisateur)
    private final ConcurrentHashMap<String, business.Salon> activeSalons = new ConcurrentHashMap<>();

    public ChatControlImpl() {
        loadPersistentSalons();
    }

    // Charge les salons existants en DB et démarre leur Socket TCP
    private void loadPersistentSalons() {
        String sql = "SELECT nom_salon, port FROM Salons";

        try (Connection conn = DBManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String nom = rs.getString("nom_salon");
                int port = rs.getInt("port");

                Salon salon = new Salon(nom, port, this);
                salon.startServer(); // Démarrage initial du Socket TCP
                activeSalons.put(nom, salon);
                System.out.println("Salon chargé et démarré : " + nom + " (Port " + port + ")");
            }
        } catch (SQLException e) {
            System.err.println("Erreur lors du chargement des salons persistants: " + e.getMessage());
        }
    }

    @Override
    public boolean authentifier(String pseudo, String password) {
        try {
            return DBManager.checkUserCredentials(pseudo, password);
        } catch (SQLException e) {
            System.err.println("Erreur d'authentification: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean inscrire(String pseudo, String password) {
        try {
            return DBManager.registerNewUser(pseudo, password);
        } catch (SQLException e) {
            System.err.println("Erreur d'inscription: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void creerSalon(String nomSalon, String utilisateur) {
        int newPort = 5000;

        try (Connection conn = DBManager.getConnection()) {

            // Déterminer un nouveau port (trouver le MAX + 1)
            try (Statement stmtMax = conn.createStatement();
                 ResultSet rs = stmtMax.executeQuery("SELECT MAX(port) AS max_port FROM Salons")) {
                if (rs.next()) {
                    int maxPort = rs.getInt("max_port");
                    newPort = Math.max(5000, maxPort) + 1;
                }
            }

            // Insertion DB
            try (PreparedStatement stmtInsert = conn.prepareStatement("INSERT INTO Salons (nom_salon, port) VALUES (?, ?)")) {
                stmtInsert.setString(1, nomSalon);
                stmtInsert.setInt(2, newPort);
                stmtInsert.executeUpdate();
            }

            // Création de l'objet Salon et démarrage du Socket
            Salon newSalon = new Salon(nomSalon, newPort, this);
            newSalon.startServer();
            activeSalons.put(nomSalon, newSalon);

        } catch (SQLException e) {
            System.err.println("Erreur lors de la création du salon en DB: " + e.getMessage());
        }
    }

    @Override
    public String rejoindreSalon(String nomSalon, String utilisateur) {

        if (activeSalons.containsKey(nomSalon)) {
            Salon salon = activeSalons.get(nomSalon);
            salon.addUser(utilisateur);
            return "192.168.100.11:" + salon.getPort();
        }

        else {
            // Salon inactif (recherche en DB et activation)
            String sql = "SELECT port FROM Salons WHERE nom_salon = ?";

            try (Connection conn = DBManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, nomSalon);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int port = rs.getInt("port");

                        Salon newSalon = new Salon(nomSalon, port, this);
                        newSalon.startServer(); // DÉMARRE LE SERVEUR SOCKET
                        activeSalons.put(nomSalon, newSalon);
                        newSalon.addUser(utilisateur);
                        return "127.0.0.1:" + newSalon.getPort();
                    }
                }
            } catch (SQLException e) {
                System.err.println("Erreur SQL lors de la recherche/activation du salon: " + e.getMessage());
            }
        }

        return "ERROR: Salon introuvable";
    }

    @Override
    public String listerSalons() {
        StringBuilder sb = new StringBuilder("Salons disponibles:\n");
        String sql = "SELECT nom_salon FROM Salons";

        try (Connection conn = DBManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                sb.append("- ").append(rs.getString("nom_salon")).append("\n");
            }
        } catch (SQLException e) {
            System.err.println("Erreur de listage des salons: " + e.getMessage());
            return "ERROR: Impossible de lister les salons.";
        }
        return sb.toString();
    }

    @Override
    public void quitterSalon(String nomSalon, String utilisateur) {
        if (activeSalons.containsKey(nomSalon)) {
            activeSalons.get(nomSalon).removeUser(utilisateur);
        }
    }

    @Override
    public String getSalonHistory(String nomSalon) {
        try {
            return DBManager.getSalonHistory(nomSalon);
        } catch (SQLException e) {
            System.err.println("Erreur CORBA lors de la récupération de l'historique pour " + nomSalon + ": " + e.getMessage());
            return "--- ERREUR SERVEUR lors du chargement de l'historique. ---";
        }
    }

    /** Callback appelé par Salon lorsque le salon devient vide et doit être retiré. */
    public void notifySalonClosed(String nomSalon) {
        activeSalons.remove(nomSalon);
        System.out.println("INFO CONTROL: Salon '" + nomSalon + "' retiré de la mémoire car vide.");
    }
}