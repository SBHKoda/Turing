package Client;

import javax.swing.*;
import java.util.concurrent.ArrayBlockingQueue;

public class NotifyReceiver extends Thread {
    private ArrayBlockingQueue<String> msgList;
    private String msg;
    private boolean flag = true;

    public NotifyReceiver(ArrayBlockingQueue<String> msgList){
        this.msgList = msgList;
    }
    @Override
    public void run(){
        while(flag){
            try {
                msg = msgList.take();
                JOptionPane.showMessageDialog(null,"Inviti ricevuti ai documenti: " + '\n' + msg, "INVITO RICEVUTO ONLINE: ", JOptionPane.INFORMATION_MESSAGE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public void stopReceiver(){
        this.flag = false;
    }
}
