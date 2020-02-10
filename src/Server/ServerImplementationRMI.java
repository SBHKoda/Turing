package Server;

import Client.NotifyInterfaceRMI;

import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerImplementationRMI extends RemoteServer implements ServerInterfaceRMI {
    private ConcurrentHashMap<String, Utente> listaUtenti;

    protected ServerImplementationRMI(ConcurrentHashMap<String, Utente> listaUtenti){
        this.listaUtenti = listaUtenti;
    }

    @Override
    public boolean registration(String username, String password) throws RemoteException {
        //Controllo la validita` delle stringhe username e password
        if(username == null || password == null){
            System.out.println("ERRORE, USERNAME o PASSWORD non valido");
            return false;
        }
        if(username.equals("") || password.equals("")){
            System.out.println("ERRORE, i campi USERNAME e PASSWORD non possono essere vuoti");
            return false;
        }
        //Controllo se username fa gia` parte della lista utenti
        if(listaUtenti.containsKey(username)){
            System.out.println("ERRORE, l'utente con username: " + username + " e` gia registrato");
            return false;
        }
        //Se arrivo qui username e password sono validi e non utilizzati, quindi creo un nuovo utente e lo aggiungo alla
        // lista degli utenti

        Utente utente = new Utente(username, password);
        listaUtenti.put(username, utente);
        System.out.println("User: "+ username +"  Password: " + password + " --- Correttamente registrato");
        return true;
    }

    @Override
    public void registerForCallback(NotifyInterfaceRMI clientInterface, String username) throws RemoteException {
        if (listaUtenti.containsKey(username)){
            listaUtenti.get(username).setClient(clientInterface);
            listaUtenti.get(username).setRegisteredForCallback();
            System.out.println("Nuovo utente : " + username + " Registrato al servizio notifiche." );
        }
    }
}
