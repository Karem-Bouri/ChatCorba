package server;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

public class MainServer {

    public static void main(String[] args) {
        try {
            // 1. Initialiser l'ORB
            ORB orb = ORB.init(args, null);

            // 2. Obtenir une référence à POA Root
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();

            // 3. Créer l'objet implémentant l'interface CORBA (charge aussi les salons persistants)
            ChatControlImpl chatImpl = new ChatControlImpl();

            // 4. Obtenir la référence de l'objet
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(chatImpl);

            // 5. Utiliser le service de nommage
            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);

            // 6. Binder l'objet au service de nommage sous le nom "ChatControl"
            String name = "ChatControl";
            NameComponent path[] = ncRef.to_name(name);
            ncRef.rebind(path, ref);

            System.out.println("Serveur CORBA 'ChatControl' prêt et en attente...");

            // 7. Attendre les appels des clients
            orb.run();

        } catch (Exception e) {
            System.err.println("Erreur Serveur CORBA: " + e);
            e.printStackTrace();
        }
    }
}