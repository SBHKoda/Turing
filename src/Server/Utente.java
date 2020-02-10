package Server;

import Client.NotifyInterfaceRMI;

import java.util.concurrent.CopyOnWriteArrayList;

public class Utente {
    private String username;
    private String password;
    private boolean isOnline;
    private boolean isRegisteredForCallback = false;

    private NotifyInterfaceRMI client;

    private CopyOnWriteArrayList<String> listaDocumentiAutorizzati;
    private CopyOnWriteArrayList<String> invitiOffline;

    public Utente(String username, String password) {
        this.username = username;
        this.password = password;

        //Appena creato un utente e` nello stato offline
        this.isOnline = false;

        this.listaDocumentiAutorizzati = new CopyOnWriteArrayList<>();
        this.invitiOffline = new CopyOnWriteArrayList<>();
    }

    public boolean checkPassword(String password){
        return this.password.equals(password);
    }
    public boolean checkOnlineStatus(){
        return this.isOnline;
    }
    public void setOnline(){
        this.isOnline = true;
    }
    public void setOffline(){
        this.isOnline = false;
    }

    public void addDocumentoAutorizzato(String docName){
        listaDocumentiAutorizzati.add(docName);
    }
    public void addInvitoOffline(String docName){
        this.invitiOffline.add(docName);
    }
    public CopyOnWriteArrayList<String> getListaDoc(){
        return this.listaDocumentiAutorizzati;
    }
    public CopyOnWriteArrayList<String> getInvitiOffline(){
        return this.invitiOffline;
    }
    public void resetInvitiOffline(){
        this.invitiOffline.clear();
    }
    public NotifyInterfaceRMI getClientCallback(){
        return client;
    }
    public void setClient(NotifyInterfaceRMI client){
        this.client = client;
    }
    public boolean getRegisteredForCallback(){
        return this.isRegisteredForCallback;
    }
    public void setRegisteredForCallback(){
        this.isRegisteredForCallback = true;
    }
}
