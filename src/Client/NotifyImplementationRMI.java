package Client;

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.concurrent.ArrayBlockingQueue;

public class NotifyImplementationRMI extends RemoteObject implements  NotifyInterfaceRMI {
    private ArrayBlockingQueue<String> msgList;

    public NotifyImplementationRMI(ArrayBlockingQueue<String> msgList) throws RemoteException{
        super();
        this.msgList = msgList;
    }

    @Override
    public void notifyEvent(String msg) throws RemoteException {
        this.msgList.add(msg);
    }
}
