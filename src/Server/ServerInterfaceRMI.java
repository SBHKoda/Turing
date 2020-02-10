package Server;

import java.rmi.Remote;
import java.rmi.RemoteException;

import Client.NotifyInterfaceRMI;

public interface ServerInterfaceRMI extends Remote {
    //Registrazione al server TURING
    boolean registration(String username, String password)throws RemoteException;
    //Registrazione ala callback per le notifiche online
    void registerForCallback(NotifyInterfaceRMI ClientInterface, String username) throws RemoteException;
}
