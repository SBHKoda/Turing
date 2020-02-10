package Client;

import java.awt.*;
import java.io.IOException;

public class ClientMain {
    public static void main(String[] args){
        ClientUI window = null;
        try {
            window = new ClientUI();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(window != null){
            window.getContentPane().setBackground(Color.lightGray);
            window.setLocation(150, 150);
            window.setVisible(true);
        }
    }
}
