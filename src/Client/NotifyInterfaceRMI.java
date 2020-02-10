package Client;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotifyInterfaceRMI extends Remote {
    void notifyEvent(String msg)throws RemoteException;
}
