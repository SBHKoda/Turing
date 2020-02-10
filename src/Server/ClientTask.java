package Server;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientTask implements Runnable {
    private Socket clientSocket;
    private BufferedReader ricevoDalClient;
    private DataOutputStream invioAlClient;
    private SocketChannel clientSocketChannel = null;
    private ServerSocketChannel serverSocketChannel = null;

    private String username = "";
    private String docName = "";
    private int numSezione;

    public ClientTask(Socket clientSocket) {
        this.clientSocket = clientSocket;
        try {
            this.ricevoDalClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.invioAlClient = new DataOutputStream(clientSocket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.numSezione = -1;
    }
    //Task che gestisce le richeste da parte del client che gli viene assegnato. Legge il comando ricevuto, legge
    //eventualmente i vari input in base al comando ricevuto, invocando un metodo per eseguire la richiesta e inoltra il
    //risultato al client
    @Override
    public void run() {
        boolean flag = true;
        while(flag){
            String password, nomeTDoc;
            int comandoRicevuto;
            try {
                comandoRicevuto = ricevoDalClient.read();
                //--------------------------------------    LOGIN   --------------------------------------
                if (comandoRicevuto == 0) {
                    this.username = ricevoDalClient.readLine();
                    password = ricevoDalClient.readLine();
                    System.out.println("-----   Comando LOGIN ricevuto  -----");
                    System.out.println("-----   Username : " + username);
                    System.out.println("-----   Password : " + password);
                    int risultato = ServerMain.login(username, password);
                    System.out.println("Ricevuto risultato : " + risultato);
                    //Invio il messaggio di risposta al client
                    invioAlClient.write(risultato);
                    if(ServerMain.getRegisterForCallbackStatus(username))
                        invioAlClient.write(1);
                    if(!ServerMain.getRegisterForCallbackStatus(username))
                        invioAlClient.write(0);

                    if (risultato == 0) {
                        ///Ottengo gli inviti ricevuti mentre l'utente era offline e li invio al client
                        System.out.println("Utente " + username + " CONNESSO");
                        CopyOnWriteArrayList<String> listaInviti = ServerMain.getListaInvitiOffline(username);
                        System.out.println("Dimensione lista inviti offline : " + listaInviti.size());
                        invioAlClient.write(listaInviti.size());
                        if (listaInviti.size() == 0) {
                            invioAlClient.writeBytes("Nessun invito ricevuto"+ '\n');
                        } else {
                            for (String s : listaInviti)
                                invioAlClient.writeBytes(s + '\n');
                            ServerMain.resetInvitiOffLine(username);
                        }
                        //Vengono creati i channel in caso non esistessero
                        if (serverSocketChannel == null)
                            creaServerSocketChannel();
                        if (clientSocketChannel == null)
                            clientSocketChannel = accettaServerSocketChannel();
                    }
                }
                //--------------------------------------    LOGOUT   --------------------------------------
                if (comandoRicevuto == 1){
                    this.username = ricevoDalClient.readLine();
                    System.out.println("-----   Comando LOGOUT ricevuto  -----");
                    System.out.println("-----   Username : " + username);
                    boolean tmp = ServerMain.logout(username);
                    int risultato = -1;
                    System.out.println("Ricevuto risultato : " + tmp);
                    if(tmp){
                        System.out.println("Utente " + username + " DISCONNESSO");
                        risultato = 0;
                        this.username = "";
                    }
                    invioAlClient.write(risultato);
                    //Chiudo tutto dato che ho terminato
                    invioAlClient.close();
                    ricevoDalClient.close();
                    serverSocketChannel.close();
                    clientSocket.close();
                    clientSocketChannel.close();
                    flag = false;
                }
                //----------------------------------------  CREATE DOCUMENT ----------------------------------------
                if(comandoRicevuto == 2) {
                    username = ricevoDalClient.readLine();
                    nomeTDoc = ricevoDalClient.readLine();
                    int sezioniTot = ricevoDalClient.read();
                    System.out.println("-----   Comando CREATE DOCUMENT ricevuto  -----");
                    System.out.println("-----   Username : " + username);
                    System.out.println("-----   Nome Documento : " + nomeTDoc);
                    System.out.println("-----   Numero sezioni : " + sezioniTot);

                    int risultato = ServerMain.creaDocumento(username, nomeTDoc, sezioniTot);
                    System.out.println("Ricevuto risultato : " + risultato);
                    invioAlClient.write(risultato);
                }
                //----------------------------------------  EDIT DOCUMENT ----------------------------------------
                if(comandoRicevuto == 3){
                    username = ricevoDalClient.readLine();
                    nomeTDoc = ricevoDalClient.readLine();
                    int numeroSezione = ricevoDalClient.read();
                    System.out.println("-----   Comando EDIT DOCUMENT ricevuto  -----");
                    System.out.println("-----   Username : " + username);
                    System.out.println("-----   Nome Documento : " + nomeTDoc);
                    System.out.println("-----   Numero sezione : " + numeroSezione);
                    int risultato = ServerMain.editTDoc(username, nomeTDoc, numeroSezione, clientSocketChannel);
                    System.out.println("Ricevuto risultato : " + risultato);
                    if(risultato == 0){
                        invioAlClient.write(risultato);
                        String tmp = ServerMain.getDocAddress(nomeTDoc);
                        System.out.println(tmp);
                        invioAlClient.writeBytes(tmp + '\n');
                        docName = nomeTDoc;
                        numSezione = numeroSezione;
                        clientSocketChannel = null;
                        clientSocketChannel = accettaServerSocketChannel();
                    }
                    else invioAlClient.write(risultato);
                }
                //---------------------------------------- END EDIT DOCUMENT ----------------------------------------
                if(comandoRicevuto == 4){
                    nomeTDoc = ricevoDalClient.readLine();
                    int numeroSezione = ricevoDalClient.read();
                    System.out.println("-----   Comando END EDIT DOCUMENT ricevuto  -----");
                    System.out.println("-----   Nome Documento : " + nomeTDoc);
                    System.out.println("-----   Numero sezione : " + numeroSezione);
                    int risultato = ServerMain.endEdit(nomeTDoc, numeroSezione, clientSocketChannel);
                    System.out.println("Ricevuto risultato : " + risultato);
                    this.numSezione = -1;
                    this.docName = "";
                    invioAlClient.write(risultato);

                    clientSocketChannel = null;
                    clientSocketChannel = accettaServerSocketChannel();
                }
                //----------------------------------------  DOCUMENT LIST   ----------------------------------------
                if(comandoRicevuto == 5) {
                    username = ricevoDalClient.readLine();
                    CopyOnWriteArrayList<String> listaRisultato = ServerMain.getDocumentList(username);
                    System.out.println("-----   Comando DOCUMENT LIST ricevuto  -----");
                    System.out.println("-----   Username : " + username);
                    invioAlClient.write(listaRisultato.size());
                    if (listaRisultato.size() == 0) {
                        invioAlClient.writeBytes("Nessun documento autorizzato alla modifica");
                    } else {
                        for (String risultato : listaRisultato)
                            invioAlClient.writeBytes(risultato + '\n');
                    }
                }
                //----------------------------------------  SHOW(S, D)   ----------------------------------------
                if(comandoRicevuto == 6){
                    username = ricevoDalClient.readLine();
                    nomeTDoc = ricevoDalClient.readLine();
                    int numeroSezione = ricevoDalClient.read();
                    System.out.println("-----   Comando SHOW(S, D) ricevuto  -----");
                    System.out.println("-----   Username : " + username);
                    System.out.println("-----   Nome Documento : " + nomeTDoc);
                    System.out.println("-----   Numero sezione : " + numeroSezione);
                    int risultato = ServerMain.show(username, nomeTDoc, numeroSezione, clientSocketChannel);
                    System.out.println("Ricevuto risultato : " + risultato);
                    invioAlClient.write(risultato);

                    clientSocketChannel = null;
                    clientSocketChannel = accettaServerSocketChannel();
                }
                //----------------------------------------  SHOW(D)   ----------------------------------------
                if(comandoRicevuto == 7){
                    username = ricevoDalClient.readLine();
                    nomeTDoc = ricevoDalClient.readLine();
                    System.out.println("-----   Comando SHOW(D) ricevuto  -----");
                    System.out.println("-----   Username : " + username);
                    System.out.println("-----   Nome Documento : " + nomeTDoc);

                    int risultato = ServerMain.show(username, nomeTDoc, clientSocketChannel);
                    System.out.println("Ricevuto risultato : " + risultato);
                    invioAlClient.write(risultato);

                    clientSocketChannel = null;
                    clientSocketChannel = accettaServerSocketChannel();
                }
                //----------------------------------------  INVITE   ----------------------------------------
                if(comandoRicevuto == 8){
                    username = ricevoDalClient.readLine();
                    String invitato = ricevoDalClient.readLine();
                    nomeTDoc = ricevoDalClient.readLine();
                    System.out.println("-----   Comando INVITE ricevuto  -----");
                    System.out.println("-----   Username : " + username);
                    System.out.println("-----   Username invitato : " + invitato);
                    System.out.println("-----   Nome Documento : " + nomeTDoc);

                    int risultato = ServerMain.inviteUser(invitato, username, nomeTDoc);
                    System.out.println("Ricevuto risultato : " + risultato);
                    invioAlClient.write(risultato);
                }
                //----------------------------------------  CHIUSURA FORZATA   ----------------------------------------
                if(comandoRicevuto == 9){
                    this.username = ricevoDalClient.readLine();
                    System.out.println("-----   Chiusura forzata ricevuta  -----");
                    System.out.println("-----   Username : " + username);
                    ServerMain.chiusuraForzata(username);

                    //Chiudo tutto dato che ho terminato
                    invioAlClient.close();
                    ricevoDalClient.close();
                    serverSocketChannel.close();
                    clientSocket.close();
                    clientSocketChannel.close();
                    System.out.println("-----  Utente : " + username + " DISCONNESSO");
                    flag = false;
                }
            }catch (Exception e) {
                try {
                    //Nel caso venisse lanciata una qualunque eccezione chiudo tutto e nel caso rilascio la lock della
                    // sezione, e termino il ciclo
                    clientSocket.close();
                    if(clientSocketChannel != null)
                       clientSocketChannel.close();
                    if(serverSocketChannel != null)
                        serverSocketChannel.close();
                    if(numSezione != -1)
                        ServerMain.unlock(docName, numSezione);
                    flag = false;
                }catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private SocketChannel accettaServerSocketChannel() throws IOException {
        SocketChannel clientSocketChannel;
        clientSocketChannel = serverSocketChannel.accept();
        return clientSocketChannel;
    }

    private void creaServerSocketChannel() throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        int port = username.hashCode() % 65535;
        if(port < 0)
            port = -port % 65535;
        if(port < 1024)
            port += 1024;
        serverSocketChannel.socket().bind(new InetSocketAddress(port));
    }
}
